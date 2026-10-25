package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.inventory;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftingWithdrawalPolicyTest
{
    private int amount(CraftInventory recipe, int index, int current, int bank)
    {
        return CraftingWithdrawalPolicy.additionalToWithdraw(
                recipe, recipe.getIngredients()[index], current, bank);
    }

    @Test
    void suppliesChiselBeforeGemBatch()
    {
        assertEquals(1, amount(CraftInventory.CUT_SAPPHIRE, 0, 0, 1));
        assertEquals(0, amount(CraftInventory.CUT_SAPPHIRE, 0, 1, 9));
    }

    @Test
    void fullGemBatchReservesReusableChiselSlot()
    {
        assertEquals(27, amount(CraftInventory.CUT_SAPPHIRE, 1, 0, 200));
    }

    @Test
    void partiallyCompletedGemBatchUsesRemainingBankStock()
    {
        assertEquals(7, amount(CraftInventory.CUT_SAPPHIRE, 1, 20, 7));
        assertEquals(2, amount(CraftInventory.CUT_SAPPHIRE, 1, 25, 7));
    }

    @Test
    void lowStockWithdrawsAllAvailableGems()
    {
        assertEquals(9, amount(CraftInventory.CUT_EMERALD, 1, 0, 9));
        assertEquals(0, amount(CraftInventory.CUT_EMERALD, 1, 9, 0));
    }

    @Test
    void avoidsUnnecessaryGemAndToolClicks()
    {
        assertEquals(0, amount(CraftInventory.CUT_RUBY, 1, 27, 100));
        assertEquals(0, amount(CraftInventory.CUT_RUBY, 0, 1, 0));
    }

    @Test
    void jewelleryRequiresOnlyConfiguredQuantities()
    {
        assertEquals(1, amount(CraftInventory.SAPPHIRE_RING, 0, 0, 1));
        assertEquals(13, amount(CraftInventory.SAPPHIRE_RING, 1, 0, 25));
        assertEquals(3, amount(CraftInventory.SAPPHIRE_RING, 2, 10, 30));
    }

    @Test
    void partialJewelleryStockDoesNotClaimCompleteRecipe()
    {
        assertEquals(4, amount(CraftInventory.EMERALD_NECKLACE, 1, 5, 4));
        assertEquals(0, amount(CraftInventory.EMERALD_NECKLACE, 1, 9, 0));
    }

    @Test
    void missingToolsCannotBeWithdrawnFromEmptyBank()
    {
        assertEquals(0, amount(CraftInventory.CUT_DIAMOND, 0, 0, 0));
    }

    @Test
    void negativeBankCountsNeverCauseAWithdrawal()
    {
        assertEquals(0, amount(CraftInventory.CUT_DIAMOND, 1, 2, -1));
    }

    @Test
    void invalidRecipeOrIngredientIsRejected()
    {
        assertEquals(0, CraftingWithdrawalPolicy.additionalToWithdraw(
                null, CraftInventory.CUT_SAPPHIRE.getIngredients()[1], 0, 10));
        assertEquals(0, CraftingWithdrawalPolicy.additionalToWithdraw(
                CraftInventory.CUT_SAPPHIRE, null, 0, 10));
        assertEquals(0, CraftingWithdrawalPolicy.additionalToWithdraw(
                CraftInventory.TIARA,
                CraftInventory.CUT_SAPPHIRE.getIngredients()[1], 0, 10));
    }
}
