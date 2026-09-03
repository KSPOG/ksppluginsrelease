package net.runelite.client.plugins.microbot.KSPTradeReceiver;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.LoginManager;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

import javax.inject.Inject;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Localhost mule lifecycle coordinator layered beside the existing Trade Receiver script.
 *
 * The existing script continues to own trade-request handling and trade-screen acceptance.
 * This service only owns cross-plugin coordination: local queueing, waking/logging the mule
 * in, selecting the active trader, matching the worker world, detecting a completed coin
 * transfer, post-transfer banking, queue progression and final logout.
 */
@Slf4j
public class KspLocalMuleCoordinatorService
{
    private static final int COINS_ID = 995;
    private static final long LOGIN_RETRY_MS = 3_000L;
    private static final long HOP_RETRY_MS = 4_000L;
    private static final long LOGOUT_RETRY_MS = 2_000L;
    private static final long COMPLETION_GRACE_MS = 400L;
    private static final long COMPLETION_TIMEOUT_MS = 10_000L;
    private static final int LOGOUT_BUTTON_PACKED_ID = 11927560;

    public static volatile String status = "Stopped";
    public static volatile String activeWorker = "-";
    public static volatile long activeCoins;
    public static volatile int queuedWorkers;
    public static volatile int pendingWorkers;
    public static volatile int localPort;
    public static volatile boolean coordinatorOnline;
    public static volatile boolean banking;

    private final ConfigManager configManager;
    private ScheduledExecutorService executor;
    private KSPTradeReceiverConfig config;
    private KspLocalMuleServer server;

    private String manualTraderBackup = "";
    private String trackedRequestId;
    private long baselineInventoryCoins;
    private long completionCandidateAt;
    private long lastLoginAttemptAt;
    private long lastWorldHopAttemptAt;
    private long lastLogoutAttemptAt;
    private long queueEmptySince;
    private WorldPoint tradeTile;
    private boolean confirmationSeen;
    private volatile boolean stopping;

    @Inject
    public KspLocalMuleCoordinatorService(ConfigManager configManager)
    {
        this.configManager = configManager;
    }

