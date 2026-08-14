package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

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
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay())
        {
            return null;
        }

        if (config.showCategoryBoxes() && Rs2Bank.isOpen())
        {
            renderBankHighlights(graphics);
        }

        panelComponent.setPreferredSize(new Dimension(220, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
            .text("KSP Bank Organizer v" + KspBankOrganizerPlugin.version)
            .color(Color.GREEN)
            .build());
        panelComponent.getChildren().add(LineComponent.builder().left("Mode").right(engine.phase().equals("Idle") ? "Ready" : engine.activeMode().displayName()).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Phase").right(engine.phase()).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Stacks planned").right(String.valueOf(engine.plannedCount())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Misplaced").right(String.valueOf(engine.misplacedCount())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Moved").right(String.valueOf(engine.movedCount())).build());
        panelComponent.getChildren().add(LineComponent.builder().left("Sort moves").right(String.valueOf(engine.sortedCount())).build());
        return super.render(graphics);
    }

    private void renderBankHighlights(Graphics2D graphics)
    {
        BankSnapshot snapshot = engine.latestSnapshot();
        if (snapshot == null)
        {
            return;
        }
        Map<Integer, ItemCategory> categories = engine.categoriesById();
        Map<Integer, Integer> targets = engine.targetsById();
        int alpha = Math.max(0, Math.min(255, config.overlayOpacity() * 255 / 100));

        Stroke previousStroke = graphics.getStroke();
        try
        {
            for (BankSnapshot.BankStack stack : snapshot.items())
            {
                Rectangle bounds = Rs2Bank.getItemBounds(stack.slot());
                if (bounds == null || !Rs2UiHelper.isRectangleWithinCanvas(bounds))
                {
                    continue;
                }

                ItemCategory category = categories.get(stack.itemId());
                if (category != null)
                {
                    Color base = category.getColor();
                    graphics.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
                    graphics.fill(bounds);
                }

                Integer target = targets.get(stack.itemId());
                if (config.highlightMisplaced() && target != null && target >= 0 && stack.tab() != target)
                {
                    graphics.setColor(config.misplacedColor());
                    graphics.setStroke(new BasicStroke(2.0f));
                    graphics.draw(bounds);
                }
            }
        }
        finally
        {
            graphics.setStroke(previousStroke);
        }
    }
}
