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

public class KspBryophytaOverlay extends OverlayPanel {
    private final KspBryophytaScript script;
    private final KspBryophytaConfig config;

    @Inject
    KspBryophytaOverlay(KspBryophytaPlugin plugin, KspBryophytaScript script, KspBryophytaConfig config) {
        super(plugin);
        this.script = script;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(235, 238));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("KSP Bryophyta v" + KspBryophytaPlugin.VERSION).color(Color.GREEN).build());
            addLine("Strategy", config.strategy());
            addLine("State", script.getState());
            addLine("Loop", script.isRunning() ? "Running" : "Stopped");
            addLine("Food", script.getFoodRemaining());
            addLine("Prayer", script.getPrayerPoints());
            addLine("Kills", script.getKills());
            addLine("Mossy keys", script.getMossyKeys());
            addLine("Chest attempts", script.getChestAttempts());
            addLine("Status", script.getStatus());
        } catch (Exception ex) {
            Microbot.logStackTrace(getClass().getSimpleName(), ex);
        }
        return super.render(graphics);
    }

    private void addLine(String left, Object right) {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(left).right(String.valueOf(right)).build());
    }
}
