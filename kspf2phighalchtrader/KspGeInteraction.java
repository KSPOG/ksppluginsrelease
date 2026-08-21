package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import lombok.RequiredArgsConstructor;
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

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/** Hardened GE interaction helpers for live slot/menu verification. */
final class KspGeInteraction {
    private static final int FIRST_GE_SLOT_CHILD = 7;
    private static final int GE_SLOT_WIDGET_COUNT = 8;
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
        while (System.currentTimeMillis() < deadline && !Thread.currentThread().isInterrupted()) {
            GrandExchangeSlots resolved = Rs2GrandExchange.findSlotForItem(itemId, false);
            if (resolved == null && itemName != null) {
                resolved = Rs2GrandExchange.findSlotForItem(itemName, false);
            }
            if (resolved != null) {
                return resolved;
            }
            sleep(70, 120);
        }

        if (Rs2GrandExchange.isOfferScreenOpen()) {
            Rs2GrandExchange.backToOverview();
            sleepUntil(() -> !Rs2GrandExchange.isOfferScreenOpen(), 1500);
        }
        return requestStarted ? Rs2GrandExchange.findSlotForItem(itemId, false) : null;
    }

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

            Microbot.getMouse().click(
                    target.clickBounds.x + target.clickBounds.width / 2,
                    target.clickBounds.y + target.clickBounds.height / 2,
                    true);

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
            return validBounds(bounds) ? new SlotTarget(widget.getId(), new Rectangle(bounds)) : null;
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
                if (++stablePolls >= MENU_STABLE_POLLS) {
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
                if (entry != null
                        && entry.getParam1() == expectedWidgetId
                        && normalize(entry.getOption()).equalsIgnoreCase(desiredOption)) {
                    return new MenuSnapshot(menuX, menuY, menuWidth, menuHeight,
                            entries.length, i, SlotMenuEntry.copyOf(entry));
                }
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

    private boolean isMenuOpen() { return onClientThread(() -> Microbot.getClient().isMenuOpen(), false); }

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
        if (actions != null) {
            for (String action : actions) {
                if (label.equalsIgnoreCase(normalize(action))) {
                    return true;
                }
            }
        }
        return false;
    }

    private boolean isVisible(Widget widget) { return widget != null && !widget.isHidden() && validBounds(widget.getBounds()); }
    private Rectangle copyBounds(Widget widget) { return widget == null || widget.getBounds() == null ? null : new Rectangle(widget.getBounds()); }
    private boolean validBounds(Rectangle rectangle) { return rectangle != null && rectangle.width > 0 && rectangle.height > 0; }
    private static String normalize(String value) { return value == null ? "" : value.replaceAll("<[^>]+>", "").trim(); }

    private <T> T onClientThread(Callable<T> callable, T fallback) {
        try {
            return Microbot.getClientThread().runOnClientThreadOptional(callable).orElse(fallback);
        } catch (RuntimeException ex) {
            return fallback;
        }
    }

    @RequiredArgsConstructor
    private static final class SlotTarget {
        private final int widgetId;
        private final Rectangle clickBounds;
    }

    @RequiredArgsConstructor
    private static final class MenuSnapshot {
        private final int menuX;
        private final int menuY;
        private final int menuWidth;
        private final int menuHeight;
        private final int entryCount;
        private final int entryIndex;
        private final SlotMenuEntry entry;

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
            return new Rectangle(menuX + 2, rowCenterY - 6,
                    Math.max(1, menuWidth - 4), 13);
        }
    }

    @RequiredArgsConstructor
    private static final class SlotMenuEntry {
        private final String option;
        private final int identifier;
        private final MenuAction type;
        private final int param0;
        private final int param1;
        private final int itemId;
        private final int worldViewId;

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
                    && normalize(option).equalsIgnoreCase(normalize(other.option));
        }
    }
}
