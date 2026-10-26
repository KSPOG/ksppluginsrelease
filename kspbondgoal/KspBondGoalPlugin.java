package net.runelite.client.plugins.microbot.kspbondgoal;

import com.google.inject.Provides;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.ui.overlay.OverlayManager;

@PluginDescriptor(
    name = PluginDescriptor.Default + "KSP Bond Goal",
    description = "Tracks GP progress toward an Old School bond plus extra coins and advises eligible money-making activities.",
    tags = {"microbot", "ksp", "bond", "money", "f2p", "advisor"},
    version = KspBondGoalPlugin.VERSION,
    minClientVersion = "2.1.32",
    enabledByDefault = false
)
public class KspBondGoalPlugin extends Plugin
{
    static final String VERSION = "1.0.4";

    private static final int COINS = 995;
    private static final int OLD_SCHOOL_BOND = 13190;
    private static final int REFRESH_TICKS = 10;
    private static final String COIN_SNAPSHOT_KEY = BondCoinState.STORAGE_KEY_PREFIX;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ItemManager itemManager;

    @Inject
    private KspBondGoalConfig config;

    @Inject
    private ConfigManager configManager;

    @Inject
    private KspBondGoalOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    private BondActivityAdvisor advisor;
    private BondTradeLimitService tradeLimits;
    private volatile BondGoalSnapshot snapshot = BondGoalSnapshot.empty();
    private volatile boolean running;

    private BondCoinState coinState = BondCoinState.empty();
    private long activeAccountHash;
    private String activeSnapshotKey;
    private String lastSavedCoinSnapshot;
    private boolean bankFreshThisSession;
    private int tickCounter;
    private long lastPositiveBondPrice;

