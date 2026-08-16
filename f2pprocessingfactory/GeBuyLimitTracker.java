package net.runelite.client.plugins.microbot.f2pprocessingfactory;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;

import java.lang.reflect.Type;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;

/**
 * Persists the quantities bought by this plugin in each item's active GE limit window.
 *
 * <p>OSRS buy-limit windows begin when the first item is bought and reset completely
 * four hours later. Additional purchases increase usage but do not move the reset time.</p>
 */
@Slf4j
public final class GeBuyLimitTracker
{
    static final long LIMIT_WINDOW_MILLIS = TimeUnit.HOURS.toMillis(4);
    private static final Type LEDGER_TYPE = new TypeToken<Map<Integer, LimitWindow>>() { }.getType();

    private final ConfigManager configManager;
    private final Gson gson;
    private final Map<Integer, LimitWindow> ledger = new HashMap<>();
    private final Set<Integer> coldStartItems = new HashSet<>();

    private String storageKey = "buyLimitLedger_unknown";

    public GeBuyLimitTracker(ConfigManager configManager, Gson gson)
    {
        this.configManager = configManager;
        this.gson = gson;
    }

    public synchronized void loadForAccount(String accountName)
    {
        storageKey = "buyLimitLedger_" + sanitizeAccountName(accountName);
        ledger.clear();
        coldStartItems.clear();

        try
        {
            String json = configManager.getConfiguration(F2PProcessingFactoryConfig.STATE_GROUP, storageKey);
            if (json != null && !json.trim().isEmpty())
            {
                Map<Integer, LimitWindow> loaded = gson.fromJson(json, LEDGER_TYPE);
                if (loaded != null)
                {
                    ledger.putAll(loaded);
                }
            }
        }
        catch (Exception ex)
        {
            log.warn("Unable to load GE buy-limit ledger for {}: {}", accountName, ex.getMessage());
        }

        purgeExpired(System.currentTimeMillis());
        persist();
    }

    public synchronized void recordPurchase(int itemId, int quantity)
    {
        if (itemId <= 0 || quantity <= 0)
        {
            return;
        }

        long now = System.currentTimeMillis();
        purgeExpired(now);

        LimitWindow window = ledger.get(itemId);
        if (window == null)
        {
            window = new LimitWindow(now, 0, coldStartItems.contains(itemId));
            ledger.put(itemId, window);
        }
        window.quantity = saturatingAdd(window.quantity, quantity);
        persist();
    }

    public synchronized int getPurchasedInCurrentWindow(int itemId)
    {
        purgeExpired(System.currentTimeMillis());
        LimitWindow window = ledger.get(itemId);
        return window == null ? 0 : Math.max(0, window.quantity);
    }

    public synchronized int getRemainingCapacity(
        int itemId,
        int officialLimit,
        int usagePercent,
        int coldStartReservePercent)
    {
        purgeExpired(System.currentTimeMillis());

        int safeLimit = Math.max(1, officialLimit);
        int usageCap = percentageOf(safeLimit, Math.max(1, Math.min(100, usagePercent)));

        LimitWindow window = ledger.get(itemId);
        boolean hasTrackedWindow = window != null && window.quantity > 0;
        if (!hasTrackedWindow)
        {
            coldStartItems.add(itemId);
        }

        boolean applyUnknownReserve = coldStartItems.contains(itemId)
            || (window != null && window.conservativeReserveApplied);
        int unknownUsageReserve = applyUnknownReserve
            ? percentageOf(safeLimit, Math.max(0, Math.min(100, coldStartReservePercent)))
            : 0;

        int permitted = Math.max(0, usageCap - unknownUsageReserve);
        int purchased = window == null ? 0 : Math.max(0, window.quantity);
        return Math.max(0, permitted - purchased);
    }

    public synchronized long getMillisUntilNextReset(int itemId)
    {
        long now = System.currentTimeMillis();
        purgeExpired(now);
        LimitWindow window = ledger.get(itemId);
        if (window == null || window.quantity <= 0)
        {
            return 0L;
        }
        return Math.max(0L, (window.startedAt + LIMIT_WINDOW_MILLIS) - now);
    }

    public synchronized long getMillisUntilAnyReset(Set<Integer> itemIds)
    {
        if (itemIds == null || itemIds.isEmpty())
        {
            return 0L;
        }

        long shortest = Long.MAX_VALUE;
        for (Integer itemId : itemIds)
        {
            if (itemId == null)
            {
                continue;
            }
            long remaining = getMillisUntilNextReset(itemId);
            if (remaining > 0)
            {
                shortest = Math.min(shortest, remaining);
            }
        }
        return shortest == Long.MAX_VALUE ? 0L : shortest;
    }

    public synchronized Map<Integer, Integer> snapshotUsage()
    {
        purgeExpired(System.currentTimeMillis());
        Map<Integer, Integer> snapshot = new HashMap<>();
        for (Map.Entry<Integer, LimitWindow> entry : ledger.entrySet())
        {
            LimitWindow window = entry.getValue();
            if (window != null && window.quantity > 0)
            {
                snapshot.put(entry.getKey(), window.quantity);
            }
        }
        return Collections.unmodifiableMap(snapshot);
    }

    private void purgeExpired(long now)
    {
        ledger.entrySet().removeIf(entry ->
        {
            LimitWindow window = entry.getValue();
            return window == null
                || window.quantity <= 0
                || window.startedAt <= 0
                || now >= window.startedAt + LIMIT_WINDOW_MILLIS;
        });
    }

    private void persist()
    {
        try
        {
            configManager.setConfiguration(
                F2PProcessingFactoryConfig.STATE_GROUP,
                storageKey,
                gson.toJson(ledger, LEDGER_TYPE)
            );
        }
        catch (Exception ex)
        {
            log.warn("Unable to persist GE buy-limit ledger: {}", ex.getMessage());
        }
    }

    private static int percentageOf(int value, int percent)
    {
        return (int) Math.floor(value * (percent / 100.0));
    }

    private static int saturatingAdd(int left, int right)
    {
        long total = (long) Math.max(0, left) + Math.max(0, right);
        return total > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) total;
    }

    private static String sanitizeAccountName(String accountName)
    {
        if (accountName == null || accountName.trim().isEmpty())
        {
            return "unknown";
        }
        return accountName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9]+", "_");
    }

    static final class LimitWindow
    {
        long startedAt;
        int quantity;
        boolean conservativeReserveApplied;

        LimitWindow()
        {
        }

        LimitWindow(long startedAt, int quantity, boolean conservativeReserveApplied)
        {
            this.startedAt = startedAt;
            this.quantity = quantity;
            this.conservativeReserveApplied = conservativeReserveApplied;
        }
    }
}
