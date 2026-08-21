package com.runelite.poisondynamite;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
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

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Target:")
			.right(npcName)
			.build());

		NpcStatsManager npcStatsManager = plugin.getNpcStatsManager();
		NpcStatsManager.NpcDefenceStats npcStats = npcStatsManager.getStats(
			npcName, plugin.getTrackedNpcId());

		if (npcStats == null)
		{
			boolean unavailable = npcStatsManager.isUnavailable(npcName, plugin.getTrackedNpcId());
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Hit chance:")
				.right(unavailable ? "Unavailable" : "Loading...")
				.rightColor(unavailable ? COLOR_LOW : Color.WHITE)
				.build());
		}
		else
		{
			String style = styleResolver.getAttackStyle();
			int effectiveLevel = styleResolver.getEffectiveAttackLevel(style);
			int equipBonus = styleResolver.getEquipmentAttackBonus(style);
			int npcDefLevel = npcStats.defenceLevel;
			int npcStyleDef = npcStats.getDefenceForStyle(style);

			double hitChance = HitChanceCalculator.calculate(
				effectiveLevel, equipBonus, npcDefLevel, npcStyleDef);
			double poisonChance = HitChanceCalculator.getPoisonChance(hitChance);
			int fmLevel = plugin.getFiremakingLevel();
			int maxHit = HitChanceCalculator.getMaxHit(fmLevel);

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

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Hit chance:")
				.right(String.format("%.1f%%", hitChance * 100))
				.rightColor(hitColor)
				.build());

			boolean immune = npcStats.isPoisonImmune();
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Poison chance:")
				.right(immune ? "Immune" : String.format("%.1f%%", poisonChance * 100))
				.rightColor(immune ? COLOR_LOW : Color.WHITE)
				.build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Max hit:")
				.right(String.valueOf(maxHit))
				.build());
		}

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
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Dynamite(p):")
				.right(String.valueOf(count))
				.rightColor(countColor)
				.build());
		}

		if (config.showSessionStats() && plugin.getSessionAttempts() > 0)
		{
			int attempts = plugin.getSessionAttempts();
			int procs = plugin.getSessionProcs();
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Session:")
				.right(String.format("%d/%d (%.0f%%)", procs, attempts,
					100.0 * procs / attempts))
				.build());
		}

		return super.render(graphics);
	}
}