    public synchronized void start(KSPTradeReceiverConfig config)
    {
        if (executor != null)
        {
            return;
        }

        this.config = config;
        this.stopping = false;
        this.manualTraderBackup = config.traderName() == null ? "" : config.traderName();
        resetTracking();
        startServer();

        executor = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "ksp-local-mule-coordinator");
            t.setDaemon(true);
            return t;
        });
        executor.scheduleWithFixedDelay(this::tickSafely, 0L, 500L, TimeUnit.MILLISECONDS);
    }

    private void startServer()
    {
        if (config == null || !config.enableLocalMule())
        {
            status = "Local mule disabled";
            coordinatorOnline = false;
            localPort = config == null ? 17841 : config.localMulePort();
            return;
        }

        try
        {
            server = new KspLocalMuleServer();
            server.start(config.localMulePort());
            coordinatorOnline = true;
            localPort = server.getPort();
            status = "Waiting for workers";
        }
        catch (Exception ex)
        {
            server = null;
            coordinatorOnline = false;
            localPort = config.localMulePort();
            status = "Local mule server failed";
            Microbot.log("KSP local mule server failed: " + ex.getMessage());
            log.error("Unable to start local mule coordinator", ex);
        }
    }

    private void tickSafely()
    {
        try
        {
            tick();
        }
        catch (Exception ex)
        {
            status = "Coordinator error - see log";
            log.error("KSP local mule coordinator tick failed", ex);
        }
    }

    private void tick()
    {
        if (stopping || config == null)
        {
            return;
        }

        if (!config.enableLocalMule())
        {
            status = "Local mule disabled";
            return;
        }

        KspLocalMuleServer currentServer = server;
        if (currentServer == null || !currentServer.isRunning())
        {
            coordinatorOnline = false;
            status = "Coordinator offline";
            return;
        }

        currentServer.expireStaleJobs(TimeUnit.MINUTES.toMillis(Math.max(1, config.jobTimeoutMinutes())));
        queuedWorkers = currentServer.queuedCount();
        pendingWorkers = currentServer.pendingCount();
        localPort = currentServer.getPort();
        coordinatorOnline = true;

        if (!Microbot.isLoggedIn())
        {
            handleLoggedOut(currentServer);
            return;
        }

        updateMuleSnapshot(currentServer);

        if (banking)
        {
            return;
        }

        KspLocalMuleServer.MuleJob active = currentServer.getActiveJob();
        if (active == null)
        {
            active = currentServer.activateNext();
            if (active != null)
            {
                beginTracking(active);
            }
        }
        else if (!active.getRequestId().equals(trackedRequestId))
        {
            beginTracking(active);
        }

        if (active == null)
        {
            restoreManualTrader();
            activeWorker = "-";
            activeCoins = 0L;
            if (currentServer.hasUnacknowledgedCompletion())
            {
                status = "Transfer complete - waiting for worker acknowledgement";
                queueEmptySince = 0L;
                return;
            }
            maybeLogoutWhenDone(currentServer);
            return;
        }

        queueEmptySince = 0L;
        setConfiguredTrader(active.getAccountName());
        activeWorker = active.getAccountName();
        activeCoins = active.getCoins();

        if (!ensureWorld(active))
        {
            return;
        }

        observeTransfer(active, currentServer);
    }

    private void handleLoggedOut(KspLocalMuleServer currentServer)
    {
        confirmationSeen = false;
        completionCandidateAt = 0L;
        banking = false;

        if (!currentServer.hasPendingJobs())
        {
            status = "Logged out - waiting for workers";
            queueEmptySince = 0L;
            restoreManualTrader();
            return;
        }

        queueEmptySince = 0L;
        KspLocalMuleServer.MuleJob next = currentServer.getActiveJob();
        if (next == null)
        {
            next = currentServer.peekNextQueuedJob();
        }

        if (next != null)
        {
            activeWorker = next.getAccountName();
            activeCoins = next.getCoins();
            setConfiguredTrader(next.getAccountName());
        }

        if (!config.autoLoginForJobs())
        {
            status = "Worker queued - auto login disabled";
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastLoginAttemptAt < LOGIN_RETRY_MS || LoginManager.isLoginAttemptActive())
        {
            status = "Worker queued - logging in";
            return;
        }

        lastLoginAttemptAt = now;
        status = "Worker ready - logging mule in";
        int world = next == null ? 0 : next.getWorld();
        boolean requested = world > 0 ? LoginManager.login(world) : LoginManager.login();
        if (!requested && LoginManager.getActiveProfile() == null)
        {
            status = "Cannot login: no active Microbot profile";
        }
    }

    private void updateMuleSnapshot(KspLocalMuleServer currentServer)
    {
        String name = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Player player = Microbot.getClient().getLocalPlayer();
            return player == null ? "" : player.getName();
        }).orElse("");
        currentServer.updateMuleSnapshot(name, Rs2Player.getWorld(), Rs2Player.getWorldLocation());
    }

    private void beginTracking(KspLocalMuleServer.MuleJob job)
    {
        trackedRequestId = job.getRequestId();
        baselineInventoryCoins = Rs2Inventory.itemQuantity(COINS_ID);
        completionCandidateAt = 0L;
        confirmationSeen = false;
        tradeTile = Rs2Player.getWorldLocation();
        activeWorker = job.getAccountName();
        activeCoins = job.getCoins();
        setConfiguredTrader(job.getAccountName());
        status = "Activated " + job.getAccountName();
    }

    private boolean ensureWorld(KspLocalMuleServer.MuleJob job)
    {
        if (job.getWorld() <= 0 || Rs2Player.getWorld() == job.getWorld())
        {
            return true;
        }

        if (Microbot.isHopping())
        {
            status = "Switching to worker world " + job.getWorld();
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastWorldHopAttemptAt < HOP_RETRY_MS)
        {
            status = "Waiting for world hop";
            return false;
        }

        lastWorldHopAttemptAt = now;
        status = "Switching to worker world " + job.getWorld();
        Microbot.hopToWorld(job.getWorld());
        return false;
    }

    private void observeTransfer(KspLocalMuleServer.MuleJob job, KspLocalMuleServer currentServer)
    {
        boolean first = Rs2Widget.isWidgetVisible(InterfaceID.Trademain.ACCEPT);
        boolean confirm = Rs2Widget.isWidgetVisible(InterfaceID.Tradeconfirm.TRADE2ACCEPT);

        if (first || confirm)
        {
            if (tradeTile == null)
            {
                tradeTile = Rs2Player.getWorldLocation();
            }
            queueEmptySince = 0L;
            if (confirm)
            {
                confirmationSeen = true;
                status = "Confirming transfer from " + job.getAccountName();
            }
            else
            {
                status = "Trading with " + job.getAccountName();
            }
            completionCandidateAt = 0L;
            return;
        }

        if (!confirmationSeen)
        {
            status = "Waiting for " + job.getAccountName();
            return;
        }

        long now = System.currentTimeMillis();
        if (completionCandidateAt == 0L)
        {
            completionCandidateAt = now;
            status = "Verifying received coins";
            return;
        }

        if (now - completionCandidateAt < COMPLETION_GRACE_MS)
        {
            return;
        }

        long inventoryCoins = Rs2Inventory.itemQuantity(COINS_ID);
        long received = Math.max(0L, inventoryCoins - baselineInventoryCoins);
        if (received >= job.getCoins())
        {
            currentServer.completeActive();
            status = "Received " + job.getCoins() + " coins";
            trackedRequestId = null;
            confirmationSeen = false;
            completionCandidateAt = 0L;
            if (config.bankAfterTransfer() && !Rs2Inventory.isEmpty())
            {
                bankReceivedInventory();
            }
            return;
        }

        if (now - completionCandidateAt > COMPLETION_TIMEOUT_MS)
        {
            currentServer.failActive("Trade closed without the expected coin increase");
            status = "Transfer verification failed";
            trackedRequestId = null;
            confirmationSeen = false;
            completionCandidateAt = 0L;
        }
    }

    private void bankReceivedInventory()
    {
        banking = true;
        try
        {
            WorldPoint returnTile = tradeTile;
            status = "Transfer complete - banking";
            if (!Rs2Bank.walkToBankAndUseBank())
            {
                status = "Transfer complete - bank unavailable";
                return;
            }

            if (!Rs2Bank.depositAll() && !Rs2Inventory.isEmpty())
            {
                status = "Transfer complete - deposit failed";
                return;
            }
            Rs2Bank.closeBank();

            if (config.returnToTradeTile() && returnTile != null)
            {
                status = "Returning to mule tile";
                if (!Rs2Walker.walkTo(returnTile, 0))
                {
                    Rs2Walker.walkTo(returnTile, 1);
                }
            }
            status = "Banked - checking queue";
        }
        finally
        {
            banking = false;
            tradeTile = Rs2Player.getWorldLocation();
        }
    }

    private void maybeLogoutWhenDone(KspLocalMuleServer currentServer)
    {
        if (currentServer.hasPendingJobs())
        {
            queueEmptySince = 0L;
            return;
        }

        if (!config.autoLogoutWhenDone())
        {
            status = "All transfers done - staying online";
            queueEmptySince = 0L;
            return;
        }

        if (Rs2Widget.isWidgetVisible(InterfaceID.Trademain.ACCEPT)
                || Rs2Widget.isWidgetVisible(InterfaceID.Tradeconfirm.TRADE2ACCEPT))
        {
            queueEmptySince = 0L;
            return;
        }

        long now = System.currentTimeMillis();
        if (queueEmptySince == 0L)
        {
            queueEmptySince = now;
        }
        long quietMs = TimeUnit.SECONDS.toMillis(Math.max(1, config.logoutQuietSeconds()));
        long remaining = quietMs - (now - queueEmptySince);
        if (remaining > 0L)
        {
            status = "All transfers done - logout in "
                    + Math.max(1L, TimeUnit.MILLISECONDS.toSeconds(remaining) + 1L) + "s";
            return;
        }

        if (now - lastLogoutAttemptAt < LOGOUT_RETRY_MS)
        {
            status = "All transfers done - logging out";
            return;
        }

        lastLogoutAttemptAt = now;
        status = "All transfers done - logging out";
        requestLogout();
    }

    private void requestLogout()
    {
        try
        {
            Microbot.getClientThread().runOnClientThreadOptional(() -> {
                if (Microbot.getClient().getGameState() != GameState.LOGGED_IN)
                {
                    return false;
                }
                Microbot.getClient().runScript(915, 10);
                return true;
            });
            Rs2Widget.clickWidget(LOGOUT_BUTTON_PACKED_ID);
        }
        catch (Exception ex)
        {
            log.debug("Logout request failed; will retry", ex);
        }
    }

    private void setConfiguredTrader(String name)
    {
        String value = name == null ? "" : name.trim();
        if (!value.equals(config.traderName()))
        {
            configManager.setConfiguration("KSPTradeReceiver", "traderName", value);
        }
    }

    private void restoreManualTrader()
    {
        if (manualTraderBackup == null)
        {
            manualTraderBackup = "";
        }
        if (!manualTraderBackup.equals(config.traderName()))
        {
            configManager.setConfiguration("KSPTradeReceiver", "traderName", manualTraderBackup);
        }
    }

    private void resetTracking()
    {
        status = "Starting";
        activeWorker = "-";
        activeCoins = 0L;
        queuedWorkers = pendingWorkers = 0;
        localPort = config == null ? 17841 : config.localMulePort();
        coordinatorOnline = false;
        banking = false;
        trackedRequestId = null;
        baselineInventoryCoins = 0L;
        completionCandidateAt = 0L;
        lastLoginAttemptAt = 0L;
        lastWorldHopAttemptAt = 0L;
        lastLogoutAttemptAt = 0L;
        queueEmptySince = 0L;
        tradeTile = null;
        confirmationSeen = false;
    }

    public synchronized void shutdown()
    {
        stopping = true;
        ScheduledExecutorService currentExecutor = executor;
        executor = null;
        if (currentExecutor != null)
        {
            currentExecutor.shutdownNow();
        }

        KspLocalMuleServer currentServer = server;
        server = null;
        if (currentServer != null)
        {
            currentServer.close();
        }

        restoreManualTrader();
        resetTracking();
        status = "Stopped";
    }
}
