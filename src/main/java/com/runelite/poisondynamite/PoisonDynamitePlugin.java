package com.runelite.poisondynamite;

import com.google.inject.Provides;
import java.time.Duration;
import java.time.Instant;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.HitsplatID;
import net.runelite.api.Hitsplat;
import net.runelite.api.InventoryID;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.KeyCode;
import net.runelite.api.MenuAction;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.MenuEntryAdded;
import net.runelite.api.events.MenuOptionClicked;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
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
	static final int POISON_TICK_MILLIS = 18200;
	private static final int IMMUNITY_THRESHOLD = 8;
	private static final int RESULT_DISPLAY_MILLIS = 3000;

	enum State
	{
		IDLE,
		AWAITING_DETONATION,
		AWAITING_POISON
	}

	@Inject
	private Client client;

	@Inject
	private PoisonDynamiteConfig config;

	@Inject
	private OverlayManager overlayManager;

	@Inject
	private ItemManager itemManager;

	@Inject
	private PoisonDynamiteOverlay overlay;

	@Inject
	private PoisonDynamiteNpcOverlay npcOverlay;

	@Inject
	private NpcStatsManager npcStatsManager;

	@Getter
	private State state = State.IDLE;

	@Getter
	private NPC trackedNpc;

	@Getter
	private String trackedNpcName;

	@Getter
	private int trackedNpcId = -1;

	@Getter
	private Instant countdownStart;

	@Getter
	private Instant resultTime;

	@Getter
	private boolean poisonSuccess;

	@Getter
	private boolean poisonFailed;

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
		overlayManager.add(npcOverlay);
		loadImmuneNpcs();
	}

	@Override
	protected void shutDown()
	{
		overlayManager.remove(overlay);
		overlayManager.remove(npcOverlay);
		clearTrackedNpc();
		npcFailCounts.clear();
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
			client.createMenuEntry(-1)
				.setOption("Track with Poison Dynamite")
				.setTarget(menuEntry.getTarget())
				.setType(MenuAction.RUNELITE)
				.setIdentifier(npc.getIndex())
				.onClick(e -> trackNpc(npc));
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

		if (immuneNpcIds.contains(npc.getId()))
		{
			String name = npc.getName() != null ? npc.getName() : "NPC " + npc.getId();
			client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
				"Warning: " + name + " may be immune to poison.", null);
		}

		trackNpc(npc);
		countdownStart = null;
		resultTime = null;
		poisonSuccess = false;
		poisonFailed = false;
		state = State.AWAITING_DETONATION;
		log.debug("Dynamite(p) used on NPC: {} (id={})", npc.getName(), npc.getId());
	}

	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (trackedNpc == null || event.getActor() != trackedNpc)
		{
			return;
		}

		Hitsplat hitsplat = event.getHitsplat();
		int type = hitsplat.getHitsplatType();

		switch (state)
		{
			case AWAITING_DETONATION:
				if (hitsplat.isMine() && hitsplat.getAmount() > 0)
				{
					log.debug("Detonation on {}, starting poison countdown", trackedNpcName);
					state = State.AWAITING_POISON;
					countdownStart = Instant.now();
				}
				break;

			case AWAITING_POISON:
				if (type == HitsplatID.POISON && !poisonSuccess && !poisonFailed)
				{
					log.debug("Poison proc on {}! Damage: {}", trackedNpcName, hitsplat.getAmount());
					poisonSuccess = true;
					resultTime = Instant.now();
					npcFailCounts.remove(trackedNpcId);
				}
				break;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (state != State.AWAITING_POISON)
		{
			return;
		}

		if (poisonSuccess || poisonFailed)
		{
			if (resultTime != null
				&& Duration.between(resultTime, Instant.now()).toMillis() >= RESULT_DISPLAY_MILLIS)
			{
				state = State.IDLE;
				countdownStart = null;
				resultTime = null;
				poisonSuccess = false;
				poisonFailed = false;
			}
		}
		else if (getRemainingMillis() <= 0)
		{
			log.debug("Poison timer expired on {}", trackedNpcName);
			poisonFailed = true;
			resultTime = Instant.now();

			if (trackedNpcId != -1)
			{
				int fails = npcFailCounts.merge(trackedNpcId, 1, Integer::sum);
				if (fails >= IMMUNITY_THRESHOLD && !immuneNpcIds.contains(trackedNpcId))
				{
					immuneNpcIds.add(trackedNpcId);
					saveImmuneNpcs();
					String name = trackedNpcName != null ? trackedNpcName : "NPC " + trackedNpcId;
					client.addChatMessage(ChatMessageType.GAMEMESSAGE, "",
						name + " flagged as likely immune to poison ("
							+ fails + " consecutive failures).", null);
				}
			}
		}
	}

	private void trackNpc(NPC npc)
	{
		trackedNpc = npc;
		trackedNpcName = npc.getName() != null ? npc.getName() : "Unknown";
		trackedNpcId = npc.getId();
		npcStatsManager.getStats(trackedNpcName, trackedNpcId);
	}

	void clearTrackedNpc()
	{
		trackedNpc = null;
		trackedNpcName = null;
		trackedNpcId = -1;
		state = State.IDLE;
		countdownStart = null;
		resultTime = null;
		poisonSuccess = false;
		poisonFailed = false;
	}

	long getRemainingMillis()
	{
		if (countdownStart == null)
		{
			return POISON_TICK_MILLIS;
		}
		return POISON_TICK_MILLIS - Duration.between(countdownStart, Instant.now()).toMillis();
	}

	double getCountdownProgress()
	{
		if (countdownStart == null)
		{
			return 0.0;
		}
		long elapsed = Duration.between(countdownStart, Instant.now()).toMillis();
		return Math.min(1.0, (double) elapsed / POISON_TICK_MILLIS);
	}

	String getAttackStyle()
	{
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment == null)
		{
			return "crush";
		}

		Item[] items = equipment.getItems();
		if (items == null)
		{
			return "crush";
		}

		int weaponIdx = EquipmentInventorySlot.WEAPON.getSlotIdx();
		if (weaponIdx >= items.length)
		{
			return "crush";
		}

		Item weapon = items[weaponIdx];
		if (weapon == null || weapon.getId() == -1)
		{
			return "crush";
		}

		ItemStats stats = itemManager.getItemStats(weapon.getId());
		if (stats == null || !stats.isEquipable())
		{
			return "crush";
		}

		ItemEquipmentStats eq = stats.getEquipment();
		int stab = eq.getAstab();
		int slash = eq.getAslash();
		int crush = eq.getAcrush();
		int magic = eq.getAmagic();
		int range = eq.getArange();

		if (range > 0 && range >= stab && range >= slash && range >= crush && range >= magic)
		{
			return "ranged";
		}
		if (magic > 0 && magic >= stab && magic >= slash && magic >= crush)
		{
			return "magic";
		}
		if (slash >= stab && slash >= crush)
		{
			return "slash";
		}
		if (stab >= crush)
		{
			return "stab";
		}
		return "crush";
	}

	int getEquipmentAttackBonus()
	{
		String style = getAttackStyle();
		ItemContainer equipment = client.getItemContainer(InventoryID.EQUIPMENT);
		if (equipment == null)
		{
			return 0;
		}

		Item[] items = equipment.getItems();
		if (items == null)
		{
			return 0;
		}

		int total = 0;
		for (Item item : items)
		{
			if (item == null || item.getId() == -1)
			{
				continue;
			}
			ItemStats stats = itemManager.getItemStats(item.getId());
			if (stats == null || !stats.isEquipable())
			{
				continue;
			}
			ItemEquipmentStats eq = stats.getEquipment();
			switch (style)
			{
				case "stab":
					total += eq.getAstab();
					break;
				case "slash":
					total += eq.getAslash();
					break;
				case "crush":
					total += eq.getAcrush();
					break;
				case "magic":
					total += eq.getAmagic();
					break;
				case "ranged":
					total += eq.getArange();
					break;
			}
		}
		return total;
	}

	int getEffectiveAttackLevel()
	{
		String style = getAttackStyle();
		Skill skill;
		switch (style)
		{
			case "ranged":
				skill = Skill.RANGED;
				break;
			case "magic":
				skill = Skill.MAGIC;
				break;
			default:
				skill = Skill.ATTACK;
				break;
		}
		int visibleLevel = client.getBoostedSkillLevel(skill);
		return HitChanceCalculator.getEffectiveLevel(visibleLevel, 1.0, 0);
	}

	int getFiremakingLevel()
	{
		return client.getBoostedSkillLevel(Skill.FIREMAKING);
	}

	NpcStatsManager getNpcStatsManager()
	{
		return npcStatsManager;
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
