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
