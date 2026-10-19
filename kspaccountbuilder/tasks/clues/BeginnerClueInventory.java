package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.clues;

import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/**
 * Beginner Treasure Trails inventory/bank discovery. This class does not
 * identify clue steps: all beginner clues share the same item ID, so the
 * active clue text/map widget must be interpreted by the solver.
 */
public final class BeginnerClueInventory
{
    public static final int BEGINNER_CLUE_ID = 23182;
    public static final int BEGINNER_SCROLL_BOX_ID = 24361;
    public static final int BEGINNER_CASKET_ID = 23245;
    private static final String CLUE_NAME = "Clue scroll (beginner)";
    private static final String BOX_NAME = "Scroll box (beginner)";
    private static final String CASKET_NAME = "Reward casket (beginner)";

    private BeginnerClueInventory() { }

    public static boolean hasClueInInventory()
    {
        return Rs2Inventory.hasItem(BEGINNER_CLUE_ID);
    }

    public static boolean hasBoxInInventory()
    {
        return Rs2Inventory.hasItem(BEGINNER_SCROLL_BOX_ID);
    }

    public static boolean hasCasketInInventory()
    {
        return Rs2Inventory.hasItem(BEGINNER_CASKET_ID);
    }

    public static boolean hasCasketAnywhere()
    {
        return hasCasketInInventory() || Rs2Bank.count(CASKET_NAME, true) > 0;
    }

    /** Call before checking clues: a newly earned casket takes precedence. */
    public static boolean openInventoryCasket()
    {
        return hasCasketInInventory() && Rs2Inventory.interact(BEGINNER_CASKET_ID, "Open");
    }

    /** For scheduler selection after a bank stock snapshot is available. */
    public static boolean hasClueOrBoxAnywhere()
    {
        return hasClueInInventory() || hasBoxInInventory() || hasCasketInInventory()
                || Rs2Bank.count(CLUE_NAME, true) > 0
                || Rs2Bank.count(BOX_NAME, true) > 0
                || Rs2Bank.count(CASKET_NAME, true) > 0;
    }

    /** Caller must open a bank first and await inventory change before retry. */
    public static boolean withdrawAvailableClueOrBox()
    {
        if (!Rs2Bank.isOpen() || hasClueInInventory() || hasBoxInInventory() || hasCasketInInventory()) return false;
        if (Rs2Bank.count(CASKET_NAME, true) > 0) return Rs2Bank.withdrawOne(CASKET_NAME);
        if (Rs2Bank.count(CLUE_NAME, true) > 0) return Rs2Bank.withdrawOne(CLUE_NAME);
        if (Rs2Bank.count(BOX_NAME, true) > 0) return Rs2Bank.withdrawOne(BOX_NAME);
        return false;
    }

    /** Never open a scroll box while a separate beginner clue is active. */
    public static boolean openInventoryBox()
    {
        return !hasClueInInventory() && hasBoxInInventory()
                && Rs2Inventory.interact(BEGINNER_SCROLL_BOX_ID, "Open");
    }
}
