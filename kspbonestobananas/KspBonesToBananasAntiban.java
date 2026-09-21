package net.runelite.client.plugins.microbot.kspbonestobananas;

import java.util.concurrent.ThreadLocalRandom;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;

final class KspBonesToBananasAntiban
{
    private long pauseUntil;
    private int castsUntilLongBreak, shortPauses, longBreaks;
    private String activity = "Ready";

    void reset(KspBonesToBananasConfig.AntibanProfile profile)
    {
        pauseUntil = 0L;
        shortPauses = longBreaks = 0;
        activity = "Ready";
        schedule(profile);
    }

    void disable()
    {
        pauseUntil = 0L;
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
            Rs2Antiban.moveMouseOffScreen();
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
            Rs2Antiban.moveMouseOffScreen();
            schedule(p);
        }
    }

    private void schedule(KspBonesToBananasConfig.AntibanProfile p)
    {
        castsUntilLongBreak = random(p.castsMin, p.castsMax);
    }

    private static int random(int min, int max)
    {
        if (max <= min) return min;
        return ThreadLocalRandom.current().nextInt(min, max + 1);
    }
}
