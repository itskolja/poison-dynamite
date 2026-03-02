package com.runelite.poisondynamite;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Notification;

@ConfigGroup(PoisonDynamiteConfig.GROUP)
public interface PoisonDynamiteConfig extends Config
{
	String GROUP = "poisondynamite";
	String IMMUNE_NPCS_KEY = "immuneNpcs";

	@ConfigItem(
		keyName = "showInfobox",
		name = "Show infobox timer",
		description = "Show a countdown infobox after dynamite detonation.",
		position = 1
	)
	default boolean showInfobox()
	{
		return true;
	}

	@ConfigItem(
		keyName = "notifyOnFailure",
		name = "Notify on failure",
		description = "Send a notification when poison fails to proc.",
		position = 2
	)
	default Notification notifyOnFailure()
	{
		return Notification.ON;
	}

	@ConfigItem(
		keyName = "showStats",
		name = "Show stats overlay",
		description = "Show session success rate and damage overlay.",
		position = 3
	)
	default boolean showStats()
	{
		return true;
	}

	@ConfigItem(
		keyName = "trackDamage",
		name = "Track poison damage",
		description = "Track cumulative poison damage dealt.",
		position = 4
	)
	default boolean trackDamage()
	{
		return true;
	}

	@ConfigItem(
		keyName = "warnImmunity",
		name = "Warn if NPC immune",
		description = "Show a chat warning when targeting an NPC that appears immune to poison.",
		position = 5
	)
	default boolean warnImmunity()
	{
		return true;
	}

	@ConfigItem(
		keyName = IMMUNE_NPCS_KEY,
		name = "Immune NPCs",
		description = "Comma-separated NPC IDs learned as immune. Clear to reset.",
		position = 6
	)
	default String immuneNpcs()
	{
		return "";
	}

	@ConfigItem(
		keyName = IMMUNE_NPCS_KEY,
		name = "",
		description = "",
		hidden = true
	)
	void setImmuneNpcs(String npcs);
}
