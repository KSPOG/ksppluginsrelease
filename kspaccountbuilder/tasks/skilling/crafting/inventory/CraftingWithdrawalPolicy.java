package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.inventory;

/**
 * Plans one bank withdrawal using inventory and remaining bank stock.
 * This class performs no RuneLite I/O and is safe to regression-test independently.
 */
public final class CraftingWithdrawalPolicy
{
    private static final int INVENTORY_SLOTS = 28;

    private CraftingWithdrawalPolicy()
    {
    }

    public static int additionalToWithdraw(
            CraftInventory recipe, CraftInventory.Ingredient ingredient,
            int inInventory, int inBank)
    {
        if (recipe == null || ingredient == null)
        {
            return 0;
        }
        int current = Math.max(0, inInventory);
        int available = Math.max(0, inBank);
        if (available == 0)
        {
            return 0;
        }

        int reusableSlots = 0;
        int consumableTypes = 0;
        boolean foundIngredient = false;
        for (CraftInventory.Ingredient candidate : recipe.getIngredients())
        {
            if (candidate.getItemName().equals(ingredient.getItemName()))
            {
                foundIngredient = true;
            }
            if (candidate.isReusable())
            {
                reusableSlots += candidate.getAmount();
            }
            else
            {
                consumableTypes++;
            }
        }
        if (!foundIngredient)
        {
            return 0;
        }

        int desired;
        if (ingredient.isReusable())
        {
            desired = ingredient.getAmount();
        }
        else if (consumableTypes == 1)
        {
            // Keep items already withdrawn when calculating how many more to take.
            desired = Math.min(Math.max(0, INVENTORY_SLOTS - reusableSlots),
                    current + available);
        }
        else
        {
            // Two-consumable recipes require a fixed quantity of each ingredient.
            desired = ingredient.getAmount();
        }
        return Math.max(0, Math.min(available, desired - current));
    }
}
