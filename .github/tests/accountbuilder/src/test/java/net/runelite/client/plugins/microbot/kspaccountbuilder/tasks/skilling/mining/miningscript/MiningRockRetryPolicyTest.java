package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.miningscript;

import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

class MiningRockRetryPolicyTest
{
    @Test void firstSearchAllowed() { assertTrue(MiningRockRetryPolicy.shouldSearch(10000L, 0L)); }
    @Test void immediateRescanSuppressed() { assertFalse(MiningRockRetryPolicy.shouldSearch(10100L, 10000L)); }
    @Test void rescanBeforeBoundarySuppressed() { assertFalse(MiningRockRetryPolicy.shouldSearch(10749L, 10000L)); }
    @Test void rescanAtBoundaryAllowed() { assertTrue(MiningRockRetryPolicy.shouldSearch(10750L, 10000L)); }
    @Test void clockRollbackAllowsRecovery() { assertTrue(MiningRockRetryPolicy.shouldSearch(9999L, 10000L)); }
    @Test void firstUnavailableReportAllowed() { assertTrue(MiningRockRetryPolicy.shouldReportUnavailable(10000L, 0L)); }
    @Test void repeatUnavailableReportSuppressed() { assertFalse(MiningRockRetryPolicy.shouldReportUnavailable(19999L, 10000L)); }
    @Test void unavailableReportAtBoundaryAllowed() { assertTrue(MiningRockRetryPolicy.shouldReportUnavailable(20000L, 10000L)); }
}
