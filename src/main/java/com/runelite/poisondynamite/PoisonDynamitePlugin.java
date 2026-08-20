package com.runelite.poisondynamite;

import com.google.inject.Provides;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.HitsplatID;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.WorldView;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
	name = "Poison Dynamite",
	description = "Hit chance calculator and countdown timer for Dynamite(p)",
	tags = {"poison", "dynamite", "timer", "skiller", "combat"}
)
@Slf4j
public class PoisonDynamitePlugin extends Plugin
{
	static final int GAME_TICK_MILLIS = 600;
	private static final int RESULT_DISPLAY_MILLIS = 3000;
	// give the detonation hitsplat a few ticks to land before giving up
	private static final int DETONATION_TIMEOUT_TICKS = 10;

	@Inject
	private Client client;

	@Inject
	private PoisonDynamiteConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private Notifier notifier;

	@Inject
	private PoisonDynamiteOverlay overlay;

	@Inject
	private PoisonDynamiteNpcOverlay npcOverlay;

	@Inject
	private NpcStatsManager npcStatsManager;

	private final Map<NPC, PoisonAttempt> attempts = new HashMap<>();

	@Getter
	private String trackedNpcName;

	@Getter
	private int trackedNpcId = -1;

	@Getter
	private int sessionAttempts;

	@Getter
	private int sessionProcs;

	private Set<Integer> trackedNpcIds = new HashSet<>();

