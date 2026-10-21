package net.runelite.client.plugins.microbot.kspbonestobananas;

import java.util.concurrent.ThreadLocalRandom;
import java.awt.Point;
import net.runelite.client.plugins.microbot.Microbot;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;

final class KspBonesToBananasAntiban
{
    private static final Logger log = LoggerFactory.getLogger(KspBonesToBananasAntiban.class);
    private long lastCanvasReturnAttemptAt;
    private long pauseUntil;
    private long offscreenSince;
    private boolean awaitingCanvasReturn;
    private int castsUntilLongBreak, shortPauses, longBreaks;
    private String activity = "Ready";

    void reset(KspBonesToBananasConfig.AntibanProfile profile)
    {
        pauseUntil = 0L;
        offscreenSince = 0L;
        awaitingCanvasReturn = false;
        lastCanvasReturnAttemptAt = 0L;
        shortPauses = longBreaks = 0;
        activity = "Ready";
        schedule(profile);
    }

    void disable()
    {
        pauseUntil = 0L;
        offscreenSince = 0L;
        awaitingCanvasReturn = false;
        lastCanvasReturnAttemptAt = 0L;
        castsUntilLongBreak = Integer.MAX_VALUE;
        activity = "Off";
    }

    boolean isPaused() { return System.currentTimeMillis() < pauseUntil; }
    int getShortPauses() { return shortPauses; }
    int getLongBreaks() { return longBreaks; }

    String getActivity()
    {
        if (isPaused()) return activity + " (" + Math.max(1L, (pauseUntil - System.currentTimeMillis()) / 1000L) + "s)";
        if ("Short pause".equals(activity) || "Long break".equals(activity) || "Cast variation".equals(activity)) activity = "Ready";
        return activity;
    }

    void afterSuccessfulCast(KspBonesToBananasConfig.AntibanProfile p)
    {
        if (p == null) return;
        ThreadLocalRandom r = ThreadLocalRandom.current();
        long now = System.currentTimeMillis();
        pauseUntil = Math.max(pauseUntil, now + random(p.jitterMinMs, p.jitterMaxMs));
        activity = "Cast variation";
        castsUntilLongBreak--;

        if (r.nextDouble() < p.moveChance)
        {
            Rs2Antiban.moveMouseRandomly();
            activity = "Mouse variation";
        }
        if (r.nextDouble() < p.offscreenChance)
        {
            moveMouseOffscreen();
            activity = "Mouse off-screen";
        }
        if (r.nextDouble() < p.shortPauseChance)
        {
            pauseUntil = Math.max(pauseUntil, now + random(p.shortPauseMinMs, p.shortPauseMaxMs));
            shortPauses++;
            activity = "Short pause";
        }
        if (castsUntilLongBreak <= 0)
        {
            pauseUntil = Math.max(pauseUntil, now + random(p.longBreakMinMs, p.longBreakMaxMs));
            longBreaks++;
            activity = "Long break";
            moveMouseOffscreen();
            schedule(p);
        }
    }

    /**
     * Resume mouse automation after the intended off-screen activity has ended.
     * Never override an actual human-input takeover: the scheduler must yield
     * to the core input arbiter when it reports manual ownership.
     */
    void resumeCanvasMouseIfReady(boolean humanInputActive)
    {
        if (!awaitingCanvasReturn || isPaused()) return;
        long now = System.currentTimeMillis();
        if (now - offscreenSince < 1_500L) return;
        // Never take input ownership while a real user is interacting.
        if (humanInputActive) return;

        try
        {
            Point position = Microbot.getMouse().getMousePosition();
            int width = Microbot.getClient().getCanvasWidth();
            int height = Microbot.getClient().getCanvasHeight();
            if (position != null && position.x >= 0 && position.y >= 0
                    && position.x < width && position.y < height)
            {
                awaitingCanvasReturn = false;
                lastCanvasReturnAttemptAt = 0L;
                activity = "Mouse resumed";
                return;
            }
            if (now - lastCanvasReturnAttemptAt < 2_000L) return;
            lastCanvasReturnAttemptAt = now;
            // moveRandom() returns before animation finishes; retain the
            // pending flag and verify the actual cursor position next tick.
            Rs2Antiban.moveMouseRandomly();
            log.info("Bones to Bananas antiban: restoring cursor after off-screen pause | durationMs={} cursor={}",
                    now - offscreenSince, position);
        }
        catch (RuntimeException ex)
        {
            log.warn("Bones to Bananas antiban cursor restore failed: {}", ex.toString());
        }
    }

    private void moveMouseOffscreen()
    {
        Rs2Antiban.moveMouseOffScreen();
        awaitingCanvasReturn = true;
        offscreenSince = System.currentTimeMillis();
    }

    private void schedule(KspBonesToBananasConfig.AntibanProfile p) { castsUntilLongBreak = random(p.castsMin, p.castsMax); }

    private static int random(int min, int max)
    {
        if (max <= min) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
