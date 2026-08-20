package com.runelite.poisondynamite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup(PoisonDynamiteConfig.GROUP)
public interface PoisonDynamiteConfig extends Config
{
	String GROUP = "poisondynamite";
	String TRACKED_NPCS_KEY = "trackedNpcs";

	@ConfigItem(
		keyName = "showNpcOverlay",
		name = "Show NPC overlay",
		description = "Show a countdown ring above the tracked NPC.",
		position = 1
	)
	default boolean showNpcOverlay()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showInfoPanel",
		name = "Show info panel",
		description = "Show the info panel with target, hit chance, poison chance and max hit.",
		position = 2
	)
	default boolean showInfoPanel()
	{
		return true;
	}

	@ConfigItem(
		keyName = "highlightTrackedNpcs",
		name = "Highlight tracked NPCs",
		description = "Outline NPCs on the tracked list.",
		position = 3
	)
	default boolean highlightTrackedNpcs()
	{
		return false;
	}

	@ConfigItem(
		keyName = "showDynamiteCount",
		name = "Show dynamite count",
		description = "Show remaining Dynamite(p) in the info panel, warning when low.",
		position = 4
	)
	default boolean showDynamiteCount()
	{
		return true;
	}

	@ConfigItem(
		keyName = "showSessionStats",
		name = "Show session stats",
		description = "Show poison procs vs attempts for this session in the info panel.",
		position = 5
	)
	default boolean showSessionStats()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyOnProc",
		name = "Notify on poison proc",
		description = "Send a system notification when the poison procs.",
		position = 6
	)
	default boolean notifyOnProc()
	{
		return false;
	}

@ConfigItem(
		keyName = TRACKED_NPCS_KEY,
		name = "Tracked NPC IDs",
		description = "Auto-managed tracked NPC list.",
		hidden = true
	)
	default String trackedNpcs()
	{
		return "";
	}

	@ConfigItem(
		keyName = TRACKED_NPCS_KEY,
		name = "",
		description = "",
		hidden = true
	)
	void setTrackedNpcs(String npcs);
}
