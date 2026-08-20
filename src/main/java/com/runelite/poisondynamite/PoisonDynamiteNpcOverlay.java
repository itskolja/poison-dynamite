package com.runelite.poisondynamite;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.api.WorldView;
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
	private static final Color COLOR_HIGHLIGHT = new Color(0, 200, 0, 120);

	private static final int RING_DIAMETER = 30;
	private static final float RING_STROKE = 3f;
	private static final int WARNING_SECONDS = 5;

	private final Client client;
	private final PoisonDynamitePlugin plugin;
	private final PoisonDynamiteConfig config;

	@Inject
	PoisonDynamiteNpcOverlay(Client client, PoisonDynamitePlugin plugin, PoisonDynamiteConfig config)
	{
		this.client = client;
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.DYNAMIC);
		setLayer(OverlayLayer.UNDER_WIDGETS);
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		graphics.setRenderingHint(
			RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);

		if (config.highlightTrackedNpcs())
		{
			renderTrackedHighlights(graphics);
		}

		if (config.showNpcOverlay())
		{
			for (PoisonAttempt attempt : plugin.getAttempts())
			{
				renderRing(graphics, attempt);
			}
		}

		return null;
	}

	private void renderTrackedHighlights(Graphics2D graphics)
	{
		WorldView wv = client.getTopLevelWorldView();
		if (wv == null)
		{
			return;
		}

		graphics.setColor(COLOR_HIGHLIGHT);
		graphics.setStroke(new BasicStroke(2f));
		for (NPC npc : wv.npcs())
		{
			if (npc == null || !plugin.getTrackedNpcIds().contains(npc.getId()))
			{
				continue;
			}
			Shape hull = npc.getConvexHull();
			if (hull != null)
			{
				graphics.draw(hull);
			}
		}
	}

	private void renderRing(Graphics2D graphics, PoisonAttempt attempt)
	{
		NPC npc = attempt.npc;
		Point point = npc.getCanvasTextLocation(graphics, "", npc.getLogicalHeight() + 40);
		if (point == null)
		{
			return;
		}

		int centerX = point.getX();
		int centerY = point.getY();
		int radius = RING_DIAMETER / 2;

		// Background ring
		graphics.setColor(COLOR_BG);
		graphics.setStroke(new BasicStroke(RING_STROKE));
		graphics.drawOval(centerX - radius, centerY - radius, RING_DIAMETER, RING_DIAMETER);

		// Progress arc
		Color ringColor = getRingColor(attempt);
		int arcAngle = (int) (attempt.getProgress() * 360);

		graphics.setColor(ringColor);
		graphics.setStroke(new BasicStroke(RING_STROKE));
		graphics.drawArc(centerX - radius, centerY - radius,
			RING_DIAMETER, RING_DIAMETER, 90, -arcAngle);

		// Countdown text
		String text = getDisplayText(attempt);
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
	}

	private static Color getRingColor(PoisonAttempt attempt)
	{
		if (attempt.poisonSuccess)
		{
			return COLOR_SUCCESS;
		}
		if (attempt.poisonFailed)
		{
			return COLOR_FAILED;
		}
		if (attempt.isCountingDown() && attempt.getRemainingSeconds() <= WARNING_SECONDS)
		{
			return COLOR_WARNING;
		}
		return COLOR_WAITING;
	}

	private static String getDisplayText(PoisonAttempt attempt)
	{
		if (attempt.poisonSuccess)
		{
			return "OK";
		}
		if (attempt.detonationMiss)
		{
			return "MISS";
		}
		if (attempt.poisonFailed)
		{
			return "X";
		}
		return String.valueOf(attempt.getRemainingSeconds());
	}
}
