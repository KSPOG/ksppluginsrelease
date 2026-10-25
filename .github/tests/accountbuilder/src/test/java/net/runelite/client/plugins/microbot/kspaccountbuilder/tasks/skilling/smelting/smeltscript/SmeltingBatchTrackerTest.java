package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.smeltscript;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class SmeltingBatchTrackerTest
{
    @Test
    void keepsBatchActiveAcrossAnimationFreeFrames()
    {
        SmeltingBatchTracker tracker = new SmeltingBatchTracker();
        tracker.begin(1_000L, 14, 500);
        assertTrue(tracker.observe(1_100L, 14, 500, false));
        assertTrue(tracker.observe(2_000L, 14, 500, true));
        // A 6-second animation gap is not proof that Make-All stopped.
        assertTrue(tracker.observe(8_000L, 14, 500, false));
        // New bars and XP acknowledge the same batch without animation.
        assertTrue(tracker.observe(8_100L, 13, 506, false));
        assertTrue(tracker.observe(18_000L, 12, 512, false));
        assertTrue(tracker.isTracking());
    }

    @Test
    void releasesBatchAfterSustainedInactivity()
    {
        SmeltingBatchTracker tracker = new SmeltingBatchTracker();
        tracker.begin(1_000L, 14, 500);
        assertTrue(tracker.observe(2_000L, 14, 500, true));
        assertTrue(tracker.observe(13_999L, 14, 500, false));
        assertFalse(tracker.observe(14_001L, 14, 500, false));
        assertFalse(tracker.isTracking());
    }

    @Test
    void retriesUnacknowledgedStartAfterTimeout()
    {
        SmeltingBatchTracker tracker = new SmeltingBatchTracker();
        tracker.begin(1_000L, 10, 400);
        assertTrue(tracker.observe(8_000L, 10, 400, false));
        assertFalse(tracker.observe(8_001L, 10, 400, false));
    }

    @Test
    void waitsForFinalBarAnimationThenFinishes()
    {
        SmeltingBatchTracker tracker = new SmeltingBatchTracker();
        tracker.begin(10_000L, 1, 500);
        assertTrue(tracker.observe(10_600L, 1, 500, true));
        assertTrue(tracker.observe(11_000L, 0, 513, true));
        assertTrue(tracker.observe(12_300L, 0, 513, false));
        assertFalse(tracker.observe(12_600L, 0, 513, false));
    }

    @Test
    void observesSmithingXpEvenIfOreSnapshotLags()
    {
        SmeltingBatchTracker tracker = new SmeltingBatchTracker();
        tracker.begin(5_000L, 27, 100);
        assertTrue(tracker.observe(5_500L, 27, 112, false));
        assertTrue(tracker.observe(16_500L, 27, 112, false));
        assertFalse(tracker.observe(17_501L, 27, 112, false));
    }

    @Test
    void resetAndEmptyBatchNeverHoldNavigation()
    {
        SmeltingBatchTracker tracker = new SmeltingBatchTracker();
        tracker.begin(5_000L, 0, 200);
        assertFalse(tracker.isTracking());
        assertFalse(tracker.observe(5_500L, 0, 200, true));
        tracker.begin(10_000L, 2, 300);
        tracker.reset();
        assertFalse(tracker.observe(10_001L, 2, 300, true));
    }
}
