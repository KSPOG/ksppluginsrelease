package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.awt.Rectangle;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
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
    private static final int BANK_SWAP_BUTTON_CHILD_ID = 17;
    private static final int BANK_INSERT_BUTTON_CHILD_ID = 19;
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
        if (Rs2Bank.isOpen())
        {
            return true;
        }
        return Rs2Bank.openBank() && Global.sleepUntil(Rs2Bank::isOpen, 5000);
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

        int childId = insert ? BANK_INSERT_BUTTON_CHILD_ID : BANK_SWAP_BUTTON_CHILD_ID;
        String targetName = insert ? "Insert" : "Swap";

        if (!Rs2Widget.isWidgetVisible(BANK_GROUP_ID, childId))
        {
            return ActuatorResult.fail(
                "Bank " + targetName + " control " + BANK_GROUP_ID + ":" + childId
                    + " is not visible. Current mode=" + current + ".");
        }

        for (int attempt = 1; attempt <= 3; attempt++)
        {
            if (Thread.currentThread().isInterrupted())
            {
                return ActuatorResult.fail("Interrupted while switching bank rearrangement mode.");
            }

            boolean clicked = Rs2Widget.clickWidget(BANK_GROUP_ID, childId);
            if (!clicked)
            {
                Global.sleep(100);
                continue;
            }

            if (Global.sleepUntil(() -> bankRearrangeMode() == desired, 1500))
            {
                return ActuatorResult.ok("Bank rearrange mode set to " + targetName + ".");
            }

            // Re-read the state before retrying. Do not blindly click if another
            // action already changed the mode.
            if (bankRearrangeMode() == desired)
            {
                return ActuatorResult.ok("Bank rearrange mode set to " + targetName + ".");
            }
            Global.sleep(120);
        }

        return ActuatorResult.fail(
            "Could not switch bank rearrange mode to " + targetName
                + " after 3 attempts. Current mode=" + bankRearrangeMode()
                + ". Expected widget=" + BANK_GROUP_ID + ":" + childId + ".");
    }

    // Bank rearrangement controls are fixed in the live bank interface used by
    // the current Microbot/RuneLite build: 12:17 = Swap and 12:19 = Insert.
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
        boolean verified = Global.sleepUntil(() -> {
            BankSnapshot.BankStack moved = stackByItemId(safeSnapshot(), itemId);
            return moved != null && moved.tab() == 0 && moved.quantity() == quantity;
        }, 5000);
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
        boolean verified = Global.sleepUntil(() -> {
            BankSnapshot.BankStack moved = stackByItemId(safeSnapshot(), itemId);
            return moved != null && moved.tab() == targetTab && moved.quantity() == quantity;
        }, 5000);
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
        boolean verified = Global.sleepUntil(() -> {
            BankSnapshot.BankStack moved = stackByItemId(safeSnapshot(), itemId);
            return moved != null && moved.tab() == newTab && moved.quantity() == quantity && tabCount(newTab) > 0;
        }, 5000);
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

    private static Rs2ItemModel findBankItem(int itemId)
    {
        return Rs2Bank.bankItems().stream()
            .filter(item -> item.getId() == itemId)
            .min(Comparator.comparingInt(Rs2ItemModel::getSlot))
            .orElse(null);
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
            if (tab == null || tab.isHidden() || !validCanvasRectangleOnClientThread(tab.getBounds()))
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
            if (!validCanvasRectangleOnClientThread(source) || !validCanvasRectangleOnClientThread(target))
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
            if (!validCanvasRectangleOnClientThread(source) || !validCanvasRectangleOnClientThread(target))
            {
                return null;
            }
            return new DragBounds(new Rectangle(source), new Rectangle(target));
        }).orElse(null);
    }

    private boolean validCanvasRectangleOnClientThread(Rectangle rectangle)
    {
        if (rectangle == null || rectangle.width <= 0 || rectangle.height <= 0 || rectangle.x < 0 || rectangle.y < 0)
        {
            return false;
        }
        return rectangle.x + rectangle.width <= client.getCanvasWidth()
            && rectangle.y + rectangle.height <= client.getCanvasHeight();
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
