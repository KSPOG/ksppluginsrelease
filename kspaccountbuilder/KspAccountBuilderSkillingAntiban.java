package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Account-Builder-specific skilling humanization.
 *
 * <p>The configured antiban percentage is a per-opportunity trigger chance.
 * Mouse movement is deliberately limited to stable, already-running skilling
 * actions so it cannot race the task's own click/input startup sequence.</p>
 */
@Slf4j
final class KspAccountBuilderSkillingAntiban
{
    private static final long SAFE_ACTION_GRACE_MS = 3_000L;
    private static final long FIRST_MOUSE_MIN_MS = 4_000L;
    private static final long FIRST_MOUSE_MAX_MS = 10_000L;
    private static final long IDLE_MOUSE_MIN_MS = 60_000L;
    private static final long IDLE_MOUSE_MAX_MS = 130_000L;
    private static final long ACTIVITY_DISPLAY_MS = 3_000L;

    private String activeTask;
    private boolean wasBusy;
    private long busyStartedAt;
    private long nextIdleMouseAt;
    private long activityUntil;
    private String activity = "Waiting for skilling";

    void reset()
    {
        activeTask = null;
        wasBusy = false;
        busyStartedAt = 0L;
        nextIdleMouseAt = 0L;
        activityUntil = 0L;
        activity = "Waiting for skilling";
    }

    void tick(String taskName, boolean enabled, int chancePercent)
    {
        if (!enabled)
        {
            reset();
            activity = "Disabled";
            return;
        }

        if (!isSkillingTask(taskName) || !Microbot.isLoggedIn())
        {
            activeTask = null;
            wasBusy = false;
            busyStartedAt = 0L;
            nextIdleMouseAt = 0L;
            if (System.currentTimeMillis() >= activityUntil)
            {
                activity = "Waiting for skilling";
            }
            return;
        }

        if (!taskName.equals(activeTask))
        {
            activeTask = taskName;
            wasBusy = false;
            busyStartedAt = 0L;
            nextIdleMouseAt = 0L;
            activityUntil = 0L;
            activity = "Waiting for action";
        }

        boolean moving;
        boolean animating;
        boolean interacting;
        try
        {
            moving = Rs2Player.isMoving();
            animating = Rs2Player.isAnimating();
            interacting = Rs2Player.isInteracting();
        }
        catch (Exception ex)
        {
            return;
        }

        // Only an already-running, stationary skilling action is eligible. Never
        // move the mouse at the action transition itself: that can overlap the
        // child script's click/menu/inventory input and steal its input window.
        boolean busy = !moving && (animating || interacting);
        long now = System.currentTimeMillis();
        int chance = Math.max(0, Math.min(100, chancePercent));

        if (busy && !wasBusy)
        {
            busyStartedAt = now;
            scheduleFirstSafeMouse(now);
            if (now >= activityUntil)
            {
                activity = "Action grace period";
            }
        }

        if (busy
                && busyStartedAt > 0L
                && now - busyStartedAt >= SAFE_ACTION_GRACE_MS
                && nextIdleMouseAt > 0L
                && now >= nextIdleMouseAt)
        {
            trySafeMouseAway(taskName, now, chance);
            scheduleIdleMouse(now);
        }

        if (!busy)
        {
            busyStartedAt = 0L;
            nextIdleMouseAt = 0L;
            if (now >= activityUntil)
            {
                activity = moving ? "Walking" : "Waiting for action";
            }
        }
        else if (now >= activityUntil)
        {
            if (now - busyStartedAt < SAFE_ACTION_GRACE_MS)
            {
                activity = "Action grace period";
            }
            else
            {
                activity = chance <= 0 ? "Monitoring (0% chance)" : "Monitoring skilling";
            }
        }

        wasBusy = busy;
    }

    String getActivity()
    {
        return activity == null || activity.isEmpty() ? "Idle" : activity;
    }

    long getTimeUntilNextActionSeconds()
    {
        if (nextIdleMouseAt <= 0L)
        {
            return -1L;
        }
        return Math.max(0L, (nextIdleMouseAt - System.currentTimeMillis() + 999L) / 1_000L);
    }

    private void trySafeMouseAway(String taskName, long now, int chancePercent)
    {
        if (!rollPercent(chancePercent))
        {
            showChanceMiss(now);
            return;
        }

        // Deliberately do not use moveMouseRandomly() here. Random canvas movement
        // can contend with the active skilling script's input. Moving off-screen
        // after a stable-action grace period is much less invasive while preserving
        // the intended custom antiban behavior.
        try
        {
            Rs2Antiban.moveMouseOffScreen();
            activity = "Mouse off-screen";
            activityUntil = now + ACTIVITY_DISPLAY_MS;
            log.debug("Account Builder safe antiban mouse off-screen | task={}", taskName);
        }
        catch (Exception ex)
        {
            log.debug("Account Builder mouse off-screen skipped | task={} reason={}", taskName, ex.getMessage());
        }
    }

    private void showChanceMiss(long now)
    {
        activity = "Chance roll skipped";
        activityUntil = now + ACTIVITY_DISPLAY_MS;
    }

    private void scheduleFirstSafeMouse(long now)
    {
        long delay = ThreadLocalRandom.current().nextLong(FIRST_MOUSE_MIN_MS, FIRST_MOUSE_MAX_MS + 1L);
        nextIdleMouseAt = now + Math.max(SAFE_ACTION_GRACE_MS, delay);
    }

    private void scheduleIdleMouse(long now)
    {
        long delay = ThreadLocalRandom.current().nextLong(IDLE_MOUSE_MIN_MS, IDLE_MOUSE_MAX_MS + 1L);
        nextIdleMouseAt = now + delay;
    }

    private static boolean rollPercent(int chancePercent)
    {
        int chance = Math.max(0, Math.min(100, chancePercent));
        if (chance <= 0)
        {
            return false;
        }
        if (chance >= 100)
        {
            return true;
        }
        return ThreadLocalRandom.current().nextInt(100) < chance;
    }

    static boolean isSkillingTask(String taskName)
    {
        if (taskName == null)
        {
            return false;
        }

        switch (taskName)
        {
            case "RUNE_ESSENCE":
            case "MINING":
            case "WOODCUTTING":
            case "FIREMAKING":
            case "FISHING":
            case "COOKING":
            case "CRAFTING":
            case "MELEE":
            case "SMITHING":
            case "SMELTING":
                return true;
            default:
                return false;
        }
    }
}
