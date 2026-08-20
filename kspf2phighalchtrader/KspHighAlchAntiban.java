package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;

import java.util.concurrent.ThreadLocalRandom;

/**
 * High-Alchemy-specific anti-ban controller.
 *
 * This deliberately does not use the global Microbot action-cooldown/micro-break flags.
 * Those flags can pause unrelated scripts and can interrupt GE/banking state transitions.
 * Instead, this controller owns a small non-blocking pause window and is only triggered
 * immediately after a confirmed successful High Alchemy cast.
 */
public final class KspHighAlchAntiban {
    private long pauseUntil = 0L;
    private int castsUntilLongBreak = 0;
    private int shortPauses = 0;
    private int longBreaks = 0;
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

    public boolean isPaused() {
        return System.currentTimeMillis() < pauseUntil;
    }

    public long remainingPauseMs() {
        return Math.max(0L, pauseUntil - System.currentTimeMillis());
    }

    public String getActivity() {
        if (isPaused()) {
            return activity + " (" + Math.max(1L, remainingPauseMs() / 1000L) + "s)";
        }
        if ("Short pause".equals(activity) || "Long break".equals(activity)) {
            activity = "Ready";
        }
        return activity;
    }

    public int getShortPauses() {
        return shortPauses;
    }

    public int getLongBreaks() {
        return longBreaks;
    }

    /**
     * Extra delay applied to the normal 3-second High Alchemy cadence.
     */
    public int nextCastJitterMs(KspHighAlchAntibanProfile profile) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        switch (profile) {
            case LIGHT:
                return rng.nextInt(0, 121);
            case HEAVY:
                return rng.nextInt(35, 351);
            case BALANCED:
            default:
                return rng.nextInt(10, 221);
        }
    }

    /**
     * Called only after a cast has been confirmed by inventory quantity change.
     * All pauses are non-blocking: the script's scheduler keeps ticking, but ALCHING
     * simply refrains from casting until pauseUntil has elapsed.
     */
    public void afterSuccessfulCast(KspHighAlchAntibanProfile profile) {
        if (profile == null) {
            return;
        }

        ThreadLocalRandom rng = ThreadLocalRandom.current();
        castsUntilLongBreak--;

        // Small visual/mouse variation. No clicks are generated.
        double moveChance;
        double offscreenChance;
        double shortPauseChance;
        int shortPauseLow;
        int shortPauseHigh;
        switch (profile) {
            case LIGHT:
                moveChance = 0.015;
                offscreenChance = 0.004;
                shortPauseChance = 0.012;
                shortPauseLow = 3_250;
                shortPauseHigh = 4_300;
                break;
            case HEAVY:
                moveChance = 0.055;
                offscreenChance = 0.018;
                shortPauseChance = 0.045;
                shortPauseLow = 3_900;
                shortPauseHigh = 8_500;
                break;
            case BALANCED:
            default:
                moveChance = 0.030;
                offscreenChance = 0.009;
                shortPauseChance = 0.025;
                shortPauseLow = 3_450;
                shortPauseHigh = 6_200;
                break;
        }

        if (rng.nextDouble() < moveChance) {
            // Uses Microbot's existing mouse movement implementation but this plugin owns
            // the timing/chance instead of enabling the global anti-ban state machine.
            Rs2Antiban.moveMouseRandomly();
            activity = "Mouse variation";
        }

        if (rng.nextDouble() < offscreenChance) {
            Rs2Antiban.moveMouseOffScreen();
            activity = "Mouse off-screen";
        }

        if (rng.nextDouble() < shortPauseChance) {
            pauseUntil = Math.max(pauseUntil, System.currentTimeMillis()
                    + rng.nextInt(shortPauseLow, shortPauseHigh + 1));
            shortPauses++;
            activity = "Short pause";
        }

        if (castsUntilLongBreak <= 0) {
            int low;
            int high;
            switch (profile) {
                case LIGHT:
                    low = 3_500;
                    high = 8_000;
                    break;
                case HEAVY:
                    low = 8_000;
                    high = 22_000;
                    break;
                case BALANCED:
                default:
                    low = 5_000;
                    high = 14_000;
                    break;
            }
            pauseUntil = Math.max(pauseUntil, System.currentTimeMillis() + rng.nextInt(low, high + 1));
            longBreaks++;
            activity = "Long break";
            Rs2Antiban.moveMouseOffScreen();
            scheduleNextLongBreak(profile);
        }
    }

    private void scheduleNextLongBreak(KspHighAlchAntibanProfile profile) {
        ThreadLocalRandom rng = ThreadLocalRandom.current();
        switch (profile) {
            case LIGHT:
                castsUntilLongBreak = rng.nextInt(300, 521);
                break;
            case HEAVY:
                castsUntilLongBreak = rng.nextInt(140, 301);
                break;
            case BALANCED:
            default:
                castsUntilLongBreak = rng.nextInt(220, 421);
                break;
        }
    }
}
