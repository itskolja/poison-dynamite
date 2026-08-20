package com.runelite.poisondynamite;

import java.time.Instant;
import net.runelite.api.NPC;

/**
 * Per-NPC state for one Dynamite(p) application: waiting for the detonation
 * hitsplat, counting down the poison cycle, then briefly showing the result.
 */
class PoisonAttempt
{
	static final int POISON_CYCLE_TICKS = 30;

	final NPC npc;
	boolean awaitingDetonationHit = true;
	int ticksAwaitingDetonation;
	int remainingTicks = -1;
	Instant resultTime;
	boolean poisonSuccess;
	boolean poisonFailed;
	boolean detonationMiss;

	PoisonAttempt(NPC npc)
	{
		this.npc = npc;
	}

	boolean isCountingDown()
	{
		return remainingTicks >= 0;
	}

	boolean isResolved()
	{
		return poisonSuccess || poisonFailed;
	}

	void startCountdown()
	{
		awaitingDetonationHit = false;
		remainingTicks = POISON_CYCLE_TICKS;
	}

	void resolve(boolean success)
	{
		poisonSuccess = success;
		poisonFailed = !success;
		awaitingDetonationHit = false;
		resultTime = Instant.now();
	}

	double getProgress()
	{
		if (!isCountingDown())
		{
			return 0.0;
		}
		return 1.0 - (double) remainingTicks / POISON_CYCLE_TICKS;
	}

	int getRemainingSeconds()
	{
		int ticks = isCountingDown() ? remainingTicks : POISON_CYCLE_TICKS;
		return (int) Math.ceil(ticks * PoisonDynamitePlugin.GAME_TICK_MILLIS / 1000.0);
	}
}
