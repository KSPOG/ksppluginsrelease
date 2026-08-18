package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.ItemComposition;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.WidgetItemOverlay;
import net.runelite.client.ui.overlay.OverlayManager;

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
    private final OverlayManager overlayManager;

    @Inject
    KspBankOrganizerItemOverlay(
        KspBankOrganizerConfig config,
        BankOrganizerEngine engine,
        AutoCategorizer categorizer,
        OverlayManager overlayManager)
    {
        this.config = config;
        this.engine = engine;
        this.categorizer = categorizer;
        this.overlayManager = overlayManager;
        this.categorizer.configure(config);

        // Register the bank item interface when the overlay is constructed.
        // WidgetItemOverlay then supplies real canvas-space WidgetItem bounds.
        showOnBank();
    }

    /**
     * Kept for compatibility with older plugin lifecycle code.
     * Registration is already performed by the constructor.
     */
    void enableBankItems()
    {
        // Intentionally empty.
    }

    /**
     * RuneLite's WidgetItemOverlay base renderer assumes every WidgetItem has a
     * live parent. During bank rebuilds a WidgetItem can transiently have a null
     * widget/parent. The base implementation dereferences both before calling
     * renderItemOverlay(), so catching exceptions in renderItemOverlay() is too
     * late. Filter those transient entries here.
     */
    @Override
    public java.awt.Dimension render(Graphics2D graphics)
    {
        final java.util.Collection<WidgetItem> widgetItems =
            overlayManager.getWidgetItems();

        if (widgetItems == null || widgetItems.isEmpty())
        {
            return null;
        }

        final java.awt.Rectangle originalClip = graphics.getClipBounds();
        net.runelite.api.widgets.Widget currentParent = null;

        for (WidgetItem widgetItem : widgetItems)
        {
            if (widgetItem == null || widgetItem.getWidget() == null)
            {
                continue;
            }

            net.runelite.api.widgets.Widget parent = widgetItem.getWidget().getParent();
            if (parent == null)
            {
                continue;
            }

            java.awt.Rectangle parentBounds = parent.getBounds();
            java.awt.Rectangle itemBounds = widgetItem.getCanvasBounds();
            if (parentBounds == null || itemBounds == null
                || itemBounds.width <= 0 || itemBounds.height <= 0)
            {
                continue;
            }

            boolean shouldClip =
                itemBounds.x < parentBounds.x
                    && itemBounds.x + itemBounds.width >= parentBounds.x
                || itemBounds.x < parentBounds.x + parentBounds.width
                    && itemBounds.x + itemBounds.width >= parentBounds.x + parentBounds.width
                || itemBounds.y < parentBounds.y
                    && itemBounds.y + itemBounds.height >= parentBounds.y
                || itemBounds.y < parentBounds.y + parentBounds.height
                    && itemBounds.y + itemBounds.height >= parentBounds.y + parentBounds.height;

            if (shouldClip)
            {
                if (currentParent != parent)
                {
                    graphics.setClip(parentBounds);
                    currentParent = parent;
                }
            }
            else if (currentParent != null && currentParent != parent)
            {
                graphics.setClip(originalClip);
                currentParent = null;
            }

            try
            {
                renderItemOverlay(graphics, widgetItem.getId(), widgetItem);
            }
            catch (Throwable ignored)
            {
                // A transient bank-widget rebuild must never break the overlay renderer.
            }
        }

        if (currentParent != null)
        {
            graphics.setClip(originalClip);
        }

        return null;
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
    {
        try
        {
            renderItemOverlaySafe(graphics, itemId, widgetItem);
        }
        catch (Throwable ignored)
        {
            // WidgetItemOverlay is rendered every frame. Never allow a transient
            // bank-widget rebuild to propagate an exception into OverlayRenderer.
        }
    }

    private void renderItemOverlaySafe(Graphics2D graphics, int itemId, WidgetItem widgetItem)
    {
        // WidgetItemOverlay can receive WidgetItems from several interfaces.
        // Restrict rendering to the actual bank item container; otherwise
        // inventory-side WidgetItems can be drawn at unrelated coordinates.
        if (widgetItem == null || widgetItem.getWidget() == null)
        {
            return;
        }

        /*
         * WidgetItem#getWidget() is the individual item widget. The bank
         * interface is its parent. Checking the item widget's own id was the
         * reason the overlay renderer kept throwing NPEs/placing nothing.
         */
        net.runelite.api.widgets.Widget parent = widgetItem.getWidget().getParent();
        if (parent == null)
        {
            return;
        }

        int parentId = parent.getId();
        if (parentId != InterfaceID.Bankmain.ITEMS
            && parentId != InterfaceID.SharedBank.ITEMS)
        {
            return;
        }

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
