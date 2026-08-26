package com.runelite.poisondynamite;

import com.google.inject.Provides;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.Client;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import net.runelite.api.InventoryID;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.WorldView;
import net.runelite.api.events.ActorDeath;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.Notifier;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.NPCManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.specialcounter.SpecialCounterUpdate;
import net.runelite.client.plugins.specialcounter.SpecialWeapon;
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

	@Inject
	private NPCManager npcManager;

	private final Map<NPC, PoisonAttempt> attempts = new HashMap<>();

	// poison ticking on NPCs we have poisoned, kept until it wears off or they die
	private final Map<NPC, NpcPoison> poisons = new HashMap<>();

	// cumulative defence drained by special attacks, per NPC instance
	private final Map<NPC, Integer> defenceDrains = new HashMap<>();

	private NPC trackedNpc;

	@Getter
	private String trackedNpcName;

	@Getter
	private int trackedNpcId = -1;

	@Getter
	private int sessionAttempts;

	@Getter
	private int sessionProcs;

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
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(npcOverlay);
		reset();
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

		PoisonAttempt existing = attempts.get(npc);
		if (existing != null && existing.absorbReclick())
		{
			return;
		}

		attempts.put(npc, new PoisonAttempt(npc));
		sessionAttempts++;
		log.debug("Dynamite(p) used on NPC: {} (id={}), awaiting detonation hit", npc.getName(), npc.getId());
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor actor = event.getActor();
		Hitsplat hitsplat = event.getHitsplat();
		int amount = hitsplat.getAmount();
		PoisonAttempt attempt = attempts.get(actor);

		if (hitsplat.getHitsplatType() == HitsplatID.POISON)
		{
			trackPoisonHit(actor, attempt, amount);
		}

		if (attempt == null || attempt.isResolved())
		{
			return;
		}

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
			else if (isPoisonImmune(attempt.npc))
			{
				log.debug("Dynamite hit on {} for {}, but it is immune to poison",
					attempt.npc.getName(), amount);
				attempt.resolve(false);
			}
			else
			{
				attempt.startCountdown();
				log.debug("Dynamite hit on {} for {}, countdown started", attempt.npc.getName(), amount);
			}
		}
	}

	/**
	 * Feed a poison hitsplat into the model for an NPC we poisoned. The first
	 * splat of an attempt starts the track; later splats keep it anchored.
	 */
	private void trackPoisonHit(Actor actor, PoisonAttempt attempt, int amount)
	{
		if (!(actor instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) actor;
		NpcPoison poison = poisons.get(npc);
		if (poison == null)
		{
			if (attempt == null || attempt.isResolved())
			{
				// not a poison we applied
				return;
			}
			poison = new NpcPoison();
			poisons.put(npc, poison);
		}

		poison.onPoisonHit(amount, Instant.now());
		log.debug("Poison on {}: {} damage, {} hits left ({} damage)", npc.getName(), amount,
			poison.getRemainingHits(), poison.getRemainingDamage());
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		updatePoisons();

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
			else if (isPoisonImmune(attempt.npc))
			{
				// wiki immunity data can land after the countdown has started
				log.debug("{} is immune to poison, abandoning countdown", attempt.npc.getName());
				attempt.resolve(false);
			}
			else if (--attempt.remainingTicks <= 0)
			{
				log.debug("Poison timer expired on {}", attempt.npc.getName());
				attempt.resolve(false);
			}
		}
	}

	/**
	 * Refresh the hitpoints estimate from the health bar while it is visible and
	 * drop poisons that have worn off.
	 */
	private void updatePoisons()
	{
		Iterator<Map.Entry<NPC, NpcPoison>> it = poisons.entrySet().iterator();
		while (it.hasNext())
		{
			Map.Entry<NPC, NpcPoison> entry = it.next();
			NpcPoison poison = entry.getValue();
			if (!poison.isActive())
			{
				log.debug("Poison wore off on {}", entry.getKey().getName());
				it.remove();
				continue;
			}

			NPC npc = entry.getKey();
			Integer maxHealth = npcManager.getHealth(npc.getId());
			if (maxHealth != null)
			{
				poison.updateHealth(npc.getHealthRatio(), npc.getHealthScale(), maxHealth);
			}
		}
	}

	private boolean isPoisonImmune(NPC npc)
	{
		NpcStatsManager.NpcDefenceStats stats = npcStatsManager.getStats(npc.getName(), npc.getId());
		return stats != null && stats.isPoisonImmune();
	}

	@Subscribe
	public void onNpcDespawned(NpcDespawned event)
	{
		attempts.remove(event.getNpc());
		poisons.remove(event.getNpc());
		defenceDrains.remove(event.getNpc());
		if (event.getNpc() == trackedNpc)
		{
			trackedNpc = null;
		}
	}

	@Subscribe
	public void onSpecialCounterUpdate(SpecialCounterUpdate event)
	{
		if (event.getWorld() != client.getWorld())
		{
			return;
		}

		WorldView wv = client.getTopLevelWorldView();
		NPC npc = wv == null ? null : wv.npcs().byIndex(event.getNpcIndex());
		if (npc == null)
		{
			return;
		}

		NpcStatsManager.NpcDefenceStats stats = npcStatsManager.getStats(npc.getName(), npc.getId());
		if (stats == null)
		{
			return;
		}

		int current = Math.max(0, stats.defenceLevel - defenceDrains.getOrDefault(npc, 0));
		int drain = defenceDrain(event.getWeapon(), event.getHit(), current);
		if (drain > 0)
		{
			defenceDrains.merge(npc, drain, Integer::sum);
			log.debug("Defence drain on {}: -{} ({} -> {})", npc.getName(), drain, current, current - drain);
		}
	}

	private static int defenceDrain(SpecialWeapon weapon, int hit, int currentDefence)
	{
		if (hit <= 0)
		{
			return 0;
		}
		switch (weapon)
		{
			case DRAGON_WARHAMMER:
				return currentDefence * 30 / 100;
			case ELDER_MAUL:
				return currentDefence * 35 / 100;
			case BANDOS_GODSWORD:
			case BONE_DAGGER:
			case DORGESHUUN_CROSSBOW:
				return hit;
			case BARRELCHEST_ANCHOR:
				return hit / 10;
			case BULWARK:
			case TONALZTICS_OF_RALOS:
			case EMBERLIGHT:
				return currentDefence * 10 / 100;
			case ACCURSED_SCEPTRE:
				return currentDefence * 15 / 100;
			case ARCLIGHT:
			case DARKLIGHT:
				return currentDefence * 5 / 100;
			default:
				return 0;
		}
	}

	@Subscribe
	public void onActorDeath(ActorDeath event)
	{
		poisons.remove(event.getActor());

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

	NpcPoison getTrackedPoison()
	{
		return trackedNpc == null ? null : poisons.get(trackedNpc);
	}

	int getTrackedDefenceDrain()
	{
		return trackedNpc == null ? 0 : defenceDrains.getOrDefault(trackedNpc, 0);
	}

	int getDynamiteCount()
	{
		ItemContainer inventory = client.getItemContainer(InventoryID.INVENTORY);
		return inventory == null ? 0 : inventory.count(ItemID.LOVAKENGJ_DYNAMITE_POISON);
	}

	NpcStatsManager getNpcStatsManager()
	{
		return npcStatsManager;
	}

	void reset()
	{
		attempts.clear();
		poisons.clear();
		defenceDrains.clear();
		trackedNpc = null;
		trackedNpcName = null;
		trackedNpcId = -1;
		sessionAttempts = 0;
		sessionProcs = 0;
	}

	private void trackNpc(NPC npc)
	{
		trackedNpc = npc;
		trackedNpcName = npc.getName() != null ? npc.getName() : "Unknown";
		trackedNpcId = npc.getId();
		npcStatsManager.getStats(trackedNpcName, trackedNpcId);
	}
}
