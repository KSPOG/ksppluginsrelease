package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.inventory;

import java.util.function.ToIntFunction;

/** Shared testable Crafting recipe ingredient eligibility rules. */
public final class CraftingRecipeSupplyPolicy
{
    private CraftingRecipeSupplyPolicy()
    {
    }

    public static boolean hasIngredients(CraftInventory recipe, ToIntFunction<String> availableCount)
    {
        if (recipe == null || availableCount == null)
        {
            return false;
        }
        for (var ingredient : recipe.getIngredients())
        {
            if (Math.max(0, availableCount.applyAsInt(ingredient.getItemName()))
                    < ingredient.getAmount())
            {
                return false;
            }
        }
        return true;
    }
}
