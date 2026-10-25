package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript;

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
}
