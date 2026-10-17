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

    private String activeTask;
    private boolean wasBusy;
    private long nextIdleMouseAt;

    void reset()
    {
        activeTask = null;
        wasBusy = false;
        nextIdleMouseAt = 0L;
    }

    void tick(String taskName, boolean enabled)
    {
        if (!enabled || !isSkillingTask(taskName) || !Microbot.isLoggedIn())
        {
            reset();
            return;
        }

        if (!taskName.equals(activeTask))
        {
            activeTask = taskName;
            wasBusy = false;
            nextIdleMouseAt = 0L;
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
            onSkillingActionStarted(taskName);
            scheduleIdleMouse(now);
        }

        // If a long-running action remains active, occasionally move the mouse
        // away just like the waiting-idle handling in the other KSP controllers.
        if (busy && nextIdleMouseAt > 0L && now >= nextIdleMouseAt)
        {
            moveMouseAway(taskName, "long skilling action");
            scheduleIdleMouse(now);
        }

        if (!busy)
        {
            nextIdleMouseAt = 0L;
        }

        wasBusy = busy;
    }

    private void onSkillingActionStarted(String taskName)
    {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        if (random.nextDouble() < ACTION_MOUSE_MOVE_CHANCE)
        {
            moveMouseRandomly(taskName, "skilling action");
        }

        if (random.nextDouble() < ACTION_MOUSE_OFFSCREEN_CHANCE)
        {
            moveMouseAway(taskName, "skilling action");
        }
    }

    private void moveMouseRandomly(String taskName, String reason)
    {
        try
        {
            Rs2Antiban.moveMouseRandomly();
            log.debug("Account Builder custom antiban random mouse | task={} reason={}", taskName, reason);
        }
        catch (Exception ex)
        {
            log.debug("Account Builder random mouse skipped | task={} reason={}", taskName, ex.getMessage());
        }
    }

    private void moveMouseAway(String taskName, String reason)
    {
        try
        {
            Rs2Antiban.moveMouseOffScreen();
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
