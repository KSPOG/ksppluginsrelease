package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.awt.Rectangle;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ItemComposition;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * Real-bank movement primitive. The widget IDs and drag flow mirror the current
 * Microbot-Hub Bank Organizer implementation instead of inventing a new bank API.
 */
final class BankActuator
{
    private static final int BANK_GROUP_ID = 12;
    private static final int BANK_REARRANGE_BUTTON_CHILD_ID = 23;
    private static final int BANK_TAB_CONTAINER_DYNAMIC_MAIN_INDEX = 10;

    private static final int[] TAB_COUNT_VARBITS = {
        Varbits.BANK_TAB_ONE_COUNT,
        Varbits.BANK_TAB_TWO_COUNT,
        Varbits.BANK_TAB_THREE_COUNT,
        Varbits.BANK_TAB_FOUR_COUNT,
        Varbits.BANK_TAB_FIVE_COUNT,
        Varbits.BANK_TAB_SIX_COUNT,
        Varbits.BANK_TAB_SEVEN_COUNT,
        Varbits.BANK_TAB_EIGHT_COUNT,
        Varbits.BANK_TAB_NINE_COUNT
    };

    private final Client client;
    private final BankSnapshotReader snapshotReader;

    @Inject
    BankActuator(Client client, BankSnapshotReader snapshotReader)
    {
        this.client = client;
        this.snapshotReader = snapshotReader;
    }

    boolean ensureBankOpen()
    {
        // Do not use nearest-bank/pathfinding when the bank interface is already
        // on screen. The live RuneLite widget tree is the authoritative UI signal.
        if (isBankUiOpenOnClient())
        {
            // Give the bank container a short settling window, but do not require
            // rearrangement controls for Preview/Scan. Those controls are only
            // needed later when Organize actually starts moving items.
            Global.sleepUntil(this::isLiveBankItemWidgetPresent, 2000);
            return true;
        }

        // Only attempt Rs2Bank.openBank() when the bank UI is genuinely closed.
        if (!Rs2Bank.openBank() && !isBankUiOpenOnClient())
        {
            return false;
        }

        return Global.sleepUntil(this::isBankUiOpenOnClient, 5000);
    }

