package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.meleescript;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MeleeBankPolicyTest
{
    @Test void confirmedCombatCompletesInspection() { assertTrue(MeleeBankPolicy.inspectionSatisfied(false, true)); }
    @Test void combatPreventsDeferredBanking() { assertFalse(MeleeBankPolicy.shouldInspectOptionalGear(MeleeBankPolicy.inspectionSatisfied(false, true), false)); }
    @Test void startupInspectionStillAllowed() { assertTrue(MeleeBankPolicy.shouldInspectOptionalGear(false, false)); }
    @Test void activeCombatDefersInspection() { assertFalse(MeleeBankPolicy.shouldInspectOptionalGear(false, true)); }
    @Test void completedInspectionPersists() { assertTrue(MeleeBankPolicy.inspectionSatisfied(true, false)); }
    @Test void bankOnlyForKnownMissingWeapon() { assertTrue(MeleeBankPolicy.needsOptionalGearBank(false, true)); assertFalse(MeleeBankPolicy.needsOptionalGearBank(true, true)); assertFalse(MeleeBankPolicy.needsOptionalGearBank(false, false)); }

    @Test void directBankOpenAcknowledgedWithoutFallback()
    {
        int[] calls = new int[2];
        MeleeBankOpenDispatcher.dispatch(() -> true, () -> calls[0]++, () -> calls[1]++);
        assertArrayEquals(new int[]{0, 1}, calls);
    }

    @Test void failedDirectBankOpenWalksAndAcknowledges()
    {
        int[] calls = new int[2];
        MeleeBankOpenDispatcher.dispatch(() -> false, () -> calls[0]++, () -> calls[1]++);
        assertArrayEquals(new int[]{1, 1}, calls);
    }

    @Test void thrownDirectOpenStillAcknowledges()
    {
        int[] acknowledgements = new int[1];
        assertThrows(IllegalStateException.class, () -> MeleeBankOpenDispatcher.dispatch(
                () -> { throw new IllegalStateException(); },
                () -> fail("Fallback must not run"),
                () -> acknowledgements[0]++));
        assertEquals(1, acknowledgements[0]);
    }

    @Test void thrownWalkFallbackStillAcknowledges()
    {
        int[] acknowledgements = new int[1];
        assertThrows(IllegalStateException.class, () -> MeleeBankOpenDispatcher.dispatch(
                () -> false,
                () -> { throw new IllegalStateException(); },
                () -> acknowledgements[0]++));
        assertEquals(1, acknowledgements[0]);
    }

    @Test void walkingFallbackDispatchesExactlyOnce()
    {
        int[] calls = new int[2];
        MeleeBankOpenDispatcher.dispatch(() -> false, () -> calls[0]++, () -> calls[1]++);
        assertEquals(1, calls[0]);
        assertEquals(1, calls[1]);
    }

}
