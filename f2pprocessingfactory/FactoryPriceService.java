package net.runelite.client.plugins.microbot.f2pprocessingfactory;

import lombok.extern.slf4j.Slf4j;
import net.runelite.api.WorldType;
import net.runelite.api.gameval.ItemID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;
import net.runelite.client.plugins.microbot.util.grandexchange.models.ItemMappingData;
import net.runelite.client.plugins.microbot.util.grandexchange.models.WikiPrice;
import net.runelite.client.plugins.microbot.util.item.Rs2ItemManager;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.LinkedHashMap;
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

        String normalizedName = itemName.trim().toLowerCase();
        if ("coins".equals(normalizedName) || "coin".equals(normalizedName))
        {
            return ItemID.COINS;
        }

        Integer cached = itemIdCache.get(normalizedName);
        if (cached != null && cached > 0)
        {
            return cached;
        }

        // Prefer the exact-match resolver. Rs2ItemManager#getItemId(String) simply
        // returns the first search result and can resolve similarly named items.
        int itemId = Rs2ItemManager.getItemIdByName(itemName.trim(), false);
        if (itemId <= 0)
        {
            // Factory recipes use canonical item names. Never accept the first fuzzy
            // RuneLite search result here: caching a near/incorrect match can make
            // banking and GE actions target an unrelated item for the rest of the run.
            log.warn("Factory exact item resolution failed for '{}'", itemName.trim());
            return -1;
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

                ItemMappingData mapping = Rs2GrandExchange.getItemMappingData(itemId);
                if (mapping != null && mapping.members && !membersAccount)
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

            ItemMappingData outputMapping = Rs2GrandExchange.getItemMappingData(outputId);
            if (outputMapping != null && outputMapping.members && !membersAccount)
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

            // Empty pots/jugs/buckets from dough and soft-clay processing are
            // tradeable outputs too. Include their sale value in the profitability
            // quote so Automatic mode does not undervalue these recipes.
            for (String secondaryOutputName : recipe.getSecondaryOutputItemNames())
            {
                int secondaryOutputId = getItemId(secondaryOutputName);
                if (secondaryOutputId <= 0)
                {
                    return ProfitQuote.invalid(recipe, "Could not resolve " + secondaryOutputName);
                }

                ItemMappingData secondaryMapping = Rs2GrandExchange.getItemMappingData(secondaryOutputId);
                if (secondaryMapping != null && secondaryMapping.members && !membersAccount)
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
            Boolean directMemberWorld = Microbot.getClientThread().runOnClientThreadOptional(() ->
            {
                java.util.Set<WorldType> worldTypes = Microbot.getClient().getWorldType();
                return worldTypes != null && worldTypes.contains(WorldType.MEMBERS);
            }).orElse(null);
            if (Boolean.TRUE.equals(directMemberWorld))
            {
                return true;
            }
        }
        catch (Exception ex)
        {
            log.debug("Unable to read client world type for pricing eligibility: {}", ex.getMessage());
        }

        try
        {
            if (Rs2Player.isInMemberWorld())
            {
                return true;
            }
        }
        catch (Exception ex)
        {
            log.debug("Unable to use member-world service signal for pricing eligibility: {}", ex.getMessage());
        }

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
            // Fall through to local calculation.
        }

        int marketPrice = getInstantBuyPrice(itemId);
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
            // Fall through to local calculation.
        }

        int marketPrice = getInstantSellPrice(itemId);
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

    private int getInstantBuyPrice(int itemId)
    {
        try
        {
            WikiPrice price = Rs2GrandExchange.getRealTimePrices(itemId);
            if (price != null && price.buyPrice > 0)
            {
                return price.buyPrice;
            }
        }
        catch (Exception ignored)
        {
            // Fall back to the item manager's GE price.
        }
        return Microbot.getRs2ItemManager().getGEPrice(itemId);
    }

    private int getInstantSellPrice(int itemId)
    {
        try
        {
            WikiPrice price = Rs2GrandExchange.getRealTimePrices(itemId);
            if (price != null && price.sellPrice > 0)
            {
                return price.sellPrice;
            }
        }
        catch (Exception ignored)
        {
            // Fall back to the item manager's GE price.
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
