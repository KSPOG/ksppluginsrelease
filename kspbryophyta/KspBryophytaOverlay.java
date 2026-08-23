package net.runelite.client.plugins.microbot.kspbryophyta;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class KspBryophytaOverlay extends OverlayPanel
{
    private final KspBryophytaScript script;
    private final KspBryophytaConfig config;

    @Inject
    KspBryophytaOverlay(
            KspBryophytaPlugin plugin,
            KspBryophytaScript script,
            KspBryophytaConfig config)
    {
        super(plugin);
        this.script = script;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        try
        {
            panelComponent.setPreferredSize(new Dimension(235, 238));

            panelComponent.getChildren().add(
                    TitleComponent.builder()
                            .text("KSP Bryophyta v" + KspBryophytaPlugin.VERSION)
                            .color(Color.GREEN)
                            .build()
            );

            panelComponent.getChildren().add(line("Strategy", config.strategy().toString()));
            panelComponent.getChildren().add(line("State", script.getState().toString()));
            panelComponent.getChildren().add(line("Loop", script.isRunning() ? "Running" : "Stopped"));
            panelComponent.getChildren().add(line("Food", Integer.toString(script.getFoodRemaining())));
            panelComponent.getChildren().add(line("Prayer", Integer.toString(script.getPrayerPoints())));
            panelComponent.getChildren().add(line("Kills", Integer.toString(script.getKills())));
            panelComponent.getChildren().add(line("Mossy keys", Integer.toString(script.getMossyKeys())));
            panelComponent.getChildren().add(line("Chest attempts", Integer.toString(script.getChestAttempts())));
            panelComponent.getChildren().add(line("Status", script.getStatus()));
        }
        catch (Exception ex)
        {
            Microbot.logStackTrace(getClass().getSimpleName(), ex);
        }

        return super.render(graphics);
    }

    private static LineComponent line(String left, String right)
    {
        return LineComponent.builder()
                .left(left)
                .right(right)
                .build();
    }
}
