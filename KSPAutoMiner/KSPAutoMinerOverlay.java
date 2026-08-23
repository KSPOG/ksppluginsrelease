package net.runelite.client.plugins.microbot.KSPAutoMiner;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.time.Duration;

public class KSPAutoMinerOverlay extends OverlayPanel {
    @Inject
    KSPAutoMinerOverlay(KSPAutoMinerPlugin plugin) {
        super(plugin);
        setPosition(OverlayPosition.TOP_CENTER);
        panelComponent.setPreferredSize(new Dimension(220, 0));
    }

    @Override
    public Dimension render(java.awt.Graphics2D graphics) {
        panelComponent.getChildren().clear();
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Auto Miner")
                .color(Color.ORANGE)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(KSPAutoMinerScript.status)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Mode:")
                .right(KSPAutoMinerScript.modeLabel)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time Running:")
                .right(formatDuration(KSPAutoMinerScript.getRuntime()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Ores Mined:")
                .right(Integer.toString(KSPAutoMinerScript.oresMined))
                .build());

        return super.render(graphics);
    }

    private String formatDuration(Duration duration) {
        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
