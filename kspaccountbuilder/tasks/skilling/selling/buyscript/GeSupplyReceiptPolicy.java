package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript;

/** Do not clear pending GE supplies based solely on offer completion or collection. */
public final class GeSupplyReceiptPolicy {
    private GeSupplyReceiptPolicy() {}

    public static boolean canClearPending(boolean bankAuditComplete, boolean supplyStillNeeded) {
        return bankAuditComplete && !supplyStillNeeded;
    }
}
