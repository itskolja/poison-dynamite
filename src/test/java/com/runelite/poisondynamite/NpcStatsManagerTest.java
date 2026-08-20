package com.runelite.poisondynamite;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;

public class NpcStatsManagerTest
{
	// Real response shape from the wiki bucket API (2026-08): one row per
	// infobox version, scalar stat fields, "id" as an array of strings.
	private static final String GUARD_RESPONSE = "{\"bucketQuery\":\"...\",\"bucket\":[" +
		"{\"id\":[\"11924\"],\"defence_level\":14,\"stab_defence_bonus\":5," +
		"\"slash_defence_bonus\":6,\"crush_defence_bonus\":7," +
		"\"magic_defence_bonus\":8,\"range_defence_bonus\":9}," +
		"{\"id\":[\"3254\",\"3255\"],\"defence_level\":21,\"stab_defence_bonus\":1," +
		"\"slash_defence_bonus\":2,\"crush_defence_bonus\":3," +
		"\"magic_defence_bonus\":4,\"range_defence_bonus\":5}]}";

	@Test
	public void parsesRowMatchingNpcId()
	{
		NpcStatsManager.NpcDefenceStats stats = NpcStatsManager.parseResponse(GUARD_RESPONSE, 3255);
		assertEquals(21, stats.defenceLevel);
		assertEquals(1, stats.stabDef);
		assertEquals(5, stats.rangeDef);
	}

	@Test
	public void fallsBackToFirstRowWhenIdNotFound()
	{
		NpcStatsManager.NpcDefenceStats stats = NpcStatsManager.parseResponse(GUARD_RESPONSE, 99999);
		assertEquals(14, stats.defenceLevel);
		assertEquals(9, stats.rangeDef);
	}

	@Test
	public void returnsNullOnEmptyBucket()
	{
		assertNull(NpcStatsManager.parseResponse("{\"bucket\":[]}", 1));
	}

	@Test
	public void returnsNullWhenBucketKeyMissing()
	{
		assertNull(NpcStatsManager.parseResponse("{\"error\":\"nope\"}", 1));
	}

	@Test
	public void missingAndNullFieldsDefaultToZero()
	{
		NpcStatsManager.NpcDefenceStats stats = NpcStatsManager.parseResponse(
			"{\"bucket\":[{\"id\":[\"5\"],\"defence_level\":null}]}", 5);
		assertEquals(0, stats.defenceLevel);
		assertEquals(0, stats.slashDef);
	}

	@Test
	public void styleLookupPicksMatchingDefence()
	{
		NpcStatsManager.NpcDefenceStats stats = NpcStatsManager.parseResponse(GUARD_RESPONSE, 11924);
		assertEquals(5, stats.getDefenceForStyle("stab"));
		assertEquals(6, stats.getDefenceForStyle("slash"));
		assertEquals(7, stats.getDefenceForStyle("crush"));
		assertEquals(8, stats.getDefenceForStyle("magic"));
		assertEquals(9, stats.getDefenceForStyle("ranged"));
	}
}
