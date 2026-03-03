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

	private final PoisonDynamitePlugin plugin;

	@Inject
	PoisonDynamiteOverlay(PoisonDynamitePlugin plugin)
	{
		super(plugin);
		this.plugin = plugin;
		setPosition(OverlayPosition.BOTTOM_LEFT);
		addMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Poison Dynamite overlay");
		addMenuEntry(RUNELITE_OVERLAY, RESET_OPTION, "Poison Dynamite overlay",
			e -> plugin.clearTrackedNpc());
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
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
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Hit chance:")
				.right("Loading...")
				.build());
		}
		else
		{
			String style = plugin.getAttackStyle();
			int effectiveLevel = plugin.getEffectiveAttackLevel();
			int equipBonus = plugin.getEquipmentAttackBonus();
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

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Poison chance:")
				.right(String.format("%.1f%%", poisonChance * 100))
				.build());

			panelComponent.getChildren().add(LineComponent.builder()
				.left("Max hit:")
				.right(String.valueOf(maxHit))
				.build());
		}

		return super.render(graphics);
	}
}
