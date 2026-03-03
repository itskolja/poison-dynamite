package com.runelite.poisondynamite;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import javax.inject.Inject;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

class PoisonDynamiteNpcOverlay extends Overlay
{
	private static final Color COLOR_WAITING = Color.WHITE;
	private static final Color COLOR_WARNING = Color.ORANGE;
	private static final Color COLOR_SUCCESS = new Color(0, 200, 0);
	private static final Color COLOR_FAILED = Color.RED;
	private static final Color COLOR_BG = new Color(0, 0, 0, 128);

	private static final int RING_DIAMETER = 30;
	private static final float RING_STROKE = 3f;

	private final PoisonDynamitePlugin plugin;
	private final PoisonDynamiteConfig config;

	@Inject
	PoisonDynamiteNpcOverlay(PoisonDynamitePlugin plugin, PoisonDynamiteConfig config)
	{
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.UNDER_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showNpcOverlay())
		{
			return null;
		}

		if (plugin.getState() != PoisonDynamitePlugin.State.AWAITING_POISON)
		{
			return null;
		}

		NPC npc = plugin.getTrackedNpc();
		if (npc == null)
		{
			return null;
		}

		Point point = npc.getCanvasTextLocation(graphics, "", npc.getLogicalHeight() + 40);
		if (point == null)
		{
			return null;
		}

		int centerX = point.getX();
		int centerY = point.getY();
		int radius = RING_DIAMETER / 2;

		graphics.setRenderingHint(
			RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		// Background ring
		graphics.setColor(COLOR_BG);
		graphics.setStroke(new BasicStroke(RING_STROKE));
		graphics.drawOval(centerX - radius, centerY - radius, RING_DIAMETER, RING_DIAMETER);

		// Progress arc
		Color ringColor = getRingColor();
		double progress = plugin.getCountdownProgress();
		int arcAngle = (int) (progress * 360);

		graphics.setColor(ringColor);
		graphics.setStroke(new BasicStroke(RING_STROKE));
		graphics.drawArc(centerX - radius, centerY - radius,
			RING_DIAMETER, RING_DIAMETER, 90, -arcAngle);

		// Countdown text
		String text = getDisplayText();
		FontMetrics fm = graphics.getFontMetrics();
		int textWidth = fm.stringWidth(text);
		int textX = centerX - textWidth / 2;
		int textY = centerY + fm.getAscent() / 2 - 1;

		// Text shadow
		graphics.setColor(Color.BLACK);
		graphics.drawString(text, textX + 1, textY + 1);
		// Text foreground
		graphics.setColor(ringColor);
		graphics.drawString(text, textX, textY);

		return null;
	}

	private Color getRingColor()
	{
		if (plugin.isPoisonSuccess())
		{
			return COLOR_SUCCESS;
		}
		if (plugin.isPoisonFailed())
		{
			return COLOR_FAILED;
		}
		if (plugin.getRemainingMillis() < 5000)
		{
			return COLOR_WARNING;
		}
		return COLOR_WAITING;
	}

	private String getDisplayText()
	{
		if (plugin.isPoisonSuccess())
		{
			return "OK";
		}
		if (plugin.isDetonationMiss())
		{
			return "MISS";
		}
		if (plugin.isPoisonFailed())
		{
			return "X";
		}
		long remaining = plugin.getRemainingMillis();
		int seconds = (int) Math.max(0, remaining / 1000);
		return String.valueOf(seconds);
	}
}
