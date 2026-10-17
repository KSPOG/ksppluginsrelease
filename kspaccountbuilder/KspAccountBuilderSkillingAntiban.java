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
 * Enabling antiban therefore keeps the controller active for the whole task;
 * individual safe antiban opportunities decide whether to perform an action.</p>
 */
@Slf4j
final class KspAccountBuilderSkillingAntiban
{
    // Existing KSP balanced profiles use roughly 18% random movement versus 32%
    // mouse-away behavior. Normalizing those values gives this action mix after
    // the user's master chance roll succeeds.
    private static final double RANDOM_MOUSE_WEIGHT = 0.36;
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
        int chance = Math.max(0, Math.min(100, chancePercent));

        // Roll once when a real skilling action begins. A failed roll only skips
        // this opportunity; it never disables antiban for the whole task.
        if (busy && !wasBusy)
        {
            tryActionStartAntiban(taskName, now, chance);
            scheduleIdleMouse(now);
        }

        // Long-running actions get another independent chance at a safe mouse-away
        // action, matching the waiting/production idle style of existing KSP antiban.
        if (busy && nextIdleMouseAt > 0L && now >= nextIdleMouseAt)
        {
            tryLongActionAntiban(taskName, now, chance);
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
            activity = chance <= 0 ? "Monitoring (0% chance)" : "Monitoring skilling";
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

    private void tryActionStartAntiban(String taskName, long now, int chancePercent)
    {
        if (!rollPercent(chancePercent))
        {
            showChanceMiss(now);
            return;
        }

        if (ThreadLocalRandom.current().nextDouble() < RANDOM_MOUSE_WEIGHT)
        {
            moveMouseRandomly(taskName, "skilling action", now);
        }
        else
        {
            moveMouseAway(taskName, "skilling action", now);
        }
    }

    private void tryLongActionAntiban(String taskName, long now, int chancePercent)
    {
        if (!rollPercent(chancePercent))
        {
            showChanceMiss(now);
            return;
        }
        moveMouseAway(taskName, "long skilling action", now);
    }

    private void showChanceMiss(long now)
    {
        activity = "Chance roll skipped";
        activityUntil = now + ACTIVITY_DISPLAY_MS;
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
