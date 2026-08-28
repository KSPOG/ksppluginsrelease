package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.ItemMappingData;

import java.lang.reflect.Field;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Seeds Microbot's item-mapping cache from RuneLite's local item data.
 *
 * <p>High Alch Trader historically called Rs2GrandExchange.getItemMappingData()
 * for the selected item. A cache miss makes Microbot perform a synchronous Wiki
 * /mapping request, which can stall the trader when the Wiki endpoint or route is
 * unavailable. RuneLite already keeps the data we actually need locally:
 * ItemComposition supplies name/membership/High Alch metadata and ItemStats
 * supplies the Grand Exchange buy limit (ge_limit).</p>
 */
@Slf4j
final class KspRuneLiteMarketBackup implements AutoCloseable
{
    private static final long REFRESH_SECONDS = 60L;
    // If RuneLite's item-stats table has not finished loading yet, keep GE-limit
    // protection enabled rather than treating an unknown item as unlimited.
    private static final int SAFE_UNKNOWN_GE_LIMIT = 1;

    private final ItemManager itemManager;
    private final ClientThread clientThread;
    private final Map<Integer, ItemMappingData> mappingCache;
    private final AtomicBoolean refreshQueued = new AtomicBoolean(false);
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r ->
    {
        Thread thread = new Thread(r, "ksp-high-alch-runelite-backup");
        thread.setDaemon(true);
        return thread;
    });

    private volatile KspF2PHighAlchTraderConfig config;
    private volatile boolean closed;
    private volatile boolean unavailableLogged;

    KspRuneLiteMarketBackup(ItemManager itemManager, ClientThread clientThread)
    {
        this.itemManager = itemManager;
        this.clientThread = clientThread;
        this.mappingCache = resolveMicrobotMappingCache();
    }

    /** Prime RuneLite data before the trader starts, then refresh periodically. */
    void start(KspF2PHighAlchTraderConfig config, Runnable onPrimed)
    {
        this.config = config;
        requestRefresh(onPrimed);
        executor.scheduleWithFixedDelay(() -> requestRefresh(null),
            REFRESH_SECONDS, REFRESH_SECONDS, TimeUnit.SECONDS);
    }

    private void requestRefresh(Runnable onComplete)
    {
        if (closed)
        {
            return;
        }

        if (clientThread == null || clientThread.isClientThread())
        {
            refreshOnClientThread();
            if (!closed && onComplete != null)
            {
                onComplete.run();
            }
            return;
        }

        // Startup completion must never be lost. Periodic refreshes can be coalesced.
        if (onComplete == null && !refreshQueued.compareAndSet(false, true))
        {
            return;
        }
        if (onComplete != null)
        {
            refreshQueued.set(true);
        }

        clientThread.invokeLater(() ->
        {
            try
            {
                if (!closed)
                {
                    refreshOnClientThread();
                    if (onComplete != null)
                    {
                        onComplete.run();
                    }
                }
            }
            finally
            {
                refreshQueued.set(false);
            }
        });
    }

    private void refreshOnClientThread()
    {
        if (closed || mappingCache == null || itemManager == null || config == null)
        {
            return;
        }

        Set<Integer> candidates = F2PAlchCatalog.buildCandidateSet(config, true);
        int seeded = 0;
        int conservative = 0;

        for (int itemId : candidates)
        {
            try
            {
                ItemComposition composition = itemManager.getItemComposition(itemId);
                if (composition == null || composition.getName() == null)
                {
                    continue;
                }

                ItemStats stats = itemManager.getItemStats(itemId);
                int geLimit = stats == null ? 0 : stats.getGeLimit();
                if (config.respectGeLimits() && geLimit <= 0)
                {
                    geLimit = SAFE_UNKNOWN_GE_LIMIT;
                    conservative++;
                }

                mappingCache.put(itemId, new ItemMappingData(
                    itemId,
                    composition.getName(),
                    "",
                    composition.isMembers(),
                    geLimit,
                    0,
                    0,
                    composition.getHaPrice(),
                    ""
                ));
                seeded++;
            }
            catch (RuntimeException ex)
            {
                // One malformed/temporarily unavailable definition must not prevent
                // the rest of the local RuneLite mapping table from being seeded.
                log.debug("RuneLite backup could not seed item {}: {}", itemId, ex.getMessage());
            }
        }

        if (seeded > 0)
        {
            unavailableLogged = false;
            log.debug("KSP High Alch Trader RuneLite backup seeded {} mappings ({} conservative limits)",
                seeded, conservative);
        }
        else if (!unavailableLogged)
        {
            unavailableLogged = true;
            log.warn("KSP High Alch Trader RuneLite mapping backup is not ready yet; will retry");
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, ItemMappingData> resolveMicrobotMappingCache()
    {
        try
        {
            Field field = Rs2GrandExchange.class.getDeclaredField("mappingCache");
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map)
            {
                return (Map<Integer, ItemMappingData>) value;
            }
        }
        catch (Exception ex)
        {
            log.error("KSP High Alch Trader could not access Microbot item mapping cache", ex);
        }
        return null;
    }

    @Override
    public void close()
    {
        closed = true;
        refreshQueued.set(false);
        executor.shutdownNow();
    }
}
