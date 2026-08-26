package com.runelite.poisondynamite;

import java.time.Duration;
import java.time.Instant;

/**
 * Models the poison ticking on a single NPC.
 *
 * NPCs have no poison varp to read, so the poison value is inferred from the
 * hitsplats we see: poison deals {@code ceil(value / 5)} damage every
 * {@link #POISON_TICK_MILLIS} and drops the value by one, and every source sets
 * the value to a multiple of five. So a splat of {@code d} damage means the value
 * was {@code 5d} for that hit whenever the damage differs from the previous
 * splat — the first splat we see, a step down as the poison decays, or a step up
 * when a stronger poison is applied. Equal damage just decrements. Any wrong
 * initial guess therefore corrects itself on the next step down.
 */
class NpcPoison
{
	static final int POISON_TICK_MILLIS = 18200;

	private int remainingHits;
	private int lastDamage;
	private Instant lastHit;
	private int healthEstimate = -1;

	void onPoisonHit(int damage, Instant now)
	{
		if (damage <= 0)
		{
			return;
		}

		if (!isActive() || damage != lastDamage)
		{
			remainingHits = damage * 5 - 1;
		}
		else
		{
			remainingHits--;
		}

		lastDamage = damage;
		lastHit = now;

		// keep the estimate fresh while the health bar is hidden
		if (healthEstimate > 0)
		{
			healthEstimate = Math.max(0, healthEstimate - damage);
		}
	}

	boolean isActive()
	{
		return remainingHits > 0;
	}

	int getNextDamage()
	{
		return damageAt(remainingHits);
	}

	int getRemainingHits()
	{
		return Math.max(0, remainingHits);
	}

	int getRemainingDamage()
	{
		return damageOverHits(getRemainingHits());
	}

	long getMillisUntilNextHit(Instant now)
	{
		if (!isActive() || lastHit == null)
		{
			return 0;
		}
		long elapsed = Duration.between(lastHit, now).toMillis();
		return Math.max(0, POISON_TICK_MILLIS - elapsed);
	}

	long getMillisUntilEnd(Instant now)
	{
		if (!isActive())
		{
			return 0;
		}
		return getMillisUntilNextHit(now) + (long) (remainingHits - 1) * POISON_TICK_MILLIS;
	}

	/**
	 * @return hits needed to finish an NPC on {@code hitpoints}, or -1 if the
	 * poison wears off first
	 */
	int getHitsUntilDeath(int hitpoints)
	{
		if (hitpoints <= 0)
		{
			return 0;
		}

		int dealt = 0;
		for (int hit = 1; hit <= remainingHits; hit++)
		{
			dealt += damageAt(remainingHits - hit + 1);
			if (dealt >= hitpoints)
			{
				return hit;
			}
		}
		return -1;
	}

	/**
	 * @return time until the poison finishes the NPC, or -1 if it never does
	 */
	long getMillisUntilDeath(int hitpoints, Instant now)
	{
		int hits = getHitsUntilDeath(hitpoints);
		if (hits < 0)
		{
			return -1;
		}
		if (hits == 0)
		{
			return 0;
		}
		return getMillisUntilNextHit(now) + (long) (hits - 1) * POISON_TICK_MILLIS;
	}

	void updateHealth(int healthRatio, int healthScale, int maxHealth)
	{
		int estimate = NpcHealth.estimate(healthRatio, healthScale, maxHealth);
		if (estimate >= 0)
		{
			healthEstimate = estimate;
		}
	}

	int getHealthEstimate()
	{
		return healthEstimate;
	}

	private static int damageAt(int poisonValue)
	{
		return poisonValue <= 0 ? 0 : (poisonValue + 4) / 5;
	}

	/**
	 * Total damage of a poison with {@code hits} hits left: five hits at each
	 * damage step as the value counts down to zero.
	 */
	private static int damageOverHits(int hits)
	{
		int steps = hits / 5;
		int remainder = hits % 5;
		return 5 * (steps * (steps + 1) / 2) + remainder * (steps + 1);
	}
}
