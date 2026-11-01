package net.runelite.client.plugins.microbot.kspseasonalevents;

import net.runelite.api.Point;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;

import java.awt.Rectangle;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * Diango's Holiday Item Retrieval (interface 78). Only his item list (78:3) is read and scrolled; each item there has
 * its item id and a left-click "Take". All widget reads run on the client thread.
 */
final class KspDiangoHolidayRetrieval {
    private KspDiangoHolidayRetrieval() { }

    static final class Snapshot {
        final boolean open;
        /** Item id -> on-screen bounds, for pieces fully inside the visible part of the list. */
        final Map<Integer, Rectangle> visible;
        /** Item id -> -1 above / 1 below the visible part, for listed pieces outside it. */
        final Map<Integer, Integer> offscreen;
        final Point scrollPoint;
        final int scrollY;
        final Rectangle closeBounds;

        Snapshot(boolean open, Map<Integer, Rectangle> visible, Map<Integer, Integer> offscreen,
                 Point scrollPoint, int scrollY, Rectangle closeBounds) {
            this.open = open;
            this.visible = visible;
            this.offscreen = offscreen;
            this.scrollPoint = scrollPoint;
            this.scrollY = scrollY;
            this.closeBounds = closeBounds;
        }

        static Snapshot closed() {
            return new Snapshot(false, Collections.emptyMap(), Collections.emptyMap(), null, 0, null);
        }

        boolean lists(int itemId) {
            return visible.containsKey(itemId) || offscreen.containsKey(itemId);
        }
    }

    static Snapshot inspect() {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget list = Microbot.getClient().getWidget(InterfaceID.RareitemsDiango.ITEMS);
            if (list == null || list.isHidden()) return Snapshot.closed();
            Rectangle viewport = list.getBounds();
            Map<Integer, Rectangle> visible = new HashMap<>();
            Map<Integer, Integer> offscreen = new HashMap<>();
            for (Widget[] group : new Widget[][]{list.getDynamicChildren(), list.getChildren()}) {
                if (group == null) continue;
                for (Widget child : group) {
                    if (child == null || child.isHidden() || child.getItemId() <= 0) continue;
                    Rectangle b = child.getBounds();
                    if (b == null || b.width <= 0 || b.height <= 0) continue;
                    if (viewport != null && viewport.contains(b)) visible.put(child.getItemId(), new Rectangle(b));
                    else offscreen.put(child.getItemId(), viewport != null && b.y < viewport.y ? -1 : 1);
                }
            }
            Point scroll = viewport == null ? null
                    : new Point(viewport.x + viewport.width / 2, viewport.y + viewport.height / 2);
            return new Snapshot(true, visible, offscreen, scroll, list.getScrollY(),
                    closeButton(Microbot.getClient().getWidget(InterfaceID.RareitemsDiango.ROOT_MODEL0), 0));
        }).orElse(Snapshot.closed());
    }

    /** The window's own Close button, searched only inside Diango's interface. */
    private static Rectangle closeButton(Widget widget, int depth) {
        if (widget == null || widget.isHidden() || depth > 6) return null;
        String[] actions = widget.getActions();
        Rectangle b = widget.getBounds();
        if (actions != null && b != null && b.width <= 40 && b.height <= 40
                && Arrays.stream(actions).anyMatch(a -> "Close".equalsIgnoreCase(a))) return new Rectangle(b);
        for (Widget[] group : new Widget[][]{widget.getStaticChildren(), widget.getDynamicChildren(), widget.getNestedChildren()}) {
            if (group == null) continue;
            for (Widget child : group) {
                Rectangle found = closeButton(child, depth + 1);
                if (found != null) return found;
            }
        }
        return null;
    }

    static boolean take(Rectangle bounds) {
        if (bounds == null) return false;
        Microbot.getMouse().click(bounds);
        return true;
    }

    static boolean scroll(Snapshot snapshot, boolean up) {
        if (!snapshot.open || snapshot.scrollPoint == null) return false;
        if (up) Microbot.getMouse().scrollUp(snapshot.scrollPoint);
        else Microbot.getMouse().scrollDown(snapshot.scrollPoint);
        return true;
    }

    static boolean close(Snapshot snapshot) {
        if (!snapshot.open || snapshot.closeBounds == null) return false;
        Microbot.getMouse().click(snapshot.closeBounds);
        return true;
    }
}
