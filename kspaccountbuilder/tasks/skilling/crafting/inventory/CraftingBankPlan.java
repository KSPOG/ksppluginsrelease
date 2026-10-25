package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.inventory;

/**
 * Inventory targets for a single Crafting banking cycle.
 * The bank and current inventory together determine the available total,
 * so each successful withdrawal does not shrink its own target.
 */
public final class CraftingBankPlan
{
    public static final int INVENTORY_CAPACITY = 28;

    private CraftingBankPlan() {}

    public static int targetAmount(CraftInventory recipe, CraftInventory.Ingredient ingredient,
                                   int inventoryCount, int bankCount)
    {
        if (recipe == null || ingredient == null)
        {
            return 0;
        }
        if (ingredient.isReusable())
        {
            return ingredient.getAmount();
        }

        int reusableSlots = 0;
        int consumableTypes = 0;
        for (CraftInventory.Ingredient candidate : recipe.getIngredients())
        {
            if (candidate.isReusable())
            {
                reusableSlots += candidate.getAmount();
            }
            else
            {
                consumableTypes++;
            }
        }

        long available = (long) Math.max(0, bankCount) + Math.max(0, inventoryCount);
        if (consumableTypes == 1)
        {
            // E.g. 1 chisel + 27 uncut gems, or 1 tiara mould + 27 silver bars.
            int capacity = Math.max(0, INVENTORY_CAPACITY - reusableSlots);
            return (int) Math.min(available, capacity);
        }

        // Mixed recipes specify their own balanced batch quantities (13+13).
        // Do not request all 27 gems first and leave no room for gold bars.
        return (int) Math.min(available, ingredient.getAmount());
    }
}
