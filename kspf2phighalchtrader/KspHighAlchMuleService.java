package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.InventoryID;
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

import static net.runelite.client.plugins.microbot.util.Global.sleep;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

/**
 * Mule companion for the existing High Alch Trader script.
 *
 * It deliberately runs beside (not inside) the trading state machine. When the configured
 * GP threshold is reached it temporarily pauses Microbot scripts, normalises the worker's
 * coin layout, communicates with KSP Trade Receiver over localhost, performs the coin trade,
 * restores the configured trading capital and then resumes the existing trader unchanged.
 */
@Slf4j
public class KspHighAlchMuleService
{
    private static final int COINS_ID = 995;
    private static final long POLL_MS = 350L;
    private static final long NETWORK_POLL_MS = 750L;
    private static final long HOP_RETRY_MS = 4_000L;
    private static final long TRADE_RETRY_MS = 1_500L;
    private static final long ACCEPT_RETRY_MS = 800L;

    public enum MuleState
    {
        IDLE,
        PREPARING,
        QUEUED,
        TRAVELLING,
        TRADING,
        CONFIRMING,
        WAITING_COMPLETE,
        RESTORING,
        FAILED
    }

    public static volatile MuleState state = MuleState.IDLE;
    public static volatile String status = "Disabled";
    public static volatile String muleName = "-";
    public static volatile long totalCoins;
    public static volatile long transferCoins;
    public static volatile long protectedBankCoins;
    public static volatile long tradingCapitalCoins;
    public static volatile int queuePosition;
    public static volatile boolean receiverOnline;

    private final KspLocalMuleClient client = new KspLocalMuleClient();
    private ScheduledExecutorService executor;
    private KspF2PHighAlchTraderConfig config;
    private String requestId;
    private long requestStartedAt;
    private long lastNetworkPollAt;
    private long lastWorldHopAt;
    private long lastTradeAttemptAt;
    private long lastAcceptAt;
    private long preparedTransfer;
    private String activeMuleName;
    private WorldPoint muleTile;
    private int muleWorld;
    private boolean offeredCoins;
    private boolean finalAccepted;
    private boolean pauseOwned;
    private volatile boolean stopping;

    public synchronized void start(KspF2PHighAlchTraderConfig config)
    {
        if (executor != null) return;
        this.config = config;
        this.stopping = false;
        resetRuntime();

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ksp-high-alch-mule");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::tickSafely, 1_000L, POLL_MS, TimeUnit.MILLISECONDS);
    }

    private void tickSafely()
    {
        try
        {
            tick();
        }
        catch (Exception ex)
        {
            log.error("High Alch mule service failed", ex);
            fail("Mule error: " + ex.getMessage(), true);
        }
    }

