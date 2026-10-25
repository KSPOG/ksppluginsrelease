package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.inventory;

import java.util.Map;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspProgressionPolicy;
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
    void craftingItemIdsResolveChiselUncutGemsAndTiaraBars()
    {
        assertEquals(1755, CraftInventory.CUT_SAPPHIRE.getTool().getItemId());
        assertEquals(1623, CraftInventory.CUT_SAPPHIRE.getIngredients()[1].getItemId());
        assertEquals(1621, CraftInventory.CUT_EMERALD.getIngredients()[1].getItemId());
        assertEquals(1619, CraftInventory.CUT_RUBY.getIngredients()[1].getItemId());
        assertEquals(1617, CraftInventory.CUT_DIAMOND.getIngredients()[1].getItemId());
        assertEquals(5523, CraftInventory.TIARA.getTool().getItemId());
        assertEquals(2355, CraftInventory.TIARA.getIngredients()[1].getItemId());
    }

    @Test
    void bankTargetsRemainStableAfterWithdrawal()
    {
        CraftInventory recipe = CraftInventory.TIARA;
        CraftInventory.Ingredient mould = recipe.getIngredients()[0];
        CraftInventory.Ingredient bars = recipe.getIngredients()[1];
        assertEquals(1, CraftingBankPlan.targetAmount(recipe, mould, 0, 1));
        assertEquals(27, CraftingBankPlan.targetAmount(recipe, bars, 0, 100));
        assertEquals(27, CraftingBankPlan.targetAmount(recipe, bars, 10, 90));
        assertEquals(27, CraftingBankPlan.targetAmount(recipe, bars, 27, 73));
        assertEquals(8, CraftingBankPlan.targetAmount(recipe, bars, 0, 8));
        assertEquals(8, CraftingBankPlan.targetAmount(recipe, bars, 8, 0));
    }

    @Test
    void chiselAndGemsFitAndMixedJewelleryReservesBothIngredients()
    {
        CraftInventory gems = CraftInventory.CUT_SAPPHIRE;
        assertEquals(1, CraftingBankPlan.targetAmount(gems, gems.getTool(), 0, 1));
        assertEquals(27, CraftingBankPlan.targetAmount(gems, gems.getIngredients()[1], 0, 200));

        CraftInventory jewellery = CraftInventory.SAPPHIRE_RING;
        assertEquals(1, CraftingBankPlan.targetAmount(jewellery, jewellery.getTool(), 0, 1));
        assertEquals(13, CraftingBankPlan.targetAmount(jewellery, jewellery.getIngredients()[1], 0, 100));
        assertEquals(13, CraftingBankPlan.targetAmount(jewellery, jewellery.getIngredients()[2], 0, 100));
    }

    @Test
    void plannerPrioritizesSellingWhenSuppliesAreUnaffordable()
    {
        int selling = KspProgressionPolicy.priority("GE_SELL", 1, 200, true, false);
        int mining = KspProgressionPolicy.priority("MINING", 10, 200, true, false);
        assertTrue(selling > mining);
        assertTrue(KspProgressionPolicy.priority("GE_BUY", 1, 5000, true, true) > mining);
        assertTrue(KspProgressionPolicy.priority("MINING", 1, 5000, false, false)
                > KspProgressionPolicy.priority("GE_BUY", 1, 5000, false, false));
    }

    @Test
    void plannerKeepsUsefulTrainingAboveSpeculativeSelling()
    {
        int fishing = KspProgressionPolicy.priority("FISHING", 9, 25000, false, false);
        int selling = KspProgressionPolicy.priority("GE_SELL", 1, 25000, false, false);
        assertTrue(fishing > selling);
        assertTrue(KspProgressionPolicy.priority("MINING", 1, 20000, false, false)
                > KspProgressionPolicy.priority("MINING", 50, 20000, false, false));
    }

    @Test
    void nullAndEmptySupplyAreRejected()
    {
        assertFalse(CraftingRecipeSupplyPolicy.hasIngredients(null, name -> 1));
        assertFalse(CraftingRecipeSupplyPolicy.hasIngredients(CraftInventory.TIARA, null));
        assertFalse(ready(CraftInventory.TIARA, Map.of()));
    }
}
