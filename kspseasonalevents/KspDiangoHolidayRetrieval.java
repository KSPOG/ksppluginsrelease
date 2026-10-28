package net.runelite.client.plugins.microbot.kspseasonalevents;

import net.runelite.api.ItemComposition;
import net.runelite.api.Point;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.Rectangle;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Discovers Diango's live Holiday Item Retrieval widget hierarchy. No fixed
 * group ID, icon order, guessed item coordinate or cached widget reference.
 * All widget reads run on the RuneLite client thread.
 */
final class KspDiangoHolidayRetrieval {
    private static final String TITLE = "Diango's Holiday Item Retrieval";

    private KspDiangoHolidayRetrieval() { }

    static final class Snapshot {
        final boolean open;
        final String itemName;
        final Rectangle itemBounds;
        final Rectangle closeBounds;
        final Point scrollPoint;
        final int scrollY;
        final int scrollMax;
        final int itemWidgets;

        Snapshot(boolean open, String itemName, Rectangle itemBounds,
                 Rectangle closeBounds, Point scrollPoint,
                 int scrollY, int scrollMax, int itemWidgets) {
            this.open = open;
            this.itemName = itemName;
            this.itemBounds = itemBounds;
            this.closeBounds = closeBounds;
            this.scrollPoint = scrollPoint;
            this.scrollY = scrollY;
            this.scrollMax = scrollMax;
            this.itemWidgets = itemWidgets;
        }

        static Snapshot closed() {
            return new Snapshot(false, null, null, null, null, 0, 0, 0);
        }

        boolean canScrollDown() {
            return scrollPoint != null && scrollY < scrollMax - 8;
        }
    }

    static Snapshot inspect(List<String> desiredNames) {
        // Rs2Widget.findWidget marshals to the client thread itself. Resolve
        // before the second client-thread operation to avoid nested waits.
        Widget title = Rs2Widget.findWidget(TITLE, true);
        if (title == null) return Snapshot.closed();
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (title.isHidden()) return Snapshot.closed();

            Widget root = title;
            for (int i = 0; i < 16 && root.getParent() != null; i++) {
                root = root.getParent();
            }

            List<Widget> all = new ArrayList<>();
            Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
            int interfaceGroup = title.getId() >>> 16;
            Widget[] roots = Microbot.getClient().getWidgetRoots();
            if (roots != null) {
                for (Widget candidate : roots) {
                    if (candidate != null && (candidate.getId() >>> 16) == interfaceGroup) {
                        collect(candidate, all, visited, 0);
                    }
                }
            }
            collect(root, all, visited, 0);
            if (all.isEmpty()) return Snapshot.closed();

            Widget scroller = null;
            int longestScroll = 0;
            for (Widget widget : all) {
                Rectangle bounds = widget.getBounds();
                if (bounds == null || bounds.width < 160 || bounds.height < 70) continue;
                int total = widget.getScrollHeight() - widget.getHeight();
                if (total > longestScroll) {
                    longestScroll = total;
                    scroller = widget;
                }
            }

            Rectangle viewport = scroller == null ? root.getBounds() : scroller.getBounds();
            String selectedName = null;
            Rectangle selectedBounds = null;
            int selectedPriority = Integer.MAX_VALUE;
            Rectangle closeBounds = null;
            int availableItemWidgets = 0;

            for (Widget widget : all) {
                Rectangle bounds = widget.getBounds();
                if (bounds == null || bounds.width < 15 || bounds.height < 15) continue;
                String[] actions = widget.getActions();
                if (actions != null && Arrays.stream(actions).anyMatch(action ->
                        action != null && action.equalsIgnoreCase("Close"))
                        && bounds.width <= 70 && bounds.height <= 70) {
                    closeBounds = new Rectangle(bounds);
                }

                String itemName = itemName(widget);
                if (itemName == null) continue;
                availableItemWidgets++;
                int priority = desiredNames.indexOf(itemName);
                if (priority == -1 || priority >= selectedPriority) continue;
                // Some widgets are present off-screen inside a scroller.
                // Never click those or a row outside the active viewport.
                if (bounds.width > 100 || bounds.height > 100
                        || viewport == null || !viewport.intersects(bounds)) continue;
                Rectangle visible = viewport.intersection(bounds);
                if (visible.width < 15 || visible.height < 15
                        || visible.width * visible.height < bounds.width * bounds.height / 2)
                    continue;

                selectedPriority = priority;
                selectedName = itemName;
                selectedBounds = new Rectangle(visible);
            }

            Point scrollPoint = null;
            int scrollY = 0;
            int scrollMax = 0;
            if (scroller != null && viewport != null && viewport.width > 160) {
                scrollPoint = new Point(viewport.x + viewport.width / 2,
                        viewport.y + viewport.height / 2);
                scrollY = scroller.getScrollY();
                scrollMax = Math.max(0, scroller.getScrollHeight() - scroller.getHeight());
            }

            return new Snapshot(true, selectedName, selectedBounds,
                    closeBounds, scrollPoint, scrollY, scrollMax, availableItemWidgets);
        }).orElse(Snapshot.closed());
    }

    private static String itemName(Widget widget) {
        int id = widget.getItemId();
        if (id > 0) {
            ItemComposition definition = Microbot.getClient().getItemDefinition(id);
            if (definition != null && definition.getName() != null) {
                return normal(definition.getName());
            }
        }
        // Some retrieval interfaces use named sprite widgets rather than
        // item widgets. Match exact visible widget names, never substrings.
        String name = normal(widget.getName());
        return name.isEmpty() ? normal(widget.getText()) : name;
    }

    private static String normal(String s) {
        return s == null ? ""
                : s.replaceAll("<[^>]*>", "").trim().toLowerCase(Locale.ROOT);
    }

    private static void collect(Widget widget, List<Widget> result,
                                Set<Widget> visited, int depth) {
        if (widget == null || widget.isHidden() || !visited.add(widget) || depth > 14) return;
        result.add(widget);
        Widget[][] children = {
                widget.getChildren(), widget.getDynamicChildren(),
                widget.getStaticChildren(), widget.getNestedChildren()
        };
        for (Widget[] group : children) {
            if (group == null) continue;
            for (Widget child : group) collect(child, result, visited, depth + 1);
        }
    }

    static boolean select(Snapshot snapshot) {
        if (!snapshot.open || snapshot.itemBounds == null) return false;
        Microbot.getMouse().click(snapshot.itemBounds);
        return true;
    }

    static boolean scrollDown(Snapshot snapshot) {
        if (!snapshot.open || !snapshot.canScrollDown()) return false;
        Microbot.getMouse().scrollDown(snapshot.scrollPoint);
        return true;
    }

    static boolean close(Snapshot snapshot) {
        if (!snapshot.open || snapshot.closeBounds == null) return false;
        Microbot.getMouse().click(snapshot.closeBounds);
        return true;
    }
}
