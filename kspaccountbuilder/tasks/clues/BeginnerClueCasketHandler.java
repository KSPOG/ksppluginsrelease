package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.clues;

import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/**
 * Tick-driven completion phase for beginner Treasure Trails.
 * Run BEFORE reading/solving the next clue. The caller must not mark the
 * clue task complete until this handler returns FINISHED.
 */
public final class BeginnerClueCasketHandler
{
    public enum Result { NOT_PRESENT, WAITING, FINISHED }

    private static final long RETRY_MS = 2_000L;
    private long lastClickAtMs;
    private int quantityBefore = -1;
    private boolean awaitingOpenConfirmation;

    public Result tick()
    {
        int count = Rs2Inventory.itemQuantity(BeginnerClueInventory.BEGINNER_CASKET_ID);
        if (awaitingOpenConfirmation)
        {
            if (count < quantityBefore)
            {
                awaitingOpenConfirmation = false;
                quantityBefore = -1;
                lastClickAtMs = 0L;
                return count > 0 ? Result.WAITING : Result.FINISHED;
            }
            if (System.currentTimeMillis() - lastClickAtMs < RETRY_MS)
                return Result.WAITING;
            awaitingOpenConfirmation = false;
        }

        if (count > 0)
        {
            if (Rs2Bank.isOpen()) { Rs2Bank.closeBank(); return Result.WAITING; }
            if (System.currentTimeMillis() - lastClickAtMs < RETRY_MS) return Result.WAITING;
            quantityBefore = count;
            lastClickAtMs = System.currentTimeMillis();
            awaitingOpenConfirmation = BeginnerClueInventory.openInventoryCasket();
            return Result.WAITING;
        }

        // A casket may have been banked when the clue was completed.
        // The task's bank preparation step must withdraw it first.
        return Result.NOT_PRESENT;
    }

    public void reset()
    {
        lastClickAtMs = 0L;
        quantityBefore = -1;
        awaitingOpenConfirmation = false;
    }
}
