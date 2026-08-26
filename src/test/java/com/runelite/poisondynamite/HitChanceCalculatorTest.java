package com.runelite.poisondynamite;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HitChanceCalculatorTest
{
	private static final double EPSILON = 1e-9;

	@Test
	public void calculateWhenAttackRollExceedsDefenceRoll()
	{
		// attackRoll = 50 * 64 = 3200, defenceRoll = 10 * 64 = 640
		double expected = 1.0 - 642.0 / 6401.0;
		assertEquals(expected, HitChanceCalculator.calculate(50, 0, 1, 0), EPSILON);
	}

	@Test
	public void calculateWhenDefenceRollExceedsAttackRoll()
	{
		// attackRoll = 10 * 64 = 640, defenceRoll = 109 * 164 = 17876
		double expected = 640.0 / 35753.0;
		assertEquals(expected, HitChanceCalculator.calculate(10, 0, 100, 100), EPSILON);
	}

	@Test
	public void calculateEqualRollsIsNearHalf()
	{
		// attackRoll == defenceRoll = 640
		double chance = HitChanceCalculator.calculate(10, 0, 1, 0);
		assertEquals(640.0 / 1281.0, chance, EPSILON);
		assertTrue(chance < 0.5);
	}

	@Test
	public void effectiveLevelFloorsPrayerBoostBeforeBonuses()
	{
		// floor(99 * 1.2) = 118, + 3 stance + 8 base
		assertEquals(129, HitChanceCalculator.getEffectiveLevel(99, 1.2, 3));
		// no prayer, no stance
		assertEquals(107, HitChanceCalculator.getEffectiveLevel(99, 1.0, 0));
	}

	@Test
	public void maxHitScalesWithFiremaking()
	{
		assertEquals(2, HitChanceCalculator.getMaxHit(1));
		assertEquals(2, HitChanceCalculator.getMaxHit(69));
		assertEquals(3, HitChanceCalculator.getMaxHit(70));
		assertEquals(3, HitChanceCalculator.getMaxHit(89));
		assertEquals(4, HitChanceCalculator.getMaxHit(90));
		assertEquals(4, HitChanceCalculator.getMaxHit(99));
	}
}
