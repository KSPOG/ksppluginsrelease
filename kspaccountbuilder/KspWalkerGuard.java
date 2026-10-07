package net.runelite.client.plugins.microbot.kspaccountbuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

/**
 * Thin Account Builder walking facade.
 *
 * Rs2Walker owns route lifecycle, stall recovery, retry timing, doors/transports,
 * interim targets and active-route recovery. Account Builder therefore uses live
 * walker/player state rather than additional millisecond refire delays.
 */
public final class KspWalkerGuard
{
    private static final Map<String, WorldPoint> KEY_TARGETS = new ConcurrentHashMap<>();
    private static volatile String activeWalkerKey;

    private KspWalkerGuard() {}

    public static boolean walkToDestination(
            String key,
            Supplier<WorldPoint> targetSupplier,
            Predicate<WorldPoint> destinationMatcher,
            int arriveDistance,
            long ignoredRefireCooldownMs)
    {
        if (blockWalkingForOpenWorldMap() || targetSupplier == null || destinationMatcher == null) return false;

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player != null && destinationMatcher.test(player))
        {
            clearKey(key, "ksp_account_builder_reached_destination");
            return false;
        }

        String walkKey = normalizeKey(key);
        WorldPoint target = KEY_TARGETS.get(walkKey);
        if (target == null)
        {
            target = targetSupplier.get();
            if (target == null) return false;
            KEY_TARGETS.put(walkKey, target);
        }

        if (!mayDispatch(walkKey, target, arriveDistance)) return false;

        activeWalkerKey = walkKey;
        Rs2Walker.walkTo(target, arriveDistance);
        return true;
    }

    public static boolean walkToPoint(String key, WorldPoint target, int arriveDistance, long ignoredRefireCooldownMs)
    {
        if (blockWalkingForOpenWorldMap() || target == null) return false;

        WorldPoint player = Rs2Player.getWorldLocation();
        if (isSameDestination(player, target, Math.max(0, arriveDistance)))
        {
            clearKey(key, "ksp_account_builder_reached_destination");
            return false;
        }

        String walkKey = normalizeKey(key);
        WorldPoint cached = KEY_TARGETS.get(walkKey);
        if (cached == null || !isSameDestination(cached, target, Math.max(2, arriveDistance + 2)))
        {
            if (cached != null) clearKey(walkKey, "ksp_account_builder_retarget");
            KEY_TARGETS.put(walkKey, target);
        }

        if (!mayDispatch(walkKey, target, arriveDistance)) return false;

        activeWalkerKey = walkKey;
        Rs2Walker.walkTo(target, arriveDistance);
        return true;
    }

    public static boolean walkFastCanvasToPoint(String key, WorldPoint target, int arriveDistance, long ignoredRefireCooldownMs)
    {
        if (blockWalkingForOpenWorldMap() || target == null) return false;

        WorldPoint player = Rs2Player.getWorldLocation();
        if (isSameDestination(player, target, Math.max(0, arriveDistance)))
        {
            clearActiveWalker("ksp_account_builder_reached_destination");
            return false;
        }

        if (Rs2Player.isMoving() || !prepareCoreWalkerTarget(target, arriveDistance)) return false;
        return Rs2Walker.walkFastCanvas(target);
    }

    public static void clear(String key)
    {
        clearKey(key, "ksp_account_builder_clear_walker");
    }

    public static void clearActiveWalker(String reason)
    {
        if (Rs2Walker.getCurrentTarget() != null)
        {
            Rs2Walker.clearWalkingRoute(reason != null ? reason : "ksp_account_builder_clear_walker");
        }
        activeWalkerKey = null;
    }

    public static void clearReachedDestination(String key, String reason)
    {
        clearKey(key, reason != null ? reason : "ksp_account_builder_reached_destination");
    }

    private static boolean mayDispatch(String key, WorldPoint target, int arriveDistance)
    {
        WorldPoint currentTarget = Rs2Walker.getCurrentTarget();
        if (currentTarget != null)
        {
            // The core walker may retarget to a reachable tile during recovery.
            // Keep the route owned by this step instead of replacing its adjusted goal.
            if (key.equals(activeWalkerKey)) return false;
            if (isSameDestination(currentTarget, target, Math.max(2, arriveDistance + 2))) return false;
            Rs2Walker.clearWalkingRoute(activeWalkerKey != null && !activeWalkerKey.equals(key)
                    ? "ksp_account_builder_owner_changed"
                    : "ksp_account_builder_retarget");
        }

        // If the previous route has already caused movement, it is still doing useful work.
        // If both route and movement are gone while the destination is not reached, refire now.
        return !Rs2Player.isMoving();
    }

    private static void clearKey(String key, String reason)
    {
        String walkKey = normalizeKey(key);
        KEY_TARGETS.remove(walkKey);

        if (walkKey.equals(activeWalkerKey))
        {
            clearActiveWalker(reason);
            activeWalkerKey = null;
        }
    }

    private static String normalizeKey(String key)
    {
        return key == null || key.isBlank() ? "ksp_account_builder_default_walk" : key;
    }

    private static boolean prepareCoreWalkerTarget(WorldPoint target, int arriveDistance)
    {
        WorldPoint currentTarget = Rs2Walker.getCurrentTarget();
        if (currentTarget == null) return true;

        int sameDestinationDistance = Math.max(2, arriveDistance + 2);
        if (isSameDestination(currentTarget, target, sameDestinationDistance)) return false;

        Rs2Walker.clearWalkingRoute("ksp_account_builder_retarget");
        return true;
    }

    private static boolean blockWalkingForOpenWorldMap()
    {
        if (!KspWorldMapGuard.closeIfOpen()) return false;
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
