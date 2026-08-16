package net.runelite.client.plugins.microbot.f2pprocessingfactory;

import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import java.util.concurrent.ThreadLocalRandom;

/**
 * Factory-specific humanization that only acts in safe windows. The controller
 * never performs random clicks, never changes tabs, and never pauses while a GE
 * offer editor or production dialogue is open.
 */
@Slf4j
final class FactoryAntibanController
{
    private final F2PProcessingFactoryConfig config;
    private long sessionStartedAt;
    private long pauseUntil;
    private String pauseReason;
    private boolean mouseMovedForPause;
    private long nextWaitingMouseAt;
    private int processedBatchesSinceLongBreak;
    private int nextLongBreakBatch;
    private volatile String activity = "Active";

    FactoryAntibanController(F2PProcessingFactoryConfig config)
    {
        this.config = config;
        reset();
    }

    void reset()
    {
        sessionStartedAt = System.currentTimeMillis();
        pauseUntil = 0L;
        pauseReason = null;
        mouseMovedForPause = false;
        nextWaitingMouseAt = 0L;
        processedBatchesSinceLongBreak = 0;
        nextLongBreakBatch = randomBetween(profile().longBreakBatchMin, profile().longBreakBatchMax);
        activity = enabled() ? "Active" : "Disabled";
    }

    boolean beforeTick(FactoryState state)
    {
        if (!enabled())
        {
            activity = "Disabled";
            return false;
        }

        long now = System.currentTimeMillis();
        if (pauseUntil > now)
        {
            if (!isPauseSafeState(state) || criticalEditorOpen())
            {
                // Never carry a humanization pause into a critical UI transition.
                clearPause();
                activity = "Active";
                return false;
            }

            if (!mouseMovedForPause && roll(profile().moveMouseAwayChance))
            {
                moveMouseAway("pause");
                mouseMovedForPause = true;
            }
            activity = (pauseReason == null ? "Pausing" : pauseReason)
                + " (" + getPauseSeconds() + "s)";
            return true;
        }

        if (pauseUntil > 0L)
        {
            clearPause();
        }

        if (state == FactoryState.WAITING_FOR_LIMIT || state == FactoryState.WAITING_FOR_MARKET)
        {
            handleWaitingIdle(now);
        }
        else
        {
            activity = "Active";
        }
        return false;
    }

    void onProductionStarted()
    {
        if (!enabled() || criticalEditorOpen())
        {
            return;
        }
        // Once Space has started a long make-all action, moving the pointer away
        // is a safe factory-specific idle behavior and does not alter the action.
        if (roll(profile().moveMouseAwayChance))
        {
            moveMouseAway("production");
            activity = "Production idle";
        }
    }

    void onBatchBanked(boolean anotherBatchAvailable)
    {
        if (!enabled() || !anotherBatchAvailable)
        {
            return;
        }

        processedBatchesSinceLongBreak++;
        if (processedBatchesSinceLongBreak >= nextLongBreakBatch)
        {
            processedBatchesSinceLongBreak = 0;
            nextLongBreakBatch = randomBetween(profile().longBreakBatchMin, profile().longBreakBatchMax);
            schedulePause(
                fatigueAdjusted(profile().longBreakMinMillis),
                fatigueAdjusted(profile().longBreakMaxMillis),
                "Bank break"
            );
            return;
        }

        if (roll(profile().shortPauseChance))
        {
            schedulePause(
                fatigueAdjusted(profile().shortPauseMinMillis),
                fatigueAdjusted(profile().shortPauseMaxMillis),
                "Batch pause"
            );
        }
    }

    void onGeWaitStart()
    {
        if (!enabled() || criticalEditorOpen())
        {
            return;
        }
        // GE offers can sit without pointer activity. Occasionally move away once
        // at the start of a wait instead of repeatedly touching the interface.
        if (roll(profile().moveMouseAwayChance * 0.75))
        {
            moveMouseAway("GE wait");
            activity = "Waiting on GE";
        }
    }

    int immediateCombineRhythmPauseMillis()
    {
        if (!enabled() || !roll(profile().immediateCombinePauseChance))
        {
            return 0;
        }
        return randomBetween(
            profile().immediateCombinePauseMinMillis,
            profile().immediateCombinePauseMaxMillis
        );
    }

