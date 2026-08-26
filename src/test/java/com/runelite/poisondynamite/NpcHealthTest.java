package com.runelite.poisondynamite;

import org.junit.Test;
import static org.junit.Assert.assertEquals;

public class NpcHealthTest
{
	@Test
	public void estimateIsExactWhenMaxHealthFitsInScale()
	{
		// server: ratio = 1 + (scale - 1) * health / maxHealth = 1 + 29 * 7 / 10 = 21
		assertEquals(7, NpcHealth.estimate(21, 30, 10));
	}

	@Test
	public void estimateInterpolatesWhenMaxHealthExceedsScale()
	{
		// ratio = 1 + 29 * 128 / 255 = 15
		assertEquals(128, NpcHealth.estimate(15, 30, 255));
	}

	@Test
	public void estimateIsMaxHealthAtFullRatio()
	{
		assertEquals(100, NpcHealth.estimate(30, 30, 100));
	}

	@Test
	public void estimateIsZeroWhenRatioIsZero()
	{
		assertEquals(0, NpcHealth.estimate(0, 30, 255));
	}

	@Test
	public void estimateIsMidpointWhenScaleCarriesNoInformation()
	{
		// scale of 1 means ratio is always 1 while alive, so only the upper bound is known
		assertEquals(51, NpcHealth.estimate(1, 1, 100));
	}

	@Test
	public void estimateIsUnknownWithoutAHealthBar()
	{
		assertEquals(-1, NpcHealth.estimate(-1, 30, 255));
		assertEquals(-1, NpcHealth.estimate(15, 0, 255));
		assertEquals(-1, NpcHealth.estimate(15, 30, 0));
	}
}
