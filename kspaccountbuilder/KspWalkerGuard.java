package net.runelite.client.plugins.microbot.kspaccountbuilder;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Predicate;
import java.util.function.Supplier;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

public final class KspWalkerGuard
{
    private static final int SAME_TARGET_DISTANCE = 8;
    private static final int CANVAS_RECOVERY_STEP_TILES = 4;
    private static final long CLEAR_COOLDOWN_MS = 1_500L;
    private static final long WALK_IDLE_RECOVERY_MS = 5_000L;
    private static final Map<String, WalkRequest> WALK_REQUESTS = new ConcurrentHashMap<>();
    private static volatile long lastGlobalClearAtMs;
    private static WorldPoint lastObservedPosition;
    private static WorldPoint lastObservedWalkerTarget;
    private static long idleStartedAtMs;
    private static boolean canvasRecoveryAttempted;

    private KspWalkerGuard()
    {
    }

    public static boolean walkToDestination(
            String key,
            Supplier<WorldPoint> targetSupplier,
            Predicate<WorldPoint> destinationMatcher,
            int arriveDistance,
            long refireCooldownMs)
    {
        if (blockWalkingForOpenWorldMap())
        {
            return false;
        }

        if (key == null || targetSupplier == null || destinationMatcher == null)
        {
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (matches(destinationMatcher, playerLocation))
        {
            clearReachedDestination(key, "ksp_account_builder_reached_destination");
            return false;
        }

        long now = System.currentTimeMillis();
        WalkRequest previous = WALK_REQUESTS.get(key);
        WorldPoint walkerTarget = Rs2Walker.getCurrentTarget();

        if (matches(destinationMatcher, walkerTarget))
        {
            if (previous == null)
            {
                WALK_REQUESTS.put(key, new WalkRequest(walkerTarget, now));
                return false;
            }

            if (now - previous.requestedAtMs < refireCooldownMs)
            {
                return false;
            }
        }

        if (previous != null
                && matches(destinationMatcher, previous.target)
                && now - previous.requestedAtMs < refireCooldownMs)
        {
            return false;
        }

        WorldPoint target = targetSupplier.get();
        if (target == null)
        {
            return false;
        }

        WALK_REQUESTS.put(key, new WalkRequest(target, now));
        Rs2Walker.walkTo(target, arriveDistance);
        return true;
    }

    public static boolean walkToPoint(String key, WorldPoint target, int arriveDistance, long refireCooldownMs)
    {
        if (blockWalkingForOpenWorldMap())
        {
            return false;
        }

        if (!shouldWalkToPoint(key, target, arriveDistance, refireCooldownMs))
        {
            return false;
        }

        Rs2Walker.walkTo(target, arriveDistance);
        return true;
    }

    public static boolean walkFastCanvasToPoint(String key, WorldPoint target, int arriveDistance, long refireCooldownMs)
    {
        if (blockWalkingForOpenWorldMap())
        {
            return false;
        }

        if (!shouldWalkToPoint(key, target, arriveDistance, refireCooldownMs))
        {
            return false;
        }

        Rs2Walker.walkFastCanvas(target);
        return true;
    }

    public static synchronized boolean recoverActiveWalkIfIdle()
    {
        if (blockWalkingForOpenWorldMap())
        {
            return false;
        }

        WorldPoint walkerTarget = Rs2Walker.getCurrentTarget();
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        long now = System.currentTimeMillis();

        if (WALK_REQUESTS.isEmpty() || walkerTarget == null || playerLocation == null)
        {
            resetIdleRecoveryState();
            return false;
        }

        if (!isSameDestination(walkerTarget, lastObservedWalkerTarget, SAME_TARGET_DISTANCE))
        {
            lastObservedWalkerTarget = walkerTarget;
            lastObservedPosition = playerLocation;
            idleStartedAtMs = now;
            canvasRecoveryAttempted = false;
            return false;
        }

        if (!playerLocation.equals(lastObservedPosition))
        {
            lastObservedPosition = playerLocation;
            idleStartedAtMs = now;
            canvasRecoveryAttempted = false;
            return false;
        }

        if (Rs2Player.isAnimating() || Rs2Player.isInteracting())
        {
            idleStartedAtMs = now;
            return false;
        }

        if (playerLocation.distanceTo(walkerTarget) <= 2
                || canvasRecoveryAttempted
                || now - idleStartedAtMs < WALK_IDLE_RECOVERY_MS)
        {
            return false;
        }

        WorldPoint recoveryTarget = getNearbyRecoveryTarget(playerLocation, walkerTarget);
        canvasRecoveryAttempted = true;
        boolean clicked = recoveryTarget != null && Rs2Walker.walkCanvas(recoveryTarget) != null;
        Microbot.log("[KSP Walker] Idle walk recovery | clicked=" + clicked
                + " player=" + playerLocation
                + " recoveryTarget=" + recoveryTarget
                + " walkerTarget=" + walkerTarget);
        return clicked;
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

    public static void clear(String key)
    {
        if (key != null)
        {
            WALK_REQUESTS.remove(key);
        }

        if (WALK_REQUESTS.isEmpty())
        {
            resetIdleRecoveryState();
        }
    }

    public static void clearActiveWalker(String reason)
    {
        if (Rs2Walker.getCurrentTarget() == null)
        {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastGlobalClearAtMs < CLEAR_COOLDOWN_MS)
        {
            return;
        }

        lastGlobalClearAtMs = now;
        Rs2Walker.clearWalkingRoute(reason != null ? reason : "ksp_account_builder_clear_walker");
        resetIdleRecoveryState();
    }

    public static void clearReachedDestination(String key, String reason)
    {
        WalkRequest removedRequest = null;
        if (key != null)
        {
            removedRequest = WALK_REQUESTS.remove(key);
        }

        WorldPoint currentTarget = Rs2Walker.getCurrentTarget();
        if (removedRequest == null
                || !isSameDestination(currentTarget, removedRequest.target, SAME_TARGET_DISTANCE))
        {
            if (WALK_REQUESTS.isEmpty())
            {
                resetIdleRecoveryState();
            }
            return;
        }

        lastGlobalClearAtMs = System.currentTimeMillis();
        Rs2Walker.clearWalkingRoute(reason != null ? reason : "ksp_account_builder_reached_destination");
        resetIdleRecoveryState();
    }

    private static boolean shouldWalkToPoint(String key, WorldPoint target, int arriveDistance, long refireCooldownMs)
    {
        if (key == null || target == null)
        {
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (isSameDestination(playerLocation, target, Math.max(0, arriveDistance)))
        {
            clearReachedDestination(key, "ksp_account_builder_reached_destination");
            return false;
        }

        long now = System.currentTimeMillis();
        WalkRequest previous = WALK_REQUESTS.get(key);
        int sameTargetDistance = Math.max(SAME_TARGET_DISTANCE, arriveDistance + 2);

        if (isSameDestination(Rs2Walker.getCurrentTarget(), target, sameTargetDistance))
        {
            if (previous == null)
            {
                WALK_REQUESTS.put(key, new WalkRequest(target, now));
                return false;
            }

            if (now - previous.requestedAtMs < refireCooldownMs)
            {
                return false;
            }
        }

        if (previous != null
                && isSameDestination(previous.target, target, sameTargetDistance)
                && now - previous.requestedAtMs < refireCooldownMs)
        {
            return false;
        }

        WALK_REQUESTS.put(key, new WalkRequest(target, now));
        return true;
    }

    private static boolean matches(Predicate<WorldPoint> matcher, WorldPoint point)
    {
        return point != null && matcher.test(point);
    }

    private static WorldPoint getNearbyRecoveryTarget(WorldPoint playerLocation, WorldPoint walkerTarget)
    {
        int deltaX = walkerTarget.getX() - playerLocation.getX();
        int deltaY = walkerTarget.getY() - playerLocation.getY();
        int largestDelta = Math.max(Math.abs(deltaX), Math.abs(deltaY));
        if (largestDelta == 0 || playerLocation.getPlane() != walkerTarget.getPlane())
        {
            return null;
        }

        double scale = Math.min(1.0D, (double) CANVAS_RECOVERY_STEP_TILES / largestDelta);
        int stepX = (int) Math.round(deltaX * scale);
        int stepY = (int) Math.round(deltaY * scale);
        return new WorldPoint(
                playerLocation.getX() + stepX,
                playerLocation.getY() + stepY,
                playerLocation.getPlane());
    }

    private static void resetIdleRecoveryState()
    {
        lastObservedPosition = null;
        lastObservedWalkerTarget = null;
        idleStartedAtMs = 0L;
        canvasRecoveryAttempted = false;
    }

    private static boolean isSameDestination(WorldPoint first, WorldPoint second, int distance)
    {
        return first != null
                && second != null
                && first.getPlane() == second.getPlane()
                && first.distanceTo(second) <= distance;
    }

    private static final class WalkRequest
    {
        private final WorldPoint target;
        private final long requestedAtMs;

        private WalkRequest(WorldPoint target, long requestedAtMs)
        {
            this.target = target;
            this.requestedAtMs = requestedAtMs;
        }
    }
}
