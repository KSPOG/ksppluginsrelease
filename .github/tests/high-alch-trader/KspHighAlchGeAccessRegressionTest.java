package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import net.runelite.api.coords.WorldPoint;

/**
 * GE-only bank/market regression suite. Run from JUnit with Microbot Hub
 * classes available, without starting the RuneLite client.
 */
public final class KspHighAlchGeAccessRegressionTest {
    private KspHighAlchGeAccessRegressionTest() {}

    public static void main(String[] args) {
        in(3153, 3477);
        in(3178, 3502);
        in(3163, 3487);
        out(3152, 3477);
        out(3179, 3487);
        out(3163, 3476);
        out(3163, 3503);
        if (KspHighAlchGeAccess.isInGrandExchange(new WorldPoint(3163, 3487, 1)))
            throw new AssertionError("GE must be on plane zero");
        if (!KspHighAlchGeAccess.isExpectedActor("Banker", false)
                || !KspHighAlchGeAccess.isExpectedActor("Grand Exchange Clerk", true))
            throw new AssertionError("Missing GE Banker or Clerk");
        for (String name : new String[]{"Bank booth", "Grand Exchange Clerk", "Banker (Varrock)"}) {
            if (KspHighAlchGeAccess.isExpectedActor(name, false))
                throw new AssertionError("Unexpected bank target: " + name);
        }
        if (KspHighAlchGeAccess.isExpectedActor("Banker", true))
            throw new AssertionError("Banker must not serve as an exchange clerk");

        WorldPoint player = new WorldPoint(3163, 3487, 0);
        if (!KspHighAlchGeAccess.isAllowedActor(player, new WorldPoint(3163, 3489, 0)))
            throw new AssertionError("Nearby GE banker rejected");
        if (KspHighAlchGeAccess.isAllowedActor(player, new WorldPoint(3148, 3449, 0)))
            throw new AssertionError("Cooking Guild bank booth allowed");
        if (KspHighAlchGeAccess.isAllowedActor(new WorldPoint(3148, 3449, 0),
                new WorldPoint(3163, 3489, 0)))
            throw new AssertionError("Offsite player allowed to bank");
        System.out.println("PASS: GE banker/clerk selection and offsite location guard");
    }

    private static void in(int x, int y) {
        if (!KspHighAlchGeAccess.isInGrandExchange(new WorldPoint(x, y, 0)))
            throw new AssertionError("Inside GE expected: " + x + "," + y);
    }
    private static void out(int x, int y) {
        if (KspHighAlchGeAccess.isInGrandExchange(new WorldPoint(x, y, 0)))
            throw new AssertionError("Outside GE expected: " + x + "," + y);
    }
}
