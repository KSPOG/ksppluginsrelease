package net.runelite.client.plugins.microbot.kspbondgoal;

/**
 * Source Loader hot-refreshes can instantiate the same plugin under several
 * classloaders. OverlayManager.add() rejects only identical *objects*, so
 * the old KspBondGoalOverlay object stays registered unless explicitly
 * removed by its stable binary name.
 *
 * Pure name/ownership checks keep the lifecycle policy testable without
 * the RuneLite UI runtime.
 */
final class BondGoalOverlayLifecycle
{
    private static final String OVERLAY_CLASS =
        "net.runelite.client.plugins.microbot.kspbondgoal.KspBondGoalOverlay";

    private BondGoalOverlayLifecycle() {}

    static boolean removeOnStartup(String existingClassName)
    {
        // Remove old versions and other classloader copies before add.
        return OVERLAY_CLASS.equals(existingClassName);
    }

    static boolean removeOnShutdown(String existingClassName, boolean ownedByThisPlugin)
    {
        // An old plugin's delayed shutdown must never remove the new
        // instance's overlay after a hot reload.
        return ownedByThisPlugin && removeOnStartup(existingClassName);
    }
}
