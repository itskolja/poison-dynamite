package com.runelite.poisondynamite;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PoisonDynamitePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PoisonDynamitePlugin.class);
		RuneLite.main(args);
	}
}
