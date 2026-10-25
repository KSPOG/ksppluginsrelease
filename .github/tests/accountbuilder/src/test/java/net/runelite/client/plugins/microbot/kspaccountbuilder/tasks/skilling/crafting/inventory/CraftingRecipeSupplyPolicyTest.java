package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.inventory;

import java.util.Map;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftingRecipeSupplyPolicyTest
{
    private boolean ready(CraftInventory recipe, Map<String, Integer> stock)
    {
        return CraftingRecipeSupplyPolicy.hasIngredients(recipe, name -> stock.getOrDefault(name, 0));
    }

    @Test
    void tiaraUsesSilverAndMouldWithoutGems()
    {
        assertTrue(ready(CraftInventory.TIARA, Map.of("Tiara mould", 1, "Silver bar", 1)));
        assertFalse(ready(CraftInventory.TIARA, Map.of("Tiara mould", 1)));
    }

    @Test
    void uncutGemsWithoutChiselAreNotExecutable()
    {
        assertFalse(ready(CraftInventory.CUT_SAPPHIRE, Map.of("Uncut sapphire", 27)));
    }

    @Test
    void chiselWithoutUncutGemsIsNotExecutable()
    {
        assertFalse(ready(CraftInventory.CUT_SAPPHIRE, Map.of("Chisel", 1)));
    }

    @Test
    void chiselAndUncutGemsEnableCutting()
    {
        assertTrue(ready(CraftInventory.CUT_SAPPHIRE, Map.of("Chisel", 1, "Uncut sapphire", 27)));
    }

    @Test
    void jewelleryRequiresMouldAndFullBatchQuantities()
    {
        assertFalse(ready(CraftInventory.SAPPHIRE_RING, Map.of("Ring mould", 1, "Sapphire", 12, "Gold bar", 13)));
        assertFalse(ready(CraftInventory.SAPPHIRE_RING, Map.of("Sapphire", 13, "Gold bar", 13)));
        assertTrue(ready(CraftInventory.SAPPHIRE_RING, Map.of("Ring mould", 1, "Sapphire", 13, "Gold bar", 13)));
    }

    @Test
    void allFourGemRecipesRequireMatchingGemAndChisel()
    {
        CraftInventory[] recipes = {CraftInventory.CUT_SAPPHIRE, CraftInventory.CUT_EMERALD, CraftInventory.CUT_RUBY, CraftInventory.CUT_DIAMOND};
        for (CraftInventory recipe : recipes)
        {
            String gem = recipe.getIngredients()[1].getItemName();
            assertEquals("Chisel", recipe.getIngredients()[0].getItemName());
            assertTrue(ready(recipe, Map.of("Chisel", 1, gem, 1)));
            assertFalse(ready(recipe, Map.of("Chisel", 1)));
        }
    }

    @Test
    void nullAndEmptySupplyAreRejected()
    {
        assertFalse(CraftingRecipeSupplyPolicy.hasIngredients(null, name -> 1));
        assertFalse(CraftingRecipeSupplyPolicy.hasIngredients(CraftInventory.TIARA, null));
        assertFalse(ready(CraftInventory.TIARA, Map.of()));
    }
}
