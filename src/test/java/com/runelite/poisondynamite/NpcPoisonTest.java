package com.runelite.poisondynamite;

import java.time.Instant;
import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class NpcPoisonTest
{
	private static final Instant T0 = Instant.ofEpochMilli(1_000_000);

	private static Instant plusMillis(long millis)
	{
		return T0.plusMillis(millis);
	}

	@Test
	public void newPoisonIsInactiveUntilTheFirstHit()
	{
		assertFalse(new NpcPoison().isActive());
	}

	@Test
	public void firstHitAnchorsPoisonValueFromItsDamage()
	{
		NpcPoison poison = new NpcPoison();
		poison.onPoisonHit(6, T0);

		assertTrue(poison.isActive());
		// damage 6 means a poison value of 30, of which this hit consumed one
		assertEquals(29, poison.getRemainingHits());
		assertEquals(6, poison.getNextDamage());
		// the full 30-hit poison deals 105, minus the 6 already dealt
		assertEquals(99, poison.getRemainingDamage());
	}

	@Test
	public void repeatedHitsOfEqualDamageDecrementRemainingHits()
	{
		NpcPoison poison = new NpcPoison();
		for (int i = 0; i < 5; i++)
		{
			poison.onPoisonHit(6, T0);
		}

		assertEquals(25, poison.getRemainingHits());
		assertEquals(5, poison.getNextDamage());
	}

	@Test
	public void damageSteppingDownReAnchorsAMisjudgedPoisonValue()
	{
		// joining mid-poison: the first splat we see is already down to 5
		NpcPoison poison = new NpcPoison();
		poison.onPoisonHit(5, T0);
		assertEquals(24, poison.getRemainingHits());

		// a step down to 4 means the value was exactly 20 for that hit
		poison.onPoisonHit(4, T0);
		assertEquals(19, poison.getRemainingHits());
	}

	@Test
	public void strongerPoisonReAnchorsUpward()
	{
		NpcPoison poison = new NpcPoison();
		poison.onPoisonHit(4, T0);
		poison.onPoisonHit(6, T0);

		assertEquals(29, poison.getRemainingHits());
	}

	@Test
	public void poisonWearsOffAfterItsFinalHit()
	{
		NpcPoison poison = new NpcPoison();
		poison.onPoisonHit(1, T0);
		assertEquals(4, poison.getRemainingHits());
		assertEquals(4, poison.getRemainingDamage());

		for (int i = 0; i < 4; i++)
		{
			poison.onPoisonHit(1, T0);
		}

		assertFalse(poison.isActive());
		assertEquals(0, poison.getRemainingHits());
		assertEquals(0, poison.getRemainingDamage());
	}

	@Test
	public void millisUntilNextHitCountsDownFromTheLastHit()
	{
		NpcPoison poison = new NpcPoison();
		poison.onPoisonHit(6, T0);

		assertEquals(NpcPoison.POISON_TICK_MILLIS, poison.getMillisUntilNextHit(T0));
		assertEquals(13_200, poison.getMillisUntilNextHit(plusMillis(5000)));
		assertEquals(0, poison.getMillisUntilNextHit(plusMillis(20_000)));
	}

	@Test
	public void millisUntilEndSpansEveryRemainingHit()
	{
		NpcPoison poison = new NpcPoison();
		poison.onPoisonHit(6, T0);

		assertEquals(29L * NpcPoison.POISON_TICK_MILLIS, poison.getMillisUntilEnd(T0));
	}

	@Test
	public void hitsUntilDeathAccumulatesTheDecayingDamage()
	{
		NpcPoison poison = new NpcPoison();
		poison.onPoisonHit(6, T0);

		// 6 + 6 + 6 + 6 = 24 finishes a 20 hitpoint NPC on the fourth hit
		assertEquals(4, poison.getHitsUntilDeath(20));
		assertEquals(4L * NpcPoison.POISON_TICK_MILLIS, poison.getMillisUntilDeath(20, T0));
	}

	@Test
	public void hitsUntilDeathIsUnknownWhenPoisonCannotFinishTheNpc()
	{
		NpcPoison poison = new NpcPoison();
		poison.onPoisonHit(6, T0);

		assertEquals(-1, poison.getHitsUntilDeath(200));
		assertEquals(-1, poison.getMillisUntilDeath(200, T0));
	}

	@Test
	public void healthEstimateFallsWithObservedPoisonDamage()
	{
		NpcPoison poison = new NpcPoison();
		poison.updateHealth(15, 30, 255);
		assertEquals(128, poison.getHealthEstimate());

		poison.onPoisonHit(6, T0);
		assertEquals(122, poison.getHealthEstimate());
	}

	@Test
	public void healthEstimateStaysUnknownWithoutAHealthBar()
	{
		NpcPoison poison = new NpcPoison();
		poison.updateHealth(-1, 30, 255);
		poison.onPoisonHit(6, T0);

		assertEquals(-1, poison.getHealthEstimate());
	}
}