    private void tick()
    {
        if (stopping || config == null) return;

        if (!config.enableMule())
        {
            if (requestId != null) cancelCurrent();
            state = MuleState.IDLE;
            status = "Disabled";
            receiverOnline = false;
            refreshCoinSummary();
            releasePause();
            return;
        }

        refreshCoinSummary();

        if (!Microbot.isLoggedIn())
        {
            status = "Waiting for worker login";
            return;
        }

        if (requestId == null)
        {
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

    private void refreshCoinSummary()
    {
        long inv = Math.max(0L, Rs2Inventory.itemQuantity(COINS_ID));
        long bank = Math.max(0L, Rs2Bank.count(COINS_ID));
        totalCoins = inv + bank;
        protectedBankCoins = Math.max(0L, config == null ? 0L : config.muleKeepInBank());
        tradingCapitalCoins = Math.max(
                Math.max(0L, config == null ? 0L : config.muleKeepCoins()),
                Math.max(0L, config == null ? 0L : config.reserveCoins()));
    }

    private void maybeStartTransfer()
    {
        if (totalCoins < Math.max(1_000L, config.muleThreshold()))
        {
            state = MuleState.IDLE;
            status = "Waiting for " + config.muleThreshold() + " total GP";
            receiverOnline = client.ping(config.mulePort());
            return;
        }

        long projected = totalCoins - protectedBankCoins - tradingCapitalCoins;
        if (projected <= 0L)
        {
            state = MuleState.IDLE;
            status = "Threshold reached but reserves consume excess GP";
            return;
        }

        acquirePause();
        state = MuleState.PREPARING;
        status = "Preparing " + projected + " GP transfer";

        long actualTransfer = prepareCoinsForTransfer();
        if (actualTransfer <= 0L)
        {
            fail("Unable to prepare transfer coins", true);
            return;
        }

        preparedTransfer = actualTransfer;
        transferCoins = actualTransfer;
        requestId = UUID.randomUUID().toString();
        requestStartedAt = System.currentTimeMillis();
        lastNetworkPollAt = 0L;

        String accountName = localPlayerName();
        if (accountName.isBlank())
        {
            cancelCurrent();
            fail("Could not determine worker account name", true);
            return;
        }

        KspLocalMuleClient.Status reply = client.ready(
                config.mulePort(), requestId, accountName, preparedTransfer, Rs2Player.getWorld());
        receiverOnline = reply.getState() != KspLocalMuleClient.State.UNKNOWN;
        applyStatus(reply);
    }

    /**
     * During the trade, the bank intentionally contains:
     *   protected bank reserve + trading capital.
     * Therefore every inventory coin is safe to Offer-All to the mule.
     */
    private long prepareCoinsForTransfer()
    {
        if (!Rs2Bank.walkToBankAndUseBank()) return -1L;
        sleep(400L);

        long inv = Math.max(0L, Rs2Inventory.itemQuantity(COINS_ID));
        long bank = Math.max(0L, Rs2Bank.count(COINS_ID));
        long total = inv + bank;
        long desiredBank = protectedBankCoins + tradingCapitalCoins;
        long transfer = total - desiredBank;
        if (transfer <= 0L)
        {
            Rs2Bank.closeBank();
            return -1L;
        }

        if (bank < desiredBank)
        {
            long deposit = desiredBank - bank;
            if (deposit > inv || deposit > Integer.MAX_VALUE
                    || !Rs2Bank.depositX(COINS_ID, (int) deposit))
            {
                Rs2Bank.closeBank();
                return -1L;
            }
            sleepUntil(() -> Rs2Bank.count(COINS_ID) >= desiredBank, 5_000);
        }
        else if (bank > desiredBank)
        {
            long withdraw = bank - desiredBank;
            if (withdraw > Integer.MAX_VALUE || !Rs2Bank.withdrawX(COINS_ID, (int) withdraw))
            {
                Rs2Bank.closeBank();
                return -1L;
            }
            sleepUntil(() -> Rs2Inventory.itemQuantity(COINS_ID) >= transfer, 5_000);
        }

        Rs2Bank.closeBank();
        long inventoryTransfer = Math.max(0L, Rs2Inventory.itemQuantity(COINS_ID));
        return inventoryTransfer == transfer ? transfer : -1L;
    }

    private void pollJob()
    {
        long now = System.currentTimeMillis();
        if (now - lastNetworkPollAt < NETWORK_POLL_MS) return;
        lastNetworkPollAt = now;

        KspLocalMuleClient.Status reply = client.status(config.mulePort(), requestId);
        receiverOnline = reply.getState() != KspLocalMuleClient.State.UNKNOWN;
        applyStatus(reply);
    }

    private void applyStatus(KspLocalMuleClient.Status reply)
    {
        switch (reply.getState())
        {
            case QUEUED:
                state = MuleState.QUEUED;
                queuePosition = reply.getQueuePosition();
                status = "Queued for mule (#" + Math.max(1, queuePosition) + ")";
                return;

            case ACTIVE:
                queuePosition = 0;
                activeMuleName = reply.getMuleName();
                muleName = activeMuleName == null || activeMuleName.isBlank() ? "-" : activeMuleName;
                muleWorld = reply.getWorld();
                if (reply.getX() > 0 && reply.getY() > 0)
                {
                    muleTile = new WorldPoint(reply.getX(), reply.getY(), reply.getPlane());
                }
                handleActiveMule(reply);
                return;

            case COMPLETE:
                state = MuleState.RESTORING;
                status = "Transfer complete - restoring capital";
                restoreTradingCapital();
                finishSuccess();
                return;

            case FAILED:
                fail(reply.getMessage().isBlank() ? "Receiver reported failure" : reply.getMessage(), true);
                return;

            case CANCELLED:
                fail("Mule request cancelled", true);
                return;

            case UNKNOWN:
            default:
                status = "Waiting for Trade Receiver";
        }
    }

    private void handleActiveMule(KspLocalMuleClient.Status reply)
    {
        if (reply.getCoins() > 0L && reply.getCoins() != preparedTransfer)
        {
            cancelCurrent();
            fail("Receiver transfer amount mismatch", true);
            return;
        }

        if (muleWorld > 0 && Rs2Player.getWorld() != muleWorld)
        {
            state = MuleState.TRAVELLING;
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
            state = MuleState.TRAVELLING;
            status = "Waiting for mule location";
            return;
        }

        WorldPoint here = Rs2Player.getWorldLocation();
        if (here == null || here.distanceTo(muleTile) > 3)
        {
            state = MuleState.TRAVELLING;
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
            state = MuleState.CONFIRMING;
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
            else if (finalAccepted)
            {
                status = "Waiting for receiver confirmation";
            }
            return;
        }

        if (isFirstTradeOpen())
        {
            state = MuleState.TRADING;
            long offered = tradeOfferQuantity(InventoryID.TRADEOFFER, COINS_ID);
            if (!offeredCoins || offered != preparedTransfer)
            {
                if (offered != 0L && offered != preparedTransfer)
                {
                    status = "Unexpected worker trade offer";
                    return;
                }
                offeredCoins = offerAllCoins();
                status = offeredCoins ? "Offered " + preparedTransfer + " coins" : "Offering transfer coins";
                return;
            }

            long now = System.currentTimeMillis();
            if (now - lastAcceptAt >= ACCEPT_RETRY_MS)
            {
                lastAcceptAt = now;
                if (Rs2Widget.clickWidget(InterfaceID.Trademain.ACCEPT))
                {
                    status = "Accepted first trade screen";
                }
            }
            return;
        }

        if (finalAccepted)
        {
            state = MuleState.WAITING_COMPLETE;
            status = "Waiting for receiver completion";
            return;
        }

        state = MuleState.TRADING;
        long now = System.currentTimeMillis();
        if (now - lastTradeAttemptAt < TRADE_RETRY_MS)
        {
            status = "Waiting for trade window";
            return;
        }
        lastTradeAttemptAt = now;
        status = "Trading with " + muleName;
        if (!tradeWithPlayer(activeMuleName))
        {
            status = "Waiting for mule to be visible";
        }
    }

    private boolean offerAllCoins()
    {
        Rs2ItemModel coins = Rs2Inventory.get(COINS_ID);
        if (coins == null || coins.getQuantity() != preparedTransfer) return false;

        NewMenuEntry entry = new NewMenuEntry()
                .option("Offer-All")
                .target(coins.getName())
                .identifier(4)
                .opcode(MenuAction.CC_OP.getId())
                .param0(coins.getSlot())
                .param1(InterfaceID.Tradeside.SIDE_LAYER)
                .itemId(COINS_ID);
        Microbot.doInvoke(entry, new Rectangle(1, 1));
        sleep(300L);
        return tradeOfferQuantity(InventoryID.TRADEOFFER, COINS_ID) == preparedTransfer;
    }

    private boolean tradeWithPlayer(String name)
    {
        String wanted = normaliseName(name);
        if (wanted.isEmpty()) return false;

        Rs2PlayerModel model = Microbot.getRs2PlayerCache().getStream()
                .filter(p -> p != null && p.getPlayer() != null)
                .filter(p -> normaliseName(p.getPlayer().getName()).equals(wanted))
                .findFirst()
                .orElse(null);
        if (model == null) return false;

        Player player = model.getPlayer();
        Integer action = nativeTradeAction();
        if (action == null) return false;

        NewMenuEntry entry = new NewMenuEntry()
                .option("Trade with")
                .target(player.getName())
                .identifier(player.getId())
                .opcode(action)
                .param0(0)
                .param1(0)
                .actor(player);
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
                if ("Trade with".equalsIgnoreCase(cleanText(options[i]))) return menuTypes[i];
            }
            return null;
        }).orElse(null);
    }

    private boolean confirmationMatchesMule()
    {
        String opponent = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Widget widget = Microbot.getClient().getWidget(InterfaceID.Tradeconfirm.TRADEOPPONENT);
            return widget == null || widget.isHidden() ? "" : cleanText(widget.getText());
        }).orElse("");
        String lower = opponent.toLowerCase(Locale.ROOT);
        if (lower.startsWith("trading with"))
        {
            int colon = opponent.indexOf(':');
            opponent = cleanText(colon >= 0 ? opponent.substring(colon + 1)
                    : opponent.substring("trading with".length()));
        }
        return normaliseName(opponent).equals(normaliseName(activeMuleName));
    }

    private long tradeOfferQuantity(int containerId, int itemId)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ItemContainer container = Microbot.getClient().getItemContainer(containerId);
            if (container == null || container.getItems() == null) return 0L;
            long amount = 0L;
            for (Item item : container.getItems())
            {
                if (item != null && item.getId() == itemId && item.getQuantity() > 0) amount += item.getQuantity();
            }
            return amount;
        }).orElse(0L);
    }

    private void restoreTradingCapital()
    {
        if (!Microbot.isLoggedIn()) return;
        if (!Rs2Bank.walkToBankAndUseBank())
        {
            status = "Transfer done - could not restore trading capital";
            return;
        }

        long bank = Math.max(0L, Rs2Bank.count(COINS_ID));
        long availableAboveReserve = Math.max(0L, bank - protectedBankCoins);
        long withdraw = Math.min(tradingCapitalCoins, availableAboveReserve);
        if (withdraw > 0L && withdraw <= Integer.MAX_VALUE)
        {
            Rs2Bank.withdrawX(COINS_ID, (int) withdraw);
            sleepUntil(() -> Rs2Inventory.itemQuantity(COINS_ID) >= withdraw, 5_000);
        }
        Rs2Bank.closeBank();
    }

    private void finishSuccess()
    {
        requestId = null;
        requestStartedAt = 0L;
        preparedTransfer = 0L;
        transferCoins = 0L;
        activeMuleName = null;
        muleName = "-";
        muleTile = null;
        muleWorld = 0;
        queuePosition = 0;
        offeredCoins = false;
        finalAccepted = false;
        state = MuleState.IDLE;
        status = "Transfer complete - trader resumed";
        releasePause();
        refreshCoinSummary();
    }

    private boolean timedOut()
    {
        return requestStartedAt > 0L && System.currentTimeMillis() - requestStartedAt
                > TimeUnit.SECONDS.toMillis(Math.max(30, config.muleRequestTimeoutSeconds()));
    }

    private void cancelCurrent()
    {
        if (requestId != null) client.cancel(config.mulePort(), requestId);
        requestId = null;
    }

    private void fail(String reason, boolean resume)
    {
        state = MuleState.FAILED;
        status = reason == null || reason.isBlank() ? "Mule failed" : reason;
        requestId = null;
        requestStartedAt = 0L;
        preparedTransfer = 0L;
        transferCoins = 0L;
        activeMuleName = null;
        muleName = "-";
        muleTile = null;
        muleWorld = 0;
        queuePosition = 0;
        offeredCoins = false;
        finalAccepted = false;
        if (resume) releasePause();
    }

    private void acquirePause()
    {
        if (!Microbot.pauseAllScripts.get())
        {
            Microbot.pauseAllScripts.set(true);
            pauseOwned = true;
        }
    }

    private void releasePause()
    {
        if (pauseOwned)
        {
            Microbot.pauseAllScripts.set(false);
            pauseOwned = false;
        }
    }

    private String localPlayerName()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player local = Microbot.getClient().getLocalPlayer();
            return local == null || local.getName() == null ? "" : local.getName();
        }).orElse("");
    }

    private static boolean isFirstTradeOpen()
    {
        return Rs2Widget.isWidgetVisible(InterfaceID.Trademain.ACCEPT);
    }

    private static boolean isConfirmationOpen()
    {
        return Rs2Widget.isWidgetVisible(InterfaceID.Tradeconfirm.TRADE2ACCEPT);
    }

    private static String cleanText(String value)
    {
        return value == null ? "" : Text.removeTags(Text.unescapeJagex(value)).trim();
    }

    private static String normaliseName(String value)
    {
        return cleanText(value).replace('_', ' ').replace('\u00A0', ' ')
                .trim().replaceAll("\\s+", " ").toLowerCase(Locale.ROOT);
    }

    private void resetRuntime()
    {
        state = MuleState.IDLE;
        status = config != null && config.enableMule() ? "Waiting for threshold" : "Disabled";
        muleName = "-";
        totalCoins = transferCoins = 0L;
        protectedBankCoins = tradingCapitalCoins = 0L;
        queuePosition = 0;
        receiverOnline = false;
        requestId = null;
        requestStartedAt = lastNetworkPollAt = lastWorldHopAt = lastTradeAttemptAt = lastAcceptAt = 0L;
        preparedTransfer = 0L;
        activeMuleName = null;
        muleTile = null;
        muleWorld = 0;
        offeredCoins = finalAccepted = pauseOwned = false;
    }

    public synchronized void shutdown()
    {
        stopping = true;
        cancelCurrent();
        ScheduledExecutorService current = executor;
        executor = null;
        if (current != null) current.shutdownNow();
        releasePause();
        resetRuntime();
        status = "Stopped";
    }
}
