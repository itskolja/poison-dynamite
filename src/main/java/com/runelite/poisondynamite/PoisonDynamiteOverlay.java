package com.runelite.poisondynamite;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import javax.inject.Inject;
import static net.runelite.api.MenuAction.RUNELITE_OVERLAY;
import static net.runelite.api.MenuAction.RUNELITE_OVERLAY_CONFIG;
import static net.runelite.client.ui.overlay.OverlayManager.OPTION_CONFIGURE;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

class PoisonDynamiteOverlay extends OverlayPanel
{
	private static final String RESET_OPTION = "Reset";
	private static final Color COLOR_HIGH = new Color(0, 200, 0);
	private static final Color COLOR_MEDIUM = Color.YELLOW;
	private static final Color COLOR_LOW = Color.RED;
	private static final int LOW_DYNAMITE_THRESHOLD = 10;

	private final PoisonDynamitePlugin plugin;
	private final PoisonDynamiteConfig config;
	private final CombatStyleResolver styleResolver;

	@Inject
	PoisonDynamiteOverlay(PoisonDynamitePlugin plugin, PoisonDynamiteConfig config,
		CombatStyleResolver styleResolver)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		this.styleResolver = styleResolver;
		setPosition(OverlayPosition.TOP_LEFT);
		addMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Poison Dynamite overlay");
		addMenuEntry(RUNELITE_OVERLAY, RESET_OPTION, "Poison Dynamite overlay",
			e -> plugin.reset());
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showInfoPanel())
		{
			return null;
		}

		String npcName = plugin.getTrackedNpcName();
		if (npcName == null)
		{
			return null;
		}

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Poison Dynamite")
			.build());

		// blank line between sections, but only where both sides have content
		boolean first = true;
		for (List<LineComponent> section : Arrays.asList(
			targetLines(npcName), poisonLines(), suppliesLines()))
		{
			if (section.isEmpty())
			{
				continue;
			}
			if (!first)
			{
				panelComponent.getChildren().add(LineComponent.builder().build());
			}
			first = false;
			panelComponent.getChildren().addAll(section);
		}

		return super.render(graphics);
	}

	private List<LineComponent> targetLines(String npcName)
	{
		List<LineComponent> lines = new ArrayList<>();
		lines.add(LineComponent.builder()
			.left("Target:")
			.right(npcName)
			.build());

		if (!config.showTargetStats())
		{
			return lines;
		}

		NpcStatsManager npcStatsManager = plugin.getNpcStatsManager();
		NpcStatsManager.NpcDefenceStats npcStats = npcStatsManager.getStats(
			npcName, plugin.getTrackedNpcId());

		if (npcStats == null)
		{
			boolean unavailable = npcStatsManager.isUnavailable(npcName, plugin.getTrackedNpcId());
			lines.add(LineComponent.builder()
				.left("Hit chance:")
				.right(unavailable ? "Unavailable" : "Loading...")
				.rightColor(unavailable ? COLOR_LOW : Color.WHITE)
				.build());
			return lines;
		}

		String style = styleResolver.getAttackStyle();
		int effectiveLevel = styleResolver.getEffectiveAttackLevel(style);
		int equipBonus = styleResolver.getEquipmentAttackBonus(style);
		int defenceDrain = plugin.getTrackedDefenceDrain();
		int npcDefLevel = Math.max(0, npcStats.defenceLevel - defenceDrain);
		int npcStyleDef = npcStats.getDefenceForStyle(style);

		lines.add(LineComponent.builder()
			.left("Def lvl:")
			.right(defenceDrain > 0
				? npcDefLevel + " / " + npcStats.defenceLevel
				: String.valueOf(npcDefLevel))
			.rightColor(defenceDrain > 0 ? COLOR_HIGH : Color.WHITE)
			.build());

		double hitChance = HitChanceCalculator.calculate(
			effectiveLevel, equipBonus, npcDefLevel, npcStyleDef);

		Color hitColor;
		if (hitChance > 0.5)
		{
			hitColor = COLOR_HIGH;
		}
		else if (hitChance > 0.25)
		{
			hitColor = COLOR_MEDIUM;
		}
		else
		{
			hitColor = COLOR_LOW;
		}

		lines.add(LineComponent.builder()
			.left("Hit chance:")
			.right(String.format("%.1f%%", hitChance * 100))
			.rightColor(hitColor)
			.build());

		// poison chance is a fixed fraction of hit chance, so only immunity is worth a line
		if (npcStats.isPoisonImmune())
		{
			lines.add(LineComponent.builder()
				.left("Poison:")
				.right("Immune")
				.rightColor(COLOR_LOW)
				.build());
		}

		return lines;
	}

	private List<LineComponent> poisonLines()
	{
		List<LineComponent> lines = new ArrayList<>();
		NpcPoison poison = plugin.getTrackedPoison();
		if (!config.showPoisonTracker() || poison == null || !poison.isActive())
		{
			return lines;
		}

		Instant now = Instant.now();
		int hitpoints = poison.getHealthEstimate();

		if (hitpoints >= 0)
		{
			lines.add(LineComponent.builder()
				.left("Target HP:")
				.right("~" + hitpoints)
				.build());
		}

		lines.add(LineComponent.builder()
			.left("Next hit:")
			.right(poison.getNextDamage() + " in " + formatTime(poison.getMillisUntilNextHit(now)))
			.build());

		lines.add(LineComponent.builder()
			.left("Ends in:")
			.right(formatTime(poison.getMillisUntilEnd(now))
				+ " (" + poison.getRemainingHits() + " hits)")
			.build());

		lines.add(LineComponent.builder()
			.left("Dmg left:")
			.right(String.valueOf(poison.getRemainingDamage()))
			.build());

		int hitsUntilDeath = hitpoints <= 0 ? -1 : poison.getHitsUntilDeath(hitpoints);
		if (hitsUntilDeath >= 0)
		{
			lines.add(LineComponent.builder()
				.left("Dies in:")
				.right(formatTime(poison.getMillisUntilDeath(hitpoints, now))
					+ " (" + hitsUntilDeath + " hits)")
				.rightColor(COLOR_HIGH)
				.build());
		}

		return lines;
	}

	private List<LineComponent> suppliesLines()
	{
		List<LineComponent> lines = new ArrayList<>();

		if (config.showDynamiteCount())
		{
			int count = plugin.getDynamiteCount();
			Color countColor;
			if (count == 0)
			{
				countColor = COLOR_LOW;
			}
			else if (count <= LOW_DYNAMITE_THRESHOLD)
			{
				countColor = COLOR_MEDIUM;
			}
			else
			{
				countColor = Color.WHITE;
			}
			lines.add(LineComponent.builder()
				.left("Dynamite(p):")
				.right(String.valueOf(count))
				.rightColor(countColor)
				.build());
		}

		if (config.showSessionStats() && plugin.getSessionAttempts() > 0)
		{
			int attempts = plugin.getSessionAttempts();
			int procs = plugin.getSessionProcs();
			lines.add(LineComponent.builder()
				.left("Session:")
				.right(String.format("%d/%d (%.0f%%)", procs, attempts,
					100.0 * procs / attempts))
				.build());
		}

		return lines;
	}

	private static String formatTime(long millis)
	{
		long seconds = millis / 1000;
		return String.format("%d:%02d", seconds / 60, seconds % 60);
	}
}
