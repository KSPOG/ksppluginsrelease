package net.runelite.client.plugins.microbot.kspjewelrycrafter;

/** Presets for Jewellery Crafter task-aware anti-ban behavior. */
public enum JewelryAntibanProfile {
    LIGHT(0.08, 450, 1_300, 18, 30, 4_000, 11_000, 0.18, 90_000, 180_000),
    BALANCED(0.15, 650, 2_100, 10, 18, 8_000, 20_000, 0.32, 60_000, 130_000),
    HIGH(0.24, 850, 3_000, 7, 13, 12_000, 30_000, 0.48, 45_000, 100_000);

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

    JewelryAntibanProfile(
            double shortPauseChance,
            int shortPauseMinMillis,
            int shortPauseMaxMillis,
            int longBreakBatchMin,
            int longBreakBatchMax,
            int longBreakMinMillis,
            int longBreakMaxMillis,
            double moveMouseAwayChance,
            int waitingMouseMinMillis,
            int waitingMouseMaxMillis
    ) {
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
    }
}
