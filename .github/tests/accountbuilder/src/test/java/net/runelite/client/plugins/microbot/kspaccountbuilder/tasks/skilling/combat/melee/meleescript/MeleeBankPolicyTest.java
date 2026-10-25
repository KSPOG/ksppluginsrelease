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
}
