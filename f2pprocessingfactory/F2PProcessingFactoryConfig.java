package net.runelite.client.plugins.microbot.f2pprocessingfactory;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(F2PProcessingFactoryConfig.GROUP)
public interface F2PProcessingFactoryConfig extends Config
{
    String GROUP = "f2pProcessingFactory";
    String STATE_GROUP = "f2pProcessingFactoryState";

    @ConfigSection(
        name = "Factory",
        description = "Processing method and cycle settings",
        position = 0
    )
    String factorySection = "factory";


    @ConfigSection(
        name = "Profitability",
        description = "Minimum margins required before a cycle starts",
        position = 1
    )
    String profitabilitySection = "profitability";

    @ConfigSection(
        name = "Grand Exchange",
        description = "Buying, selling and offer retry settings",
        position = 2
    )
    String geSection = "grandExchange";

    @ConfigSection(
        name = "Buy limits",
        description = "Four-hour GE buy-limit safeguards",
        position = 3
    )
    String buyLimitSection = "buyLimits";

    @ConfigSection(
        name = "Anti-ban",
        description = "Factory-aware behavior variation that only runs in safe idle windows",
        position = 4
    )
    String antibanSection = "antiban";

    @ConfigItem(
        keyName = "mode",
        name = "Mode",
        description = "Automatically select the best eligible recipe or use one fixed recipe",
        position = 0,
        section = factorySection
    )
    default FactoryMode mode()
    {
        return FactoryMode.AUTO_BEST_PROFIT;
    }

    @ConfigItem(
        keyName = "fixedRecipe",
        name = "Fixed recipe",
        description = "Recipe used when Mode is Fixed recipe",
        position = 1,
        section = factorySection
    )
    default FactoryRecipe fixedRecipe()
    {
        return FactoryRecipe.CHOCOLATE_DUST;
    }

    @Range(min = 1, max = 10_000)
    @ConfigItem(
        keyName = "targetUnitsPerCycle",
        name = "Units per cycle (minimum)",
        description = "Minimum cycle target. When buying inputs, the factory scales above this and buys the largest affordable amount allowed by spendable coins, stock and GE buy limits",
        position = 2,
        section = factorySection
    )
    default int targetUnitsPerCycle()
    {
        return 500;
    }

    @ConfigItem(
        keyName = "buyInputs",
        name = "Buy missing inputs",
        description = "Automatically purchase missing materials from the Grand Exchange",
        position = 4,
        section = factorySection
    )
    default boolean buyInputs()
    {
        return true;
    }

    @ConfigItem(
        keyName = "sellOutputs",
        name = "Sell finished outputs",
        description = "Automatically sell completed products on the Grand Exchange",
        position = 5,
        section = factorySection
    )
    default boolean sellOutputs()
    {
        return true;
    }

    @Range(min = 0, max = 1_000_000)
    @ConfigItem(
        keyName = "minimumProfitPerUnit",
        name = "Minimum profit / unit",
        description = "Required estimated profit after input costs and configured GE tax; non-positive recipes are always skipped",
        position = 0,
        section = profitabilitySection
    )
    default int minimumProfitPerUnit()
    {
        return 10;
    }

    @Range(min = 0, max = 500)
    @ConfigItem(
        keyName = "minimumRoiPercent",
        name = "Minimum ROI %",
        description = "Minimum estimated return on input cost",
        position = 1,
        section = profitabilitySection
    )
    default int minimumRoiPercent()
    {
        return 2;
    }

    @Range(min = 1, max = 60)
    @ConfigItem(
        keyName = "reevaluateMinutes",
        name = "Market recheck (minutes)",
        description = "How often waiting states refresh prices and search for a profitable recipe",
        position = 2,
        section = profitabilitySection
    )
    default int reevaluateMinutes()
    {
        return 5;
    }

    @Range(min = 0, max = 25)
    @ConfigItem(
        keyName = "geTaxPercent",
        name = "Estimated GE tax %",
        description = "Tax percentage used for projected profit calculations",
        position = 3,
        section = profitabilitySection
    )
    default int geTaxPercent()
    {
        return 2;
    }

