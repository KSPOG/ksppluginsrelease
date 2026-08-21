package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import lombok.RequiredArgsConstructor;

/** Profile-specific timing and probability values for the High Alch anti-ban controller. */
@RequiredArgsConstructor
public enum KspHighAlchAntibanProfile {
    LIGHT(0, 120, 0.015, 0.004, 0.012, 3_250, 4_300, 3_500, 8_000, 300, 520),
    BALANCED(10, 220, 0.030, 0.009, 0.025, 3_450, 6_200, 5_000, 14_000, 220, 420),
    HEAVY(35, 350, 0.055, 0.018, 0.045, 3_900, 8_500, 8_000, 22_000, 140, 300);

    final int castJitterMinMillis;
    final int castJitterMaxMillis;
    final double moveChance;
    final double offscreenChance;
    final double shortPauseChance;
    final int shortPauseMinMillis;
    final int shortPauseMaxMillis;
    final int longBreakMinMillis;
    final int longBreakMaxMillis;
    final int castsUntilBreakMin;
    final int castsUntilBreakMax;
}
