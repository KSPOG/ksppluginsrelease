package net.runelite.client.plugins.microbot.kspaccountbuilder;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Account-Builder-specific skilling humanization.
 *
 * <p>This follows the same pattern used by the existing KSP custom antiban
 * controllers: act only in safe activity windows, use probabilistic mouse
 * variation, and schedule longer-spaced idle mouse-away actions instead of
 * moving the cursor on a fixed loop.</p>
 */
@Slf4j
final class KspAccountBuilderSkillingAntiban
{
    // Balanced values intentionally mirror the general intensity used by the
    // Jewelry Crafter / Smart Smelter custom antiban profiles.
    private static final double ACTION_MOUSE_MOVE_CHANCE = 0.18;
    private static final double ACTION_MOUSE_OFFSCREEN_CHANCE = 0.32;
    private static final long IDLE_MOUSE_MIN_MS = 60_000L;
    private static final long IDLE_MOUSE_MAX_MS = 130_000L;
    private static final long ACTIVITY_DISPLAY_MS = 3_000L;

    private String activeTask;
    private boolean wasBusy;
    private long nextIdleMouseAt;
    private long activityUntil;
    private String activity = "Waiting for skilling";

    void reset()
    {
        activeTask = null;
        wasBusy = false;
        nextIdleMouseAt = 0L;
        activityUntil = 0L;
        activity = "Waiting for skilling";
    }

    void tick(String taskName, boolean enabled)
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

        boolean busy = !moving && (animating || interacting);
        long now = System.currentTimeMillis();

        // Equivalent to the production-start hooks used by Jewelry Crafter and
        // Smart Smelter: only roll when a new skilling action actually begins.
        if (busy && !wasBusy)
        {
            onSkillingActionStarted(taskName, now);
            scheduleIdleMouse(now);
        }

        // If a long-running action remains active, occasionally move the mouse
        // away just like the waiting-idle handling in the other KSP controllers.
        if (busy && nextIdleMouseAt > 0L && now >= nextIdleMouseAt)
        {
            moveMouseAway(taskName, "long skilling action", now);
            scheduleIdleMouse(now);
        }

        if (!busy)
        {
            nextIdleMouseAt = 0L;
            if (now >= activityUntil)
            {
                activity = moving ? "Walking" : "Waiting for action";
            }
        }
        else if (now >= activityUntil)
        {
            activity = "Monitoring skilling";
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

    private void onSkillingActionStarted(String taskName, long now)
    {
        ThreadLocalRandom random = ThreadLocalRandom.current();
        boolean moved = false;

        if (random.nextDouble() < ACTION_MOUSE_MOVE_CHANCE)
        {
            moveMouseRandomly(taskName, "skilling action", now);
            moved = true;
        }

        if (random.nextDouble() < ACTION_MOUSE_OFFSCREEN_CHANCE)
        {
            moveMouseAway(taskName, "skilling action", now);
            moved = true;
        }

        if (!moved)
        {
            activity = "Monitoring skilling";
        }
    }

    private void moveMouseRandomly(String taskName, String reason, long now)
    {
        try
        {
            Rs2Antiban.moveMouseRandomly();
            activity = "Random mouse";
            activityUntil = now + ACTIVITY_DISPLAY_MS;
            log.debug("Account Builder custom antiban random mouse | task={} reason={}", taskName, reason);
        }
        catch (Exception ex)
        {
            log.debug("Account Builder random mouse skipped | task={} reason={}", taskName, ex.getMessage());
        }
    }

    private void moveMouseAway(String taskName, String reason, long now)
    {
        try
        {
            Rs2Antiban.moveMouseOffScreen();
            activity = "Mouse off-screen";
            activityUntil = now + ACTIVITY_DISPLAY_MS;
            log.debug("Account Builder custom antiban mouse off-screen | task={} reason={}", taskName, reason);
        }
        catch (Exception ex)
        {
            log.debug("Account Builder mouse off-screen skipped | task={} reason={}", taskName, ex.getMessage());
        }
    }

    private void scheduleIdleMouse(long now)
    {
        long delay = ThreadLocalRandom.current().nextLong(IDLE_MOUSE_MIN_MS, IDLE_MOUSE_MAX_MS + 1L);
        nextIdleMouseAt = now + delay;
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
