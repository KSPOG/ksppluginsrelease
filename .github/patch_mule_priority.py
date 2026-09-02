from pathlib import Path

# Shared mule service
p = Path('kspmule/KspMuleWorkerService.java')
s = p.read_text()

anchor = 'import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;\n'
if 'util.grandexchange.Rs2GrandExchange' not in s:
    assert anchor in s
    s = s.replace(anchor, anchor + 'import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;\n', 1)

old = '''    private static final long POLL_MS = 1_000L, NETWORK_POLL_MS = 1_000L, HOP_RETRY_MS = 4_000L,
            TRADE_RETRY_MS = 1_500L, ACCEPT_RETRY_MS = 800L, CONFIRM_TRANSITION_TIMEOUT_MS = 6_000L;
'''
new = '''    private static final long POLL_MS = 1_000L, NETWORK_POLL_MS = 1_000L, HOP_RETRY_MS = 4_000L,
            TRADE_RETRY_MS = 1_500L, ACCEPT_RETRY_MS = 800L, CONFIRM_TRANSITION_TIMEOUT_MS = 6_000L,
            FAILED_RETRY_BACKOFF_MS = 10_000L;
'''
assert old in s
s = s.replace(old, new, 1)

old = '    private volatile boolean stopping;\n'
assert old in s
s = s.replace(old, old + '    private volatile long retryNotBefore;\n', 1)

old = '''    public KspMuleWorkerService(String ownerName)
    {
        this.ownerName = ownerName == null || ownerName.isBlank() ? "KSP worker" : ownerName;
    }
'''
new = old + '''
    /** True while any local KSP mule transfer owns interaction priority. */
    public static boolean isTransferPriorityActive()
    {
        return GLOBAL_TRANSFER_LOCK.get();
    }
'''
assert old in s
s = s.replace(old, new, 1)

old = '''    private void maybeStartTransfer()
    {
        KspMuleConfig c = config;
        if (c == null) return;
        long threshold = Math.max(1_000L, c.muleTransferAt());
'''
new = '''    private void maybeStartTransfer()
    {
        KspMuleConfig c = config;
        if (c == null) return;
        long now = System.currentTimeMillis();
        if (retryNotBefore > now)
        {
            state = State.FAILED;
            long seconds = Math.max(1L, (retryNotBefore - now + 999L) / 1_000L);
            status = "Mule retry backoff (" + seconds + "s)";
            return;
        }
        long threshold = Math.max(1_000L, c.muleTransferAt());
'''
assert old in s
s = s.replace(old, new, 1)

old = '''    private long prepareCoinsForTransfer()
    {
        KspMuleConfig c = config;
        if (c == null || !Rs2Bank.walkToBankAndUseBank()) return -1L;
        sleep(400);
'''
new = '''    private long prepareCoinsForTransfer()
    {
        KspMuleConfig c = config;
        if (c == null) return -1L;
        if (Rs2GrandExchange.isOpen())
        {
            status = "Closing Grand Exchange for mule transfer";
            Rs2GrandExchange.closeExchange();
            if (!sleepUntil(() -> !Rs2GrandExchange.isOpen(), 2_500)) return -1L;
        }
        if (!Rs2Bank.walkToBankAndUseBank()) return -1L;
        sleep(400);
'''
assert old in s
s = s.replace(old, new, 1)

old = '''        if (activeMuleName == null || activeMuleName.isBlank())
        {
            state = State.QUEUED;
            status = "Waiting for mule identity";
            return;
        }
        if (muleWorld > 0 && Rs2Player.getWorld() != muleWorld)
'''
new = '''        if (activeMuleName == null || activeMuleName.isBlank())
        {
            state = State.QUEUED;
            status = "Waiting for mule identity";
            return;
        }
        if (!prepareClientForTrade()) return;
        if (muleWorld > 0 && Rs2Player.getWorld() != muleWorld)
'''
assert old in s
s = s.replace(old, new, 1)

