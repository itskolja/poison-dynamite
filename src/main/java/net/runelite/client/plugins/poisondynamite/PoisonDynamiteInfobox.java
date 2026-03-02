package net.runelite.client.plugins.poisondynamite;

import java.awt.Color;
import java.awt.image.BufferedImage;
import java.time.Duration;
import java.time.Instant;
import net.runelite.client.ui.overlay.infobox.InfoBox;

class PoisonDynamiteInfobox extends InfoBox
{
	private static final Color COLOR_WAITING = Color.WHITE;
	private static final Color COLOR_WARNING = Color.ORANGE;
	private static final Color COLOR_FAILED = Color.RED;
	private static final Color COLOR_SUCCESS = new Color(0, 200, 0);

	private final PoisonDynamitePlugin plugin;
	private final Instant startTime;
	private final long durationMillis;
	private Instant cullTime;
	private boolean success;
	private boolean failed;

	PoisonDynamiteInfobox(BufferedImage image, PoisonDynamitePlugin plugin, long durationMillis)
	{
		super(image, plugin);
		this.plugin = plugin;
		this.startTime = Instant.now();
		this.durationMillis = durationMillis;
	}

	@Override
	public String getText()
	{
		if (success)
		{
			return "OK";
		}
		if (failed)
		{
			return "FAIL";
		}

		long remaining = getRemainingMillis();
		if (remaining <= 0)
		{
			return "0s";
		}
		int seconds = (int) (remaining / 1000L);
		return seconds + "s";
	}

	@Override
	public Color getTextColor()
	{
		if (success)
		{
			return COLOR_SUCCESS;
		}
		if (failed)
		{
			return COLOR_FAILED;
		}

		long remaining = getRemainingMillis();
		if (remaining < 5000)
		{
			return COLOR_WARNING;
		}
		return COLOR_WAITING;
	}

	@Override
	public String getTooltip()
	{
		String npcName = plugin.getTrackedNpcName();
		String status;
		if (success)
		{
			status = "Poison applied!";
		}
		else if (failed)
		{
			status = "Poison failed";
		}
		else
		{
			status = "Awaiting poison proc...";
		}

		int attempts = plugin.getAttempts();
		int successes = plugin.getSuccesses();
		int rate = attempts > 0 ? (successes * 100 / attempts) : 0;
		int totalDmg = plugin.getTotalPoisonDamage();

		return "<html>"
			+ "Target: " + npcName + "<br>"
			+ "Status: " + status + "<br>"
			+ "Session: " + successes + "/" + attempts + " (" + rate + "%)<br>"
			+ "Poison damage: " + totalDmg
			+ "</html>";
	}

	@Override
	public boolean cull()
	{
		if (cullTime != null)
		{
			return Instant.now().isAfter(cullTime);
		}
		return false;
	}

	void markSuccess()
	{
		success = true;
		cullTime = Instant.now().plusSeconds(3);
	}

	void markFailed()
	{
		failed = true;
		cullTime = Instant.now().plusSeconds(3);
	}

	boolean isExpired()
	{
		return getRemainingMillis() <= 0 && !success && !failed;
	}

	private long getRemainingMillis()
	{
		return durationMillis - Duration.between(startTime, Instant.now()).toMillis();
	}
}
