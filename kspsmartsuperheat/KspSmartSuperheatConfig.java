package net.runelite.client.plugins.microbot.kspsmartsuperheat;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("kspSmartSuperheat")
public interface KspSmartSuperheatConfig extends Config
{
    @Range(min = 0, max = 100000)
    @ConfigItem(
        keyName = "minProfitPerBar",
        name = "Minimum profit / bar",
        description = "A recipe must clear at least this many GP per bar after input costs and GE tax.",
        position = 0
    )
    default int minProfitPerBar()
    {
        return 25;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
        keyName = "minRoiPercent",
        name = "Minimum ROI %",
        description = "Minimum estimated return on the total per-bar input cost.",
        position = 1
    )
    default int minRoiPercent()
    {
        return 1;
    }

    @Range(min = 0, max = 10000000)
    @ConfigItem(
        keyName = "minProjectedGpHour",
        name = "Minimum projected GP/h",
        description = "Ignore recipes below this projected hourly profit. Set to 0 to disable.",
        position = 2
    )
    default int minProjectedGpHour()
    {
        return 0;
    }

    @Range(min = 0, max = 20)
    @ConfigItem(
        keyName = "buyMarkupPercent",
        name = "GE buy markup %",
        description = "Added to the current instant-buy price when restocking. This extra cost is included in profitability.",
        position = 3
    )
    default int buyMarkupPercent()
    {
        return 1;
    }

    @Range(min = 0, max = 20)
    @ConfigItem(
        keyName = "sellDiscountPercent",
        name = "GE sell discount %",
        description = "Subtracted from the current instant-sell price. Profitability includes this discount and the GE tax.",
        position = 4
    )
    default int sellDiscountPercent()
    {
        return 1;
    }

    @Range(min = 1, max = 5000)
    @ConfigItem(
        keyName = "restockTargetBars",
        name = "Restock target",
        description = "Maximum number of bars worth of ingredients to restock at once. Actual quantity is reduced to fit available capital.",
        position = 5
    )
    default int restockTargetBars()
    {
        return 500;
    }

    @Range(min = 0, max = 100000000)
    @ConfigItem(
        keyName = "cashReserve",
        name = "Cash reserve",
        description = "Coins the plugin will leave untouched when deciding how much it can spend.",
        position = 6
    )
    default int cashReserve()
    {
        return 10000;
    }

    @Range(min = 1, max = 100)
    @ConfigItem(
        keyName = "maxSpendPercent",
        name = "Max spend %",
        description = "Maximum percentage of spendable cash that one restock plan may commit.",
        position = 7
    )
    default int maxSpendPercent()
    {
        return 85;
    }

    @Range(min = 15, max = 300)
    @ConfigItem(
        keyName = "priceRefreshSeconds",
        name = "Price refresh",
        description = "Seconds between profitability re-checks while processing.",
        position = 8
    )
    default int priceRefreshSeconds()
    {
        return 60;
    }

    @Range(min = 10, max = 180)
    @ConfigItem(
        keyName = "geOfferTimeoutSeconds",
        name = "GE offer timeout",
        description = "How long to wait for a restock/sale offer before aborting and collecting the partial fill.",
        position = 9
    )
    default int geOfferTimeoutSeconds()
    {
        return 35;
    }

    @Range(min = 1, max = 30)
    @ConfigItem(
        keyName = "bankOverheadSeconds",
        name = "Bank overhead estimate",
        description = "Estimated seconds spent banking per inventory. Used only when ranking recipes by projected GP/h.",
        position = 10
    )
    default int bankOverheadSeconds()
    {
        return 5;
    }

    @Range(min = 50, max = 1000)
    @ConfigItem(
        keyName = "castDelayMinMs",
        name = "Cast delay minimum",
        description = "Minimum helper delay used by the Superheat Item interaction.",
        position = 11
    )
    default int castDelayMinMs()
    {
        return 150;
    }

    @Range(min = 100, max = 1500)
    @ConfigItem(
        keyName = "castDelayMaxMs",
        name = "Cast delay maximum",
        description = "Maximum helper delay used by the Superheat Item interaction.",
        position = 12
    )
    default int castDelayMaxMs()
    {
        return 300;
    }

    @ConfigItem(
        keyName = "autoSellOutput",
        name = "Auto-sell output",
        description = "Sell bars made by this plugin session when more cash is needed for restocking. Existing bars are not intentionally liquidated.",
        position = 13
    )
    default boolean autoSellOutput()
    {
        return true;
    }

    @ConfigItem(
        keyName = "bankWholeInventory",
        name = "Bank whole inventory",
        description = "Deposit the whole inventory before preparing a Superheat batch. Disable to leave unrelated inventory items untouched.",
        position = 14
    )
    default boolean bankWholeInventory()
    {
        return true;
    }
}
