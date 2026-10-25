package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript;

public final class GeOfferCommitPolicy {
    private GeOfferCommitPolicy() {}

    public static boolean isCommitted(int before, int after, boolean exchangeOpen) {
        return exchangeOpen && before > 0 && after >= 0 && after < before;
    }
}
