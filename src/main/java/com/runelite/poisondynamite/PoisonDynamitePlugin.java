package com.runelite.poisondynamite;

import com.google.inject.Provides;
import java.awt.image.BufferedImage;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Actor;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.HitsplatID;
import net.runelite.api.Hitsplat;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.Notifier;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.infobox.InfoBoxManager;

@PluginDescriptor(
	name = "Poison Dynamite",
	description = "Tracks Dynamite(p) poison proc chance, countdown, and success rate",
	tags = {"poison", "dynamite", "timer", "skiller", "combat"}
)
@Slf4j
public class PoisonDynamitePlugin extends Plugin
{
	private static final int POISON_TICK_MILLIS = 18200;
	private static final int IMMUNITY_THRESHOLD = 8;

	private enum State
	{
		IDLE,
		AWAITING_DETONATION,
		AWAITING_POISON,
		TRACKING_POISON
	}

	@Inject
	private Client client;

	@Inject
	private ClientThread clientThread;

	@Inject
	private PoisonDynamiteConfig config;

	@Inject
	private Notifier notifier;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private InfoBoxManager infoBoxManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private PoisonDynamiteOverlay overlay;

	private State state = State.IDLE;
	private NPC trackedNpc;
	private PoisonDynamiteInfobox infobox;

	@Getter
	private int attempts;
	@Getter
	private int successes;
	@Getter
	private int totalPoisonDamage;

	private final Map<Integer, Integer> npcFailCounts = new HashMap<>();
	private Set<Integer> immuneNpcIds = new HashSet<>();

	@Provides
	PoisonDynamiteConfig getConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PoisonDynamiteConfig.class);
	}

	@Override
	protected void startUp()
	{
		overlayManager.add(overlay);
		loadImmuneNpcs();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		removeInfobox();
		resetState();
	}

	@Subscribe
	public void onMenuOptionClicked(MenuOptionClicked event)
	{
		if (event.getMenuAction() != MenuAction.WIDGET_TARGET_ON_NPC)
		{
			return;
		}

		// Check if the selected widget item is Dynamite(p)
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

		// Warn if NPC is flagged as immune
		if (config.warnImmunity() && immuneNpcIds.contains(npc.getId()))
		{
			String name = npc.getName() != null ? npc.getName() : "NPC";
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"Warning: " + name + " may be immune to poison.", null);
		}

		trackedNpc = npc;
		state = State.AWAITING_DETONATION;
		log.debug("Dynamite(p) used on NPC: {} (id={})", npc.getName(), npc.getId());
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		Actor target = event.getActor();
		Hitsplat hitsplat = event.getHitsplat();

		if (trackedNpc == null || target != trackedNpc)
		{
			return;
		}

		int type = hitsplat.getHitsplatType();

		switch (state)
		{
			case AWAITING_DETONATION:
				// Any damage hitsplat on the tracked NPC after using dynamite means detonation
				if (hitsplat.isMine() && hitsplat.getAmount() > 0)
				{
					log.debug("Detonation hitsplat on {}, starting poison countdown", trackedNpc.getName());
					state = State.AWAITING_POISON;
					attempts++;
					showInfobox();
				}
				break;

			case AWAITING_POISON:
				if (type == HitsplatID.POISON)
				{
					log.debug("Poison proc on {}! Damage: {}", trackedNpc.getName(), hitsplat.getAmount());
					state = State.TRACKING_POISON;
					successes++;
					if (config.trackDamage())
					{
						totalPoisonDamage += hitsplat.getAmount();
					}
					// Reset fail count for this NPC on success
					npcFailCounts.remove(trackedNpc.getId());
					if (infobox != null)
					{
						infobox.markSuccess();
					}
				}
				break;

			case TRACKING_POISON:
				if (type == HitsplatID.POISON && config.trackDamage())
				{
					totalPoisonDamage += hitsplat.getAmount();
					log.debug("Ongoing poison damage on {}: {} (total: {})",
						trackedNpc.getName(), hitsplat.getAmount(), totalPoisonDamage);
				}
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (state != State.AWAITING_POISON || infobox == null)
		{
			return;
		}

		if (infobox.isExpired())
		{
			log.debug("Poison timer expired without proc on {}", trackedNpc.getName());
			infobox.markFailed();
			notifier.notify(config.notifyOnFailure(), "Poison dynamite failed to proc!");

			// Track consecutive failures for immunity learning
			if (trackedNpc != null)
			{
				int npcId = trackedNpc.getId();
				int fails = npcFailCounts.merge(npcId, 1, Integer::sum);
				if (fails >= IMMUNITY_THRESHOLD && !immuneNpcIds.contains(npcId))
				{
					immuneNpcIds.add(npcId);
					saveImmuneNpcs();
					String name = trackedNpc.getName() != null ? trackedNpc.getName() : "NPC " + npcId;
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
						name + " flagged as likely immune to poison (" + fails + " consecutive failures).", null);
				}
			}

			state = State.IDLE;
		}
	}

	String getTrackedNpcName()
	{
		if (trackedNpc != null && trackedNpc.getName() != null)
		{
			return trackedNpc.getName();
		}
		return "Unknown";
	}

	void resetStats()
	{
		attempts = 0;
		successes = 0;
		totalPoisonDamage = 0;
		npcFailCounts.clear();
	}

	private void showInfobox()
	{
		if (!config.showInfobox())
		{
			return;
		}

		removeInfobox();

		BufferedImage image = itemManager.getImage(ItemID.LOVAKENGJ_DYNAMITE_POISON);
		infobox = new PoisonDynamiteInfobox(image, this, POISON_TICK_MILLIS);
		infoBoxManager.addInfoBox(infobox);
	}

	private void removeInfobox()
	{
		if (infobox != null)
		{
			infoBoxManager.removeInfoBox(infobox);
			infobox = null;
		}
	}

	private void resetState()
	{
		state = State.IDLE;
		trackedNpc = null;
		removeInfobox();
	}

	private void loadImmuneNpcs()
	{
		immuneNpcIds.clear();
		String saved = config.immuneNpcs();
		if (saved != null && !saved.isEmpty())
		{
			try
			{
				immuneNpcIds = Arrays.stream(saved.split(","))
					.map(String::trim)
					.filter(s -> !s.isEmpty())
					.map(Integer::parseInt)
					.collect(Collectors.toCollection(HashSet::new));
			}
			catch (NumberFormatException e)
			{
				log.warn("Failed to parse immune NPC IDs: {}", saved, e);
			}
		}
	}

	private void saveImmuneNpcs()
	{
		String value = immuneNpcIds.stream()
			.map(String::valueOf)
			.collect(Collectors.joining(","));
		config.setImmuneNpcs(value);
	}
}
