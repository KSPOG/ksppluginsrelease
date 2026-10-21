package net.runelite.client.plugins.microbot.kspbonestobananas;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.HashMap;
import java.util.Map;
import net.runelite.api.gameval.ItemID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

final class BonesToBananasPriceService
{
    private static final Logger log = LoggerFactory.getLogger(BonesToBananasPriceService.class);
    private static final String WIKI_LATEST = "https://prices.runescape.wiki/api/v1/osrs/latest";
    private static final long REFRESH_MS = 60_000L;
    private static final long RETRY_AFTER_FAILURE_MS = 30_000L;
    private static final HttpClient HTTP = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).build();
    private final Map<Integer, MarketPrice> cache = new HashMap<>();
    private long nextRefreshAt;
    private boolean lastRefreshFailed;

    private static final class MarketPrice
    {
        final int buyPrice, sellPrice;
        MarketPrice(int buyPrice, int sellPrice)
        {
            this.buyPrice = buyPrice;
            this.sellPrice = sellPrice;
        }
    }

    private synchronized MarketPrice price(int itemId)
    {
        refreshIfNeeded();
        return cache.get(itemId);
    }

    private void refreshIfNeeded()
    {
        long now = System.currentTimeMillis();
        if (now < nextRefreshAt) return;
        nextRefreshAt = now + RETRY_AFTER_FAILURE_MS;
        try
        {
            HttpRequest request = HttpRequest.newBuilder(URI.create(WIKI_LATEST))
                    .timeout(Duration.ofSeconds(8))
                    .header("Accept", "application/json")
                    .header("User-Agent", "KSPBonesToBananas/0.0.10 (KSPOG Microbot plugin)")
                    .GET().build();
            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            String body = response.body();
            if (response.statusCode() != 200 || body == null || !body.trim().startsWith("{"))
            {
                throw new IllegalStateException("HTTP " + response.statusCode()
                        + ", response is not valid JSON object");
            }
            JsonElement root = new JsonParser().parse(body);
            if (!root.isJsonObject() || !root.getAsJsonObject().has("data")
                    || !root.getAsJsonObject().get("data").isJsonObject())
                throw new IllegalStateException("Missing price data object");

            JsonObject data = root.getAsJsonObject().getAsJsonObject("data");
            Map<Integer, MarketPrice> fresh = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : data.entrySet())
            {
                try
                {
                    JsonObject item = entry.getValue().getAsJsonObject();
                    JsonElement high = item.get("high");
                    JsonElement low = item.get("low");
                    if (high == null || low == null || high.isJsonNull() || low.isJsonNull()) continue;
                    int buy = high.getAsInt();
                    int sell = low.getAsInt();
                    if (buy > 0 && sell > 0)
                        fresh.put(Integer.parseInt(entry.getKey()), new MarketPrice(buy, sell));
                }
                catch (RuntimeException ignored)
                {
                    // Skip incomplete or malformed individual item entries.
                }
            }
            if (fresh.isEmpty()) throw new IllegalStateException("Empty live price response");
            cache.clear();
            cache.putAll(fresh);
            nextRefreshAt = now + REFRESH_MS;
            if (lastRefreshFailed) log.info("Bones to Bananas live price feed recovered");
            lastRefreshFailed = false;
        }
        catch (Exception ex)
        {
            // Fail closed: never buy or reprice against stale prices.
            cache.clear();
            if (!lastRefreshFailed)
                log.warn("Bones to Bananas live prices unavailable: {}. Waiting 30 seconds before retry", ex.toString());
            lastRefreshFailed = true;
            if (ex instanceof InterruptedException) Thread.currentThread().interrupt();
        }
    }

    BonesToBananasQuote quote(BananaBoneType bone, KspBonesToBananasConfig config,
                              boolean freeWater, boolean freeEarth, int batchSize)
    {
        MarketPrice bonePrice = price(bone.getItemId());
        MarketPrice banana = price(ItemID.BANANA);
        MarketPrice nature = price(ItemID.NATURERUNE);
        MarketPrice water = freeWater ? null : price(ItemID.WATERRUNE);
        MarketPrice earth = freeEarth ? null : price(ItemID.EARTHRUNE);

        if (!usable(bonePrice)) return BonesToBananasQuote.invalid(bone, "No live GE price for " + bone.getItemName(), batchSize, freeWater, freeEarth);
        if (!usable(banana)) return BonesToBananasQuote.invalid(bone, "No live GE price for Banana", batchSize, freeWater, freeEarth);
        if (!usable(nature)) return BonesToBananasQuote.invalid(bone, "No live GE price for Nature rune", batchSize, freeWater, freeEarth);
        if (!freeWater && !usable(water)) return BonesToBananasQuote.invalid(bone, "No live GE price for Water rune", batchSize, false, freeEarth);
        if (!freeEarth && !usable(earth)) return BonesToBananasQuote.invalid(bone, "No live GE price for Earth rune", batchSize, freeWater, false);

        int boneBuy = adjustedBuy(bonePrice.buyPrice, config.buyMarkupPercent(), 0);
        int bananaSell = adjustedSell(banana.sellPrice, config.sellDiscountPercent(), 0);
        int natureBuy = adjustedBuy(nature.buyPrice, config.buyMarkupPercent(), 0);
        int waterBuy = freeWater ? 0 : adjustedBuy(water.buyPrice, config.buyMarkupPercent(), 0);
        int earthBuy = freeEarth ? 0 : adjustedBuy(earth.buyPrice, config.buyMarkupPercent(), 0);
        if (boneBuy <= 0 || bananaSell <= 0 || natureBuy <= 0 || (!freeWater && waterBuy <= 0) || (!freeEarth && earthBuy <= 0))
            return BonesToBananasQuote.invalid(bone, "Incomplete live market quote", batchSize, freeWater, freeEarth);

        int tax = geTax(bananaSell);
        long runeCostLong = natureBuy + (freeWater ? 0L : 2L * waterBuy) + (freeEarth ? 0L : 2L * earthBuy);
        int runeCost = clamp(runeCostLong);
        int inputCost = clamp((long) boneBuy * batchSize + runeCost);
        int profit = clamp((long) (bananaSell - tax) * batchSize - (long) boneBuy * batchSize - runeCost);
        double roi = inputCost <= 0 ? 0D : profit * 100D / inputCost;
        double cycleSeconds = Math.max(1, config.bankOverheadSeconds()) + 0.6D;
        long gpHour = Math.round(profit * 3600D / cycleSeconds);

        return BonesToBananasQuote.valid(bone, batchSize, boneBuy, bananaSell, tax, natureBuy,
                waterBuy, earthBuy, runeCost, inputCost, profit, freeWater, freeEarth, roi, gpHour);
    }

    int buyOfferPrice(int itemId, int markupPercent, int retry)
    {
        MarketPrice p = price(itemId);
        return usable(p) ? adjustedBuy(p.buyPrice, markupPercent, retry) : -1;
    }

    int sellOfferPrice(int itemId, int discountPercent, int retry)
    {
        MarketPrice p = price(itemId);
        return usable(p) ? adjustedSell(p.sellPrice, discountPercent, retry) : -1;
    }

    private static boolean usable(MarketPrice p) { return p != null && p.buyPrice > 0 && p.sellPrice > 0; }
    private static int adjustedBuy(int market, int basePercent, int retry)
    {
        if (market <= 0) return -1;
        double pct = Math.max(0, basePercent) + Math.max(0, retry) * 2D;
        return Math.max(1, (int) Math.ceil(market * (1D + pct / 100D)));
    }
    private static int adjustedSell(int market, int basePercent, int retry)
    {
        if (market <= 0) return -1;
        double pct = Math.max(0, basePercent) + Math.max(0, retry) * 2D;
        return Math.max(1, (int) Math.floor(market * Math.max(.01D, 1D - pct / 100D)));
    }
    private static int geTax(int sellPrice)
    {
        if (sellPrice <= 0) return 0;
        return (int) Math.min(5_000_000L, (long) Math.floor(sellPrice * .02D));
    }
    private static int clamp(long value)
    {
        if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (value < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) value;
    }
}
