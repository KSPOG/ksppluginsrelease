package net.runelite.client.plugins.microbot.mining;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class AutoMiningOverlay extends OverlayPanel {
    private final AutoMiningScript autoMiningScript;

    @Inject
    AutoMiningOverlay(AutoMiningPlugin plugin, AutoMiningScript autoMiningScript) {
        super(plugin);
        this.autoMiningScript = autoMiningScript;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        try {
            panelComponent.setPreferredSize(new Dimension(340, 150));
            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("AutoMining Plugin V" + AutoMiningPlugin.version)
                    .color(Color.GREEN)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder().build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time Running:")
                    .right(autoMiningScript.getFormattedRuntime())
                    .build());

            int currentLevel = autoMiningScript.getCurrentMiningLevel();
            int levelsGained = autoMiningScript.getMiningLevelsGained();
            String levelText = currentLevel > 0
                    ? currentLevel + " (+" + levelsGained + ")"
                    : "Loading...";
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Mining Lvl:")
                    .right(levelText)
                    .build());

            String status = Microbot.status;
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Status:")
                    .right(status == null || status.trim().isEmpty() ? "Starting..." : status)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Current Ore:")
                    .right(autoMiningScript.getCurrentOreDisplay())
                    .build());
        } catch (Exception ex) {
            System.out.println(ex.getMessage());
        }
        return super.render(graphics);
    }
}
