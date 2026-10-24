package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.meleescript;

/**
 * Side-effect-free Melee banking decisions.
 *
 * Optional equipment inspection must not send an already armed player across
 * the map. Missing food is a reason to leave training only once the player
 * is out of combat and actually needs resupply for safe training.
 */
final class MeleeBankPolicy
{
    private MeleeBankPolicy() {}

    static boolean needsOptionalGearBank(boolean usableWeaponPresent, boolean usableBankWeaponKnown)
    {
        return !usableWeaponPresent && usableBankWeaponKnown;
    }

    static boolean needsFoodBank(int currentHp, int maxHp, long noFoodObservedMs,
                                 boolean combatOrAnimationActive)
    {
        return currentHp > 0 && maxHp > 0
                && (long) currentHp * 2L <= maxHp
                && noFoodObservedMs >= 2500L
                && !combatOrAnimationActive;
    }
}
