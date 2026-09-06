package net.runelite.client.plugins.microbot.kspjewelrycrafter;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.plugins.microbot.Microbot;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Conservative live-price service based on the OSRS Wiki real-time price API.
 * Inputs are valued at instant-buy/high prices; outputs at instant-sell/low prices.
 */
@Slf4j
public final class JewelryPriceService
{
    private static final String LATEST_URL = "https://prices.runescape.wiki/api/v1/osrs/latest";
    private static final long REFRESH_MS = 60_000L;
    private static final long MAX_STALE_MS = 120_000L;
    // Reject sides that have not actually traded recently. This prevents a stale
    // low-volume item (especially high-tier jewellery inputs) from looking
    // artificially profitable merely because the API still has an old quote.
    private static final long MAX_SIDE_TRADE_AGE_SECONDS = 15 * 60L;
    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(4))
        .build();

    private static volatile Map<Integer, MarketPrice> snapshot = Collections.emptyMap();
    private static volatile long lastRefreshAt;
    private static volatile long nextRefreshAt;

    private final Map<String, Integer> itemIds = new ConcurrentHashMap<>();

    public JewelryQuote quote(JewelryRecipe recipe, KspJewelryCrafterConfig config)
    {
        int barId = getItemId(recipe.getBarName());
        int outputId = getItemId(recipe.getOutputName());
        if (barId <= 0 || outputId <= 0)
            return JewelryQuote.invalid(recipe, "Could not resolve bar/output item id");

        int bar = adjustedBuy(getInstantBuy(barId), config.buyMarkupPercent(), 0);
        if (bar <= 0) return JewelryQuote.invalid(recipe, "No live buy price for " + recipe.getBarName());

        int gem = 0;
        if (recipe.usesGem())
        {
            int gemId = getItemId(recipe.getGemName());
            if (gemId <= 0) return JewelryQuote.invalid(recipe, "Could not resolve " + recipe.getGemName());
            gem = adjustedBuy(getInstantBuy(gemId), config.buyMarkupPercent(), 0);
            if (gem <= 0) return JewelryQuote.invalid(recipe, "No live buy price for " + recipe.getGemName());
        }

        int sell = adjustedSell(getInstantSell(outputId), config.sellDiscountPercent(), 0);
        if (sell <= 0) return JewelryQuote.invalid(recipe, "No live sell price for " + recipe.getOutputName());

        int inputCost = bar + gem;
        int tax = estimatedGeTax(sell);
        int profit = sell - tax - inputCost;
        double roi = inputCost <= 0 ? 0.0 : profit * 100.0 / inputCost;
        return JewelryQuote.valid(recipe, bar, gem, sell, inputCost, tax, profit, roi);
    }

    public int getItemId(String itemName)
    {
        if (itemName == null || itemName.isBlank()) return -1;
        String normalized = itemName.trim();
        String key = normalized.toLowerCase(Locale.ROOT);
        Integer cached = itemIds.get(key);
        if (cached != null) return cached;

        // Resolve one canonical item ID by exact name, cache it, then let the
        // crafting runtime operate exclusively on IDs. Never use fuzzy results.
        int id = Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            try
            {
                return Microbot.getItemManager().search(normalized).stream()
                    .filter(item -> item != null && item.getName() != null
                        && item.getName().equalsIgnoreCase(normalized))
                    .mapToInt(item -> item.getId())
                    .findFirst()
                    .orElse(-1);
            }
            catch (Exception ignored)
            {
                return -1;
            }
        }).orElse(-1);

        if (id > 0) itemIds.put(key, id);
        return id;
    }

    public int buyOfferPrice(String itemName, int baseMarkupPercent, int retry)
    {
        int id = getItemId(itemName);
        return id <= 0 ? -1 : adjustedBuy(getInstantBuy(id), baseMarkupPercent, retry);
    }

    public int sellOfferPrice(String itemName, int baseDiscountPercent, int retry)
    {
        int id = getItemId(itemName);
        return id <= 0 ? -1 : adjustedSell(getInstantSell(id), baseDiscountPercent, retry);
    }

    private int getInstantBuy(int itemId)
    {
        MarketPrice p = getMarketPrice(itemId);
        return p == null || !p.highIsFresh() ? -1 : p.high;
    }

    private int getInstantSell(int itemId)
    {
        MarketPrice p = getMarketPrice(itemId);
        return p == null || !p.lowIsFresh() ? -1 : p.low;
    }

    private static int adjustedBuy(int market, int basePercent, int retry)
    {
        if (market <= 0) return -1;
        double pct = Math.max(0, basePercent) + Math.max(0, retry) * 2.0;
        return Math.max(1, (int) Math.ceil(market * (1.0 + pct / 100.0)));
    }

    private static int adjustedSell(int market, int basePercent, int retry)
    {
        if (market <= 0) return -1;
        double pct = Math.max(0, basePercent) + Math.max(0, retry) * 2.0;
        return Math.max(1, (int) Math.floor(market * Math.max(0.01, 1.0 - pct / 100.0)));
    }

    private static int estimatedGeTax(int salePrice)
    {
        // Current OSRS GE tax: 2%, rounded down, capped at 5,000,000 gp per item.
        if (salePrice <= 0) return 0;
        return Math.min(5_000_000, (int) Math.floor(salePrice * 0.02));
    }

    private static MarketPrice getMarketPrice(int itemId)
    {
        long now = System.currentTimeMillis();
        if (now >= nextRefreshAt) refresh(now);
        if (lastRefreshAt <= 0 || now - lastRefreshAt > MAX_STALE_MS) return null;
        return snapshot.get(itemId);
    }

    private static synchronized void refresh(long now)
    {
        if (now < nextRefreshAt) return;
        nextRefreshAt = now + 10_000L;
        try
        {
            HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_URL))
                .timeout(Duration.ofSeconds(6))
                .header("User-Agent", "KSP-Jewelry-Crafter/0.1.1")
                .GET().build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());

            JsonObject root = new JsonParser().parse(response.body()).getAsJsonObject();
            JsonObject data = root.getAsJsonObject("data");
            if (data == null || data.entrySet().isEmpty()) throw new IllegalStateException("empty price response");

            Map<Integer, MarketPrice> fresh = new HashMap<>();
            for (Map.Entry<String, JsonElement> e : data.entrySet())
            {
                try
                {
                    JsonObject o = e.getValue().getAsJsonObject();
                    JsonElement highValue = o.get("high");
                    JsonElement lowValue = o.get("low");
                    if (highValue == null || lowValue == null || highValue.isJsonNull() || lowValue.isJsonNull()) continue;
                    JsonElement highTimeValue = o.get("highTime");
                    JsonElement lowTimeValue = o.get("lowTime");
                    if (highTimeValue == null || lowTimeValue == null
                        || highTimeValue.isJsonNull() || lowTimeValue.isJsonNull()) continue;
                    int high = highValue.getAsInt();
                    int low = lowValue.getAsInt();
                    long highTime = highTimeValue.getAsLong();
                    long lowTime = lowTimeValue.getAsLong();
                    if (high > 0 && low > 0 && highTime > 0 && lowTime > 0)
                        fresh.put(Integer.parseInt(e.getKey()), new MarketPrice(high, low, highTime, lowTime));
                }
                catch (Exception ignored) { }
            }
            if (fresh.isEmpty()) throw new IllegalStateException("no two-sided market prices");
            snapshot = Collections.unmodifiableMap(fresh);
            lastRefreshAt = System.currentTimeMillis();
            nextRefreshAt = lastRefreshAt + REFRESH_MS;
        }
        catch (Exception ex)
        {
            log.warn("KSP Jewelry Crafter price refresh failed: {}", ex.getMessage());
        }
    }

    private static final class MarketPrice
    {
        final int high;
        final int low;
        final long highTime;
        final long lowTime;

        MarketPrice(int high, int low, long highTime, long lowTime)
        {
            this.high = high;
            this.low = low;
            this.highTime = highTime;
            this.lowTime = lowTime;
        }

        boolean highIsFresh() { return sideFresh(highTime); }
        boolean lowIsFresh() { return sideFresh(lowTime); }

        private static boolean sideFresh(long epochSeconds)
        {
            long now = System.currentTimeMillis() / 1000L;
            return epochSeconds > 0 && now - epochSeconds <= MAX_SIDE_TRADE_AGE_SECONDS;
        }
    }
}
