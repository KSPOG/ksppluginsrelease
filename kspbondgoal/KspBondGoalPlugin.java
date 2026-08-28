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
    enabledByDefault = false
)
public class KspBondGoalPlugin extends Plugin
{
    static final String VERSION = "1.0.1";

    private static final int COINS = 995;
    private static final int OLD_SCHOOL_BOND = 13190;
    private static final int REFRESH_TICKS = 10;

    @Inject
    private Client client;

    @Inject
    private ClientThread clientThread;

    @Inject
    private ItemManager itemManager;

    @Inject
    private KspBondGoalConfig config;

    @Inject
    private KspBondGoalOverlay overlay;

    @Inject
    private OverlayManager overlayManager;

    private BondActivityAdvisor advisor;
    private volatile BondGoalSnapshot snapshot = BondGoalSnapshot.empty();
    private volatile boolean running;

    private long cachedBankCoins;
    private boolean bankKnown;
    private int tickCounter;
    private int lastPositiveBondPrice;

    @Provides
    KspBondGoalConfig provideConfig(ConfigManager configManager)
    {
        return configManager.getConfig(KspBondGoalConfig.class);
    }

    @Override
    protected void startUp()
    {
        advisor = new BondActivityAdvisor(client, itemManager, config);
        cachedBankCoins = 0;
        bankKnown = false;
        tickCounter = 0;
        lastPositiveBondPrice = 0;
        running = true;

        overlayManager.add(overlay);

        // Source-loaded plugins may be started from the Swing/AWT thread. Client container
        // access is client-thread-only, so initialise the live account state there.
        clientThread.invokeLater(() ->
        {
            if (!running)
            {
                return;
            }

            ItemContainer bank = client.getItemContainer(InventoryID.BANK);
            if (bank != null)
            {
                cachedBankCoins = countItem(bank, COINS);
                bankKnown = true;
            }

            refreshSnapshotOnClientThread();
        });
    }

    @Override
    protected void shutDown()
    {
        running = false;
        overlayManager.remove(overlay);
        snapshot = BondGoalSnapshot.empty();
        advisor = null;
        cachedBankCoins = 0;
        bankKnown = false;
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
    }

    @Subscribe
    public void onItemContainerChanged(ItemContainerChanged event)
    {
        if (!running)
        {
            return;
        }

        if (event.getContainerId() == InventoryID.BANK)
        {
            cachedBankCoins = countItem(event.getItemContainer(), COINS);
            bankKnown = true;
            refreshSnapshot();
        }
        else if (event.getContainerId() == InventoryID.INV)
        {
            refreshSnapshot();
        }
    }

    @Subscribe
    public void onGameStateChanged(GameStateChanged event)
    {
        GameState state = event.getGameState();
        if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING)
        {
            // Prevent a previous account's bank cache being shown after account changes.
            cachedBankCoins = 0;
            bankKnown = false;
            snapshot = BondGoalSnapshot.empty();
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event)
    {
        if (KspBondGoalConfig.GROUP.equals(event.getGroup()))
        {
            refreshSnapshot();
        }
    }

    BondGoalSnapshot getSnapshot()
    {
        return snapshot;
    }

    KspBondGoalConfig getConfig()
    {
        return config;
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

        if (client.getGameState() != GameState.LOGGED_IN)
        {
            snapshot = BondGoalSnapshot.empty();
            return;
        }

        long inventoryCoins = countItem(client.getItemContainer(InventoryID.INV), COINS);
        long currentCoins = inventoryCoins;

        if (config.includeBankCoins() && bankKnown)
        {
            currentCoins += cachedBankCoins;
        }

        int override = config.bondPriceOverride();
        int fetchedBondPrice = override > 0 ? override : itemManager.getItemPrice(OLD_SCHOOL_BOND);

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
            bankKnown,
            progress,
            activities
        );
    }

    private static long countItem(ItemContainer container, int itemId)
    {
        if (container == null)
        {
            return 0;
        }

        long count = 0;
        for (Item item : container.getItems())
        {
            if (item.getId() == itemId && item.getQuantity() > 0)
            {
                count += item.getQuantity();
            }
        }
        return count;
    }
}
