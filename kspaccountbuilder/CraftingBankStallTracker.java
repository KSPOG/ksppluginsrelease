package net.runelite.client.plugins.microbot.kspaccountbuilder;

/** Tracks eligible Crafting banking inactivity without RuneLite dependencies. */
final class CraftingBankStallTracker
{
    static final long TIMEOUT_MS = 120_000L;
    private static final long MAX_SAMPLE_MS = 10_000L;
    private long observedMillis;
    private long previousSampleMillis;
    private int previousSignature;
    private boolean hasSample;

    boolean sample(long nowMillis, boolean eligible, int progressSignature)
    {
        if (!eligible)
        {
            reset();
            return false;
        }
        if (!hasSample || previousSignature != progressSignature
                || nowMillis < previousSampleMillis)
        {
            observedMillis = 0L;
            previousSampleMillis = nowMillis;
            previousSignature = progressSignature;
            hasSample = true;
            return false;
        }
        long elapsed = Math.max(0L, nowMillis - previousSampleMillis);
        // A prolonged unobserved interval may contain a break, pause or blocking UI action.
        // Rebaseline it rather than claiming that unseen time was eligible inactivity.
        if (elapsed > MAX_SAMPLE_MS)
        {
            observedMillis = 0L;
            previousSampleMillis = nowMillis;
            previousSignature = progressSignature;
            return false;
        }
        observedMillis += elapsed;
        previousSampleMillis = nowMillis;
        if (observedMillis < TIMEOUT_MS)
        {
            return false;
        }
        reset();
        return true;
    }

    long getObservedMillis()
    {
        return observedMillis;
    }

    void reset()
    {
        observedMillis = 0L;
        previousSampleMillis = 0L;
        previousSignature = 0;
        hasSample = false;
    }
}
