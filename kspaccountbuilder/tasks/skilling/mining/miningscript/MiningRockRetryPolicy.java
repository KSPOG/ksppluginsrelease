package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.miningscript;

final class MiningRockRetryPolicy
{
    static final long SEARCH_RETRY_MS = 750L;
    static final long UNAVAILABLE_LOG_INTERVAL_MS = 10_000L;

    private MiningRockRetryPolicy() {}

    static boolean shouldSearch(long nowMs, long lastUnavailableAtMs)
    {
        return lastUnavailableAtMs <= 0L || nowMs < lastUnavailableAtMs
                || nowMs - lastUnavailableAtMs >= SEARCH_RETRY_MS;
    }

    static boolean shouldReportUnavailable(long nowMs, long lastReportAtMs)
    {
        return lastReportAtMs <= 0L || nowMs < lastReportAtMs
                || nowMs - lastReportAtMs >= UNAVAILABLE_LOG_INTERVAL_MS;
    }
}
