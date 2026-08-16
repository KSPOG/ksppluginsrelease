package net.runelite.client.plugins.microbot.f2pprocessingfactory;

/**
 * Presets for the factory-local humanization layer. These values intentionally
 * affect only safe idle windows between factory actions; they never inject
 * misclicks or interrupt a production/GE editor.
 */
public enum FactoryAntibanProfile
{
    LIGHT(
        0.08, 450, 1_300,
        18, 30, 4_000, 11_000,
        0.18, 90_000, 180_000,
        0.08, 160, 420,
        0.06
    ),
    BALANCED(
        0.15, 650, 2_100,
        10, 18, 8_000, 20_000,
        0.32, 60_000, 130_000,
        0.14, 180, 620,
        0.10
    ),
    HIGH(
        0.24, 850, 3_000,
        7, 13, 12_000, 30_000,
        0.48, 45_000, 100_000,
        0.22, 220, 850,
        0.14
    );

    final double shortPauseChance;
    final int shortPauseMinMillis;
    final int shortPauseMaxMillis;
    final int longBreakBatchMin;
    final int longBreakBatchMax;
    final int longBreakMinMillis;
    final int longBreakMaxMillis;
    final double moveMouseAwayChance;
    final int waitingMouseMinMillis;
    final int waitingMouseMaxMillis;
    final double immediateCombinePauseChance;
    final int immediateCombinePauseMinMillis;
    final int immediateCombinePauseMaxMillis;
    final double offerTimeoutJitterFraction;

    FactoryAntibanProfile(
        double shortPauseChance,
        int shortPauseMinMillis,
        int shortPauseMaxMillis,
        int longBreakBatchMin,
        int longBreakBatchMax,
        int longBreakMinMillis,
        int longBreakMaxMillis,
        double moveMouseAwayChance,
        int waitingMouseMinMillis,
        int waitingMouseMaxMillis,
        double immediateCombinePauseChance,
        int immediateCombinePauseMinMillis,
        int immediateCombinePauseMaxMillis,
        double offerTimeoutJitterFraction)
    {
        this.shortPauseChance = shortPauseChance;
        this.shortPauseMinMillis = shortPauseMinMillis;
        this.shortPauseMaxMillis = shortPauseMaxMillis;
        this.longBreakBatchMin = longBreakBatchMin;
        this.longBreakBatchMax = longBreakBatchMax;
        this.longBreakMinMillis = longBreakMinMillis;
        this.longBreakMaxMillis = longBreakMaxMillis;
        this.moveMouseAwayChance = moveMouseAwayChance;
        this.waitingMouseMinMillis = waitingMouseMinMillis;
        this.waitingMouseMaxMillis = waitingMouseMaxMillis;
        this.immediateCombinePauseChance = immediateCombinePauseChance;
        this.immediateCombinePauseMinMillis = immediateCombinePauseMinMillis;
        this.immediateCombinePauseMaxMillis = immediateCombinePauseMaxMillis;
        this.offerTimeoutJitterFraction = offerTimeoutJitterFraction;
    }
}
