package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.util.Collection;
import java.util.Map;
import javax.inject.Inject;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Bank-item category/highlight overlay using live WidgetItem canvas bounds. */
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
        categorizer.configure(config);
        showOnBank();
    }

    /** Filters transient bank-widget rebuild entries before the base renderer can dereference them. */
    @Override
    public Dimension render(Graphics2D graphics)
    {
        Collection<WidgetItem> widgetItems = overlayManager.getWidgetItems();
        if (widgetItems == null || widgetItems.isEmpty()) return null;

        Rectangle originalClip = graphics.getClipBounds();
        Widget currentParent = null;
        for (WidgetItem widgetItem : widgetItems)
        {
            try
            {
                if (widgetItem == null || widgetItem.getWidget() == null) continue;
                Widget parent = widgetItem.getWidget().getParent();
                if (parent == null) continue;

                Rectangle parentBounds = parent.getBounds();
                Rectangle itemBounds = widgetItem.getCanvasBounds();
                if (!validBounds(parentBounds) || !validBounds(itemBounds)) continue;

                if (crossesBoundary(itemBounds, parentBounds))
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

                renderItemOverlay(graphics, widgetItem.getId(), widgetItem);
            }
            catch (Throwable ignored)
            {
                // Bank widgets can invalidate between reads during a rebuild.
            }
        }

        graphics.setClip(originalClip);
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
            // Never propagate a transient WidgetItem rebuild into OverlayRenderer.
        }
    }

    private void renderItemOverlaySafe(Graphics2D graphics, int itemId, WidgetItem widgetItem)
    {
        if (widgetItem == null || widgetItem.getWidget() == null) return;
        Widget parent = widgetItem.getWidget().getParent();
        if (parent == null) return;

        int parentId = parent.getId();
        if (parentId != InterfaceID.Bankmain.ITEMS && parentId != InterfaceID.SharedBank.ITEMS) return;
        if (!config.showOverlay() || !config.showCategoryBoxes()) return;

        Rectangle bounds = widgetItem.getCanvasBounds();
        if (!validBounds(bounds)) return;

        ItemCategory category = resolveCategory(itemId, widgetItem.getQuantity());
        if (category == null) return;

        int alpha = Math.max(0, Math.min(255, config.overlayOpacity() * 255 / 100));
        Color base = category.getColor();
        graphics.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
        graphics.fill(bounds);

        if (!config.highlightMisplaced()) return;
        Integer target = engine.targetsById().get(itemId);
        BankSnapshot snapshot = engine.latestSnapshot();
        if (target == null || target < 0 || snapshot == null) return;

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
        if (category != null) return category;

        try
        {
            ItemComposition definition = Microbot.getClient().getItemDefinition(itemId);
            if (definition == null) return null;

            BankSnapshot.BankStack stack = new BankSnapshot.BankStack(
                itemId, definition.getName(), quantity, 0, 0, 0, 0,
                definition.isStackable(), definition.isTradeable(), definition.isTradeable(),
                definition.getInventoryActions() != null);
            return categorizer.categorize(stack);
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private static boolean validBounds(Rectangle bounds)
    {
        return bounds != null && bounds.width > 0 && bounds.height > 0;
    }

    private static boolean crossesBoundary(Rectangle item, Rectangle parent)
    {
        return item.x < parent.x && item.x + item.width >= parent.x
            || item.x < parent.x + parent.width && item.x + item.width >= parent.x + parent.width
            || item.y < parent.y && item.y + item.height >= parent.y
            || item.y < parent.y + parent.height && item.y + item.height >= parent.y + parent.height;
    }
}
