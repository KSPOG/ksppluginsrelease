package net.runelite.client.plugins.microbot.kspaccountbuilder;

import java.util.function.Predicate;
import java.util.function.Supplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Thin Account Builder walking facade.
 *
 * Rs2Walker now owns route lifecycle, stall recovery, retry timing, doors/transports,
 * interim targets and active-route recovery. Keep only Account Builder-specific
 * world-map safety and destination checks here.
 */
public final class KspWalkerGuard
{
    private KspWalkerGuard() {}

    public static boolean walkToDestination(
            String key,
            Supplier<WorldPoint> targetSupplier,
            Predicate<WorldPoint> destinationMatcher,
            int arriveDistance,
            long refireCooldownMs)
    {
        if (blockWalkingForOpenWorldMap() || targetSupplier == null || destinationMatcher == null)
        {
            return false;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player != null && destinationMatcher.test(player))
        {
            clearActiveWalker("ksp_account_builder_reached_destination");
            return false;
        }

        WorldPoint target = targetSupplier.get();
        if (target == null)
        {
            return false;
        }

        Rs2Walker.walkTo(target, arriveDistance);
        return true;
    }

    public static boolean walkToPoint(String key, WorldPoint target, int arriveDistance, long refireCooldownMs)
    {
        if (blockWalkingForOpenWorldMap() || target == null)
        {
            return false;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (isSameDestination(player, target, Math.max(0, arriveDistance)))
        {
            clearActiveWalker("ksp_account_builder_reached_destination");
            return false;
        }

        Rs2Walker.walkTo(target, arriveDistance);
        return true;
    }

    public static boolean walkFastCanvasToPoint(String key, WorldPoint target, int arriveDistance, long refireCooldownMs)
    {
        if (blockWalkingForOpenWorldMap() || target == null)
        {
            return false;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (isSameDestination(player, target, Math.max(0, arriveDistance)))
        {
            clearActiveWalker("ksp_account_builder_reached_destination");
            return false;
        }

        return Rs2Walker.walkFastCanvas(target);
    }

    public static void clear(String key)
    {
        // No local route/request state remains.
    }

    public static void clearActiveWalker(String reason)
    {
        if (Rs2Walker.getCurrentTarget() != null)
        {
            Rs2Walker.clearWalkingRoute(reason != null ? reason : "ksp_account_builder_clear_walker");
        }
    }

    public static void clearReachedDestination(String key, String reason)
    {
        clearActiveWalker(reason != null ? reason : "ksp_account_builder_reached_destination");
    }

    private static boolean blockWalkingForOpenWorldMap()
    {
        if (!KspWorldMapGuard.closeIfOpen())
        {
            return false;
        }

        clearActiveWalker("ksp_account_builder_world_map_open");
        return true;
    }

    private static boolean isSameDestination(WorldPoint first, WorldPoint second, int distance)
    {
        return first != null
                && second != null
                && first.getPlane() == second.getPlane()
                && first.distanceTo(second) <= distance;
    }
}
