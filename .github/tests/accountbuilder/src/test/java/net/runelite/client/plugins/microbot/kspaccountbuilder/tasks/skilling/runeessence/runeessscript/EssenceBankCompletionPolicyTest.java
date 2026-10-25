package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.runeessence.runeessscript;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class EssenceBankCompletionPolicyTest
{
    @Test void continueBankingAfterPartialDeposit()
    {
        assertTrue(EssenceBankCompletionPolicy.shouldContinueBanking(EssenceState.BANKING, false, true));
    }

    @Test void bankWhenInventoryIsFull()
    {
        assertTrue(EssenceBankCompletionPolicy.shouldContinueBanking(EssenceState.PREPARING, true, true));
    }

    @Test void bankWhenPickaxeIsMissing()
    {
        assertTrue(EssenceBankCompletionPolicy.shouldContinueBanking(EssenceState.PREPARING, false, false));
    }

    @Test void resumeTaskAfterCompletedBanking()
    {
        assertFalse(EssenceBankCompletionPolicy.shouldContinueBanking(EssenceState.PREPARING, false, true));
    }

    @Test void closeBankWhenOnlyPickaxeRemains()
    {
        assertTrue(EssenceBankCompletionPolicy.readyToClose(27, true));
    }

    @Test void closeBankWhenInventoryEmptyAndPickaxeEquipped()
    {
        assertTrue(EssenceBankCompletionPolicy.readyToClose(28, false));
    }

    @Test void retainBankWhenEssenceRemains()
    {
        assertFalse(EssenceBankCompletionPolicy.readyToClose(26, true));
    }

    @Test void retainBankWhenUnexpectedItemRemains()
    {
        assertFalse(EssenceBankCompletionPolicy.readyToClose(27, false));
    }
}
