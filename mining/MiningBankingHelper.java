package net.runelite.client.plugins.microbot.mining;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/**
 * Deterministic banking flow for Auto Mining.
 *
 * The normal bank path intentionally clicks the bank's Deposit inventory button
 * instead of depositing configured item names one by one. A carried pickaxe is
 * equipped first when possible, so the button leaves it outside the inventory.
 * The pickaxe helper then upgrades/re-equips the best available pickaxe and
 * deposits the displaced outdated tool.
 */
public final class MiningBankingHelper
{
    private static final long DEFAULT_TIMEOUT_MS = 8_000L;

    private MiningBankingHelper() {}

    public static boolean depositInventoryExceptPickaxe()
    {
        return depositInventoryExceptPickaxeUntilClear(DEFAULT_TIMEOUT_MS);
    }

    public static boolean depositInventoryExceptPickaxeUntilClear(long timeoutMs)
    {
        if (!Rs2Bank.isOpen())
        {
            return false;
        }

        final long deadline = System.currentTimeMillis() + Math.max(3_000L, timeoutMs);

        // Keep a carried pickaxe out of the inventory before the raw bank button
        // is clicked whenever the account has the Attack level to wield it.
        PickaxeUpgradeHelper.equipBestCarriedPickaxeIfPossible();

        if (!clickDepositInventoryUntilEmpty(deadline))
        {
            Microbot.status = "Deposit inventory failed";
            return false;
        }

        Microbot.status = "Checking pickaxe upgrades...";
        if (!PickaxeUpgradeHelper.ensureBestPickaxeAfterDeposit())
        {
            Microbot.status = "No usable pickaxe available";
            return false;
        }

        // The final inventory may contain a pickaxe only when it cannot be
        // equipped. No ore, gems, clues, or other mined items may remain.
        return !hasNonPickaxeInventoryItems();
    }

    private static boolean clickDepositInventoryUntilEmpty(long deadline)
    {
        while (System.currentTimeMillis() < deadline)
        {
            if (!Rs2Bank.isOpen())
            {
                return false;
            }

            if (Rs2Inventory.isEmpty())
            {
                return true;
            }

            Microbot.status = "Clicking Deposit inventory...";
            final int beforeSlots = inventorySlotCount();

            // Rs2Bank.depositAll() locates BANK_DEPOSIT_INVENTORY and clicks the
            // actual Deposit inventory button in the bank widget.
            Rs2Bank.depositAll();

            if (waitUntil(Rs2Inventory::isEmpty, 1_800L))
            {
                return true;
            }

            // A partial inventory change still counts as progress; immediately
            // click the button again instead of falling back to per-item menus.
            if (inventorySlotCount() < beforeSlots)
            {
                continue;
            }

            sleep(180L);
        }

        return Rs2Inventory.isEmpty();
    }

    private static boolean hasNonPickaxeInventoryItems()
    {
        try
        {
            return Rs2Inventory.items().anyMatch(item -> item != null
                    && item.getName() != null
                    && !PickaxeUpgradeHelper.isPickaxeName(item.getName()));
        }
        catch (Throwable ignored)
        {
            return !Rs2Inventory.isEmpty();
        }
    }

    private static int inventorySlotCount()
    {
        try
        {
            return (int) Rs2Inventory.items().count();
        }
        catch (Throwable ignored)
        {
            return Rs2Inventory.isEmpty() ? 0 : 1;
        }
    }

    private static boolean waitUntil(Check check, long timeoutMs)
    {
        final long end = System.currentTimeMillis() + Math.max(0L, timeoutMs);
        while (System.currentTimeMillis() < end)
        {
            if (check.get())
            {
                return true;
            }
            sleep(50L);
        }
        return check.get();
    }

    private static void sleep(long ms)
    {
        try
        {
            Thread.sleep(ms);
        }
        catch (InterruptedException e)
        {
            Thread.currentThread().interrupt();
        }
    }

    @FunctionalInterface
    private interface Check
    {
        boolean get();
    }
}
