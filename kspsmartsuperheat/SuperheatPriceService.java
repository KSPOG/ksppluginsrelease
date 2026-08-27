package net.runelite.client.plugins.microbot.kspsmartsuperheat;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ItemID;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public final class SuperheatPriceService
{
    private static final String LATEST_URL = "https://prices.runescape.wiki/api/v1/osrs/latest";
    private static final long REFRESH_MS = 60_000L;
    private static final long FAILURE_RETRY_MS = 10_000L;
    private static final long MAX_STALE_MS = 120_000L;

    private static final HttpClient HTTP = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(4))
        .build();

    private static volatile Map<Integer, MarketPrice> snapshot = Collections.emptyMap();
    private static volatile long lastSuccessAt;
    private static volatile long nextRefreshAt;

    public SuperheatQuote quote(
        SuperheatRecipe recipe,
        KspSmartSuperheatConfig config,
        boolean freeFireRunes)
    {
        MarketPrice primary = get(recipe.getPrimaryOreId());
        MarketPrice output = get(recipe.getOutputId());
        MarketPrice nature = get(ItemID.NATURERUNE);

        if (primary == null) return SuperheatQuote.invalid(recipe, "No live price for " + recipe.getPrimaryOreName(), freeFireRunes);
        if (output == null) return SuperheatQuote.invalid(recipe, "No live price for " + recipe.getOutputName(), freeFireRunes);
        if (nature == null) return SuperheatQuote.invalid(recipe, "No live price for Nature rune", freeFireRunes);

        MarketPrice secondary = recipe.hasSecondaryOre() ? get(recipe.getSecondaryOreId()) : null;
        MarketPrice coal = recipe.getCoalPerBar() > 0 ? get(ItemID.COAL) : null;
        MarketPrice fire = freeFireRunes ? null : get(ItemID.FIRERUNE);

        if (recipe.hasSecondaryOre() && secondary == null)
            return SuperheatQuote.invalid(recipe, "No live price for " + recipe.getSecondaryOreName(), freeFireRunes);
        if (recipe.getCoalPerBar() > 0 && coal == null)
            return SuperheatQuote.invalid(recipe, "No live price for Coal", freeFireRunes);
        if (!freeFireRunes && fire == null)
            return SuperheatQuote.invalid(recipe, "No live price for Fire rune", false);

        int primaryBuy = buyPrice(primary.high, config.buyMarkupPercent());
        int secondaryBuy = secondary == null ? 0 : buyPrice(secondary.high, config.buyMarkupPercent());
        int coalBuy = coal == null ? 0 : buyPrice(coal.high, config.buyMarkupPercent());
        int natureBuy = buyPrice(nature.high, config.buyMarkupPercent());
        int fireBuy = fire == null ? 0 : buyPrice(fire.high, config.buyMarkupPercent());
        int outputSell = sellPrice(output.low, config.sellDiscountPercent());

        long inputCostLong = (long) primaryBuy * recipe.getPrimaryOrePerBar();
        inputCostLong += (long) secondaryBuy * recipe.getSecondaryOrePerBar();
        inputCostLong += (long) coalBuy * recipe.getCoalPerBar();
        inputCostLong += natureBuy;
        if (!freeFireRunes)
        {
            inputCostLong += (long) fireBuy * 4L;
        }

        int inputCost = clampInt(inputCostLong);
        int tax = calculateGeTax(outputSell);
        int profit = clampInt((long) outputSell - tax - inputCost);
        double roi = inputCost <= 0 ? 0.0 : profit * 100.0 / inputCost;

        int batchSize = calculateBatchSize(recipe, freeFireRunes);
        double cycleSeconds = batchSize * 1.8 + Math.max(1, config.bankOverheadSeconds());
        long projectedGpHour = batchSize <= 0
            ? 0L
            : Math.round((profit * (double) batchSize) * 3600.0 / cycleSeconds);

        return SuperheatQuote.valid(
            recipe,
            freeFireRunes,
            primaryBuy,
            secondaryBuy,
            coalBuy,
            natureBuy,
            fireBuy,
            outputSell,
            inputCost,
            tax,
            profit,
            roi,
            batchSize,
            projectedGpHour
        );
    }

    public long getLastSuccessAt()
    {
        return lastSuccessAt;
    }

    private static int calculateBatchSize(SuperheatRecipe recipe, boolean freeFireRunes)
    {
        int stackSlots = freeFireRunes ? 1 : 2; // Nature + optional Fire rune stack
        int freeMaterialSlots = Math.max(0, 28 - stackSlots);
        return Math.max(1, freeMaterialSlots / recipe.getMaterialSlotsPerBar());
    }

    private static int calculateGeTax(int sellPrice)
    {
        if (sellPrice <= 0) return 0;
        long tax = (long) Math.floor(sellPrice * 0.02d);
        return (int) Math.min(5_000_000L, tax);
    }

    private static int buyPrice(int marketHigh, int markupPercent)
    {
        return Math.max(1, (int) Math.ceil(marketHigh * (1.0 + Math.max(0, markupPercent) / 100.0)));
    }

    private static int sellPrice(int marketLow, int discountPercent)
    {
        return Math.max(1, (int) Math.floor(marketLow * Math.max(0.01, 1.0 - Math.max(0, discountPercent) / 100.0)));
    }

    private static MarketPrice get(int itemId)
    {
        long now = System.currentTimeMillis();
        if (now >= nextRefreshAt)
        {
            refresh(now);
        }

        if (lastSuccessAt <= 0 || now - lastSuccessAt > MAX_STALE_MS)
        {
            return null;
        }

        return snapshot.get(itemId);
    }

    private static synchronized void refresh(long now)
    {
        if (now < nextRefreshAt) return;
        nextRefreshAt = now + FAILURE_RETRY_MS;

        try
        {
            HttpRequest request = HttpRequest.newBuilder(URI.create(LATEST_URL))
                .timeout(Duration.ofSeconds(5))
                .header("User-Agent", "KSP-Smart-Superheat/1.0.0 (KSPOG)")
                .GET()
                .build();

            HttpResponse<String> response = HTTP.send(request, HttpResponse.BodyHandlers.ofString());
            if (response.statusCode() != 200)
            {
                throw new IllegalStateException("HTTP " + response.statusCode());
            }

            JsonObject root = new JsonParser().parse(response.body()).getAsJsonObject();
            JsonObject data = root.getAsJsonObject("data");
            if (data == null || data.entrySet().isEmpty())
            {
                throw new IllegalStateException("empty price response");
            }

            Map<Integer, MarketPrice> fresh = new HashMap<>();
            for (Map.Entry<String, JsonElement> entry : data.entrySet())
            {
                try
                {
                    JsonObject item = entry.getValue().getAsJsonObject();
                    int high = positive(item, "high");
                    int low = positive(item, "low");
                    if (high > 0 && low > 0)
                    {
                        fresh.put(Integer.parseInt(entry.getKey()), new MarketPrice(high, low));
                    }
                }
                catch (RuntimeException ignored)
                {
                }
            }

            if (fresh.isEmpty())
            {
                throw new IllegalStateException("no two-sided prices");
            }

            snapshot = Collections.unmodifiableMap(fresh);
            lastSuccessAt = System.currentTimeMillis();
            nextRefreshAt = lastSuccessAt + REFRESH_MS;
        }
        catch (Exception ex)
        {
            log.warn("KSP Smart Superheat price refresh failed: {}", ex.getMessage());
        }
    }

    private static int positive(JsonObject object, String key)
    {
        JsonElement value = object.get(key);
        return value == null || value.isJsonNull() ? -1 : value.getAsInt();
    }

    private static int clampInt(long value)
    {
        if (value > Integer.MAX_VALUE) return Integer.MAX_VALUE;
        if (value < Integer.MIN_VALUE) return Integer.MIN_VALUE;
        return (int) value;
    }

    private static final class MarketPrice
    {
        private final int high;
        private final int low;

        private MarketPrice(int high, int low)
        {
            this.high = high;
            this.low = low;
        }
    }
}
