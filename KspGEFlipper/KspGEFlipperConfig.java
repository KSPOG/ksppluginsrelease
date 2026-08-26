package net.runelite.client.plugins.microbot.kspgeflipper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("kspgeflipper")
public interface KspGEFlipperConfig extends Config {
    enum RiskLevel {
        LOW,
        MEDIUM,
        HIGH
    }

    @ConfigSection(name = "Trading", description = "Capital, account and GE-slot allocation", position = 0)
    String trading = "trading";

    @ConfigSection(name = "Strategy", description = "Risk, timeframe and opportunity types", position = 1)
    String strategy = "strategy";

    @ConfigSection(name = "Market filter", description = "Profitability, quality and liquidity filters", position = 2)
    String market = "market";

    @ConfigSection(name = "Execution", description = "Offer pricing, reevaluation and hysteresis", position = 3)
    String execution = "execution";

    @ConfigItem(keyName = "walkToGe", name = "Walk to GE", description = "Walk to the Grand Exchange when needed", position = 0, section = trading)
    default boolean walkToGe() { return true; }

    @ConfigItem(keyName = "maxSlots", name = "Max slots", description = "Maximum GE slots this plugin may use. Actual account/world availability is still respected.", position = 1, section = trading)
    default int maxSlots() { return 8; }

    @ConfigItem(keyName = "reservedSlots", name = "Reserved slots", description = "GE slots kept free for manual trading or other plugins", position = 2, section = trading)
    default int reservedSlots() { return 0; }

    @ConfigItem(keyName = "reserveCoins", name = "Coin reserve", description = "Coins kept out of new flips", position = 3, section = trading)
    default int reserveCoins() { return 100_000; }

    @ConfigItem(keyName = "maxCapitalPercent", name = "Max capital / flip %", description = "Hard cap on usable cash committed to one flip. The selected risk profile may impose a lower cap.", position = 4, section = trading)
    default double maxCapitalPercent() { return 30.0; }

    @ConfigItem(keyName = "customItems", name = "Item whitelist", description = "Optional comma-separated item names. Blank = automatic market scan", position = 5, section = trading)
    default String customItems() { return ""; }

    @ConfigItem(keyName = "blockedItems", name = "Blocked items", description = "Comma-separated item names that must never be selected", position = 6, section = trading)
    default String blockedItems() { return ""; }

    @ConfigItem(keyName = "riskLevel", name = "Risk level", description = "Controls confidence floor, capital exposure and liquidity participation", position = 0, section = strategy)
    default RiskLevel riskLevel() { return RiskLevel.MEDIUM; }

    @ConfigItem(keyName = "timeframeMinutes", name = "Timeframe (minutes)", description = "Desired strategy horizon used by sizing and execution-duration scoring", position = 1, section = strategy)
    default int timeframeMinutes() { return 30; }

    @ConfigItem(keyName = "enableDumpOpportunities", name = "Enable dump opportunities", description = "Allow a separate short-horizon recovery detector. Disabled by default because it is higher risk.", position = 2, section = strategy)
    default boolean enableDumpOpportunities() { return false; }

    @ConfigItem(keyName = "dumpDropPercent", name = "Dump drop threshold %", description = "Minimum latest-low deviation below the 1h low average before an item can be treated as a dump", position = 3, section = strategy)
    default double dumpDropPercent() { return 2.5; }

    @ConfigItem(keyName = "dumpMinPredictedProfit", name = "Min dump profit", description = "Minimum estimated net profit for a dump-recovery candidate", position = 4, section = strategy)
    default int dumpMinPredictedProfit() { return 5_000; }

    @ConfigItem(keyName = "minNetRoi", name = "Min net ROI %", description = "Minimum ROI after GE tax", position = 0, section = market)
    default double minNetRoi() { return 0.30; }

    @ConfigItem(keyName = "minTradeProfit", name = "Min trade profit", description = "Minimum estimated profit for the sized normal flip", position = 1, section = market)
    default int minTradeProfit() { return 1_000; }

    @ConfigItem(keyName = "minExpectedGpPerHour", name = "Min expected GP/h", description = "Optional execution-adjusted GP/hour floor. Zero disables this filter.", position = 2, section = market)
    default int minExpectedGpPerHour() { return 0; }

    @ConfigItem(keyName = "minHourlyVolume", name = "Min hourly volume", description = "Minimum matched two-sided 1h volume", position = 3, section = market)
    default int minHourlyVolume() { return 100; }

    @ConfigItem(keyName = "quoteAge", name = "Max quote age (s)", description = "Reject items whose latest high/low trade is older than this", position = 4, section = market)
    default int quoteAge() { return 180; }

    @ConfigItem(keyName = "edgePercent", name = "Price edge %", description = "Base amount moved inside the latest spread when searching executable buy/sell prices", position = 0, section = execution)
    default double edgePercent() { return 0.02; }

    @ConfigItem(keyName = "offerTimeout", name = "Reevaluate after (s)", description = "Reevaluate plugin offers after this many seconds", position = 1, section = execution)
    default int offerTimeout() { return 300; }

    @ConfigItem(keyName = "modifyImprovementPercent", name = "Modify improvement %", description = "Required fresh utility improvement before a stale buy is cancelled and relisted", position = 2, section = execution)
    default double modifyImprovementPercent() { return 10.0; }

    @ConfigItem(keyName = "abortDeteriorationPercent", name = "Abort deterioration %", description = "Abort a stale unfilled buy when estimated utility has deteriorated by at least this amount", position = 3, section = execution)
    default double abortDeteriorationPercent() { return 20.0; }

    @ConfigItem(keyName = "sellRepricePercent", name = "Sell reprice delta %", description = "Minimum tax-safe sell-target change required before cancelling and relisting a stale sell", position = 4, section = execution)
    default double sellRepricePercent() { return 0.10; }
}
