package net.runelite.client.plugins.microbot.f2pprocessingfactory;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.item.Rs2ItemManager;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public final class FactoryPriceService
{
    private static final double RETRY_STEP_PERCENT = 2.0;
    private static final String WIKI_LATEST_URL = "https://prices.runescape.wiki/api/v1/osrs/latest";
    private static final String WIKI_MAPPING_URL = "https://prices.runescape.wiki/api/v1/osrs/mapping";
    private static final long PRICE_REFRESH_MS = 60_000L, PRICE_FAILURE_RETRY_MS = 10_000L, PRICE_MAX_STALE_MS = 120_000L;
    private static final long MAPPING_REFRESH_MS = 21_600_000L, MAPPING_FAILURE_RETRY_MS = 60_000L;
    private static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3)).build();
    private static volatile Map<Integer, MarketPrice> marketSnapshot = Collections.emptyMap();
    private static volatile Map<Integer, Integer> tradeLimitSnapshot = Collections.emptyMap();
    private static volatile long nextMarketRefreshAt, lastMarketSuccessAt, nextMappingRefreshAt;

    private final Map<String, Integer> itemIdCache = new ConcurrentHashMap<>();

    public int getItemId(String itemName)
    {
        if (itemName == null || itemName.trim().isEmpty()) return -1;
        String trimmed = itemName.trim(), normalized = trimmed.toLowerCase(Locale.ROOT);
        if ("coins".equals(normalized) || "coin".equals(normalized)) return ItemID.COINS;
        Integer cached = itemIdCache.get(normalized);
        if (cached != null && cached > 0) return cached;
        int id = Rs2ItemManager.getItemIdByName(trimmed, false);
        if (id <= 0) try { id = Microbot.getRs2ItemManager().getItemId(trimmed); } catch (Exception ignored) { id = -1; }
        if (id > 0) itemIdCache.put(normalized, id);
        return id;
    }

    public ProfitQuote quote(FactoryRecipe recipe, F2PProcessingFactoryConfig config)
    {
        try
        {
            if (recipe.isMembersOnly() && !isMembersAccount()) return ProfitQuote.invalid(recipe, "Requires a members account");
            Map<String,Integer> inputPrices = new LinkedHashMap<>();
            long inputCost = 0;
            for (RecipeInput input : recipe.getInputs())
            {
                int id = getItemId(input.getItemName());
                if (id <= 0) return ProfitQuote.invalid(recipe, "Could not resolve " + input.getItemName());
                if (!input.isConsumed()) continue;
                int price = getBuyOfferPrice(id, config.buyMarkupPercent(), 0);
                if (price <= 0) return ProfitQuote.invalid(recipe, "No current instant-buy price for " + input.getItemName());
                inputPrices.put(input.getItemName(), price);
                inputCost += input.getEstimatedCostPerOutput(price);
            }

            int outputId = getItemId(recipe.getOutputItemName());
            if (outputId <= 0) return ProfitQuote.invalid(recipe, "Could not resolve " + recipe.getOutputItemName());
            int outputPrice = getSellOfferPrice(outputId, config.sellDiscountPercent(), 0);
            if (outputPrice <= 0) return ProfitQuote.invalid(recipe, "No current instant-sell price for " + recipe.getOutputItemName());
            long revenue = outputPrice, tax = calculateEstimatedTax(outputPrice, config.geTaxPercent());

            for (String name : recipe.getSecondaryOutputItemNames())
            {
                int id = getItemId(name);
                if (id <= 0) return ProfitQuote.invalid(recipe, "Could not resolve " + name);
                int price = getSellOfferPrice(id, config.sellDiscountPercent(), 0);
                if (price <= 0) return ProfitQuote.invalid(recipe, "No current instant-sell price for " + name);
                revenue += price;
                tax += calculateEstimatedTax(price, config.geTaxPercent());
            }

            int cost = clampInt(inputCost), estimatedTax = clampInt(tax);
            int profit = clampInt(revenue - tax - cost);
            double roi = cost <= 0 ? 0.0 : profit * 100.0 / cost;
            return ProfitQuote.valid(recipe, inputPrices, outputPrice, cost, estimatedTax, profit, roi);
        }
        catch (Exception ex)
        {
            log.warn("Failed to quote {}: {}", recipe, ex.getMessage());
            return ProfitQuote.invalid(recipe, ex.getMessage() == null ? "Price lookup failed" : ex.getMessage());
        }
    }

    private boolean isMembersAccount()
    {
        try { return Rs2Player.isMember(); }
        catch (Exception ex) { log.debug("Unable to resolve account membership: {}", ex.getMessage()); return false; }
    }

    public int getBuyOfferPrice(int itemId, int markupPercent, int retryAttempt)
    {
        int market = getInstantPrice(itemId, true);
        if (market <= 0) return -1;
        double adjustment = Math.max(0, markupPercent) + Math.max(0, retryAttempt) * RETRY_STEP_PERCENT;
        return Math.max(1, (int)Math.ceil(market * (1.0 + adjustment / 100.0)));
    }

    public int getSellOfferPrice(int itemId, int discountPercent, int retryAttempt)
    {
        int market = getInstantPrice(itemId, false);
        if (market <= 0) return -1;
        double adjustment = Math.max(0, discountPercent) + Math.max(0, retryAttempt) * RETRY_STEP_PERCENT;
        return Math.max(1, (int)Math.floor(market * Math.max(0.01, 1.0 - adjustment / 100.0)));
    }

    public int getTradeLimit(int itemId, int unknownLimitFallback)
    {
        long now = System.currentTimeMillis();
        if (now >= nextMappingRefreshAt) refreshTradeLimits(now);
        return Math.max(1, tradeLimitSnapshot.getOrDefault(itemId, unknownLimitFallback));
    }

    private int getInstantPrice(int itemId, boolean buy)
    {
        MarketPrice price = getMarketPrice(itemId);
        return price == null ? -1 : buy ? price.high : price.low;
    }

    private static MarketPrice getMarketPrice(int itemId)
    {
        long now = System.currentTimeMillis();
        if (now >= nextMarketRefreshAt) refreshMarketSnapshot(now);
        if (lastMarketSuccessAt <= 0 || now - lastMarketSuccessAt > PRICE_MAX_STALE_MS) return null;
        return marketSnapshot.get(itemId);
    }

    private static synchronized void refreshMarketSnapshot(long now)
    {
        if (now < nextMarketRefreshAt) return;
        nextMarketRefreshAt = now + PRICE_FAILURE_RETRY_MS;
        try
        {
            JsonObject data = requestJson(WIKI_LATEST_URL, 4).getAsJsonObject().getAsJsonObject("data");
            if (data == null || data.entrySet().isEmpty()) throw new IllegalStateException("empty Wiki price response");
            Map<Integer,MarketPrice> fresh = new HashMap<>();
            for (Map.Entry<String,JsonElement> entry : data.entrySet())
            {
                try
                {
                    JsonObject item = entry.getValue().getAsJsonObject();
                    int high = positive(item, "high"), low = positive(item, "low");
                    if (high > 0 && low > 0) fresh.put(Integer.parseInt(entry.getKey()), new MarketPrice(high, low));
                }
                catch (Exception ignored) { }
            }
            if (fresh.isEmpty()) throw new IllegalStateException("no two-sided Wiki prices");
            marketSnapshot = Collections.unmodifiableMap(fresh);
            lastMarketSuccessAt = System.currentTimeMillis();
            nextMarketRefreshAt = lastMarketSuccessAt + PRICE_REFRESH_MS;
            log.debug("Factory market snapshot refreshed: {} item(s)", fresh.size());
        }
        catch (Exception ex)
        {
            log.warn("Factory live-price refresh failed: {}; retrying in {}s", ex.getMessage(), PRICE_FAILURE_RETRY_MS / 1000L);
        }
    }

    private static synchronized void refreshTradeLimits(long now)
    {
        if (now < nextMappingRefreshAt) return;
        nextMappingRefreshAt = now + MAPPING_FAILURE_RETRY_MS;
        try
        {
            JsonArray mapping = requestJson(WIKI_MAPPING_URL, 5).getAsJsonArray();
            Map<Integer,Integer> fresh = new HashMap<>();
            for (JsonElement element : mapping)
            {
                try
                {
                    JsonObject item = element.getAsJsonObject();
                    int id = positive(item, "id"), limit = positive(item, "limit");
                    if (id > 0 && limit > 0) fresh.put(id, limit);
                }
                catch (Exception ignored) { }
            }
            if (fresh.isEmpty()) throw new IllegalStateException("empty Wiki mapping response");
            tradeLimitSnapshot = Collections.unmodifiableMap(fresh);
            nextMappingRefreshAt = System.currentTimeMillis() + MAPPING_REFRESH_MS;
            log.debug("Factory trade-limit snapshot refreshed: {} item(s)", fresh.size());
        }
        catch (Exception ex)
        {
            log.warn("Factory trade-limit refresh failed: {}; using configured unknown-limit fallback", ex.getMessage());
        }
    }

    private static JsonElement requestJson(String url, int timeoutSeconds) throws Exception
    {
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
            .timeout(Duration.ofSeconds(timeoutSeconds))
            .header("User-Agent", "KSP-AIO-Factory/1.0.47 (KSPOG/ksppluginsrelease)")
            .GET().build();
        HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() != 200) throw new IllegalStateException("HTTP " + response.statusCode());
        return new JsonParser().parse(response.body());
    }

    private static int positive(JsonObject object, String key)
    {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? -1 : value.getAsInt();
    }

    private static int clampInt(long value)
    {
        return value > Integer.MAX_VALUE ? Integer.MAX_VALUE : value < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int)value;
    }

    static int calculateEstimatedTax(int salePrice, int taxPercent)
    {
        return salePrice <= 0 || taxPercent <= 0 ? 0 : (int)Math.floor(salePrice * (taxPercent / 100.0));
    }

    private static final class MarketPrice
    {
        private final int high, low;
        private MarketPrice(int high, int low) { this.high = high; this.low = low; }
    }
}
