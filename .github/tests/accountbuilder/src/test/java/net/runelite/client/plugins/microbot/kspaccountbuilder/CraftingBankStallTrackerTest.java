package net.runelite.client.plugins.microbot.kspaccountbuilder;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class CraftingBankStallTrackerTest
{
    @Test
    void unchangedBankStateTimesOutOnceAfter120Seconds()
    {
        CraftingBankStallTracker t = new CraftingBankStallTracker();
        assertFalse(t.sample(0L, true, 5));
        for (long s = 1; s < 120; s++) assertFalse(t.sample(s * 1000L, true, 5));
        assertEquals(119_000L, t.getObservedMillis());
        assertTrue(t.sample(120_000L, true, 5));
        assertFalse(t.sample(121_000L, true, 5));
    }

    @Test
    void inventoryOrXpProgressRestartsObservation()
    {
        CraftingBankStallTracker t = new CraftingBankStallTracker();
        t.sample(0L, true, 10);
        for (long s = 1; s <= 100; s++) assertFalse(t.sample(s * 1000L, true, 10));
        assertFalse(t.sample(101_000L, true, 11));
        assertEquals(0L, t.getObservedMillis());
        for (long s = 102; s <= 220; s++) assertFalse(t.sample(s * 1000L, true, 11));
        assertTrue(t.sample(221_000L, true, 11));
    }

    @Test
    void pauseOrMovementExcludesInactiveTime()
    {
        CraftingBankStallTracker t = new CraftingBankStallTracker();
        t.sample(0L, true, 6);
        for (long s = 1; s <= 90; s++) t.sample(s * 1000L, true, 6);
        assertFalse(t.sample(91_000L, false, 6));
        assertEquals(0L, t.getObservedMillis());
        assertFalse(t.sample(200_000L, true, 6));
        assertFalse(t.sample(201_000L, true, 6));
        assertEquals(1_000L, t.getObservedMillis());
    }

    @Test
    void longUnobservedGapRestartsObservation()
    {
        CraftingBankStallTracker t = new CraftingBankStallTracker();
        t.sample(0L, true, 30);
        assertFalse(t.sample(600_000L, true, 30));
        assertEquals(0L, t.getObservedMillis());
        assertFalse(t.sample(601_000L, true, 30));
        assertEquals(1_000L, t.getObservedMillis());
    }

    @Test
    void backwardsClockResetsObservation()
    {
        CraftingBankStallTracker t = new CraftingBankStallTracker();
        t.sample(100_000L, true, 44);
        t.sample(101_000L, true, 44);
        assertFalse(t.sample(50_000L, true, 44));
        assertEquals(0L, t.getObservedMillis());
        assertFalse(t.sample(51_000L, true, 44));
        assertEquals(1_000L, t.getObservedMillis());
    }

    @Test
    void recipeChangeResetsStallTimer()
    {
        CraftingBankStallTracker t = new CraftingBankStallTracker();
        t.sample(0L, true, 51);
        for (long s = 1; s <= 110; s++) assertFalse(t.sample(s * 1000L, true, 51));
        assertFalse(t.sample(111_000L, true, 52));
        assertEquals(0L, t.getObservedMillis());
        assertFalse(t.sample(112_000L, true, 52));
        assertEquals(1_000L, t.getObservedMillis());
    }
    @Test
    void fiveSecondSchedulerTicksExpireAtActual120Seconds()
    {
        CraftingBankStallTracker t = new CraftingBankStallTracker();
        assertFalse(t.sample(0L, true, 91));
        for (long tick = 1; tick < 24; tick++)
            assertFalse(t.sample(tick * 5_000L, true, 91));
        assertEquals(115_000L, t.getObservedMillis());
        assertTrue(t.sample(120_000L, true, 91));
    }

    @Test
    void tenSecondTicksAndExplicitPauseDoNotAccumulatePausedTime()
    {
        CraftingBankStallTracker t = new CraftingBankStallTracker();
        assertFalse(t.sample(0L, true, 92));
        for (long tick = 1; tick <= 6; tick++)
            assertFalse(t.sample(tick * 10_000L, true, 92));
        assertEquals(60_000L, t.getObservedMillis());
        assertFalse(t.sample(61_000L, false, 92));
        assertEquals(0L, t.getObservedMillis());
        assertFalse(t.sample(300_000L, true, 92));
        for (long tick = 1; tick < 12; tick++)
            assertFalse(t.sample(300_000L + tick * 10_000L, true, 92));
        assertTrue(t.sample(420_000L, true, 92));
    }

}
