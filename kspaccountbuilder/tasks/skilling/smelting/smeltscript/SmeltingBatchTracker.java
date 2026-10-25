package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltscript;

/**
 * Tracks a Make-All furnace job across gaps between OSRS animation frames.
 *
 * Animation is only one acknowledgement. Decreasing ore batches and increasing
 * Smithing XP also establish progress, so a single animation-free game tick
 * must not cause a bank trip or another furnace click.
 *
 * No client APIs or timers are used here, allowing deterministic tests.
 */
public final class SmeltingBatchTracker
{
    private static final long START_TIMEOUT_MS = 7_000L;
    private static final long INACTIVITY_TIMEOUT_MS = 12_000L;
    private static final long FINAL_ANIMATION_GRACE_MS = 1_500L;

    private volatile boolean tracking;
    private boolean sawActivity;
    private int previousCycles;
    private int previousXp;
    private long startedAtMs;
    private long lastActivityAtMs;
    private long lastAnimationAtMs;
    private long finishedAtMs;

    public void begin(long now, int availableCycles, int smithingXp)
    {
        reset();
        if (availableCycles <= 0) return;
        tracking = true;
        previousCycles = availableCycles;
        previousXp = smithingXp;
        startedAtMs = now;
        lastActivityAtMs = now;
    }

    public boolean isTracking()
    {
        return tracking;
    }

    /**
     * @return true while the furnace operation is starting, running, or
     * completing its final animation. False after completion or a real stall.
     */
    public boolean observe(long now, int availableCycles, int smithingXp, boolean animating)
    {
        if (!tracking) return false;
        int cycles = Math.max(0, availableCycles);

        boolean progress = cycles < previousCycles || smithingXp > previousXp;
        if (progress || animating)
        {
            sawActivity = true;
            lastActivityAtMs = now;
        }
        if (animating) lastAnimationAtMs = now;
        previousCycles = cycles;
        previousXp = Math.max(previousXp, smithingXp);

        // Do not deposit/restock mid-animation after the final ore was taken.
        if (cycles == 0)
        {
            if (finishedAtMs == 0L) finishedAtMs = now;
            if (animating || now - finishedAtMs < FINAL_ANIMATION_GRACE_MS
                    || (lastAnimationAtMs > 0L && now - lastAnimationAtMs < FINAL_ANIMATION_GRACE_MS))
                return true;
            reset();
            return false;
        }

        // First click may take a few ticks to acknowledge; not forever.
        if (!sawActivity && now - startedAtMs <= START_TIMEOUT_MS) return true;

        // Smithing can have idle animation gaps between individual bars.
        if (sawActivity && now - lastActivityAtMs <= INACTIVITY_TIMEOUT_MS) return true;

        reset();
        return false;
    }

    public void reset()
    {
        tracking = false;
        sawActivity = false;
        previousCycles = 0;
        previousXp = 0;
        startedAtMs = 0L;
        lastActivityAtMs = 0L;
        lastAnimationAtMs = 0L;
        finishedAtMs = 0L;
    }
}
