package net.runelite.client.plugins.microbot.kspbossgear;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Stroke;
import java.util.Collection;
import net.runelite.api.widgets.Widget;
import net.runelite.api.widgets.WidgetItem;
import net.runelite.client.ui.overlay.OverlayManager;
import net.runelite.client.ui.overlay.WidgetItemOverlay;

/** Shared crash-resistant rendering used by the bank and inventory overlays. */
abstract class BossGearItemOverlay extends WidgetItemOverlay
{
    private static final Color ALT_COLOR = new Color(245, 190, 70);

    protected final KspBossGearConfig config;
    protected final BossGearService gearService;
    private final OverlayManager overlayManager;

    BossGearItemOverlay(KspBossGearConfig config, BossGearService gearService, OverlayManager overlayManager)
    {
        this.config = config;
        this.gearService = gearService;
        this.overlayManager = overlayManager;
    }

    protected abstract boolean acceptsParent(int parentId);

    protected abstract boolean enabled();

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!enabled()) return null;
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
                if (parent == null || !acceptsParent(parent.getId())) continue;

                Rectangle parentBounds = parent.getBounds();
                Rectangle itemBounds = widgetItem.getCanvasBounds();
                if (!valid(parentBounds) || !valid(itemBounds)) continue;

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
                // Bank and inventory widgets can invalidate during a client rebuild.
            }
        }

        graphics.setClip(originalClip);
        return null;
    }

    @Override
    public void renderItemOverlay(Graphics2D graphics, int itemId, WidgetItem widgetItem)
    {
        if (widgetItem == null || widgetItem.getWidget() == null) return;
        Widget parent = widgetItem.getWidget().getParent();
        if (parent == null || !acceptsParent(parent.getId())) return;

        BossGearService.HighlightKind kind = gearService.classify(itemId);
        if (kind == BossGearService.HighlightKind.NONE) return;
        if (kind == BossGearService.HighlightKind.ALTERNATIVE && !config.highlightAlternatives()) return;

        Rectangle bounds = widgetItem.getCanvasBounds();
        if (!valid(bounds)) return;

        Color base = kind == BossGearService.HighlightKind.PRIMARY
            ? gearService.getSelectedTier().getColor()
            : ALT_COLOR;
        int alpha = Math.max(0, Math.min(255, config.highlightOpacity() * 255 / 100));

        graphics.setColor(new Color(base.getRed(), base.getGreen(), base.getBlue(), alpha));
        graphics.fill(bounds);

        Stroke oldStroke = graphics.getStroke();
        graphics.setStroke(new BasicStroke(kind == BossGearService.HighlightKind.PRIMARY ? 2.0f : 1.0f));
        graphics.setColor(base);
        graphics.draw(bounds);
        graphics.setStroke(oldStroke);
    }

    private static boolean valid(Rectangle bounds)
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
