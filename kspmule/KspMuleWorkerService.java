package net.runelite.client.plugins.microbot.kspmule;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.player.models.Rs2PlayerModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import net.runelite.client.util.Text;

import java.awt.Rectangle;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/** Reusable worker-side localhost mule state machine for KSP money-making plugins. */
@Slf4j
public final class KspMuleWorkerService
{
    private static final int COINS_ID = 995;
    private static final long POLL_MS = 250L, NETWORK_POLL_MS = 700L, HOP_RETRY_MS = 4_000L,
            TRADE_RETRY_MS = 1_500L, ACCEPT_RETRY_MS = 800L, CONFIRM_TRANSITION_TIMEOUT_MS = 6_000L;
    private static final AtomicBoolean GLOBAL_TRANSFER_LOCK = new AtomicBoolean(false);

    public enum State { IDLE, PREPARING, QUEUED, TRAVELLING, TRADING, CONFIRMING, WAITING_COMPLETE, RESTORING, FAILED }

    private final String ownerName;
    private final KspLocalMuleClient client = new KspLocalMuleClient();
    private ScheduledExecutorService executor;
    private KspMuleConfig config;

    private volatile State state = State.IDLE;
    private volatile String status = "Disabled", muleName = "-";
    private volatile long totalCoins, transferCoins;
    private volatile int queuePosition;
    private volatile boolean receiverOnline;

    private String requestId, activeMuleName;
    private long requestStartedAt, lastNetworkPollAt, lastWorldHopAt, lastTradeAttemptAt, lastAcceptAt,
            firstAcceptedAt, preparedTransfer;
    private WorldPoint muleTile;
    private int muleWorld;
    private boolean offeredCoins, firstAccepted, finalAccepted, pauseOwned, transferLockOwned;
    private volatile boolean stopping;

    public KspMuleWorkerService(String ownerName)
    {
        this.ownerName = ownerName == null || ownerName.isBlank() ? "KSP worker" : ownerName;
    }