    private boolean isBankUiOpenOnClient()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            // Bankmain.UNIVERSE (12:1) is the same root used by Rs2Bank.isOpen(),
            // but reading it directly avoids a race with the Microbot bank mirror.
            Widget universe = client.getWidget(BANK_GROUP_ID, 1);
            if (universe == null || universe.isHidden())
            {
                return false;
            }
            Rectangle bounds = universe.getBounds();
            return bounds != null && bounds.width > 0 && bounds.height > 0;
        }).orElse(false);
    }

    private boolean isRs2BankOpen()
    {
        try
        {
            if (Rs2Bank.isOpen())
            {
                return true;
            }
        }
        catch (Throwable ignored)
        {
            // Fall through to the direct widget check. This keeps the organizer
            // usable during the short period where Rs2Bank is synchronizing.
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            // Rs2Bank.isOpen() currently resolves Bankmain.UNIVERSE (12:1).
            Widget universe = client.getWidget(BANK_GROUP_ID, 1);
            return universe != null
                && !universe.isHidden()
                && universe.getBounds() != null
                && universe.getBounds().width > 0
                && universe.getBounds().height > 0;
        }).orElse(false);
    }

    private boolean isLiveBankInterfacePresent()
    {
        return isRs2BankOpen();
    }

    private boolean isLiveBankItemWidgetPresent()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            // Current Bankmain layout exposes the actual item widget at 12:12.
            // Keep the container fallback because some builds expose 12:9 first.
            Widget items = client.getWidget(BANK_GROUP_ID, 12);
            if (items != null && !items.isHidden())
            {
                return items.getBounds() != null && items.getBounds().width > 0 && items.getBounds().height > 0;
            }

            Widget container = client.getWidget(BANK_GROUP_ID, 9);
            return container != null
                && !container.isHidden()
                && container.getBounds() != null
                && container.getBounds().width > 0
                && container.getBounds().height > 0;
        }).orElse(false);
    }

    private boolean isRearrangeControlReadyOnClient(boolean insert)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget widget = client.getWidget(BANK_GROUP_ID, BANK_REARRANGE_BUTTON_CHILD_ID);
            if (widget == null || widget.isHidden())
            {
                return false;
            }

            // The bank uses ONE SWAP/INSERT toggle at 12:23. Its action changes
            // with the current mode. Do not require a separate 12:19 widget and
            // do not reject the control merely because getBounds() is temporarily
            // unavailable during a bank-widget rebuild.
            String[] actions = widget.getActions();
            if (actions == null)
            {
                return false;
            }

            String wanted = insert ? "insert" : "swap";
            for (String action : actions)
            {
                if (action != null && action.toLowerCase(java.util.Locale.ROOT).contains(wanted))
                {
                    return true;
                }
            }
            return false;
        }).orElse(false);
    }

    private boolean clickRearrangeControlOnClient(boolean insert)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget widget = client.getWidget(BANK_GROUP_ID, BANK_REARRANGE_BUTTON_CHILD_ID);
            if (widget == null || widget.isHidden())
            {
                return false;
            }

            String[] actions = widget.getActions();
            String wanted = insert ? "insert" : "swap";
            boolean matchingAction = false;
            if (actions != null)
            {
                for (String action : actions)
                {
                    if (action != null && action.toLowerCase(java.util.Locale.ROOT).contains(wanted))
                    {
                        matchingAction = true;
                        break;
                    }
                }
            }

            if (!matchingAction)
            {
                return false;
            }

            Rectangle bounds = widget.getBounds();
            if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
            {
                return false;
            }

            Microbot.getMouse().click(bounds);
            return true;
        }).orElse(false);
    }

    boolean isBankInsertMode()
    {
        return bankRearrangeMode() == 1;
    }

    boolean isBankSwapMode()
    {
        return bankRearrangeMode() == 0;
    }

    ActuatorResult ensureBankInsertMode()
    {
        return ensureBankRearrangeMode(true);
    }

    ActuatorResult ensureBankSwapMode()
    {
        return ensureBankRearrangeMode(false);
    }

    private ActuatorResult ensureBankRearrangeMode(boolean insert)
    {
        if (!ensureBankOpen())
        {
            return ActuatorResult.fail("Bank is not open.");
        }

        int desired = insert ? 1 : 0;
        int current = bankRearrangeMode();
        if (current == desired)
        {
            return ActuatorResult.ok("Bank rearrange mode is already " + (insert ? "Insert" : "Swap") + ".");
        }

        int childId = BANK_REARRANGE_BUTTON_CHILD_ID;
        String targetName = insert ? "Insert" : "Swap";

        // 12:23 is the single Bankmain.SWAP_INSERT toggle. Wait for the actual action exposed by the
        // live widget rather than merely checking bounds.
        boolean controlReady = Global.sleepUntil(
            () -> isRearrangeControlReadyOnClient(insert),
            5000);
        if (!controlReady)
        {
            return ActuatorResult.fail(
                "Bank rearrange toggle " + BANK_GROUP_ID + ":" + childId
                    + " did not expose the '" + targetName + "' action within 5 seconds. Current mode="
                    + bankRearrangeMode() + ".");
        }

        for (int attempt = 1; attempt <= 5; attempt++)
        {
            if (Thread.currentThread().isInterrupted())
            {
                return ActuatorResult.fail("Interrupted while switching bank rearrangement mode.");
            }

            if (!isRearrangeControlReadyOnClient(insert))
            {
                if (!Global.sleepUntil(() -> isRearrangeControlReadyOnClient(insert), 1500))
                {
                    continue;
                }
            }

            boolean clicked = clickRearrangeControlOnClient(insert);
            if (!clicked)
            {
                Global.sleep(200);
                continue;
            }

            if (Global.sleepUntil(() -> bankRearrangeMode() == desired, 1500))
            {
                return ActuatorResult.ok("Bank rearrange mode set to " + targetName + ".");
            }

            if (bankRearrangeMode() == desired)
            {
                return ActuatorResult.ok("Bank rearrange mode set to " + targetName + ".");
            }

            Global.sleep(120);
        }

        return ActuatorResult.fail(
            "Could not switch bank rearrange mode to " + targetName
                + " after 5 attempts. Current mode=" + bankRearrangeMode()
                + ". Toggle widget=" + BANK_GROUP_ID + ":" + childId + ".");
    }

    // Bank rearrangement uses one live toggle in the bank interface: 12:23.
    // Its action changes with BANK_REARRANGE_MODE, so there is no separate
    // There is no separate Insert control.
    // Use Rs2Widget's client-thread-aware lookup/click path instead of recursively
    // selecting an arbitrary widget whose action happens to contain "swap".
    ActuatorResult openTab(int tabIndex)
    {
        if (!ensureBankOpen())
        {
            return ActuatorResult.fail("Bank is not open.");
        }
        if (tabIndex == 0)
        {
            if (currentTab() == 0)
            {
                return ActuatorResult.ok("Main tab already open.");
            }
            if (!invokeBankTab(0))
            {
                return ActuatorResult.fail("Could not invoke the main bank tab on the client thread.");
            }
            return Global.sleepUntil(() -> currentTab() == 0, 2500)
                ? ActuatorResult.ok("Main tab opened.")
                : ActuatorResult.fail("Main tab did not become active.");
        }

        if (tabIndex < 1 || tabIndex > 9 || tabCount(tabIndex) <= 0)
        {
            return ActuatorResult.fail("Tab " + tabIndex + " does not exist.");
        }
        if (currentTab() == tabIndex)
        {
            return ActuatorResult.ok("Tab " + tabIndex + " already open.");
        }
        if (!invokeBankTab(tabIndex))
        {
            return ActuatorResult.fail("Could not invoke tab " + tabIndex + " on the client thread.");
        }
        return Global.sleepUntil(() -> currentTab() == tabIndex, 2500)
            ? ActuatorResult.ok("Tab " + tabIndex + " opened.")
            : ActuatorResult.fail("Tab " + tabIndex + " did not become active.");
    }

    ActuatorResult moveToMain(int itemId, int sourceTab)
    {
        if (sourceTab == 0)
        {
            return ActuatorResult.ok("Item is already in main.");
        }
        ActuatorResult open = openTab(sourceTab);
        if (!open.success())
        {
            return open;
        }
        Rs2ItemModel item = findBankItem(itemId);
        if (item == null)
        {
            return ActuatorResult.fail("Could not find item " + itemId + " in the bank.");
        }
        if (!scrollBankToSlotSafe(item.getSlot()))
        {
            return ActuatorResult.fail("Could not scroll source item into view.");
        }
        DragBounds bounds = itemToTabBounds(item.getSlot(), BANK_TAB_CONTAINER_DYNAMIC_MAIN_INDEX);
        if (bounds == null)
        {
            return ActuatorResult.fail("Source or main-tab bounds were unavailable/outside the canvas.");
        }
        int quantity = item.getQuantity();
        Microbot.drag(bounds.source(), bounds.target());
        boolean verified = waitForItemTab(itemId, 0, quantity, 5000);
        return verified ? ActuatorResult.ok("Moved item to main.") : ActuatorResult.fail("Move to main was not verified.");
    }

    ActuatorResult moveToExistingTab(int itemId, int sourceTab, int targetTab)
    {
        if (sourceTab == targetTab)
        {
            return ActuatorResult.ok("Item is already in tab " + targetTab + ".");
        }
        if (targetTab < 1 || targetTab > 9 || tabCount(targetTab) <= 0)
        {
            return ActuatorResult.fail("Destination tab " + targetTab + " does not exist.");
        }
        ActuatorResult open = openTab(sourceTab);
        if (!open.success())
        {
            return open;
        }
        Rs2ItemModel item = findBankItem(itemId);
        if (item == null)
        {
            return ActuatorResult.fail("Could not find item " + itemId + " in the bank.");
        }
        if (!scrollBankToSlotSafe(item.getSlot()))
        {
            return ActuatorResult.fail("Could not scroll source item into view.");
        }
        DragBounds bounds = itemToTabBounds(item.getSlot(), BANK_TAB_CONTAINER_DYNAMIC_MAIN_INDEX + targetTab);
        if (bounds == null)
        {
            return ActuatorResult.fail("Source or destination-tab bounds were unavailable/outside the canvas.");
        }
        int quantity = item.getQuantity();
        Microbot.drag(bounds.source(), bounds.target());
        boolean verified = waitForItemTab(itemId, targetTab, quantity, 5000);
        return verified
            ? ActuatorResult.ok("Moved item to tab " + targetTab + ".")
            : ActuatorResult.fail("Move to tab " + targetTab + " was not verified.");
    }

    ActuatorResult moveToNewTab(int itemId, int sourceTab)
    {
        ActuatorResult open = openTab(sourceTab);
        if (!open.success())
        {
            return open;
        }
        int newTab = realTabCount() + 1;
        if (newTab > 9)
        {
            return ActuatorResult.fail("All nine real bank tabs already exist.");
        }
        Rs2ItemModel item = findBankItem(itemId);
        if (item == null)
        {
            return ActuatorResult.fail("Could not find item " + itemId + " in the bank.");
        }
        if (!scrollBankToSlotSafe(item.getSlot()))
        {
            return ActuatorResult.fail("Could not scroll source item into view.");
        }
        DragBounds bounds = itemToTabBounds(item.getSlot(), BANK_TAB_CONTAINER_DYNAMIC_MAIN_INDEX + newTab);
        if (bounds == null)
        {
            return ActuatorResult.fail("Source or new-tab bounds were unavailable/outside the canvas.");
        }
        int quantity = item.getQuantity();
        Microbot.drag(bounds.source(), bounds.target());
        boolean verified = waitForItemTab(itemId, newTab, quantity, 5000);
        return verified
            ? ActuatorResult.ok("Created tab " + newTab + " with item.")
            : ActuatorResult.fail("New-tab drag was not verified.");
    }

    ActuatorResult moveWithinOpenTab(BankSnapshot.BankStack sourceStack, BankSnapshot.BankStack targetStack)
    {
        if (!scrollBankToSlotSafe(sourceStack.slot()))
        {
            return ActuatorResult.fail("Could not scroll source item into view.");
        }
        DragBounds bounds = itemToItemBounds(sourceStack.slot(), targetStack.slot());
        if (bounds == null)
        {
            return ActuatorResult.fail("Source or target slot bounds were unavailable/outside the canvas.");
        }
        Microbot.drag(bounds.source(), bounds.target());
        return ActuatorResult.ok("Inserted " + sourceStack.name() + " before " + targetStack.name() + ".");
    }

    int realTabCount()
    {
        int highest = 0;
        for (int i = 1; i <= 9; i++)
        {
            if (tabCount(i) > 0)
            {
                highest = i;
            }
        }
        return highest;
    }

    int tabCount(int tabIndex)
    {
        if (tabIndex < 1 || tabIndex > TAB_COUNT_VARBITS.length)
        {
            return 0;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(
            () -> client.getVarbitValue(TAB_COUNT_VARBITS[tabIndex - 1])).orElse(0);
    }

    int bankRearrangeMode()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(
            () -> client.getVarbitValue(Varbits.BANK_REARRANGE_MODE)).orElse(-1);
    }

    private Rs2ItemModel findBankItem(int itemId)
    {
        /*
         * Do not use Rs2Bank.bankItems() here. That is a mirrored helper cache
         * and can lag the live BANK ItemContainer by a tick after a tab move.
         * The snapshot reader reads the actual client ItemContainer and is the
         * authoritative source for the slot used by the drag.
         */
        try
        {
            BankSnapshot snapshot = snapshotReader.read();
            BankSnapshot.BankStack stack = stackByItemId(snapshot, itemId);
            if (stack == null)
            {
                return null;
            }

            return Microbot.getClientThread().runOnClientThreadOptional(() ->
            {
                ItemContainer container = client.getItemContainer(InventoryID.BANK);
                if (container == null)
                {
                    return null;
                }

                Item item = container.getItem(stack.slot());
                if (item == null || item.getId() != itemId)
                {
                    return null;
                }

                ItemComposition composition = client.getItemDefinition(itemId);
                return composition == null ? null : new Rs2ItemModel(item, composition, stack.slot());
            }).orElse(null);
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private int currentTab()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(Rs2Bank::getCurrentTab).orElse(-1);
    }

    /** Invoke a real bank tab without allowing a Widget object to escape the client thread. */
    private boolean invokeBankTab(int tabIndex)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            List<Widget> tabs = Rs2Bank.getTabs();
            int dynamicIndex = BANK_TAB_CONTAINER_DYNAMIC_MAIN_INDEX + tabIndex;
            if (tabs == null || dynamicIndex < 0 || dynamicIndex >= tabs.size())
            {
                return false;
            }

            Widget tab = tabs.get(dynamicIndex);
            if (tab == null || tab.isHidden() || !hasUsableCanvasRectangle(tab.getBounds()))
            {
                return false;
            }

            Rs2Widget.clickWidgetFast(tab, dynamicIndex);
            return true;
        }).orElse(false);
    }

    /**
     * Rs2Bank.scrollBankToSlot currently performs live Widget reads/writes. Run
     * the complete helper on the client thread so its Widget access never occurs
     * on the organizer worker.
     */
    private boolean scrollBankToSlotSafe(int slot)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(
            () -> Rs2Bank.scrollBankToSlot(slot)).orElse(false);
    }

    /**
     * Reads all Widget-backed drag rectangles on the RuneLite client thread and
     * copies them into ordinary AWT rectangles before returning to the worker.
     * No Widget instance escapes this method.
     */
    private DragBounds itemToTabBounds(int sourceSlot, int dynamicTabIndex)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            /*
             * IMPORTANT: Rs2Bank.getItemBounds() expects the ORIGINAL global
             * BANK ItemContainer slot. Microbot's own itemBounds(Rs2ItemModel)
             * passes rs2Item.getSlot() directly; it does not convert it to a
             * per-tab local index.
             */
            Rectangle source = Rs2Bank.getItemBounds(sourceSlot);
            List<Widget> tabs = Rs2Bank.getTabs();
            if (tabs == null || dynamicTabIndex < 0 || dynamicTabIndex >= tabs.size())
            {
                return null;
            }

            Widget tab = tabs.get(dynamicTabIndex);
            if (tab == null || tab.isHidden())
            {
                return null;
            }

            Rectangle target = tab.getBounds();
            if (!hasUsableCanvasRectangle(source) || !hasUsableCanvasRectangle(target))
            {
                return null;
            }

            return new DragBounds(new Rectangle(source), new Rectangle(target));
        }).orElse(null);
    }

    private DragBounds itemToItemBounds(int sourceSlot, int targetSlot)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Rectangle source = Rs2Bank.getItemBounds(sourceSlot);
            Rectangle target = Rs2Bank.getItemBounds(targetSlot);
            if (!hasUsableCanvasRectangle(source) || !hasUsableCanvasRectangle(target))
            {
                return null;
            }
            return new DragBounds(new Rectangle(source), new Rectangle(target));
        }).orElse(null);
    }

    private boolean hasUsableCanvasRectangle(Rectangle rectangle)
    {
        return rectangle != null
            && rectangle.width > 0
            && rectangle.height > 0
            && rectangle.x >= 0
            && rectangle.y >= 0
            && rectangle.x < client.getCanvasWidth()
            && rectangle.y < client.getCanvasHeight();
    }

    private boolean waitForItemTab(int itemId, int expectedTab, int expectedQuantity, int timeoutMs)
    {
        /*
         * Verification must answer the question that actually matters:
         * "Is this item now inside the destination tab?"
         *
         * Do not infer this from a raw global slot or from tab-count arithmetic.
         * After a drag, the bank can compact/reindex items. Instead, open the
         * destination tab and inspect the live Bankmain.ITEMS widget children.
         */
        return Global.sleepUntil(() ->
        {
            if (!isBankOpenOnClientThread())
            {
                return false;
            }

            if (!openTabForVerification(expectedTab))
            {
                return false;
            }

            return Microbot.getClientThread().runOnClientThreadOptional(() ->
            {
                Widget bankItemsWidget = client.getWidget(Rs2Bank.BANK_ITEM_CONTAINER);
                if (bankItemsWidget == null || bankItemsWidget.isHidden())
                {
                    return false;
                }

                Widget[] children = bankItemsWidget.getDynamicChildren();
                if (children == null)
                {
                    return false;
                }

                for (Widget child : children)
                {
                    if (child == null)
                    {
                        continue;
                    }

                    if (child.getItemId() == itemId
                        && (expectedQuantity <= 0 || child.getItemQuantity() == expectedQuantity))
                    {
                        return true;
                    }
                }

                return false;
            }).orElse(false);
        }, timeoutMs);
    }

    private boolean openTabForVerification(int tab)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (Rs2Bank.getCurrentTab() == tab)
            {
                return true;
            }

            List<Widget> tabs = Rs2Bank.getTabs();
            int dynamicIndex = BANK_TAB_CONTAINER_DYNAMIC_MAIN_INDEX + tab;
            if (tabs == null || dynamicIndex < 0 || dynamicIndex >= tabs.size())
            {
                return false;
            }

            Widget widget = tabs.get(dynamicIndex);
            if (widget == null || widget.isHidden())
            {
                return false;
            }

            Rectangle bounds = widget.getBounds();
            if (!hasUsableCanvasRectangle(bounds))
            {
                return false;
            }

            Microbot.getMouse().click(bounds);
            return true;
        }).orElse(false);
    }

    private boolean isBankOpenOnClientThread()
    {
        return client.getWidget(12, 1) != null
            && !client.getWidget(12, 1).isHidden();
    }

    private BankSnapshot safeSnapshot()
    {
        try
        {
            return snapshotReader.read();
        }
        catch (Throwable ignored)
        {
            return null;
        }
    }

    private static BankSnapshot.BankStack stackByItemId(BankSnapshot snapshot, int itemId)
    {
        if (snapshot == null)
        {
            return null;
        }
        for (BankSnapshot.BankStack stack : snapshot.items())
        {
            if (stack.itemId() == itemId)
            {
                return stack;
            }
        }
        return null;
    }

    private enum InsertInvokeState
    {
        INVOKED,
        MISSING,
        OUTSIDE_CANVAS,
        CLIENT_THREAD_FAILED
    }

    private static final class DragBounds
    {
        private final Rectangle source;
        private final Rectangle target;

        private DragBounds(Rectangle source, Rectangle target)
        {
            this.source = source;
            this.target = target;
        }

        Rectangle source() { return source; }
        Rectangle target() { return target; }
    }

    static final class ActuatorResult
    {
        private final boolean success;
        private final String message;

        private ActuatorResult(boolean success, String message)
        {
            this.success = success;
            this.message = message;
        }

        static ActuatorResult ok(String message) { return new ActuatorResult(true, message); }
        static ActuatorResult fail(String message) { return new ActuatorResult(false, message); }
        boolean success() { return success; }
        String message() { return message; }
    }
}
