package net.runelite.client.plugins.microbot.kspdirectfishing;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class KspDirectFishingOverlay extends OverlayPanel
{
    private final KspDirectFishingPlugin plugin;
    private final KspDirectFishingScript script;
    private final KspDirectFishingConfig config;

    @Inject
    KspDirectFishingOverlay(
            KspDirectFishingPlugin plugin,
            KspDirectFishingScript script,
            KspDirectFishingConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.script = script;
        this.config = config;

        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        panelComponent.setPreferredSize(new Dimension(220, 150));
        panelComponent.setBackgroundColor(new Color(0, 0, 0, 155));

        panelComponent.getChildren().add(
                TitleComponent.builder()
                        .text("KSP Direct Fishing v" + KspDirectFishingPlugin.VERSION)
                        .color(Color.BLUE)
                        .build()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Mode:")
                        .right(config.fishingMode().toString())
                        .build()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("State:")
                        .right(script.getState().toString())
                        .build()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Fishing XP:")
                        .right(String.valueOf(plugin.getFishingXpGained()))
                        .build()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Cooking XP:")
                        .right(String.valueOf(plugin.getCookingXpGained()))
                        .build()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Runtime:")
                        .right(plugin.getFormattedRuntime())
                        .build()
        );

        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("Status:")
                        .right(script.getStatus())
                        .build()
        );

        return super.render(graphics);
    }
}