    @Provides
    KspBondGoalConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspBondGoalConfig.class);
    }

    @Override
    protected void startUp()
    {
        tradeLimits = new BondTradeLimitService();
        advisor = new BondActivityAdvisor(client, itemManager, config, tradeLimits);
        clearAccountSession();
        tickCounter = 0;
        lastPositiveBondPrice = 0;
        running = true;

        // A source reload can construct a new copy of KspBondGoalOverlay
        // from a different classloader before the old Plugin is shut down.
        // Removing our injected instance by identity alone cannot remove
        // those old v1.0.2/v1.0.3 overlays. OverlayManager.removeIf
        // also rebuilds render layers so stale panels disappear immediately.
        overlayManager.removeIf(existing -> BondGoalOverlayLifecycle.removeOnStartup(
            existing.getClass().getName()));
        overlayManager.add(overlay);
        tradeLimits.ensureLoaded(this::refreshSnapshot);

        // Source-loaded plugins may be started from the Swing/AWT thread. Client container
        // access is client-thread-only, so initialise the live account state there.
        clientThread.invokeLater(() ->
        {
            if (!running)
            {
                return;
            }

            refreshSnapshotOnClientThread();
        });
    }

    @Override
    protected void shutDown()
    {
        running = false;
        // Protect the *new* plugin if an older copy is shut down later:
        // only remove overlays owned by this exact Plugin object.
        overlayManager.removeIf(existing -> BondGoalOverlayLifecycle.removeOnShutdown(
            existing.getClass().getName(), existing.getPlugin() == this));
        snapshot = BondGoalSnapshot.empty();
        advisor = null;

        BondTradeLimitService limitService = tradeLimits;
        tradeLimits = null;
        if (limitService != null)
        {
            limitService.close();
        }

        clearAccountSession();
    }

    @Subscribe
    public void onGameTick(GameTick event)
    {
        if (!running)
        {
            return;
        }

        if (++tickCounter >= REFRESH_TICKS)
        {
            tickCounter = 0;
            refreshSnapshot();
        }
        else if (activeAccountHash == 0L && client.getGameState() == GameState.LOGGED_IN)
        {
            // Some client builds expose the account hash a few ticks after
            // LOGGED_IN. Recover a saved balance as soon as it is available.
            refreshSnapshot();
        }
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (!running || client.getGameState() != GameState.LOGGED_IN
            || !ensureAccountLoaded())
        {
            return;
        }

        if (event.getContainerId() == InventoryID.BANK)
        {
            // Ignore stale/unloaded bank containers emitted during login or
            // world hopping. Only a visible bank confirms the live bank GP.
            if (Rs2Bank.isOpen() && event.getItemContainer() != null)
            {
                observeBank(event.getItemContainer());
            }
        }
        else if (event.getContainerId() == InventoryID.INV
            && event.getItemContainer() != null)
        {
            observeInventory(event.getItemContainer());
        }
        refreshSnapshot();
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();
        if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
        {
            // The values were saved when observed, so release the in-memory
            // account reference without modifying the persistent balance.
            // Never show the previous account's GP after a character switch.
            clearAccountSession();
            snapshot = BondGoalSnapshot.empty();
        }
        else if (state == GameState.LOGGED_IN && running)
        {
            refreshSnapshot();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (KspBondGoalConfig.GROUP.equals(event.getGroup())
            && (event.getKey() == null
                || !event.getKey().startsWith(COIN_SNAPSHOT_KEY)))
        {
            // Writes to our internal snapshot key must not recursively
            // trigger another expensive advisor/GE-price refresh.
            refreshSnapshot();
        }
    }

    BondGoalSnapshot getSnapshot()
    {
        return snapshot;
    }

    boolean isRunning()
    {
        return running;
    }

    KspBondGoalConfig getConfig()
    {
        return config;
    }

    boolean isBankBalanceCached()
    {
        return config.includeBankCoins() && coinState.isBankKnown() && !bankFreshThisSession;
    }

    boolean areTradeLimitsReady()
    {
        BondTradeLimitService service = tradeLimits;
        return service != null && service.isLoaded();
    }

    String getTradeLimitStatus()
    {
        BondTradeLimitService service = tradeLimits;
        return service == null ? "Unavailable" : service.getStatus();
    }

    /**
     * Refreshes the snapshot immediately on the client thread, or safely schedules it
     * when invoked from AWT/config/plugin lifecycle code.
     */
    private void refreshSnapshot()
    {
        if (!running)
        {
            return;
        }

        if (!clientThread.isClientThread())
        {
            clientThread.invokeLater(() ->
            {
                if (running)
                {
                    refreshSnapshotOnClientThread();
                }
            });
            return;
        }

        refreshSnapshotOnClientThread();
    }

    private void refreshSnapshotOnClientThread()
    {
        if (!running)
        {
            return;
        }

        BondTradeLimitService service = tradeLimits;
        if (service != null)
        {
            // Non-blocking; retries at most once per minute if the Wiki request failed.
            service.ensureLoaded(this::refreshSnapshot);
        }

        if (client.getGameState() != GameState.LOGGED_IN)
        {
            snapshot = BondGoalSnapshot.empty();
            return;
        }
        if (!ensureAccountLoaded())
        {
            snapshot = BondGoalSnapshot.empty();
            return;
        }

        ItemContainer inventory = client.getItemContainer(InventoryID.INV);
        if (inventory != null)
        {
            observeInventory(inventory);
        }

        // Fresh bank data is authoritative, including a true 0 GP stack.
        // If the bank is not open, keep the saved last-known amount.
        if (Rs2Bank.isOpen())
        {
            ItemContainer bank = client.getItemContainer(InventoryID.BANK);
            if (bank != null) observeBank(bank);
        }

        long currentCoins = coinState.currentCoins(config.includeBankCoins());

        int override = config.bondPriceOverride();
        long fetchedBondPrice = override > 0 ? override : itemManager.getItemPrice(OLD_SCHOOL_BOND);

        if (fetchedBondPrice > 0)
        {
            lastPositiveBondPrice = fetchedBondPrice;
        }

        long bondPrice = override > 0 ? override : lastPositiveBondPrice;
        long extra = Math.max(0, config.extraCoins());

        long target = bondPrice > 0 ? bondPrice + extra : 0;
        long remaining = target > 0 ? Math.max(0, target - currentCoins) : 0;
        double progress = target > 0
            ? Math.min(100.0, (currentCoins * 100.0) / target)
            : 0.0;

        List<BondActivityAdvisor.ActivityEstimate> activities =
            advisor == null ? java.util.Collections.emptyList() : advisor.evaluate();

        snapshot = new BondGoalSnapshot(
            bondPrice,
            extra,
            target,
            currentCoins,
            remaining,
            coinState.isBankKnown(),
            progress,
            activities
        );
    }

    /**
     * Save by immutable account hash, never character name. Plain RuneLite
     * config survives plugin reloads, logout, client restarts, and world hops.
     * A zero/unknown account hash must never share a global coin snapshot.
     */
    private boolean ensureAccountLoaded()
    {
        if (client.getGameState() != GameState.LOGGED_IN) return false;
        long hash = client.getAccountHash();
        if (hash == 0L) return false;

        if (activeAccountHash != hash)
        {
            clearAccountSession();
            activeAccountHash = hash;
            activeSnapshotKey = BondCoinState.keyForAccount(hash);
            String stored = configManager.getConfiguration(KspBondGoalConfig.GROUP, activeSnapshotKey);
            coinState = BondCoinState.deserialize(stored);
            lastSavedCoinSnapshot = stored;
        }
        return true;
    }

    private void observeInventory(ItemContainer inventory)
    {
        if (inventory == null) return;
        updateCoinState(coinState.observedInventory(countItem(inventory, COINS)));
    }

    private void observeBank(ItemContainer bank)
    {
        if (bank == null) return;
        bankFreshThisSession = true;
        updateCoinState(coinState.observedBank(countItem(bank, COINS)));
    }

    private void updateCoinState(BondCoinState newState)
    {
        coinState = newState;
        if (activeSnapshotKey == null) return;
        String serialized = newState.serialize();
        if (!serialized.equals(lastSavedCoinSnapshot))
        {
            // Avoid writing to ConfigManager every game tick. A changed
            // inventory/bank balance is persisted immediately after receipt.
            lastSavedCoinSnapshot = serialized;
            configManager.setConfiguration(KspBondGoalConfig.GROUP, activeSnapshotKey, serialized);
        }
    }

    private void clearAccountSession()
    {
        activeAccountHash = 0L;
        activeSnapshotKey = null;
        lastSavedCoinSnapshot = null;
        bankFreshThisSession = false;
        coinState = BondCoinState.empty();
    }

    private static long countItem(ItemContainer container, int itemId)
    {
        if (container == null)
        {
            return 0;
        }

        long count = 0;
        Item[] items = container.getItems();
        if (items == null) return 0;
        for (Item item : items)
        {
            if (item != null && item.getId() == itemId && item.getQuantity() > 0)
            {
                count += item.getQuantity();
            }
        }
        return count;
    }
}
