package net.runelite.client.plugins.microbot.kspaccountbuilder.task.taskwalker;

import net.runelite.api.coords.WorldArea;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.function.Predicate;

public final class TaskWalker
{
    private static final long WALK_REFIRE_COOLDOWN_MS = 8_000L;

    private TaskWalker()
    {
    }

    public static synchronized boolean walkToArea(WorldArea area, WorldPoint target, int reachedDistance)
    {
        if (area == null || target == null)
        {
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation != null && area.contains(playerLocation))
        {
            KspWalkerGuard.clearActiveWalker("ksp_account_builder_taskwalker_reached_area");
            return true;
        }

        return KspWalkerGuard.walkToPoint(
                "TaskWalker:area:" + target.getX() + ":" + target.getY() + ":" + target.getPlane(),
                target,
                reachedDistance,
                WALK_REFIRE_COOLDOWN_MS
        );
    }

    public static synchronized boolean walkToArea(Predicate<WorldPoint> containsTargetArea, WorldArea area, WorldPoint target, int reachedDistance)
    {
        if (containsTargetArea == null || target == null)
        {
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation != null && containsTargetArea.test(playerLocation))
        {
            KspWalkerGuard.clearActiveWalker("ksp_account_builder_taskwalker_reached_area");
            return true;
        }

        return KspWalkerGuard.walkToPoint(
                "TaskWalker:predicate:" + target.getX() + ":" + target.getY() + ":" + target.getPlane(),
                target,
                reachedDistance,
                WALK_REFIRE_COOLDOWN_MS
        );
    }

    public static synchronized boolean walkToNearestBankAndOpen()
    {
        return Rs2Bank.isOpen() || Rs2Bank.walkToBankAndUseBank() || Rs2Bank.openBank();
    }
}
