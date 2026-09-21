package net.runelite.client.plugins.microbot.kspbonestobananas;

import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;

final class BonesToBananasPriceService
{
    BonesToBananasQuote quote(BananaBoneType bone, KspBonesToBananasConfig config,
                              boolean freeWater, boolean freeEarth, int batchSize)
    {
        WikiPrice bonePrice = Rs2GrandExchange.getRealTimePrices(bone.getItemId());
        WikiPrice banana = Rs2GrandExchange.getRealTimePrices(ItemID.BANANA);
        WikiPrice nature = Rs2GrandExchange.getRealTimePrices(ItemID.NATURERUNE);
        WikiPrice water = freeWater ? null : Rs2GrandExchange.getRealTimePrices(ItemID.WATERRUNE);
        WikiPrice earth = freeEarth ? null : Rs2GrandExchange.getRealTimePrices(ItemID.EARTHRUNE);

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
        WikiPrice p = Rs2GrandExchange.getRealTimePrices(itemId);
        return usable(p) ? adjustedBuy(p.buyPrice, markupPercent, retry) : -1;
    }

    int sellOfferPrice(int itemId, int discountPercent, int retry)
    {
        WikiPrice p = Rs2GrandExchange.getRealTimePrices(itemId);
        return usable(p) ? adjustedSell(p.sellPrice, discountPercent, retry) : -1;
    }

    private static boolean usable(WikiPrice p) { return p != null && p.buyPrice > 0 && p.sellPrice > 0; }
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
