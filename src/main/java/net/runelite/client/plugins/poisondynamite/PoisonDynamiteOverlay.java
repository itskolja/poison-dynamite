package net.runelite.client.plugins.poisondynamite;

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

	private final PoisonDynamitePlugin plugin;
	private final PoisonDynamiteConfig config;

	@Inject
	PoisonDynamiteOverlay(PoisonDynamitePlugin plugin, PoisonDynamiteConfig config)
	{
		super(plugin);
		this.plugin = plugin;
		this.config = config;
		setPosition(OverlayPosition.BOTTOM_LEFT);
		addMenuEntry(RUNELITE_OVERLAY_CONFIG, OPTION_CONFIGURE, "Poison Dynamite overlay");
		addMenuEntry(RUNELITE_OVERLAY, RESET_OPTION, "Poison Dynamite overlay", e -> plugin.resetStats());
	}

	@Override
	public Dimension render(Graphics2D graphics)
	{
		if (!config.showStats())
		{
			return null;
		}

		int attempts = plugin.getAttempts();
		if (attempts == 0)
		{
			return null;
		}

		int successes = plugin.getSuccesses();
		int rate = attempts > 0 ? (successes * 100 / attempts) : 0;

		panelComponent.getChildren().add(TitleComponent.builder()
			.text("Poison Dynamite")
			.build());

		panelComponent.getChildren().add(LineComponent.builder()
			.left("Success rate:")
			.right(successes + "/" + attempts + " (" + rate + "%)")
			.build());

		if (config.trackDamage())
		{
			panelComponent.getChildren().add(LineComponent.builder()
				.left("Poison damage:")
				.right(Integer.toString(plugin.getTotalPoisonDamage()))
				.build());
		}

		return super.render(graphics);
	}
}
