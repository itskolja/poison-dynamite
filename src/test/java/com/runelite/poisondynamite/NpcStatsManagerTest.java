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

	private static String parseApiJson(String wikitext)
	{
		return "{\"parse\":{\"title\":\"X\",\"wikitext\":{\"*\":" +
			new com.google.gson.Gson().toJson(wikitext) + "}}}";
	}

	@Test
	public void parsesUnversionedPoisonResistance()
	{
		String json = parseApiJson("{{Infobox Monster\n|id = 3010,3011\n|poisonous = No\n|poisonresistance = 100\n}}");
		assertEquals("100", NpcStatsManager.parsePoisonResistance(json, 3011));
	}

	@Test
	public void parsesVersionedPoisonResistanceMatchingId()
	{
		String json = parseApiJson("{{Infobox Monster\n|id1 = 100\n|poisonresistance1 = 0\n|id2 = 200,201\n|poisonresistance2 = poison\n}}");
		assertEquals("poison", NpcStatsManager.parsePoisonResistance(json, 201));
		assertEquals("0", NpcStatsManager.parsePoisonResistance(json, 100));
	}

	@Test
	public void fallsBackToFirstResistanceWhenIdUnmatched()
	{
		String json = parseApiJson("{{Infobox Monster\n|id1 = 100\n|poisonresistance1 = 200\n|id2 = 300\n|poisonresistance2 = 0\n}}");
		assertEquals("200", NpcStatsManager.parsePoisonResistance(json, 99999));
	}

	@Test
	public void returnsNullWhenResistanceMissing()
	{
		assertNull(NpcStatsManager.parsePoisonResistance(
			parseApiJson("{{Infobox Monster\n|id = 5\n|poisonous = No\n}}"), 5));
		assertNull(NpcStatsManager.parsePoisonResistance("{\"error\":{\"code\":\"missingtitle\"}}", 5));
	}

	@Test
	public void poisonImmunity()
	{
		NpcStatsManager.NpcDefenceStats base = new NpcStatsManager.NpcDefenceStats(1, 0, 0, 0, 0, 0);
		assertEquals(false, base.isPoisonImmune());
		assertEquals(false, base.withPoisonResistance("0").isPoisonImmune());
		assertEquals(true, base.withPoisonResistance("100").isPoisonImmune());
		assertEquals(true, base.withPoisonResistance("200").isPoisonImmune());
		assertEquals(true, base.withPoisonResistance("poison").isPoisonImmune());
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
