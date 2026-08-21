package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import lombok.Getter;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;

import java.util.concurrent.ThreadLocalRandom;

/** High-Alchemy-specific non-blocking anti-ban controller. */
public final class KspHighAlchAntiban {
    private long pauseUntil;
    private int castsUntilLongBreak;
    @Getter private int shortPauses;
    @Getter private int longBreaks;
    private String activity = "Ready";

    public void reset(KspHighAlchAntibanProfile profile) {
        pauseUntil = 0L;
        shortPauses = 0;
        longBreaks = 0;
        activity = "Ready";
        scheduleNextLongBreak(profile);
    }

    public void disabled() {
        pauseUntil = 0L;
        castsUntilLongBreak = Integer.MAX_VALUE;
        activity = "Off";
    }

    public boolean isPaused() { return System.currentTimeMillis() < pauseUntil; }
    public long remainingPauseMs() { return Math.max(0L, pauseUntil - System.currentTimeMillis()); }

    public String getActivity() {
        if (isPaused()) {
            return activity + " (" + Math.max(1L, remainingPauseMs() / 1000L) + "s)";
        }
        if ("Short pause".equals(activity) || "Long break".equals(activity)) {
            activity = "Ready";
        }
        return activity;
    }

    public int nextCastJitterMs(KspHighAlchAntibanProfile profile) {
        return randomInclusive(profile.castJitterMinMillis, profile.castJitterMaxMillis);
    }

    public void afterSuccessfulCast(KspHighAlchAntibanProfile profile) {
        if (profile == null) {
            return;
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        castsUntilLongBreak--;

        if (rng.nextDouble() < profile.moveChance) {
            Rs2Antiban.moveMouseRandomly();
            activity = "Mouse variation";
        }

        if (rng.nextDouble() < profile.offscreenChance) {
            Rs2Antiban.moveMouseOffScreen();
            activity = "Mouse off-screen";
        }

        if (rng.nextDouble() < profile.shortPauseChance) {
            pauseUntil = Math.max(pauseUntil, System.currentTimeMillis()
                    + randomInclusive(profile.shortPauseMinMillis, profile.shortPauseMaxMillis));
            shortPauses++;
            activity = "Short pause";
        }

        if (castsUntilLongBreak <= 0) {
            pauseUntil = Math.max(pauseUntil, System.currentTimeMillis()
                    + randomInclusive(profile.longBreakMinMillis, profile.longBreakMaxMillis));
            longBreaks++;
            activity = "Long break";
            Rs2Antiban.moveMouseOffScreen();
            scheduleNextLongBreak(profile);
        }
    }

    private void scheduleNextLongBreak(KspHighAlchAntibanProfile profile) {
        castsUntilLongBreak = randomInclusive(profile.castsUntilBreakMin, profile.castsUntilBreakMax);
    }

    private static int randomInclusive(int minimum, int maximum) {
        return ThreadLocalRandom.current().nextInt(minimum, maximum + 1);
    }
}
