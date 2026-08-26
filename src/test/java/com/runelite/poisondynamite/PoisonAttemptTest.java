package com.runelite.poisondynamite;

import org.junit.Test;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class PoisonAttemptTest
{
	@Test
	public void reclickWhileAwaitingDetonationRestartsTheDetonationWindow()
	{
		PoisonAttempt attempt = new PoisonAttempt(null);
		attempt.ticksAwaitingDetonation = 5;

		// the previous throw was cancelled; this click is the live one
		assertTrue(attempt.absorbReclick());
		assertEquals(0, attempt.ticksAwaitingDetonation);
		assertTrue(attempt.awaitingDetonationHit);
	}

	@Test
	public void reclickDuringCountdownKeepsTheRunningTimer()
	{
		PoisonAttempt attempt = new PoisonAttempt(null);
		attempt.startCountdown();
		attempt.remainingTicks = 20;

		assertTrue(attempt.absorbReclick());
		assertEquals(20, attempt.remainingTicks);
	}

	@Test
	public void reclickOnAResolvedAttemptIsNotAbsorbed()
	{
		PoisonAttempt attempt = new PoisonAttempt(null);
		attempt.resolve(false);

		assertFalse(attempt.absorbReclick());
	}
}
