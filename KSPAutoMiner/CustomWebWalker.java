package net.runelite.client.plugins.microbot.KSPAutoMiner;

import java.util.List;
import java.util.concurrent.ThreadLocalRandom;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

public final class CustomWebWalker {

    private static final int LOOKAHEAD_MIN = 8;
    private static final int LOOKAHEAD_MAX = 14;
    private static final int MAX_DISTANCE_FROM_PATH = 12;
    private static final long STUCK_TIMEOUT_MS = 3_000L;
    private static final long WALK_ACTION_COOLDOWN_MS = 600L;
    private static final long POLL_DELAY_MIN_MS = 120L;
    private static final long POLL_DELAY_MAX_MS = 240L;

    private CustomWebWalker() {
    }

    public static WalkerState walkTo(WorldPoint destination, int reachedDistance, long timeoutMs) {
        if (destination == null || timeoutMs <= 0) {
            return WalkerState.UNREACHABLE;
        }

        int reachDistance = Math.max(reachedDistance, 0);
        long startTime = System.currentTimeMillis();
        long lastMoveTime = startTime;
        long lastWalkActionTime = 0L;
        WorldPoint lastLocation = Rs2Player.getWorldLocation();

        List<WorldPoint> path = Rs2Walker.getWalkPath(destination);
        if (path == null || path.isEmpty()) {
            return WalkerState.UNREACHABLE;
        }

        while (System.currentTimeMillis() - startTime < timeoutMs) {
            WorldPoint currentLocation = Rs2Player.getWorldLocation();
            if (currentLocation == null) {
                return WalkerState.UNREACHABLE;
            }

            if (currentLocation.distanceTo(destination) <= reachDistance) {
                return WalkerState.ARRIVED;
            }

            if (!currentLocation.equals(lastLocation)) {
                lastLocation = currentLocation;
                lastMoveTime = System.currentTimeMillis();
            }

            if (isTooFarFromPath(path, currentLocation, MAX_DISTANCE_FROM_PATH)
                    || System.currentTimeMillis() - lastMoveTime > STUCK_TIMEOUT_MS) {
                path = Rs2Walker.getWalkPath(destination);
                if (path == null || path.isEmpty()) {
                    return WalkerState.UNREACHABLE;
                }
                lastMoveTime = System.currentTimeMillis();
            }

            if (!Rs2Player.isMoving()
                    && System.currentTimeMillis() - lastWalkActionTime > WALK_ACTION_COOLDOWN_MS) {
                WorldPoint nextCheckpoint = getNextCheckpoint(path);
                if (nextCheckpoint == null) {
                    return WalkerState.UNREACHABLE;
                }
                Rs2Walker.walkFastCanvas(nextCheckpoint);
                lastWalkActionTime = System.currentTimeMillis();
            }

            sleepRandom(POLL_DELAY_MIN_MS, POLL_DELAY_MAX_MS);
        }

        return WalkerState.UNREACHABLE;
    }

    public static WalkerState walkTo(WorldPoint destination, int reachedDistance) {
        return walkTo(destination, reachedDistance, 90_000L);
    }

    public static WalkerState walkTo(WorldPoint destination) {
        return walkTo(destination, 3, 90_000L);
    }

    private static WorldPoint getNextCheckpoint(List<WorldPoint> path) {
        if (path == null || path.isEmpty()) {
            return null;
        }
        int currentIndex = Rs2Walker.getClosestTileIndex(path);
        int lookahead = ThreadLocalRandom.current().nextInt(LOOKAHEAD_MIN, LOOKAHEAD_MAX + 1);
        int nextIndex = Math.min(currentIndex + lookahead, path.size() - 1);
        return path.get(nextIndex);
    }

    private static boolean isTooFarFromPath(List<WorldPoint> path, WorldPoint currentLocation, int maxDistance) {
        if (path == null || path.isEmpty()) {
            return true;
        }
        int closestIndex = Rs2Walker.getClosestTileIndex(path);
        WorldPoint closestPoint = path.get(closestIndex);
        return currentLocation.distanceTo(closestPoint) > maxDistance;
    }

    private static void sleepRandom(long minMs, long maxMs) {
        long delay = ThreadLocalRandom.current().nextLong(minMs, maxMs + 1);
        try {
            Thread.sleep(delay);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    public enum WalkerState {
        ARRIVED,
        UNREACHABLE
    }
}