	@Provides
	PoisonDynamiteConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PoisonDynamiteConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		overlayManager.add(npcOverlay);
		loadTrackedNpcs();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(npcOverlay);
		reset();
	}

	@Subscribe
	public void onMenuEntryAdded(MenuEntryAdded event)
	{
		var menuEntry = event.getMenuEntry();
		NPC npc = menuEntry.getNpc();

		if (npc == null)
		{
			return;
		}

		if (menuEntry.getType() == MenuAction.EXAMINE_NPC
			&& client.isKeyPressed(KeyCode.KC_SHIFT))
		{
			String option = trackedNpcIds.contains(npc.getId())
				? "Hide from Poison Dynamite"
				: "Track with Poison Dynamite";

			client.createMenuEntry(-1)
				.setOption(option)
				.setTarget(menuEntry.getTarget())
				.setType(MenuAction.RUNELITE)
				.setWorldViewId(menuEntry.getWorldViewId())
				.setIdentifier(npc.getIndex())
				.onClick(this::onTrackMenuClicked);
		}
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getMenuAction() != MenuAction.WIDGET_TARGET_ON_NPC)
		{
			return;
		}

		var selectedWidget = client.getSelectedWidget();
		if (selectedWidget == null || selectedWidget.getItemId() != ItemID.LOVAKENGJ_DYNAMITE_POISON)
		{
			return;
		}

		NPC npc = event.getMenuEntry().getNpc();
		if (npc == null)
		{
			return;
		}

		trackNpc(npc);
		attempts.put(npc, new PoisonAttempt(npc));
		sessionAttempts++;
		log.debug("Dynamite(p) used on NPC: {} (id={}), awaiting detonation hit", npc.getName(), npc.getId());
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		PoisonAttempt attempt = attempts.get(event.getActor());
		if (attempt == null || attempt.isResolved())
		{
			return;
		}

		var hitsplat = event.getHitsplat();
		int amount = hitsplat.getAmount();

		if (hitsplat.getHitsplatType() == HitsplatID.POISON)
		{
			log.debug("Poison proc on {}! Damage: {}", attempt.npc.getName(), amount);
			attempt.resolve(true);
			sessionProcs++;
			if (config.notifyOnProc())
			{
				notifier.notify("Poison proc on " + attempt.npc.getName());
			}
			return;
		}

		// only our own hitsplat can be the detonation result
		if (attempt.awaitingDetonationHit && hitsplat.isMine())
		{
			if (amount <= 0)
			{
				log.debug("Dynamite missed on {} (hit 0)", attempt.npc.getName());
				attempt.detonationMiss = true;
				attempt.resolve(false);
			}
			else
			{
				attempt.startCountdown();
				log.debug("Dynamite hit on {} for {}, countdown started", attempt.npc.getName(), amount);
			}
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		Iterator<PoisonAttempt> it = attempts.values().iterator();
		while (it.hasNext())
		{
			PoisonAttempt attempt = it.next();
			if (attempt.isResolved())
			{
				if (attempt.resultTime != null
					&& Duration.between(attempt.resultTime, Instant.now()).toMillis() >= RESULT_DISPLAY_MILLIS)
				{
					it.remove();
				}
			}
			else if (attempt.awaitingDetonationHit)
			{
				if (++attempt.ticksAwaitingDetonation > DETONATION_TIMEOUT_TICKS)
				{
					log.debug("No detonation hitsplat on {}, abandoning attempt", attempt.npc.getName());
					it.remove();
				}
			}
			else if (--attempt.remainingTicks <= 0)
			{
				log.debug("Poison timer expired on {}", attempt.npc.getName());
				attempt.resolve(false);
			}
		}
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		attempts.remove(event.getNpc());
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		PoisonAttempt attempt = attempts.get(event.getActor());
		if (attempt != null && !attempt.isResolved())
		{
			attempt.resolve(false);
		}
	}

	Iterable<PoisonAttempt> getAttempts()
	{
		return attempts.values();
	}

	Set<Integer> getTrackedNpcIds()
	{
		return trackedNpcIds;
	}

	int getDynamiteCount()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		return inventory == null ? 0 : inventory.count(ItemID.LOVAKENGJ_DYNAMITE_POISON);
	}

	int getFiremakingLevel()
	{
		return client.getBoostedSkillLevel(Skill.FIREMAKING);
	}

	NpcStatsManager getNpcStatsManager()
	{
		return npcStatsManager;
	}

	void reset()
	{
		attempts.clear();
		trackedNpcName = null;
		trackedNpcId = -1;
		sessionAttempts = 0;
		sessionProcs = 0;
	}

	private void trackNpc(NPC npc)
	{
		trackedNpcName = npc.getName() != null ? npc.getName() : "Unknown";
		trackedNpcId = npc.getId();
		if (trackedNpcIds.add(trackedNpcId))
		{
			saveTrackedNpcs();
		}
		npcStatsManager.getStats(trackedNpcName, trackedNpcId);
	}

	private void onTrackMenuClicked(MenuEntry entry)
	{
		WorldView wv = client.getWorldView(entry.getWorldViewId());
		if (wv == null)
		{
			return;
		}

		NPC npc = wv.npcs().byIndex(entry.getIdentifier());
		if (npc == null)
		{
			return;
		}

		if (trackedNpcIds.contains(npc.getId()))
		{
			untrackNpc(npc);
		}
		else
		{
			trackNpc(npc);
		}
	}

	private void untrackNpc(NPC npc)
	{
		int id = npc.getId();
		trackedNpcIds.remove(id);
		saveTrackedNpcs();
		attempts.keySet().removeIf(n -> n.getId() == id);
		if (trackedNpcId == id)
		{
			trackedNpcName = null;
			trackedNpcId = -1;
		}
		log.debug("Untracked NPC: {} (id={})", npc.getName(), id);
	}

	private void loadTrackedNpcs()
	{
		trackedNpcIds = parseNpcIdSet(config.trackedNpcs());
	}

	private void saveTrackedNpcs()
	{
		config.setTrackedNpcs(serializeNpcIdSet(trackedNpcIds));
	}

	private Set<Integer> parseNpcIdSet(String saved)
	{
		if (saved == null || saved.isEmpty())
		{
			return new HashSet<>();
		}
		try
		{
			return Arrays.stream(saved.split(","))
				.map(String::trim)
				.filter(s -> !s.isEmpty())
				.map(Integer::parseInt)
				.collect(Collectors.toCollection(HashSet::new));
		}
		catch (NumberFormatException e)
		{
			log.warn("Failed to parse NPC IDs: {}", saved, e);
			return new HashSet<>();
		}
	}

	private static String serializeNpcIdSet(Set<Integer> ids)
	{
		return ids.stream()
			.map(String::valueOf)
			.collect(Collectors.joining(","));
	}
}
