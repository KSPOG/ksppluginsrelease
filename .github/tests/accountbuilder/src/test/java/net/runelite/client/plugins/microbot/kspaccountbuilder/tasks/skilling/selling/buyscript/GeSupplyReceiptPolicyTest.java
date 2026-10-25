package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class GeSupplyReceiptPolicyTest {
    @Test void pendingOfferCannotClearBeforeBankAudit() {
        assertFalse(GeSupplyReceiptPolicy.canClearPending(false, true));
    }
    @Test void collectedButUnauditedSupplyCannotClear() {
        assertFalse(GeSupplyReceiptPolicy.canClearPending(false, false));
    }
    @Test void auditedButMissingSupplyMustRemainPending() {
        assertFalse(GeSupplyReceiptPolicy.canClearPending(true, true));
    }
    @Test void auditedAndReceivedSupplyCanClear() {
        assertTrue(GeSupplyReceiptPolicy.canClearPending(true, false));
    }
}
