package net.runelite.client.plugins.microbot.kspseasonalevents;

import java.util.Random;

/**
 * Per-run pacing: a right-skewed reaction before every world click and a skim before every Continue, drawn once per
 * start so no two runs share a rhythm. Pure: randomness only from the seed and the Random passed in. Not Rs2Antiban,
 * whose settings are process-wide.
 */
final class KspHalloweenPace {
    static final int REACTION_FLOOR_MS = 140;
    static final int REACTION_CAP_MS = 2_500;
    static final int READ_CAP_MS = 2_000;

    private final double reactionMedian;
    private final double msPerChar;
    private final double slowChance;

    private KspHalloweenPace(double reactionMedian, double msPerChar, double slowChance) {
        this.reactionMedian = reactionMedian;
        this.msPerChar = msPerChar;
        this.slowChance = slowChance;
    }

    static KspHalloweenPace draw(long seed) {
        Random r = new Random(seed);
        return new KspHalloweenPace(250 + r.nextDouble() * 60, 6 + r.nextDouble() * 6, 0.06 + r.nextDouble() * 0.08);
    }

    /** Before a world click: right-skewed, now and then a slower moment. */
    int reactionMs(Random r) {
        double ms = reactionMedian * Math.exp(0.28 * r.nextGaussian());
        if (r.nextDouble() < slowChance) ms += 500 + r.nextDouble() * 1_200;
        return (int) Math.min(REACTION_CAP_MS, Math.max(REACTION_FLOOR_MS, Math.round(ms)));
    }

    /** Before pressing Continue: a reaction plus time to read the page. */
    int readMs(String text, Random r) {
        int chars = text == null ? 0 : text.replaceAll("<[^>]*>", "").trim().length();
        double read = chars * msPerChar * Math.exp(0.20 * r.nextGaussian());
        return (int) Math.min(READ_CAP_MS, reactionMs(r) + Math.round(read));
    }
}
