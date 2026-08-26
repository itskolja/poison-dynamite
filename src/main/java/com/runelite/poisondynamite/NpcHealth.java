package com.runelite.poisondynamite;

/**
 * Recovers an NPC's hitpoints from the health bar ratio the server sends.
 *
 * The server computes {@code ratio = 1 + (scale - 1) * health / maxHealth} for a
 * living NPC, so inverting it gives a range of possible hitpoints; we take the
 * midpoint. The result is exact when maxHealth fits inside the scale.
 */
final class NpcHealth
{
	private NpcHealth()
	{
	}

	/**
	 * @return estimated current hitpoints, 0 if dead, or -1 when unknown
	 */
	static int estimate(int healthRatio, int healthScale, int maxHealth)
	{
		if (healthRatio < 0 || healthScale <= 0 || maxHealth <= 0)
		{
			return -1;
		}

		if (healthRatio == 0)
		{
			return 0;
		}

		int minHealth = 1;
		int maxPossible;
		if (healthScale > 1)
		{
			if (healthRatio > 1)
			{
				// health = 0 forces ratio = 0 on the server, so ratio = 1 carries no lower bound
				minHealth = (maxHealth * (healthRatio - 1) + healthScale - 2) / (healthScale - 1);
			}
			maxPossible = Math.min(maxHealth, (maxHealth * healthRatio - 1) / (healthScale - 1));
		}
		else
		{
			maxPossible = maxHealth;
		}

		return (minHealth + maxPossible + 1) / 2;
	}
}
