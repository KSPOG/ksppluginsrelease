package net.runelite.client.plugins.microbot.kspseasonalevents;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public final class KspSeasonalEventsOverlay extends OverlayPanel {
    private final KspHalloween2026Script script;
    private final KspSeasonalEventsConfig config;

    @Inject
    KspSeasonalEventsOverlay(KspSeasonalEventsPlugin plugin,
                              KspHalloween2026Script script, KspSeasonalEventsConfig config) {
        super(plugin);
        this.script = script;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override public Dimension render(Graphics2D graphics) {
        if (!script.isActive() || !config.showOverlay()) return null;
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(270, 143));
        panelComponent.setBackgroundColor(new Color(17, 13, 22, 200));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Seasonal Events").color(Color.WHITE).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Event").right("Halloween").build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Stage").right(script.getStage().toString()).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Lanterns").right(script.getLanternCount() + "/8").build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Child scares verified")
                .right(script.getChildSpookAttempts() + "/"
                        + script.getChildSpookCheckpoint()).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Child objective")
                .right(script.isChildObjectiveConfirmed() ? "Confirmed" : "Not verified").build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Adults").right(script.getAdultsGiven() + "/5").build());
        return super.render(graphics);
    }
}
