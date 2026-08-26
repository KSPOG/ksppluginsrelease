package net.runelite.client.plugins.microbot.mining;

import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

import java.util.List;
import java.util.stream.Collectors;

import static net.runelite.client.plugins.microbot.util.Global.sleep;

/** Inventory cleanup used when banking is disabled. */
public final class MiningInventoryCleanupHelper
{
    private MiningInventoryCleanupHelper() {}

    /**
     * Fast-drops the inventory while automatically preserving every carried pickaxe.
     * Uses Microbot's configured interaction order without its 150-300 ms per-item drop delay.
     */
    public static void dropAllExceptPickaxes(InteractOrder order)
    {
        List<Rs2ItemModel> itemsToDrop = Rs2Inventory.calculateInteractOrder(
                Rs2Inventory.items(item -> item != null
                                && item.getName() != null
                                && !PickaxeUpgradeHelper.isPickaxeName(item.getName()))
                        .collect(Collectors.toList()), order);

        for (Rs2ItemModel item : itemsToDrop)
        {
            if (item == null) continue;
            if (Rs2Inventory.slotInteract(item.getSlot(), "Drop")) sleep(25, 45);
        }

        // Give the final inventory update a brief moment to reach the cached container.
        sleep(60, 100);
    }
}
