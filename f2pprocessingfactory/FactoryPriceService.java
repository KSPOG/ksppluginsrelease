package net.runelite.client.plugins.microbot.f2pprocessingfactory;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.ItemMappingData;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;
import net.runelite.client.plugins.microbot.util.item.Rs2ItemManager;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public final class FactoryPriceService
{
    private final Map<String, Integer> itemIdCache = new ConcurrentHashMap<>();

    public int getItemId(String itemName)
    {
        if (itemName == null || itemName.trim().isEmpty())
        {
            return -1;
        }

        String trimmed = itemName.trim();
        String normalizedName = trimmed.toLowerCase(Locale.ROOT);
        if ("coins".equals(normalizedName) || "coin".equals(normalizedName))
        {
            return ItemID.COINS;
        }

        Integer cached = itemIdCache.get(normalizedName);
        if (cached != null && cached > 0)
        {
            return cached;
        }

        int itemId = Rs2ItemManager.getItemIdByName(trimmed, false);
        if (itemId <= 0)
        {
            try
            {
                itemId = Microbot.getRs2ItemManager().getItemId(trimmed);
            }
            catch (Exception ignored)
            {
                itemId = -1;
            }
        }

        if (itemId > 0)
        {
            itemIdCache.put(normalizedName, itemId);
        }
        return itemId;
    }

    public ProfitQuote quote(FactoryRecipe recipe, F2PProcessingFactoryConfig config)
    {
        try
        {
            boolean membersAccount = isMembersAccount();
            if (recipe.isMembersOnly() && !membersAccount)
            {
                return ProfitQuote.invalid(recipe, "Requires a members account");
            }

            Map<String, Integer> inputPrices = new LinkedHashMap<>();
            long totalInputCost = 0;

            for (RecipeInput input : recipe.getInputs())
            {
                int itemId = getItemId(input.getItemName());
                if (itemId <= 0)
                {
                    return ProfitQuote.invalid(recipe, "Could not resolve " + input.getItemName());
                }
                if (!membersAccount && isMembersOnly(itemId))
                {
                    return ProfitQuote.invalid(recipe, input.getItemName() + " is members-only");
                }
                if (!input.isConsumed())
                {
                    continue;
                }

                int buyPrice = getBuyOfferPrice(itemId, config.buyMarkupPercent(), 0);
                if (buyPrice <= 0)
                {
                    return ProfitQuote.invalid(recipe, "No buy price for " + input.getItemName());
                }
                inputPrices.put(input.getItemName(), buyPrice);
                totalInputCost += input.getEstimatedCostPerOutput(buyPrice);
            }

            int outputId = getItemId(recipe.getOutputItemName());
            if (outputId <= 0)
            {
                return ProfitQuote.invalid(recipe, "Could not resolve " + recipe.getOutputItemName());
            }
            if (!membersAccount && isMembersOnly(outputId))
            {
                return ProfitQuote.invalid(recipe, recipe.getOutputItemName() + " is members-only");
            }

            int outputPrice = getSellOfferPrice(outputId, config.sellDiscountPercent(), 0);
            if (outputPrice <= 0)
            {
                return ProfitQuote.invalid(recipe, "No sell price for " + recipe.getOutputItemName());
            }

            long totalOutputRevenue = outputPrice;
            long totalTax = calculateEstimatedTax(outputPrice, config.geTaxPercent());

            for (String secondaryOutputName : recipe.getSecondaryOutputItemNames())
            {
                int secondaryOutputId = getItemId(secondaryOutputName);
                if (secondaryOutputId <= 0)
                {
                    return ProfitQuote.invalid(recipe, "Could not resolve " + secondaryOutputName);
                }
                if (!membersAccount && isMembersOnly(secondaryOutputId))
                {
                    return ProfitQuote.invalid(recipe, secondaryOutputName + " is members-only");
                }

                int secondaryPrice = getSellOfferPrice(secondaryOutputId, config.sellDiscountPercent(), 0);
                if (secondaryPrice <= 0)
                {
                    return ProfitQuote.invalid(recipe, "No sell price for " + secondaryOutputName);
                }
                totalOutputRevenue += secondaryPrice;
                totalTax += calculateEstimatedTax(secondaryPrice, config.geTaxPercent());
            }

            int inputCost = totalInputCost > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalInputCost;
            int tax = totalTax > Integer.MAX_VALUE ? Integer.MAX_VALUE : (int) totalTax;
            long profitLong = totalOutputRevenue - totalTax - inputCost;
            int profit = profitLong > Integer.MAX_VALUE
                ? Integer.MAX_VALUE
                : profitLong < Integer.MIN_VALUE ? Integer.MIN_VALUE : (int) profitLong;
            double roi = inputCost <= 0 ? 0.0 : (profit * 100.0) / inputCost;

            return ProfitQuote.valid(recipe, inputPrices, outputPrice, inputCost, tax, profit, roi);
        }
        catch (Exception ex)
        {
            log.warn("Failed to quote {}: {}", recipe, ex.getMessage());
            return ProfitQuote.invalid(recipe, ex.getMessage() == null ? "Price lookup failed" : ex.getMessage());
        }
    }

    private boolean isMembersAccount()
    {
        try
        {
            return Rs2Player.isMember();
        }
        catch (Exception ex)
        {
            log.debug("Unable to resolve account membership: {}", ex.getMessage());
            return false;
        }
    }

    private boolean isMembersOnly(int itemId)
    {
        ItemMappingData mapping = Rs2GrandExchange.getItemMappingData(itemId);
        return mapping != null && mapping.members;
    }

    public int getBuyOfferPrice(int itemId, int markupPercent, int retryAttempt)
    {
        double basePercentage = 1.0 + (Math.max(0, markupPercent) / 100.0);
        try
        {
            int adaptivePrice = Rs2GrandExchange.getAdaptiveBuyPrice(itemId, basePercentage, retryAttempt);
            if (adaptivePrice > 0)
            {
                return adaptivePrice;
            }
        }
        catch (Exception ignored)
        {
        }

        int marketPrice = getInstantPrice(itemId, true);
        if (marketPrice <= 0)
        {
            return marketPrice;
        }
        double retryMultiplier = 1.0 + ((Math.max(0, markupPercent) + (retryAttempt * 2.0)) / 100.0);
        return Math.max(1, (int) Math.ceil(marketPrice * retryMultiplier));
    }

    public int getSellOfferPrice(int itemId, int discountPercent, int retryAttempt)
    {
        double basePercentage = Math.max(0.01, 1.0 - (Math.max(0, discountPercent) / 100.0));
        try
        {
            int adaptivePrice = Rs2GrandExchange.getAdaptiveSellPrice(itemId, basePercentage, retryAttempt);
            if (adaptivePrice > 0)
            {
                return adaptivePrice;
            }
        }
        catch (Exception ignored)
        {
        }

        int marketPrice = getInstantPrice(itemId, false);
        if (marketPrice <= 0)
        {
            return marketPrice;
        }
        double retryMultiplier = Math.max(
            0.01,
            1.0 - ((Math.max(0, discountPercent) + (retryAttempt * 2.0)) / 100.0)
        );
        return Math.max(1, (int) Math.floor(marketPrice * retryMultiplier));
    }

    public int getTradeLimit(int itemId, int unknownLimitFallback)
    {
        try
        {
            ItemMappingData mapping = Rs2GrandExchange.getItemMappingData(itemId);
            if (mapping != null && mapping.tradeLimitPer4Hours > 0)
            {
                return mapping.tradeLimitPer4Hours;
            }
        }
        catch (Exception ex)
        {
            log.debug("Unable to load trade limit for item {}: {}", itemId, ex.getMessage());
        }
        return Math.max(1, unknownLimitFallback);
    }

    private int getInstantPrice(int itemId, boolean buy)
    {
        try
        {
            WikiPrice price = Rs2GrandExchange.getRealTimePrices(itemId);
            int livePrice = price == null ? 0 : buy ? price.buyPrice : price.sellPrice;
            if (livePrice > 0)
            {
                return livePrice;
            }
        }
        catch (Exception ignored)
        {
        }
        return Microbot.getRs2ItemManager().getGEPrice(itemId);
    }

    static int calculateEstimatedTax(int salePrice, int taxPercent)
    {
        if (salePrice <= 0 || taxPercent <= 0)
        {
            return 0;
        }
        return (int) Math.floor(salePrice * (taxPercent / 100.0));
    }
}
