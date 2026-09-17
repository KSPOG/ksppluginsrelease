package net.runelite.client.plugins.microbot.mining;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/**
 * Deterministic banking flow for Auto Mining.
 *
 * Mining level determines the best usable pickaxe. Attack level only controls
 * whether that pickaxe can be equipped. If the best carried mining pickaxe
 * cannot be equipped, banking preserves it in inventory instead of depositing
 * it and downgrading to an Attack-compatible tool.
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

        // Decide what must remain in inventory before attempting to equip a
        // lower Attack-compatible pickaxe. Example: 41 Mining / 1 Attack keeps
        // a Rune pickaxe in inventory even though it cannot be wielded.
        final String retainedPickaxe = PickaxeUpgradeHelper.bestCarriedPickaxeToKeepInInventory();

        PickaxeUpgradeHelper.equipBestCarriedPickaxeIfPossible();

        if (!depositInventoryUntilReady(deadline, retainedPickaxe))
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

        // A final inventory pickaxe is expected when Mining permits a stronger
        // pickaxe than Attack permits the account to equip.
        return !hasNonPickaxeInventoryItems();
    }

    private static boolean depositInventoryUntilReady(long deadline, String retainedPickaxe)
    {
        while (System.currentTimeMillis() < deadline)
        {
            if (!Rs2Bank.isOpen())
            {
                return false;
            }

            if (!hasNonPickaxeInventoryItems())
            {
                return true;
            }

            final int beforeSlots = inventorySlotCount();

            if (retainedPickaxe != null && Rs2Inventory.hasItem(retainedPickaxe))
            {
                final var keepItem = Rs2Inventory.get(retainedPickaxe);
                if (keepItem != null)
                {
                    Microbot.status = "Depositing inventory - keeping " + retainedPickaxe;
                    Rs2Bank.depositAllExcept(keepItem.getId());
                }
                else
                {
                    Microbot.status = "Clicking Deposit inventory...";
                    Rs2Bank.depositAll();
                }
            }
            else
            {
                Microbot.status = "Clicking Deposit inventory...";
                Rs2Bank.depositAll();
            }

            if (waitUntil(() -> !hasNonPickaxeInventoryItems(), 1_800L))
            {
                return true;
            }

            if (inventorySlotCount() < beforeSlots)
            {
                continue;
            }

            sleep(180L);
        }

        return !hasNonPickaxeInventoryItems();
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
