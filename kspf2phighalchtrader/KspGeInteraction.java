package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.grandexchange.GrandExchangeSlots;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Set;
import java.util.concurrent.Callable;
import java.util.concurrent.ThreadLocalRandom;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * GE interaction layer hardened from the behavior observed in the supplied
 * FlipperPlugin-1.0.0.jar. The important difference from a blind CC_OP invoke is
 * that slot context actions are resolved from the live slot widget, verified
 * against the live client menu, required to remain stable for several polls, and
 * only then clicked.
 */
final class KspGeInteraction {
    private static final int FIRST_GE_SLOT_CHILD = 7;
    private static final int GE_SLOT_WIDGET_COUNT = 8; // Client layout; plugin still owns only one pending offer.
    private static final int COLLECT_CONTAINER_CHILD = 6;

    private static final long MENU_OPEN_TIMEOUT_MS = 2500L;
    private static final int MENU_STABLE_POLLS = 3;
    private static final int MENU_POLL_MIN_MS = 65;
    private static final int MENU_POLL_MAX_MS = 95;
    private static final int MENU_ENTRY_HEIGHT = 15;

    private static final long BUY_SLOT_RESOLVE_TIMEOUT_MS = 3500L;

    boolean ensureOverview() {
        if (Rs2GrandExchange.isOfferScreenOpen()) {
            Rs2GrandExchange.backToOverview();
            sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), 1800);
        }
        return Rs2GrandExchange.isOpen() && !Rs2GrandExchange.isOfferScreenOpen();
    }

    /**
     * Place a buy and confirm that a live GE slot for the requested item exists.
     * This prevents the state machine from advancing merely because buyItem()
     * returned after a UI interaction while the offer never actually reached a slot.
     */
    GrandExchangeSlots placeBuyOffer(String itemName, int itemId, int price, int quantity) {
        GrandExchangeSlots existing = Rs2GrandExchange.findSlotForItem(itemId, false);
        if (existing != null) {
            return existing;
        }

        if (!ensureOverview()) {
            return null;
        }

        boolean requestStarted = Rs2GrandExchange.buyItem(itemName, price, quantity);
        long deadline = System.currentTimeMillis() + BUY_SLOT_RESOLVE_TIMEOUT_MS;
        GrandExchangeSlots resolved = null;
        while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
            resolved = Rs2GrandExchange.findSlotForItem(itemId, false);
            if (resolved == null && itemName != null) {
                resolved = Rs2GrandExchange.findSlotForItem(itemName, false);
            }
            if (resolved != null) {
                return resolved;
            }
            sleep(70, 120);
        }

        // A failed/partial helper interaction can leave the setup screen open. Reset
        // it before the caller retries so the next buy starts from a known GE overview.
        if (Rs2GrandExchange.isOfferScreenOpen()) {
            Rs2GrandExchange.backToOverview();
            sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), 1500);
        }

        // If buyItem said it started but a slot never materialized, returning null is
        // safer than assuming success and later losing track of the offer.
        return requestStarted ? Rs2GrandExchange.findSlotForItem(itemId, false) : null;
    }

    /**
     * Click the visible overview Collect control itself, matching the supplied
     * Flipper JAR's preferred behavior. The fixed Rs2GrandExchange invocation is
     * retained only as a fallback when the live widget cannot be resolved/clicked.
     */
    boolean collectOverviewToInventory() {
        if (!ensureOverview()) {
            return false;
        }

        Widget collectWidget = resolveCollectWidget();
        if (collectWidget != null) {
            if (Rs2Widget.clickWidget(collectWidget)) {
                sleep(120, 220);
                return true;
            }

            Rectangle bounds = copyBounds(collectWidget);
            if (validBounds(bounds)) {
                Microbot.getMouse().click(bounds);
                sleep(120, 220);
                return true;
            }
        }

        return Rs2GrandExchange.collectAllToInventory();
    }

    /**
     * Use the same robust right-click menu strategy observed in the supplied Flipper
     * JAR for "Abort offer": resolve the current slot, close stale menus, right-click
     * the slot, wait for a stable matching menu entry, verify param1 still references
     * the exact slot widget, and then click the calculated row bounds.
     */
    boolean abortOfferViaStableMenu(GrandExchangeSlots slot) {
        if (slot == null || !ensureOverview()) {
            return false;
        }

        for (int attempt = 0; attempt < 2; attempt++) {
            SlotTarget target = resolveSlotTarget(slot);
            if (target == null) {
                return false;
            }

            if (!closeOpenMenu()) {
                continue;
            }

            int x = target.clickBounds.x + target.clickBounds.width / 2;
            int y = target.clickBounds.y + target.clickBounds.height / 2;
            Microbot.getMouse().click(x, y, true);

            MenuSnapshot snapshot = waitForStableSlotMenu("Abort offer", target.widgetId);
            if (snapshot == null || snapshot.entry.param1 != target.widgetId) {
                closeOpenMenu();
                sleep(100, 180);
                continue;
            }

            Rectangle rowBounds = snapshot.entryBounds();
            if (!validBounds(rowBounds)) {
                closeOpenMenu();
                continue;
            }

            Microbot.getMouse().click(rowBounds);
            if (!sleepUntil(() -> !isMenuOpen(), 1200)) {
                closeOpenMenu();
                continue;
            }

            sleep(120, 220);
            return true;
        }

        return false;
    }

    private SlotTarget resolveSlotTarget(GrandExchangeSlots slot) {
        return onClientThread(() -> {
            int index = slot.ordinal();
            if (index < 0 || index >= GE_SLOT_WIDGET_COUNT) {
                return null;
            }

            Client client = Microbot.getClient();
            Widget widget = client.getWidget(InterfaceID.GE_OFFERS, FIRST_GE_SLOT_CHILD + index);
            if (!isVisible(widget)) {
                return null;
            }

            Rectangle bounds = widget.getBounds();
            if (!validBounds(bounds)) {
                return null;
            }

            return new SlotTarget(widget.getId(), new Rectangle(bounds));
        }, null);
    }

    private Widget resolveCollectWidget() {
        return onClientThread(() -> {
            Widget root = Microbot.getClient().getWidget(InterfaceID.GE_OFFERS, COLLECT_CONTAINER_CHILD);
            if (root == null) {
                return null;
            }
            for (Widget widget : descendants(root)) {
                if (isVisible(widget) && hasLabel(widget, "Collect")) {
                    return widget;
                }
            }
            return isVisible(root) && hasLabel(root, "Collect") ? root : null;
        }, null);
    }

    private MenuSnapshot waitForStableSlotMenu(String option, int expectedWidgetId) {
        long deadline = System.currentTimeMillis() + MENU_OPEN_TIMEOUT_MS;
        MenuSnapshot previous = null;
        int stablePolls = 0;

        while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
            MenuSnapshot current = readOpenSlotMenu(option, expectedWidgetId);
            if (current == null) {
                previous = null;
                stablePolls = 0;
            } else if (current.sameLayoutAndEntry(previous)) {
                stablePolls++;
                if (stablePolls >= MENU_STABLE_POLLS) {
                    return current;
                }
            } else {
                previous = current;
                stablePolls = 1;
            }
            sleep(MENU_POLL_MIN_MS, MENU_POLL_MAX_MS);
        }

        return null;
    }

    private MenuSnapshot readOpenSlotMenu(String desiredOption, int expectedWidgetId) {
        return onClientThread(() -> {
            Client client = Microbot.getClient();
            if (!client.isMenuOpen()) {
                return null;
            }

            Menu menu = client.getMenu();
            if (menu == null) {
                return null;
            }

            int menuX = menu.getMenuX();
            int menuY = menu.getMenuY();
            int menuWidth = menu.getMenuWidth();
            int menuHeight = menu.getMenuHeight();
            MenuEntry[] entries = menu.getMenuEntries();
            if (menuWidth <= 0 || menuHeight <= 0 || entries == null || entries.length == 0) {
                return null;
            }

            for (int i = entries.length - 1; i >= 0; i--) {
                MenuEntry entry = entries[i];
                if (entry == null) {
                    continue;
                }
                if (entry.getParam1() != expectedWidgetId) {
                    continue;
                }
                if (!normalize(entry.getOption()).equalsIgnoreCase(desiredOption)) {
                    continue;
                }
                return new MenuSnapshot(menuX, menuY, menuWidth, menuHeight,
                        entries.length, i, SlotMenuEntry.copyOf(entry));
            }
            return null;
        }, null);
    }

    private boolean closeOpenMenu() {
        if (!isMenuOpen()) {
            return true;
        }
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        return sleepUntil(() -> !isMenuOpen(), 800);
    }

    private boolean isMenuOpen() {
        return onClientThread(() -> Microbot.getClient().isMenuOpen(), false);
    }

    private List<Widget> descendants(Widget root) {
        List<Widget> result = new ArrayList<>();
        Set<Widget> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        collect(root, result, visited);
        return result;
    }

    private void collect(Widget widget, List<Widget> output, Set<Widget> visited) {
        if (widget == null || !visited.add(widget)) {
            return;
        }
        output.add(widget);
        collect(widget.getDynamicChildren(), output, visited);
        collect(widget.getChildren(), output, visited);
        collect(widget.getNestedChildren(), output, visited);
        collect(widget.getStaticChildren(), output, visited);
    }

    private void collect(Widget[] widgets, List<Widget> output, Set<Widget> visited) {
        if (widgets == null) {
            return;
        }
        for (Widget widget : widgets) {
            collect(widget, output, visited);
        }
    }

    private boolean hasLabel(Widget widget, String label) {
        if (label.equalsIgnoreCase(normalize(widget.getText()))
                || label.equalsIgnoreCase(normalize(widget.getName()))) {
            return true;
        }
        String[] actions = widget.getActions();
        if (actions == null) {
            return false;
        }
        for (String action : actions) {
            if (label.equalsIgnoreCase(normalize(action))) {
                return true;
            }
        }
        return false;
    }

    private boolean isVisible(Widget widget) {
        return widget != null && !widget.isHidden() && validBounds(widget.getBounds());
    }

    private Rectangle copyBounds(Widget widget) {
        if (widget == null || widget.getBounds() == null) {
            return null;
        }
        return new Rectangle(widget.getBounds());
    }

    private boolean validBounds(Rectangle rectangle) {
        return rectangle != null && rectangle.width > 0 && rectangle.height > 0;
    }

    private String normalize(String value) {
        return value == null ? "" : value.replaceAll("<[^>]+>", "").trim();
    }

    private <T> T onClientThread(Callable<T> callable, T fallback) {
        try {
            return Microbot.getClientThread().runOnClientThreadOptional(callable).orElse(fallback);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    private static final class SlotTarget {
        private final int widgetId;
        private final Rectangle clickBounds;

        private SlotTarget(int widgetId, Rectangle clickBounds) {
            this.widgetId = widgetId;
            this.clickBounds = clickBounds;
        }
    }

    private static final class MenuSnapshot {
        private final int menuX;
        private final int menuY;
        private final int menuWidth;
        private final int menuHeight;
        private final int entryCount;
        private final int entryIndex;
        private final SlotMenuEntry entry;

        private MenuSnapshot(int menuX, int menuY, int menuWidth, int menuHeight,
                             int entryCount, int entryIndex, SlotMenuEntry entry) {
            this.menuX = menuX;
            this.menuY = menuY;
            this.menuWidth = menuWidth;
            this.menuHeight = menuHeight;
            this.entryCount = entryCount;
            this.entryIndex = entryIndex;
            this.entry = entry;
        }

        private boolean sameLayoutAndEntry(MenuSnapshot other) {
            return other != null
                    && menuX == other.menuX
                    && menuY == other.menuY
                    && menuWidth == other.menuWidth
                    && menuHeight == other.menuHeight
                    && entryCount == other.entryCount
                    && entryIndex == other.entryIndex
                    && entry.sameAction(other.entry);
        }

        private Rectangle entryBounds() {
            if (entryCount <= 0 || entryIndex < 0 || entryIndex >= entryCount
                    || menuWidth <= 4 || menuHeight <= MENU_ENTRY_HEIGHT) {
                return null;
            }
            int rowCenterY = menuY + menuHeight - 8 - entryIndex * MENU_ENTRY_HEIGHT;
            int top = rowCenterY - 7 + 1;
            return new Rectangle(menuX + 2, top,
                    Math.max(1, menuWidth - 4), 13);
        }
    }

    private static final class SlotMenuEntry {
        private final String option;
        private final int identifier;
        private final MenuAction type;
        private final int param0;
        private final int param1;
        private final int itemId;
        private final int worldViewId;

        private SlotMenuEntry(String option, int identifier, MenuAction type,
                              int param0, int param1, int itemId, int worldViewId) {
            this.option = option;
            this.identifier = identifier;
            this.type = type;
            this.param0 = param0;
            this.param1 = param1;
            this.itemId = itemId;
            this.worldViewId = worldViewId;
        }

        private static SlotMenuEntry copyOf(MenuEntry entry) {
            return new SlotMenuEntry(entry.getOption(), entry.getIdentifier(), entry.getType(),
                    entry.getParam0(), entry.getParam1(), entry.getItemId(), entry.getWorldViewId());
        }

        private boolean sameAction(SlotMenuEntry other) {
            return other != null
                    && identifier == other.identifier
                    && type == other.type
                    && param0 == other.param0
                    && param1 == other.param1
                    && itemId == other.itemId
                    && worldViewId == other.worldViewId
                    && normalizeStatic(option).equalsIgnoreCase(normalizeStatic(other.option));
        }

        private static String normalizeStatic(String value) {
            return value == null ? "" : value.replaceAll("<[^>]+>", "").trim();
        }
    }
}