    public synchronized void start(KspMuleConfig config)
    {
        if (executor != null) return;
        this.config = config;
        stopping = false;
        resetRuntime();
        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ksp-mule-" + ownerName.replaceAll("[^A-Za-z0-9]", "-").toLowerCase(Locale.ROOT));
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::tickSafely, 1_000L, POLL_MS, TimeUnit.MILLISECONDS);
    }

    private void tickSafely()
    {
        try { tick(); }
        catch (Exception ex)
        {
            log.error("{} mule service failed", ownerName, ex);
            fail("Mule error: " + ex.getMessage(), true);
        }
    }

    private void tick()
    {
        KspMuleConfig c = config;
        if (stopping || c == null) return;
        refreshCoinSummary();

        if (!c.muleEnabled())
        {
            if (requestId != null) cancelCurrent();
            state = State.IDLE;
            status = "Disabled";
            receiverOnline = false;
            releaseOwnership();
            return;
        }
        if (!Microbot.isLoggedIn())
        {
            status = "Waiting for worker login";
            return;
        }
        if (requestId == null)
        {
            enforceBankReserveIfOpen();
            maybeStartTransfer();
            return;
        }
        if (timedOut())
        {
            cancelCurrent();
            fail("Mule request timed out", true);
            return;
        }
        pollJob();
    }

    private long effectiveBankReserve(KspMuleConfig c)
    {
        return Math.max(0L, Math.max((long) c.muleKeepInBank(), (long) c.muleMinimumBankReserve()));
    }

    private long effectiveTradingCapital(KspMuleConfig c)
    {
        return Math.max(0L, Math.max((long) c.muleKeepTradingCapital(), (long) c.muleMinimumTradingCapital()));
    }

    private void refreshCoinSummary()
    {
        totalCoins = Math.max(0L, Rs2Inventory.itemQuantity(COINS_ID)) + Math.max(0L, Rs2Bank.count(COINS_ID));
    }

    private void maybeStartTransfer()
    {
        KspMuleConfig c = config;
        if (c == null) return;
        long threshold = Math.max(1_000L, c.muleTransferAt());
        if (totalCoins < threshold)
        {
            state = State.IDLE;
            status = "Waiting for " + threshold + " total GP";
            receiverOnline = client.ping(c.muleReceiverPort());
            return;
        }

        long keepBank = effectiveBankReserve(c), keepTrading = effectiveTradingCapital(c);
        if (totalCoins <= keepBank + keepTrading)
        {
            state = State.IDLE;
            status = "Threshold reached but reserves consume excess GP";
            return;
        }
        if (!GLOBAL_TRANSFER_LOCK.compareAndSet(false, true))
        {
            state = State.QUEUED;
            status = "Another local KSP plugin is preparing a mule transfer";
            return;
        }
        transferLockOwned = true;
        acquirePause();
        state = State.PREPARING;
        status = "Preparing mule transfer";

        long actualTransfer = prepareCoinsForTransfer();
        if (actualTransfer <= 0L)
        {
            fail("Unable to prepare transfer coins", true);
            return;
        }
        preparedTransfer = transferCoins = actualTransfer;
        String accountName = localPlayerName();
        if (accountName.isBlank())
        {
            fail("Could not determine worker account name", true);
            return;
        }

        requestId = UUID.randomUUID().toString();
        requestStartedAt = System.currentTimeMillis();
        lastNetworkPollAt = 0L;
        KspLocalMuleClient.Status reply = client.ready(c.muleReceiverPort(), requestId, accountName, preparedTransfer, Rs2Player.getWorld());
        receiverOnline = reply.state != KspLocalMuleClient.State.UNKNOWN;
        applyStatus(reply);
    }

    private long prepareCoinsForTransfer()
    {
        KspMuleConfig c = config;
        if (c == null || !Rs2Bank.walkToBankAndUseBank()) return -1L;
        sleep(400);

        long inv = Math.max(0L, Rs2Inventory.itemQuantity(COINS_ID));
        long bank = Math.max(0L, Rs2Bank.count(COINS_ID));
        long total = inv + bank;
        if (total < Math.max(1_000L, c.muleTransferAt()))
        {
            Rs2Bank.closeBank();
            return 0L;
        }

        long desiredBank = Math.min(total, effectiveBankReserve(c) + effectiveTradingCapital(c));
        long transfer = total - desiredBank;
        if (transfer <= 0L || transfer > Integer.MAX_VALUE)
        {
            Rs2Bank.closeBank();
            return -1L;
        }

        if (bank < desiredBank)
        {
            long amount = desiredBank - bank;
            if (amount > inv || amount > Integer.MAX_VALUE || !Rs2Bank.depositX(COINS_ID, (int) amount))
            {
                Rs2Bank.closeBank();
                return -1L;
            }
            sleepUntil(() -> Math.max(0L, Rs2Bank.count(COINS_ID)) >= desiredBank, 5_000);
        }
        else if (bank > desiredBank)
        {
            long amount = bank - desiredBank;
            if (amount > Integer.MAX_VALUE || !Rs2Bank.withdrawX(COINS_ID, (int) amount))
            {
                Rs2Bank.closeBank();
                return -1L;
            }
            sleepUntil(() -> Math.max(0L, Rs2Inventory.itemQuantity(COINS_ID)) >= transfer, 5_000);
        }

        if (Math.max(0L, Rs2Bank.count(COINS_ID)) != desiredBank)
        {
            Rs2Bank.closeBank();
            return -1L;
        }
        Rs2Bank.closeBank();
        return Math.max(0L, Rs2Inventory.itemQuantity(COINS_ID)) == transfer ? transfer : -1L;
    }

    private void pollJob()
    {
        long now = System.currentTimeMillis();
        if (now - lastNetworkPollAt < NETWORK_POLL_MS) return;
        lastNetworkPollAt = now;
        KspMuleConfig c = config;
        if (c == null) return;
        KspLocalMuleClient.Status reply = client.status(c.muleReceiverPort(), requestId);
        receiverOnline = reply.state != KspLocalMuleClient.State.UNKNOWN;
        applyStatus(reply);
    }

    private void applyStatus(KspLocalMuleClient.Status reply)
    {
        switch (reply.state)
        {
            case QUEUED:
                state = State.QUEUED;
                queuePosition = reply.queuePosition;
                status = "Queued for mule (#" + Math.max(1, queuePosition) + ")";
                return;
            case ACTIVE:
                queuePosition = 0;
                activeMuleName = reply.muleName == null ? "" : reply.muleName.trim();
                muleName = activeMuleName.isBlank() ? "-" : activeMuleName;
                muleWorld = reply.world;
                if (reply.x > 0 && reply.y > 0) muleTile = new WorldPoint(reply.x, reply.y, reply.plane);
                handleActive(reply);
                return;
            case COMPLETE:
                state = State.RESTORING;
                status = "Transfer complete - restoring capital";
                restoreTradingCapital();
                finishSuccess();
                return;
            case FAILED:
                fail(reply.message == null || reply.message.isBlank() ? "Receiver reported failure" : reply.message, true);
                return;
            case CANCELLED:
                fail("Mule request cancelled", true);
                return;
            default:
                status = "Waiting for Trade Receiver";
        }
    }

    private void handleActive(KspLocalMuleClient.Status reply)
    {
        if (reply.coins > 0L && reply.coins != preparedTransfer)
        {
            cancelCurrent();
            fail("Receiver transfer amount mismatch", true);
            return;
        }
        if (activeMuleName == null || activeMuleName.isBlank())
        {
            state = State.QUEUED;
            status = "Waiting for mule identity";
            return;
        }
        if (muleWorld > 0 && Rs2Player.getWorld() != muleWorld)
        {
            state = State.TRAVELLING;
            status = "Switching to mule world " + muleWorld;
            long now = System.currentTimeMillis();
            if (!Microbot.isHopping() && now - lastWorldHopAt >= HOP_RETRY_MS)
            {
                lastWorldHopAt = now;
                Microbot.hopToWorld(muleWorld);
            }
            return;
        }
        if (muleTile == null)
        {
            state = State.TRAVELLING;
            status = "Waiting for mule location";
            return;
        }
        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null || here.distanceTo(muleTile) > 3)
        {
            state = State.TRAVELLING;
            status = "Walking to mule";
            Rs2Walker.walkTo(muleTile, 2);
            return;
        }
        handleTrade();
    }

    private void handleTrade()
    {
        if (isConfirmationOpen())
        {
            state = State.CONFIRMING;
            firstAccepted = true;
            if (!confirmationMatchesMule())
            {
                status = "Confirmation opponent mismatch";
                return;
            }
            long now = System.currentTimeMillis();
            if (!finalAccepted && now - lastAcceptAt >= ACCEPT_RETRY_MS)
            {
                lastAcceptAt = now;
                finalAccepted = Rs2Widget.clickWidget(InterfaceID.Tradeconfirm.TRADE2ACCEPT);
                status = finalAccepted ? "Accepted confirmation" : "Waiting for confirmation Accept";
            }
            else if (finalAccepted) status = "Waiting for receiver completion";
            return;
        }

        if (isFirstTradeOpen())
        {
            state = State.TRADING;
            if (!firstScreenMatchesMule())
            {
                status = "Trade opponent mismatch";
                return;
            }
            if (!visibleOwnOfferHasExactCoins(preparedTransfer))
            {
                if (!offeredCoins || System.currentTimeMillis() - lastTradeAttemptAt >= TRADE_RETRY_MS)
                {
                    lastTradeAttemptAt = System.currentTimeMillis();
                    offeredCoins = offerAllCoins();
                }
                status = offeredCoins ? "Waiting for visible coin offer" : "Offering transfer coins";
                return;
            }
            offeredCoins = true;

            if (!firstAccepted)
            {
                long now = System.currentTimeMillis();
                if (now - lastAcceptAt >= ACCEPT_RETRY_MS)
                {
                    lastAcceptAt = now;
                    if (Rs2Widget.clickWidget(InterfaceID.Trademain.ACCEPT))
                    {
                        firstAccepted = true;
                        firstAcceptedAt = now;
                        status = "Accepted first trade screen";
                    }
                    else status = "Waiting for first-screen Accept";
                }
            }
            else status = "Waiting for receiver first-screen Accept";
            return;
        }

        if (finalAccepted)
        {
            state = State.WAITING_COMPLETE;
            status = "Waiting for receiver completion";
            return;
        }
        if (firstAccepted)
        {
            state = State.CONFIRMING;
            if (System.currentTimeMillis() - firstAcceptedAt <= CONFIRM_TRANSITION_TIMEOUT_MS)
            {
                status = "Waiting for confirmation screen";
                return;
            }
            firstAccepted = false;
            firstAcceptedAt = 0L;
            offeredCoins = false;
        }

        state = State.TRADING;
        long now = System.currentTimeMillis();
        if (now - lastTradeAttemptAt < TRADE_RETRY_MS)
        {
            status = "Waiting for trade window";
            return;
        }
        lastTradeAttemptAt = now;
        status = "Trading with " + muleName;
        if (!tradeWithPlayer(activeMuleName)) status = "Waiting for mule to be visible";
    }

    private boolean offerAllCoins()
    {
        Rs2ItemModel coins = Rs2Inventory.get(COINS_ID);
        if (coins == null || coins.getQuantity() != preparedTransfer) return false;
        NewMenuEntry entry = new NewMenuEntry().option("Offer-All").target(coins.getName()).identifier(4)
                .opcode(MenuAction.CC_OP.getId()).param0(coins.getSlot()).param1(InterfaceID.Tradeside.SIDE_LAYER).itemId(COINS_ID);
        Microbot.doInvoke(entry, new Rectangle(1, 1));
        sleep(300);
        return visibleOwnOfferHasExactCoins(preparedTransfer);
    }

    private boolean tradeWithPlayer(String name)
    {
        String wanted = normaliseName(name);
        if (wanted.isEmpty()) return false;
        Rs2PlayerModel model = Microbot.getRs2PlayerCache().getStream()
                .filter(p -> p != null && p.getPlayer() != null)
                .filter(p -> normaliseName(p.getPlayer().getName()).equals(wanted)).findFirst().orElse(null);
        if (model == null) return false;
        Player player = model.getPlayer();
        Integer action = nativeTradeAction();
        if (action == null) return false;
        NewMenuEntry entry = new NewMenuEntry().option("Trade with").target(player.getName()).identifier(player.getId())
                .opcode(action).param0(0).param1(0).actor(player);
        Microbot.doInvoke(entry, new Rectangle(1, 1));
        return true;
    }

    private Integer nativeTradeAction()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            String[] options = Microbot.getClient().getPlayerOptions();
            int[] menuTypes = Microbot.getClient().getPlayerMenuTypes();
            if (options == null || menuTypes == null) return null;
            for (int i = 0; i < Math.min(options.length, menuTypes.length); i++)
            {
                if (!"Trade with".equalsIgnoreCase(cleanText(options[i]))) continue;
                int id = menuTypes[i];
                if (id >= MenuAction.MENU_ACTION_DEPRIORITIZE_OFFSET) id -= MenuAction.MENU_ACTION_DEPRIORITIZE_OFFSET;
                MenuAction action = MenuAction.of(id);
                if (action == MenuAction.PLAYER_FIRST_OPTION || action == MenuAction.PLAYER_SECOND_OPTION
                        || action == MenuAction.PLAYER_THIRD_OPTION || action == MenuAction.PLAYER_FOURTH_OPTION
                        || action == MenuAction.PLAYER_FIFTH_OPTION || action == MenuAction.PLAYER_SIXTH_OPTION
                        || action == MenuAction.PLAYER_SEVENTH_OPTION || action == MenuAction.PLAYER_EIGHTH_OPTION) return id;
            }
            return null;
        }).orElse(null);
    }

    private boolean visibleOwnOfferHasExactCoins(long expected)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget root = Microbot.getClient().getWidget(InterfaceID.Trademain.YOUR_OFFER);
            return root != null && !root.isHidden() && treeHasExactItem(root, COINS_ID, expected);
        }).orElse(false);
    }

    private boolean firstScreenMatchesMule()
    {
        String wanted = normaliseName(activeMuleName);
        if (wanted.isEmpty()) return false;
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget root = Microbot.getClient().getWidget(InterfaceID.Trademain.WHOLESCREEN);
            return root != null && !root.isHidden() && treeMatchesName(root, wanted);
        }).orElse(false);
    }

    private boolean confirmationMatchesMule()
    {
        String opponent = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget w = Microbot.getClient().getWidget(InterfaceID.Tradeconfirm.TRADEOPPONENT);
            return w == null || w.isHidden() ? "" : cleanText(w.getText());
        }).orElse("");
        if (opponent.toLowerCase(Locale.ROOT).startsWith("trading with"))
        {
            int colon = opponent.indexOf(':');
            opponent = cleanText(colon >= 0 ? opponent.substring(colon + 1) : opponent.substring("trading with".length()));
        }
        return normaliseName(opponent).equals(normaliseName(activeMuleName));
    }

    private static boolean treeHasExactItem(Widget widget, int itemId, long expected)
    {
        if (widget == null || widget.isHidden()) return false;
        if (widget.getItemId() == itemId && widget.getItemQuantity() == expected) return true;
        Widget[][] groups = {widget.getChildren(), widget.getDynamicChildren(), widget.getNestedChildren(), widget.getStaticChildren()};
        for (Widget[] group : groups) if (group != null) for (Widget child : group) if (treeHasExactItem(child, itemId, expected)) return true;
        return false;
    }

    private static boolean treeMatchesName(Widget widget, String wanted)
    {
        if (widget == null || widget.isHidden()) return false;
        String text = cleanText(widget.getText());
        if (!text.isEmpty())
        {
            if (text.toLowerCase(Locale.ROOT).startsWith("trading with"))
            {
                int colon = text.indexOf(':');
                String value = colon >= 0 ? text.substring(colon + 1) : text.substring("trading with".length());
                if (normaliseName(value).equals(wanted)) return true;
            }
            if (normaliseName(text).equals(wanted)) return true;
        }
        Widget[][] groups = {widget.getChildren(), widget.getDynamicChildren(), widget.getNestedChildren(), widget.getStaticChildren()};
        for (Widget[] group : groups) if (group != null) for (Widget child : group) if (treeMatchesName(child, wanted)) return true;
        return false;
    }

    private void restoreTradingCapital()
    {
        KspMuleConfig c = config;
        if (c == null || !Microbot.isLoggedIn()) return;
        if (!Rs2Bank.walkToBankAndUseBank())
        {
            status = "Transfer done - could not restore trading capital";
            return;
        }
        long keepBank = effectiveBankReserve(c);
        long bank = Math.max(0L, Rs2Bank.count(COINS_ID));
        long withdraw = Math.min(effectiveTradingCapital(c), Math.max(0L, bank - keepBank));
        if (withdraw > 0L && withdraw <= Integer.MAX_VALUE)
        {
            Rs2Bank.withdrawX(COINS_ID, (int) withdraw);
            sleepUntil(() -> Math.max(0L, Rs2Inventory.itemQuantity(COINS_ID)) >= withdraw, 5_000);
        }
        Rs2Bank.closeBank();
    }

    private void enforceBankReserveIfOpen()
    {
        KspMuleConfig c = config;
        if (c == null || !Rs2Bank.isOpen()) return;
        long keep = effectiveBankReserve(c);
        if (keep <= 0L) return;
        long bank = Math.max(0L, Rs2Bank.count(COINS_ID));
        long missing = keep - bank;
        long inv = Math.max(0L, Rs2Inventory.itemQuantity(COINS_ID));
        if (missing > 0L && missing <= inv && missing <= Integer.MAX_VALUE) Rs2Bank.depositX(COINS_ID, (int) missing);
    }

    private void finishSuccess()
    {
        clearTransferState();
        state = State.IDLE;
        status = "Transfer complete - worker resumed";
        releaseOwnership();
        refreshCoinSummary();
    }

    private void fail(String reason, boolean recoverCapital)
    {
        state = State.FAILED;
        status = reason == null || reason.isBlank() ? "Mule failed" : reason;
        if (recoverCapital && pauseOwned && Microbot.isLoggedIn() && !isFirstTradeOpen() && !isConfirmationOpen())
            try { restoreTradingCapital(); } catch (Exception ex) { log.debug("Unable to restore capital after mule failure", ex); }
        clearTransferState();
        releaseOwnership();
    }

    private void clearTransferState()
    {
        requestId = activeMuleName = null;
        requestStartedAt = preparedTransfer = transferCoins = firstAcceptedAt = 0L;
        muleName = "-";
        muleTile = null;
        muleWorld = queuePosition = 0;
        offeredCoins = firstAccepted = finalAccepted = false;
    }

    private boolean timedOut()
    {
        KspMuleConfig c = config;
        return c != null && requestStartedAt > 0L && System.currentTimeMillis() - requestStartedAt
                > TimeUnit.SECONDS.toMillis(Math.max(30, c.muleTimeoutSeconds()));
    }

    private void cancelCurrent()
    {
        KspMuleConfig c = config;
        if (c != null && requestId != null) client.cancel(c.muleReceiverPort(), requestId);
        requestId = null;
    }

    private void acquirePause()
    {
        if (!Microbot.pauseAllScripts.get())
        {
            Microbot.pauseAllScripts.set(true);
            pauseOwned = true;
        }
    }

    private void releaseOwnership()
    {
        if (pauseOwned)
        {
            Microbot.pauseAllScripts.set(false);
            pauseOwned = false;
        }
        if (transferLockOwned)
        {
            GLOBAL_TRANSFER_LOCK.set(false);
            transferLockOwned = false;
        }
    }

    private String localPlayerName()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player local = Microbot.getClient().getLocalPlayer();
            return local == null || local.getName() == null ? "" : local.getName();
        }).orElse("");
    }

    private static boolean isFirstTradeOpen() { return Rs2Widget.isWidgetVisible(InterfaceID.Trademain.ACCEPT); }
    private static boolean isConfirmationOpen() { return Rs2Widget.isWidgetVisible(InterfaceID.Tradeconfirm.TRADE2ACCEPT); }
    private static String cleanText(String value) { return value == null ? "" : Text.removeTags(Text.unescapeJagex(value)).trim(); }
    private static String normaliseName(String value)
    {
        return cleanText(value).replace('_', ' ').replace('\u00A0', ' ').trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private void resetRuntime()
    {
        state = State.IDLE;
        status = config != null && config.muleEnabled() ? "Waiting for threshold" : "Disabled";
        muleName = "-";
        totalCoins = transferCoins = requestStartedAt = lastNetworkPollAt = lastWorldHopAt = lastTradeAttemptAt = lastAcceptAt
                = firstAcceptedAt = preparedTransfer = 0L;
        queuePosition = muleWorld = 0;
        receiverOnline = false;
        requestId = activeMuleName = null;
        muleTile = null;
        offeredCoins = firstAccepted = finalAccepted = pauseOwned = transferLockOwned = false;
    }

    public State getState() { return state; }
    public String getStatus() { return status; }
    public String getMuleName() { return muleName; }
    public long getTotalCoins() { return totalCoins; }
    public long getTransferCoins() { return transferCoins; }
    public int getQueuePosition() { return queuePosition; }
    public boolean isReceiverOnline() { return receiverOnline; }

    public synchronized void shutdown()
    {
        stopping = true;
        cancelCurrent();
        ScheduledExecutorService current = executor;
        executor = null;
        if (current != null) current.shutdownNow();
        releaseOwnership();
        config = null;
        resetRuntime();
        status = "Stopped";
    }
}