marker = '    private void handleTrade()\n'
helper = '''    private boolean prepareClientForTrade()
    {
        if (isFirstTradeOpen() || isConfirmationOpen()) return true;

        boolean closedSomething = false;
        if (Rs2GrandExchange.isOpen())
        {
            status = "Closing Grand Exchange for mule trade";
            Rs2GrandExchange.closeExchange();
            closedSomething = true;
        }
        if (Rs2Bank.isOpen())
        {
            status = "Closing bank for mule trade";
            Rs2Bank.closeBank();
            closedSomething = true;
        }
        if (closedSomething)
            sleepUntil(() -> !Rs2GrandExchange.isOpen() && !Rs2Bank.isOpen(), 2_500);

        if (Rs2GrandExchange.isOpen() || Rs2Bank.isOpen())
        {
            status = "Waiting for interfaces to close before mule trade";
            return false;
        }
        return true;
    }

'''
assert marker in s and 'private boolean prepareClientForTrade()' not in s
s = s.replace(marker, helper + marker, 1)

old = '''    private void finishSuccess()
    {
        clearTransferState();
        state = State.IDLE;
'''
new = '''    private void finishSuccess()
    {
        retryNotBefore = 0L;
        clearTransferState();
        state = State.IDLE;
'''
assert old in s
s = s.replace(old, new, 1)

old = '''    private void fail(String reason, boolean recoverCapital)
    {
        state = State.FAILED;
        status = reason == null || reason.isBlank() ? "Mule failed" : reason;
'''
new = '''    private void fail(String reason, boolean recoverCapital)
    {
        state = State.FAILED;
        status = reason == null || reason.isBlank() ? "Mule failed" : reason;
        retryNotBefore = System.currentTimeMillis() + FAILED_RETRY_BACKOFF_MS;
'''
assert old in s
s = s.replace(old, new, 1)

old = '''        totalCoins = transferCoins = requestStartedAt = lastNetworkPollAt = lastWorldHopAt = lastTradeAttemptAt = lastAcceptAt
                = firstAcceptedAt = preparedTransfer = 0L;
'''
new = '''        totalCoins = transferCoins = requestStartedAt = lastNetworkPollAt = lastWorldHopAt = lastTradeAttemptAt = lastAcceptAt
                = firstAcceptedAt = preparedTransfer = retryNotBefore = 0L;
'''
assert old in s
s = s.replace(old, new, 1)
p.write_text(s)

# Jewelry script
p = Path('kspjewelrycrafter/KspJewelryCrafterScript.java')
s = p.read_text()
anchor = 'import net.runelite.client.plugins.microbot.Script;\n'
if 'kspmule.KspMuleWorkerService' not in s:
    assert anchor in s
    s = s.replace(anchor, anchor + 'import net.runelite.client.plugins.microbot.kspmule.KspMuleWorkerService;\n', 1)

old = '''            try
            {
                if (!super.run() || !Microbot.isLoggedIn()) return;
                tick();
'''
new = '''            try
            {
                if (!Microbot.isLoggedIn()) return;
                if (KspMuleWorkerService.isTransferPriorityActive())
                {
                    status = "Mule transfer has priority";
                    return;
                }
                if (!super.run()) return;
                tick();
'''
assert old in s
s = s.replace(old, new, 1)

old = '''    private void tick()
    {
        switch (state)
'''
new = '''    private void tick()
    {
        if (KspMuleWorkerService.isTransferPriorityActive())
        {
            status = "Mule transfer has priority";
            return;
        }
        switch (state)
'''
assert old in s
s = s.replace(old, new, 1)

old = '''    private boolean openVerifiedBank(boolean edgeville, String openingStatus)
    {
        boolean wasOpen = bankWidgetOpen();
'''
new = '''    private boolean openVerifiedBank(boolean edgeville, String openingStatus)
    {
        if (KspMuleWorkerService.isTransferPriorityActive())
        {
            status = "Mule transfer has priority";
            return false;
        }
        boolean wasOpen = bankWidgetOpen();
'''
assert old in s
s = s.replace(old, new, 1)

old = '''    private boolean openVerifiedGe(String openingStatus)
    {
        if (Rs2GrandExchange.isOpen()) return true;
'''
new = '''    private boolean openVerifiedGe(String openingStatus)
    {
        if (KspMuleWorkerService.isTransferPriorityActive())
        {
            status = "Mule transfer has priority";
            return false;
        }
        if (Rs2GrandExchange.isOpen()) return true;
'''
assert old in s
s = s.replace(old, new, 1)
p.write_text(s)

# Visible version bump
p = Path('kspjewelrycrafter/KspJewelryCrafterPlugin.java')
s = p.read_text()
assert 'VERSION = "0.1.25"' in s
p.write_text(s.replace('VERSION = "0.1.25"', 'VERSION = "0.1.26"', 1))
