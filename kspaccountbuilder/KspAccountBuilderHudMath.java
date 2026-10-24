package net.runelite.client.plugins.microbot.kspaccountbuilder;

/** Overflow-safe, deterministic XP and ETA calculations for all HUD variants. */
final class KspAccountBuilderHudMath
{
    private KspAccountBuilderHudMath() {}

    static double levelProgress(long xp, long lowerLevelXp, long nextLevelXp, boolean maxLevel)
    {
        if (maxLevel) return 1.0;
        if (nextLevelXp <= lowerLevelXp) return Double.NaN;
        return Math.max(0.0, Math.min(1.0,
                (xp - lowerLevelXp) / (double) (nextLevelXp - lowerLevelXp)));
    }

    static long hourlyXp(long xpGained, long elapsedMillis)
    {
        if (xpGained <= 0 || elapsedMillis < 60000) return 0;
        double rate = xpGained * 3600000.0 / elapsedMillis;
        return (long) Math.min(Long.MAX_VALUE, rate);
    }

    static long etaSeconds(long xpRemaining, long xpPerHour)
    {
        if (xpRemaining <= 0) return 0;
        if (xpPerHour <= 0) return -1;
        double seconds = xpRemaining * 3600.0 / xpPerHour;
        return (long) Math.min(Long.MAX_VALUE, Math.ceil(seconds));
    }

    /**
     * Progress towards an explicit task-duration target. A missing target
     * is not interchangeable with progress towards the next skill level.
     */
    static double taskDurationProgress(long completedSeconds, long targetSeconds)
    {
        if (targetSeconds <= 0) return Double.NaN;
        if (completedSeconds <= 0) return 0.0;
        return Math.max(0.0, Math.min(1.0,
                (double) completedSeconds / (double) targetSeconds));
    }

    /**
     * Height of the original 1395x482 cinematic reference at the requested
     * pixel width. Round up so the original artwork cannot be clipped.
     * This also defines the geometry used by reference HUD hitboxes.
     */
    static int referenceHeight(int width)
    {
        if (width <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE,
                ((long) width * 482L + 1395L - 1L) / 1395L);
    }

    /**
     * Width which fits the reference image within a hard vertical limit.
     * Unlike stretching height separately, this preserves its aspect ratio.
     */
    static int referenceWidthForHeight(int height)
    {
        if (height <= 0) return 0;
        return (int) Math.min(Integer.MAX_VALUE,
                (long) height * 1395L / 482L);
    }

    static int scaledCoordinate(int coordinate, double scale)
    {
        if (!Double.isFinite(scale) || scale <= 0) return -1;
        return (int) Math.floor(coordinate / scale);
    }
}
