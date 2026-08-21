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

/** Status overlay; bank item highlighting is handled by KspBankOrganizerItemOverlay. */
final class KspBankOrganizerOverlay extends OverlayPanel
{
    private final KspBankOrganizerConfig config;
    private final BankOrganizerEngine engine;

    @Inject
    KspBankOrganizerOverlay(KspBankOrganizerPlugin plugin, KspBankOrganizerConfig config, BankOrganizerEngine engine)
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
        if (!config.showOverlay()) return null;

        panelComponent.setPreferredSize(new Dimension(220, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("KSP Bank Organizer v" + KspBankOrganizerPlugin.VERSION)
            .color(Color.GREEN)
            .build());
        addLine("Mode", engine.phase().equals("Idle") ? "Ready" : engine.activeMode().displayName());
        addLine("Phase", engine.phase());
        addLine("Stacks planned", engine.plannedCount());
        addLine("Misplaced", engine.misplacedCount());
        addLine("Moved", engine.movedCount());
        addLine("Sort moves", engine.sortedCount());
        return super.render(graphics);
    }

    private void addLine(String label, Object value)
    {
        panelComponent.getChildren().add(LineComponent.builder().left(label).right(String.valueOf(value)).build());
    }
}
