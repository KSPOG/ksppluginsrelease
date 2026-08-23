package net.runelite.client.plugins.microbot.mining;

import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

/** Inventory cleanup used when banking is disabled. */
public final class MiningInventoryCleanupHelper
{
    private MiningInventoryCleanupHelper() {}

    /**
     * Drops the inventory while automatically preserving every carried pickaxe.
     * No user-maintained keep list is required.
     */
    public static void dropAllExceptPickaxes(InteractOrder order)
    {
        String[] carriedPickaxes = Rs2Inventory.items()
                .filter(item -> item != null
                        && item.getName() != null
                        && PickaxeUpgradeHelper.isPickaxeName(item.getName()))
                .map(item -> item.getName().trim())
                .distinct()
                .toArray(String[]::new);

        Rs2Inventory.dropAllExcept(false, order, carriedPickaxes);
    }
}
