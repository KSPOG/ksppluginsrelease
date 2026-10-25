package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript;

import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.GeOfferRepricePolicy;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeOfferCommitPolicyTest {
    @Test void consumedSlotConfirmsCommit() {
        assertTrue(GeOfferCommitPolicy.isCommitted(2, 1, true));
    }
    @Test void unchangedSlotCountRejectsCommit() {
        assertFalse(GeOfferCommitPolicy.isCommitted(2, 2, true));
    }
    @Test void dismissedOfferEditorRejectsCommit() {
        assertFalse(GeOfferCommitPolicy.isCommitted(3, 3, true));
    }
    @Test void closedExchangeRejectsCommit() {
        assertFalse(GeOfferCommitPolicy.isCommitted(2, 1, false));
    }
    @Test void zeroInitialSlotsRejectsCommit() {
        assertFalse(GeOfferCommitPolicy.isCommitted(0, 0, true));
    }
    @Test void invalidPostActionSnapshotRejectsCommit() {
        assertFalse(GeOfferCommitPolicy.isCommitted(2, -1, true));
    }
    @Test void increasingAvailableSlotsRejectsCommit() {
        assertFalse(GeOfferCommitPolicy.isCommitted(2, 3, true));
    }

    @Test void stallsLowerSalesAndRaisePurchases()
    {
        assertEquals(95, GeOfferRepricePolicy.nextPrice(100, 100, 0, false));
        assertEquals(105, GeOfferRepricePolicy.nextPrice(100, 100, 0, true));
        assertEquals(1, GeOfferRepricePolicy.nextPrice(2, 2, 0, false));
        assertEquals(2, GeOfferRepricePolicy.nextPrice(1, 1, 0, true));
    }

    @Test void repricingIsBoundedBySafetyLimits()
    {
        assertEquals(66, GeOfferRepricePolicy.nextPrice(100, 70, 4, false));
        assertEquals(150, GeOfferRepricePolicy.nextPrice(100, 148, 4, true));
        assertEquals(0, GeOfferRepricePolicy.nextPrice(100, 65, 4, false));
        assertEquals(0, GeOfferRepricePolicy.nextPrice(100, 150, 4, true));
        assertEquals(0, GeOfferRepricePolicy.nextPrice(100, 100, GeOfferRepricePolicy.MAX_REPRICES, true));
        assertEquals(0, GeOfferRepricePolicy.nextPrice(100, 100, GeOfferRepricePolicy.MAX_REPRICES, false));
    }

    @Test void partialFillsResetStallTimer()
    {
        GeOfferRepricePolicy tracker = new GeOfferRepricePolicy();
        tracker.placed(1755, 100, 10_000L);
        assertEquals(0, tracker.candidate(1755, 100, 0, 99_999L, true));
        assertEquals(105, tracker.candidate(1755, 100, 0, 100_000L, true));
        assertEquals(0, tracker.candidate(1755, 100, 3, 100_000L, true));
        assertEquals(0, tracker.candidate(1755, 100, 3, 170_000L, true));
        assertEquals(105, tracker.candidate(1755, 100, 3, 190_000L, true));
    }

    @Test void cancellationWaitsAndResumesWithOnlyNewPrice()
    {
        GeOfferRepricePolicy tracker = new GeOfferRepricePolicy();
        tracker.placed(2355, 200, 1_000L);
        int higher = tracker.candidate(2355, 200, 0, 100_000L, true);
        assertEquals(210, higher);
        tracker.requestCancellation(2355, higher);
        assertTrue(tracker.awaitingCancellation(2355));
        assertEquals(0, tracker.candidate(2355, 200, 0, 200_000L, true));
        tracker.cancellationConfirmed(2355, 200_000L);
        assertEquals(1, tracker.retries(2355));
        assertEquals(210, tracker.priceForNewOffer(2355, 200));
        tracker.placed(2355, 210, 202_000L);
        assertEquals(0, tracker.candidate(2355, 210, 0, 211_000L, true));
        assertEquals(221, tracker.candidate(2355, 210, 0, 300_000L, true));
    }

    @Test void doesNotTouchUnknownOrManuallyRepricedOffer()
    {
        GeOfferRepricePolicy tracker = new GeOfferRepricePolicy();
        assertEquals(0, tracker.candidate(1001, 250, 0, 1_000_000L, false));
        tracker.placed(1001, 250, 1_000L);
        assertEquals(0, tracker.candidate(1001, 247, 0, 1_000_000L, false));
        assertEquals(250, tracker.priceForNewOffer(1001, 250));
    }

    @Test void resetDropsQueuedPricesFromPriorSession()
    {
        GeOfferRepricePolicy tracker = new GeOfferRepricePolicy();
        tracker.placed(1002, 300, 1_000L);
        tracker.requestCancellation(1002, 285);
        tracker.cancellationConfirmed(1002, 1_000L);
        assertEquals(285, tracker.priceForNewOffer(1002, 300));
        tracker.reset();
        assertEquals(300, tracker.priceForNewOffer(1002, 300));
    }
}