    @Range(min = 0, max = 25)
    @ConfigItem(
        keyName = "buyMarkupPercent",
        name = "Buy markup %",
        description = "Initial input offer premium above the current instant-buy price",
        position = 0,
        section = geSection
    )
    default int buyMarkupPercent()
    {
        return 2;
    }

    @Range(min = 0, max = 25)
    @ConfigItem(
        keyName = "sellDiscountPercent",
        name = "Sell discount %",
        description = "Initial output offer discount below the current instant-sell price",
        position = 1,
        section = geSection
    )
    default int sellDiscountPercent()
    {
        return 1;
    }

    @Range(min = 15, max = 600)
    @ConfigItem(
        keyName = "offerTimeoutSeconds",
        name = "Offer timeout (seconds)",
        description = "Reprice a stalled plugin offer through Modify offer after this time",
        position = 2,
        section = geSection
    )
    default int offerTimeoutSeconds()
    {
        return 90;
    }

    @Range(min = 0, max = 10)
    @ConfigItem(
        keyName = "maxPriceRetries",
        name = "Maximum reprices",
        description = "Maximum number of price adjustments for each buy or sell request",
        position = 3,
        section = geSection
    )
    default int maxPriceRetries()
    {
        return 3;
    }

    @Range(min = 0, max = 100_000_000)
    @ConfigItem(
        keyName = "cashReserve",
        name = "Cash reserve",
        description = "Coins that the plugin must leave untouched in the bank",
        position = 4,
        section = geSection
    )
    default int cashReserve()
    {
        return 100_000;
    }

    @ConfigItem(
        keyName = "abortStalledOffers",
        name = "Handle stalled offers",
        description = "Use Modify offer for timed-out plugin offers; cancel only after maximum reprices is exhausted",
        position = 5,
        section = geSection
    )
    default boolean abortStalledOffers()
    {
        return true;
    }

    @Range(min = 1, max = 100)
    @ConfigItem(
        keyName = "buyLimitUsagePercent",
        name = "Use up to % of limit",
        description = "Maximum portion of each Wiki four-hour buy limit that this plugin may allocate",
        position = 0,
        section = buyLimitSection
    )
    default int buyLimitUsagePercent()
    {
        return 90;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
        keyName = "coldStartReservePercent",
        name = "Unknown-usage reserve %",
        description = "Reserve part of a limit when the plugin has no tracked purchases for that item in the current window",
        position = 1,
        section = buyLimitSection
    )
    default int coldStartReservePercent()
    {
        return 15;
    }

    @Range(min = 1, max = 100_000)
    @ConfigItem(
        keyName = "unknownItemLimit",
        name = "Unknown-limit fallback",
        description = "Conservative four-hour limit used when mapping data does not provide a valid limit",
        position = 2,
        section = buyLimitSection
    )
    default int unknownItemLimit()
    {
        return 1_000;
    }

    @ConfigItem(
        keyName = "limitExhaustedAction",
        name = "When limit is exhausted",
        description = "Action used when a required input has no tracked buy-limit capacity remaining",
        position = 3,
        section = buyLimitSection
    )
    default LimitExhaustedAction limitExhaustedAction()
    {
        return LimitExhaustedAction.SWITCH_RECIPE;
    }

    @ConfigItem(
        keyName = "customAntiban",
        name = "Custom anti-ban",
        description = "Enable KSP AIO Factory's own safe, task-aware humanization layer",
        position = 0,
        section = antibanSection
    )
    default boolean customAntiban()
    {
        return true;
    }

    @ConfigItem(
        keyName = "antibanProfile",
        name = "Anti-ban profile",
        description = "Light keeps delays small, Balanced is the default, High adds longer pauses and more mouse-away behavior",
        position = 1,
        section = antibanSection
    )
    default FactoryAntibanProfile antibanProfile()
    {
        return FactoryAntibanProfile.BALANCED;
    }

}
