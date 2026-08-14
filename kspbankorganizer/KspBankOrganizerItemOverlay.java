package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.ItemComposition;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/**
 * Bank item overlay implemented using RuneLite's WidgetItemOverlay.
 *
 * This is deliberately separate from the status OverlayPanel. WidgetItemOverlay
 * receives the actual bank WidgetItem objects and their canvas bounds, avoiding
 * relative-widget-coordinate errors and the wrong-interface placement seen when
 * calculating bank slot rectangles manually.
 */
final class KspBankOrganizerItemOverlay extends WidgetItemOverlay
{
    private final KspBankOrganizerConfig config;
    private final BankOrganizerEngine engine;
    private final AutoCategorizer categorizer;

    @Inject
    KspBankOrganizerItemOverlay(
        KspBankOrganizerConfig config,
        BankOrganizerEngine engine)
    {
        this.config = config;
        this.engine = engine;
        this.categorizer = new AutoCategorizer();
        this.categorizer.configure(config);
    }

    /**
     * Register the bank item interface with RuneLite's WidgetItemOverlay.
     * WidgetItemOverlay exposes showOnBank() as protected, so the plugin
     * lifecycle delegates through this public package-facing method.
     */
    void enableBankItems()
    {
        showOnBank();
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
    {
        if (!config.showOverlay() || !config.showCategoryBoxes())
        {
            return;
        }

        Rectangle bounds = widgetItem.getCanvasBounds();
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
        {
            return;
        }

        ItemCategory category = resolveCategory(itemId, widgetItem.getQuantity());
        if (category == null)
        {
            return;
        }

        int alpha = Math.max(0, Math.min(255, config.overlayOpacity() * 255 / 100));
        Color base = category.getColor();

        graphics.setColor(new Color(
            base.getRed(),
            base.getGreen(),
            base.getBlue(),
            alpha));
        graphics.fill(bounds);

        if (!config.highlightMisplaced())
        {
            return;
        }

        Integer target = engine.targetsById().get(itemId);
        if (target == null || target < 0)
        {
            return;
        }

        BankSnapshot snapshot = engine.latestSnapshot();
        if (snapshot == null)
        {
            return;
        }

        for (BankSnapshot.BankStack stack : snapshot.items())
        {
            if (stack.itemId() == itemId && stack.tab() != target)
            {
                graphics.setColor(config.misplacedColor());
                graphics.setStroke(new BasicStroke(2.0f));
                graphics.draw(bounds);
                return;
            }
        }
    }

    private ItemCategory resolveCategory(int itemId, int quantity)
    {
        Map<Integer, ItemCategory> categories = engine.categoriesById();
        ItemCategory category = categories.get(itemId);
        if (category != null)
        {
            return category;
        }

        // During Preview/idle, the engine may not have a snapshot yet. Build a
        // minimal client-thread-local stack from the actual item definition so
        // the overlay can still classify visible bank items.
        try
        {
            ItemComposition definition = Microbot.getClient().getItemDefinition(itemId);
            if (definition == null)
            {
                return null;
            }

            String name = definition.getName();
            BankSnapshot.BankStack stack = new BankSnapshot.BankStack(
                itemId,
                name,
                quantity,
                0,
                0,
                0,
                definition.isStackable(),
                definition.isTradeable(),
                definition.isTradeable(),
                definition.getInventoryActions() != null);

            return categorizer.categorize(stack);
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }
}
