package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Status-only overlay. Bank item highlighting is handled by
 * KspBankOrganizerItemOverlay using RuneLite's WidgetItemOverlay.
 */
final class KspBankOrganizerOverlay extends OverlayPanel
{
    private final KspBankOrganizerConfig config;
    private final BankOrganizerEngine engine;

    @Inject
    KspBankOrganizerOverlay(
        KspBankOrganizerPlugin plugin,
        KspBankOrganizerConfig config,
        BankOrganizerEngine engine)
    {
        super(plugin);
        this.config = config;
        this.engine = engine;

        setLayer(OverlayLayer.ABOVE_WIDGETS);
        setPriority(PRIORITY_HIGHEST);
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay())
        {
            return null;
        }

        panelComponent.setPreferredSize(new Dimension(220, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("KSP Bank Organizer v" + KspBankOrganizerPlugin.VERSION)
            .color(Color.GREEN)
            .build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Mode")
            .right(engine.phase().equals("Idle") ? "Ready" : engine.activeMode().displayName())
            .build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Phase")
            .right(engine.phase())
            .build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Stacks planned")
            .right(String.valueOf(engine.plannedCount()))
            .build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Misplaced")
            .right(String.valueOf(engine.misplacedCount()))
            .build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Moved")
            .right(String.valueOf(engine.movedCount()))
            .build());
        panelComponent.getChildren().add(LineComponent.builder()
            .left("Sort moves")
            .right(String.valueOf(engine.sortedCount()))
            .build());

        return super.render(graphics);
    }
}
