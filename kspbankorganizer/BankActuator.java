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
import net.runelite.api.MenuAction;
import net.runelite.api.NPCComposition;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;

/**
 * Real-bank movement primitive. The widget IDs and drag flow mirror the current
 * Microbot-Hub Bank Organizer implementation instead of inventing a new bank API.
 */
final class BankActuator
{
    private static final int BANK_GROUP_ID = 12;
    private static final int BANK_REARRANGE_BUTTON_CHILD_ID = 23;

    // Bank widgets are rebuilt asynchronously by the client. These pauses are
    // deliberately conservative so we never take drag coordinates from a
    // partially rebuilt bank.
    private static final int TAB_SETTLE_MS = 450;
    private static final int ACTION_SETTLE_MS = 750;
    private static final int MOVE_VERIFY_MS = 5000;

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
            // Do not advance merely because the bank root is visible. A newly
            // opened bank rebuilds its item widget before the live BANK
            // ItemContainer used by BankSnapshotReader is always available.
            return Global.sleepUntil(this::isBankReadyForSnapshotOnClient, 4000);
        }

        // When a banker is already beside the player, invoke its Bank option
        // directly. Rs2Bank.openBank() correctly handles distant banks, but its
        // shared unreachable-target recovery can enqueue a redundant walk before
        // reaching this interaction. That stale walk is cancelled when the bank
        // opens and can outlive the successful bank action.
        boolean bankInteractionStarted = openNearbyBanker() || Rs2Bank.openBank();
        if (!bankInteractionStarted && !isBankUiOpenOnClient())
        {
            return false;
        }

        // A visible bank root can precede the usable item container while the
        // interface is rebuilding. Do not report the bank interaction as
        // complete until the same live container required by snapshot reads is
        // available.
        if (Global.sleepUntil(this::isBankReadyForSnapshotOnClient, 5000))
        {
            return true;
        }

        // openBank() may first walk to a booth. Once that walk completes, retry
        // the interaction against the now-near banker before declaring the
        // opening failed. This is a postcondition-driven retry, not a blind
        // delay, and avoids leaving a completed approach with no bank click.
        bankInteractionStarted = openNearbyBanker() || Rs2Bank.openBank();
        return bankInteractionStarted
            && Global.sleepUntil(this::isBankReadyForSnapshotOnClient, 5000);
    }

    private boolean openNearbyBanker()
    {
        Rs2NpcModel banker = Rs2Npc.getBankerNPC();
        if (banker == null || !isNearPlayerOnClient(banker, 4))
        {
            return false;
        }

        return invokeNpcBankAction(banker);
    }

    private boolean isNearPlayerOnClient(Rs2NpcModel npc, int maxDistance)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (client.getLocalPlayer() == null
                || client.getLocalPlayer().getWorldLocation() == null
                || npc.getWorldLocation() == null)
            {
                return false;
            }
            return npc.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation()) <= maxDistance;
        }).orElse(false);
    }

    /**
     * This is the same client-menu invocation used by Rs2Npc.interact, without
     * the global unreachable-target recovery that is unnecessary for a banker
     * already within interaction range.
     */
    private boolean invokeNpcBankAction(Rs2NpcModel npc)
    {
        NPCComposition composition = Microbot.getClientThread().runOnClientThreadOptional(
            () -> client.getNpcDefinition(npc.getId())).orElse(null);
        if (composition == null || composition.getActions() == null)
        {
            return false;
        }

        String[] actions = composition.getActions();
        for (int index = 0; index < actions.length; index++)
        {
            if (!"Bank".equalsIgnoreCase(actions[index]))
            {
                continue;
            }

            MenuAction menuAction = npcMenuAction(index);
            if (menuAction == null || npc.getLocalLocation() == null)
            {
                return false;
            }
            if (!Rs2Camera.isTileOnScreen(npc.getLocalLocation()))
            {
                Rs2Camera.turnTo(npc);
            }

            Microbot.doInvoke(new NewMenuEntry()
                    .param0(0)
                    .param1(0)
                    .opcode(menuAction.getId())
                    .identifier(npc.getIndex())
                    .itemId(-1)
                    .target(npc.getName())
                    .actor(npc)
                    .option(actions[index]),
                Rs2UiHelper.getActorClickbox(npc));
            return true;
        }
        return false;
    }

    private MenuAction npcMenuAction(int index)
    {
        switch (index)
        {
            case 0:
                return MenuAction.NPC_FIRST_OPTION;
            case 1:
                return MenuAction.NPC_SECOND_OPTION;
            case 2:
                return MenuAction.NPC_THIRD_OPTION;
            case 3:
                return MenuAction.NPC_FOURTH_OPTION;
            case 4:
                return MenuAction.NPC_FIFTH_OPTION;
            default:
                return null;
        }
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

    /**
     * The organizer reads the live BANK ItemContainer immediately after
     * opening. A rendered item widget alone is therefore insufficient: it can
     * briefly survive a bank-interface rebuild after its backing container has
     * been cleared. Keep the UI and data postconditions together.
     */
    private boolean isBankReadyForSnapshotOnClient()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget universe = client.getWidget(BANK_GROUP_ID, 1);
            if (universe == null || universe.isHidden() || !hasUsableCanvasRectangle(universe.getBounds()))
            {
                return false;
            }

            Widget items = client.getWidget(BANK_GROUP_ID, 12);
            if (items == null || items.isHidden() || !hasUsableCanvasRectangle(items.getBounds()))
            {
                items = client.getWidget(BANK_GROUP_ID, 9);
                if (items == null || items.isHidden() || !hasUsableCanvasRectangle(items.getBounds()))
                {
                    return false;
                }
            }

            ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
            return bankContainer != null;
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
            Global.sleep(TAB_SETTLE_MS);
            return Global.sleepUntil(() -> currentTab() == 0, 4000)
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
        Global.sleep(TAB_SETTLE_MS);
        return Global.sleepUntil(() -> currentTab() == tabIndex, 4000)
            ? ActuatorResult.ok("Tab " + tabIndex + " opened.")
            : ActuatorResult.fail("Tab " + tabIndex + " did not become active.");
    }

    ActuatorResult moveToMain(int itemId, int sourceTab)
    {
        if (sourceTab == 0)
        {
            return ActuatorResult.ok("Item is already in main.");
        }
        return moveItemToTabWithRetry(itemId, sourceTab, 0, "main");
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
        return moveItemToTabWithRetry(itemId, sourceTab, targetTab, "tab " + targetTab);
    }

    ActuatorResult moveToNewTab(int itemId, int sourceTab)
    {
        int newTab = realTabCount() + 1;
        if (newTab > 9)
        {
            return ActuatorResult.fail("All nine real bank tabs already exist.");
        }
        return moveItemToTabWithRetry(itemId, sourceTab, newTab, "new tab " + newTab);
    }

    private ActuatorResult moveItemToTabWithRetry(int itemId, int sourceTab, int targetTab, String destinationName)
    {
        for (int attempt = 1; attempt <= 4; attempt++)
        {
            if (Thread.currentThread().isInterrupted())
            {
                return ActuatorResult.fail("Interrupted while moving item to " + destinationName + ".");
            }

            ActuatorResult open = openTab(sourceTab);
            if (!open.success())
            {
                if (attempt == 4) return open;
                Global.sleep(250);
                continue;
            }

            Rs2ItemModel item = findBankItem(itemId);
            if (item == null)
            {
                if (attempt == 4)
                    return ActuatorResult.fail("Could not find item " + itemId + " in the live bank.");
                Global.sleep(250);
                continue;
            }

            int quantity = item.getQuantity();

            if (!waitForBankUiStable(1200))
            {
                if (attempt == 4)
                    return ActuatorResult.fail("Bank UI did not stabilize before moving item " + itemId + ".");
                Global.sleep(TAB_SETTLE_MS);
                continue;
            }

            if (!scrollBankToSlotSafe(item.getSlot()))
            {
                if (attempt == 4)
                    return ActuatorResult.fail("Could not scroll item " + itemId + " into view.");
                Global.sleep(250);
                continue;
            }

            if (!waitForBankUiStable(1200))
            {
                if (attempt == 4)
                    return ActuatorResult.fail("Bank UI did not stabilize after scrolling item " + itemId + ".");
                Global.sleep(TAB_SETTLE_MS);
                continue;
            }

            DragBounds bounds = itemToTabBounds(itemId, targetTab);
            if (bounds == null)
            {
                if (attempt == 4)
                    return ActuatorResult.fail("Could not locate live source/destination widgets for item "
                        + itemId + " -> " + destinationName + ".");
                Global.sleep(250);
                continue;
            }

            Rectangle source = centerRect(bounds.source());
            Rectangle target = centerRect(bounds.target());

            Microbot.drag(source, target);
            Global.sleep(ACTION_SETTLE_MS);

            if (waitForItemTab(itemId, targetTab, quantity, MOVE_VERIFY_MS))
            {
                return ActuatorResult.ok("Moved item to " + destinationName + ".");
            }

            Global.sleep(250);
        }

        return ActuatorResult.fail(
            "Move to " + destinationName + " was not verified after 4 fresh attempts.");
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
        Global.sleep(ACTION_SETTLE_MS);
        waitForBankUiStable(1200);
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
            /*
             * Do not index Rs2Bank.getTabs() directly. The returned list is a
             * dynamic widget collection and its list position is not the same
             * thing as the logical bank-tab number. getTabWidget(tabIndex)
             * resolves the actual Bankmain tab widget for the requested tab.
             */
            Widget tab = Rs2Bank.getTabWidget(tabIndex);
            if (tab == null || tab.isHidden() || !hasUsableCanvasRectangle(tab.getBounds()))
            {
                return false;
            }

            /*
             * Use the same live widget bounds + mouse path used by the
             * verification path below. This avoids depending on the dynamic
             * child index/identifier of Bankmain's tab container.
             */
            Microbot.getMouse().click(new Rectangle(tab.getBounds()));
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
    private DragBounds itemToTabBounds(int itemId, int dynamicTabIndex)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            /*
             * Do not derive the source rectangle from the bank ItemContainer slot.
             * The bank can contain placeholders and the visual widget list is
             * rebuilt/reindexed during tab changes. Find the actual rendered item
             * widget by item ID in the currently open tab instead.
             */
            Widget itemContainer = client.getWidget(Rs2Bank.BANK_ITEM_CONTAINER);
            if (itemContainer == null || itemContainer.isHidden())
            {
                return null;
            }

            Rectangle source = null;
            Widget[] children = itemContainer.getDynamicChildren();
            if (children != null)
            {
                for (Widget child : children)
                {
                    if (child == null || child.isHidden() || child.getItemId() != itemId)
                    {
                        continue;
                    }

                    Rectangle bounds = child.getBounds();
                    if (hasUsableCanvasRectangle(bounds))
                    {
                        source = new Rectangle(bounds);
                        break;
                    }
                }
            }

            if (source == null)
            {
                return null;
            }

            /*
             * dynamicTabIndex is now the logical bank tab number (0 = main,
             * 1..9 = real tabs). Resolve it through Rs2Bank instead of using
             * a hard-coded offset into getTabs(). This is critical because
             * getTabs() exposes the live dynamic widget list, whose list index
             * can change independently of the logical tab number.
             */
            Widget tab = Rs2Bank.getTabWidget(dynamicTabIndex);
            if (tab == null || tab.isHidden())
            {
                return null;
            }

            Rectangle target = tab.getBounds();
            if (!hasUsableCanvasRectangle(target))
            {
                return null;
            }

            return new DragBounds(source, new Rectangle(target));
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

    private Rectangle centerRect(Rectangle rectangle)
    {
        if (rectangle == null)
        {
            return null;
        }

        int width = Math.max(4, Math.min(20, rectangle.width));
        int height = Math.max(4, Math.min(20, rectangle.height));
        int x = rectangle.x + Math.max(0, (rectangle.width - width) / 2);
        int y = rectangle.y + Math.max(0, (rectangle.height - height) / 2);
        return new Rectangle(x, y, width, height);
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

    private boolean waitForBankUiStable(int timeoutMs)
    {
        return Global.sleepUntil(() ->
            Microbot.getClientThread().runOnClientThreadOptional(() ->
            {
                Widget root = client.getWidget(BANK_GROUP_ID, 1);
                Widget items = client.getWidget(Rs2Bank.BANK_ITEM_CONTAINER);
                if (root == null || root.isHidden() || items == null || items.isHidden())
                {
                    return false;
                }

                Rectangle bounds = items.getBounds();
                return hasUsableCanvasRectangle(bounds);
            }).orElse(false),
            timeoutMs);
    }

    private boolean waitForItemTab(int itemId, int expectedTab, int expectedQuantity, int timeoutMs)
    {
        return Global.sleepUntil(() ->
            Microbot.getClientThread().runOnClientThreadOptional(() ->
            {
                /*
                 * Global.sleepUntil executes on the organizer worker thread.
                 * Anything touching RuneLite Widget objects must therefore happen
                 * inside this client-thread callback.
                 */
                Widget bankRoot = client.getWidget(12, 1);
                if (bankRoot == null || bankRoot.isHidden())
                {
                    return false;
                }

                if (Rs2Bank.getCurrentTab() != expectedTab)
                {
                    Widget tabWidget = Rs2Bank.getTabWidget(expectedTab);
                    if (tabWidget == null || tabWidget.isHidden())
                    {
                        return false;
                    }

                    Rectangle bounds = tabWidget.getBounds();
                    if (!hasUsableCanvasRectangle(bounds))
                    {
                        return false;
                    }

                    Microbot.getMouse().click(bounds);
                    return false;
                }

                Widget itemContainer = client.getWidget(Rs2Bank.BANK_ITEM_CONTAINER);
                if (itemContainer == null || itemContainer.isHidden())
                {
                    return false;
                }

                Widget[] children = itemContainer.getDynamicChildren();
                if (children == null)
                {
                    return false;
                }

                for (Widget child : children)
                {
                    if (child == null || child.isHidden())
                    {
                        continue;
                    }

                    if (child.getItemId() == itemId)
                    {
                        /*
                         * Presence in the destination tab is authoritative.
                         * Quantity text can lag behind the widget rebuild.
                         */
                        return expectedQuantity <= 0 || child.getItemQuantity() >= 1;
                    }
                }

                return false;
            }).orElse(false),
            timeoutMs);
    }

    private boolean openTabForVerification(int tab)
    {
        if (tab < 0 || tab > 9)
        {
            return false;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Widget bankRoot = client.getWidget(12, 1);
            if (bankRoot == null || bankRoot.isHidden())
            {
                return false;
            }

            if (Rs2Bank.getCurrentTab() == tab)
            {
                return true;
            }

            Widget widget = Rs2Bank.getTabWidget(tab);
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
