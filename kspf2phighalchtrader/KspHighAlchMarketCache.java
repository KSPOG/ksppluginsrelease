package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;

import java.lang.reflect.Field;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * Keeps Microbot's GE price cache warm from one bulk OSRS Wiki request instead of
 * making one HTTP request per High Alch candidate. This prevents transient Wiki
 * timeouts from stalling a full market scan.
 */
@Slf4j
final class KspHighAlchMarketCache implements AutoCloseable
{
    private static final String LATEST_URL = "https://prices.runescape.wiki/api/v1/osrs/latest";
    private static final long REFRESH_SECONDS = 45L;
    private static final long MAX_STALE_LIVE_MS = TimeUnit.MINUTES.toMillis(15);
    private static final int NATURE_RUNE_ID = 561;
    private static final int FIRE_RUNE_ID = 554;
    private static final double FALLBACK_BUY_PREMIUM = 1.02;

    private final ItemManager itemManager;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r ->
    {
        Thread thread = new Thread(r, "ksp-high-alch-market-cache");
        thread.setDaemon(true);
        return thread;
    });
    private final HttpClient httpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(5))
        .version(HttpClient.Version.HTTP_1_1)
        .build();

    private final Map<Integer, WikiPrice> targetCache;
    private volatile Map<Integer, PricePoint> lastLiveSnapshot = Collections.emptyMap();
    private volatile long lastLiveAt;
    private volatile boolean closed;
    private volatile boolean outageLogged;
    private KspF2PHighAlchTraderConfig config;

    KspHighAlchMarketCache(ItemManager itemManager)
    {
        this.itemManager = itemManager;
        this.targetCache = resolveMicrobotPriceCache();
    }

    void start(KspF2PHighAlchTraderConfig config)
    {
        this.config = config;

        // Prime immediately from RuneLite's local price cache so the trader never has
        // to fall through to Microbot's per-item HTTP path during startup.
        publishFallback();

        executor.scheduleWithFixedDelay(this::refreshSafely, 0L, REFRESH_SECONDS, TimeUnit.SECONDS);
    }

    boolean isAvailable()
    {
        return targetCache != null;
    }

    private void refreshSafely()
    {
        if (closed || targetCache == null)
        {
            return;
        }

        try
        {
            Map<Integer, PricePoint> live = fetchBulkLatest();
            if (live.isEmpty())
            {
                throw new IllegalStateException("OSRS Wiki returned no price data");
            }

            lastLiveSnapshot = Collections.unmodifiableMap(live);
            lastLiveAt = System.currentTimeMillis();
            publish(live);

            if (outageLogged)
            {
                log.info("KSP High Alch Trader bulk OSRS Wiki prices recovered");
                outageLogged = false;
            }
        }
        catch (Exception ex)
        {
            if (!outageLogged)
            {
                log.warn("KSP High Alch Trader bulk OSRS Wiki refresh failed; using cached fallback prices: {}",
                    ex.getMessage());
                outageLogged = true;
            }

            long liveAge = lastLiveAt <= 0 ? Long.MAX_VALUE : System.currentTimeMillis() - lastLiveAt;
            if (!lastLiveSnapshot.isEmpty() && liveAge <= MAX_STALE_LIVE_MS)
            {
                // Re-publish the last good values so Microbot's one-minute cache TTL does
                // not expire and trigger its per-item network fallback.
                publish(lastLiveSnapshot);
            }
            else
            {
                publishFallback();
            }
        }
    }

    private Map<Integer, PricePoint> fetchBulkLatest() throws Exception
    {
        HttpRequest request = HttpRequest.newBuilder()
            .uri(URI.create(LATEST_URL))
            .header("User-Agent", "KSP-High-Alch-Trader/0.2.8 (github.com/KSPOG/ksppluginsrelease)")
            .timeout(Duration.ofSeconds(8))
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw new IllegalStateException("HTTP " + response.statusCode());
        }

        JsonElement root = new JsonParser().parse(response.body());
        if (!root.isJsonObject())
        {
            throw new IllegalStateException("Unexpected OSRS Wiki response");
        }

        JsonObject data = root.getAsJsonObject().getAsJsonObject("data");
        if (data == null)
        {
            throw new IllegalStateException("OSRS Wiki response has no data object");
        }

        Map<Integer, PricePoint> result = new HashMap<>();
        for (Map.Entry<String, JsonElement> entry : data.entrySet())
        {
            if (!entry.getValue().isJsonObject())
            {
                continue;
            }

            int itemId;
            try
            {
                itemId = Integer.parseInt(entry.getKey());
            }
            catch (NumberFormatException ignored)
            {
                continue;
            }

            JsonObject item = entry.getValue().getAsJsonObject();
            int high = jsonInt(item, "high");
            int low = jsonInt(item, "low");
            if (high <= 0 && low <= 0)
            {
                continue;
            }

            int buy = high > 0 ? high : low;
            int sell = low > 0 ? low : buy;
            result.put(itemId, new PricePoint(buy, sell));
        }
        return result;
    }

    private void publishFallback()
    {
        if (closed || targetCache == null || itemManager == null)
        {
            return;
        }

        Map<Integer, PricePoint> fallback = new HashMap<>();
        fallbackGuidePrice(fallback, NATURE_RUNE_ID);
        fallbackGuidePrice(fallback, FIRE_RUNE_ID);

        KspF2PHighAlchTraderConfig currentConfig = config;
        if (currentConfig != null)
        {
            Set<Integer> candidates = F2PAlchCatalog.buildCandidateSet(currentConfig, true);
            for (int itemId : candidates)
            {
                fallbackGuidePrice(fallback, itemId);
            }
        }

        publish(fallback);
    }

    private void fallbackGuidePrice(Map<Integer, PricePoint> target, int itemId)
    {
        int guide = itemManager.getItemPrice(itemId);
        if (guide <= 0)
        {
            return;
        }

        // A guide-price fallback is intentionally biased upward for buying so a Wiki
        // outage cannot make an item look more profitable than the local cache suggests.
        int conservativeBuy = Math.max(1, (int) Math.ceil(guide * FALLBACK_BUY_PREMIUM));
        target.put(itemId, new PricePoint(conservativeBuy, guide));
    }

    private void publish(Map<Integer, PricePoint> prices)
    {
        if (closed || targetCache == null || prices == null || prices.isEmpty())
        {
            return;
        }

        for (Map.Entry<Integer, PricePoint> entry : prices.entrySet())
        {
            PricePoint point = entry.getValue();
            if (point.buyPrice > 0)
            {
                targetCache.put(entry.getKey(), new WikiPrice(point.buyPrice, point.sellPrice, 0));
            }
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<Integer, WikiPrice> resolveMicrobotPriceCache()
    {
        try
        {
            Field field = Rs2GrandExchange.class.getDeclaredField("priceCache");
            field.setAccessible(true);
            Object value = field.get(null);
            if (value instanceof Map)
            {
                return (Map<Integer, WikiPrice>) value;
            }
        }
        catch (Exception ex)
        {
            log.error("KSP High Alch Trader could not access Microbot GE price cache", ex);
        }
        return null;
    }

    private static int jsonInt(JsonObject object, String key)
    {
        if (object == null || !object.has(key) || object.get(key).isJsonNull())
        {
            return 0;
        }
        try
        {
            return object.get(key).getAsInt();
        }
        catch (Exception ignored)
        {
            return 0;
        }
    }

    @Override
    public void close()
    {
        closed = true;
        executor.shutdownNow();
    }

    private static final class PricePoint
    {
        private final int buyPrice;
        private final int sellPrice;

        private PricePoint(int buyPrice, int sellPrice)
        {
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
        }
    }
}