    long jitterOfferTimeout(long baseMillis)
    {
        if (!enabled() || baseMillis <= 1_000L)
        {
            return Math.max(1_000L, baseMillis);
        }
        double fraction = profile().offerTimeoutJitterFraction;
        double multiplier = ThreadLocalRandom.current().nextDouble(1.0 - fraction, 1.0 + fraction);
        return Math.max(1_000L, Math.round(baseMillis * multiplier));
    }

    String getStatus()
    {
        if (!enabled())
        {
            return "Disabled";
        }
        return profile().name().charAt(0)
            + profile().name().substring(1).toLowerCase()
            + " · " + activity;
    }

    long getPauseSeconds()
    {
        return Math.max(0L, (pauseUntil - System.currentTimeMillis() + 999L) / 1_000L);
    }

    private void handleWaitingIdle(long now)
    {
        if (nextWaitingMouseAt == 0L)
        {
            nextWaitingMouseAt = now + randomBetween(
                profile().waitingMouseMinMillis,
                profile().waitingMouseMaxMillis
            );
            activity = "Market idle";
            return;
        }

        if (now >= nextWaitingMouseAt && !criticalEditorOpen())
        {
            moveMouseAway("market wait");
            nextWaitingMouseAt = now + randomBetween(
                profile().waitingMouseMinMillis,
                profile().waitingMouseMaxMillis
            );
            activity = "Market idle";
        }
    }

    private void schedulePause(int minimumMillis, int maximumMillis, String reason)
    {
        if (criticalEditorOpen())
        {
            return;
        }
        int duration = randomBetween(minimumMillis, maximumMillis);
        pauseUntil = System.currentTimeMillis() + duration;
        pauseReason = reason;
        mouseMovedForPause = false;
        activity = reason;
        log.debug("KSP AIO Factory anti-ban scheduled {} for {}ms", reason, duration);
    }

    private void clearPause()
    {
        pauseUntil = 0L;
        pauseReason = null;
        mouseMovedForPause = false;
    }

    private boolean criticalEditorOpen()
    {
        try
        {
            return Rs2GrandExchange.isOfferScreenOpen()
                || Rs2Widget.isProductionWidgetOpen()
                || Rs2Widget.hasWidget("Set a price for each item:")
                || Rs2Widget.hasWidget("How many do you wish to make")
                || Rs2Widget.hasWidget("How many would you like to make");
        }
        catch (Exception ignored)
        {
            // Failing closed here means no anti-ban action for this tick.
            return true;
        }
    }

    private static boolean isPauseSafeState(FactoryState state)
    {
        return state == FactoryState.EVALUATING_RECIPES
            || state == FactoryState.PREPARING_CYCLE
            || state == FactoryState.PREPARING_INVENTORY
            || state == FactoryState.WAITING_FOR_LIMIT
            || state == FactoryState.WAITING_FOR_MARKET;
    }

    private void moveMouseAway(String reason)
    {
        try
        {
            Rs2Antiban.moveMouseOffScreen();
            log.debug("KSP AIO Factory anti-ban mouse away: {}", reason);
        }
        catch (Exception ex)
        {
            log.debug("KSP AIO Factory anti-ban mouse-away skipped: {}", ex.getMessage());
        }
    }

    private int fatigueAdjusted(int baseMillis)
    {
        long runtimeMillis = Math.max(0L, System.currentTimeMillis() - sessionStartedAt);
        double hours = runtimeMillis / 3_600_000.0;
        double multiplier = 1.0 + Math.min(0.25, hours * 0.06);
        return Math.max(1, (int) Math.round(baseMillis * multiplier));
    }

    private boolean enabled()
    {
        return config != null && config.customAntiban();
    }

    private FactoryAntibanProfile profile()
    {
        if (config == null || config.antibanProfile() == null)
        {
            return FactoryAntibanProfile.BALANCED;
        }
        return config.antibanProfile();
    }

    private static boolean roll(double chance)
    {
        return chance > 0.0 && ThreadLocalRandom.current().nextDouble() < Math.min(1.0, chance);
    }

    private static int randomBetween(int minimum, int maximum)
    {
        int low = Math.min(minimum, maximum);
        int high = Math.max(minimum, maximum);
        if (low == high)
        {
            return low;
        }
        return ThreadLocalRandom.current().nextInt(low, high + 1);
    }
}
