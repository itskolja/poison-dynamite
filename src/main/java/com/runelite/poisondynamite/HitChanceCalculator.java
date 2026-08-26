package com.runelite.poisondynamite;

final class HitChanceCalculator
{
	private HitChanceCalculator()
	{
	}

	static double calculate(int effectiveAttackLevel, int equipmentAttackBonus,
		int npcDefenceLevel, int npcStyleDefenceBonus)
	{
		int attackRoll = effectiveAttackLevel * (equipmentAttackBonus + 64);
		int defenceRoll = (npcDefenceLevel + 9) * (npcStyleDefenceBonus + 64);

		if (attackRoll > defenceRoll)
		{
			return 1.0 - (defenceRoll + 2.0) / (2.0 * attackRoll + 1.0);
		}
		else
		{
			return (double) attackRoll / (2.0 * defenceRoll + 1.0);
		}
	}

	static int getEffectiveLevel(int visibleLevel, double prayerMult, int stanceBonus)
	{
		return (int) Math.floor(visibleLevel * prayerMult) + stanceBonus + 8;
	}
}
