package com.runelite.poisondynamite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(PoisonDynamiteConfig.GROUP)
public interface PoisonDynamiteConfig extends Config
{
	String GROUP = "poisondynamite";

	@ConfigSection(
		name = "Overlays",
		description = "Which overlays to draw.",
		position = 0
	)
	String overlaysSection = "overlays";

	@ConfigSection(
		name = "Info panel",
		description = "Which sections the info panel shows.",
		position = 1
	)
	String infoPanelSection = "infoPanel";

	@ConfigSection(
		name = "Notifications",
		description = "When to notify.",
		position = 2
	)
	String notificationsSection = "notifications";

	@ConfigItem(
		keyName = "showNpcOverlay",
		name = "NPC countdown ring",
		description = "Show a countdown ring above the tracked NPC.",
		position = 1,
		section = overlaysSection
	)
	default boolean showNpcOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showInfoPanel",
		name = "Info panel",
		description = "Show the info panel with target and hit chance.",
		position = 2,
		section = overlaysSection
	)
	default boolean showInfoPanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showTargetStats",
		name = "Target stats",
		description = "Show the target's defence level, hit chance and poison immunity.",
		position = 1,
		section = infoPanelSection
	)
	default boolean showTargetStats()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showPoisonTracker",
		name = "Poison tracker",
		description = "Show remaining poison damage, time until it wears off and time until it kills the target.",
		position = 2,
		section = infoPanelSection
	)
	default boolean showPoisonTracker()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showDynamiteCount",
		name = "Dynamite count",
		description = "Show remaining Dynamite(p) in the info panel, warning when low.",
		position = 3,
		section = infoPanelSection
	)
	default boolean showDynamiteCount()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSessionStats",
		name = "Session stats",
		description = "Show poison procs vs attempts for this session in the info panel.",
		position = 4,
		section = infoPanelSection
	)
	default boolean showSessionStats()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyOnProc",
		name = "Notify on poison proc",
		description = "Send a system notification when the poison procs.",
		position = 1,
		section = notificationsSection
	)
	default boolean notifyOnProc()
	{
		return false;
	}
}
