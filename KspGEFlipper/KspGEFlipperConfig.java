package net.runelite.client.plugins.microbot.kspgeflipper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("kspgeflipper")
public interface KspGEFlipperConfig extends Config {
    @ConfigSection(name = "Trading", description = "Capital and slot usage", position = 0)
    String trading = "trading";

    @ConfigSection(name = "Market filter", description = "Profitability and liquidity filters", position = 1)
    String market = "market";

    @ConfigSection(name = "Execution", description = "Offer pricing and repricing", position = 2)
    String execution = "execution";

    @ConfigItem(keyName = "walkToGe", name = "Walk to GE", description = "Walk to the Grand Exchange when needed", position = 0, section = trading)
    default boolean walkToGe() { return true; }

    @ConfigItem(keyName = "maxSlots", name = "Max slots", description = "Maximum GE slots this plugin may use", position = 1, section = trading)
    default int maxSlots() { return 8; }

    @ConfigItem(keyName = "reserveCoins", name = "Coin reserve", description = "Coins kept out of new flips", position = 2, section = trading)
    default int reserveCoins() { return 100_000; }

    @ConfigItem(keyName = "maxCapitalPercent", name = "Max capital / flip %", description = "Maximum usable cash committed to one flip", position = 3, section = trading)
    default double maxCapitalPercent() { return 30.0; }

    @ConfigItem(keyName = "customItems", name = "Item whitelist", description = "Optional comma-separated item names. Blank = automatic market scan", position = 4, section = trading)
    default String customItems() { return ""; }

    @ConfigItem(keyName = "minNetRoi", name = "Min net ROI %", description = "Minimum ROI after GE tax", position = 0, section = market)
    default double minNetRoi() { return 0.30; }

    @ConfigItem(keyName = "minTradeProfit", name = "Min trade profit", description = "Minimum estimated profit for the sized flip", position = 1, section = market)
    default int minTradeProfit() { return 1_000; }

    @ConfigItem(keyName = "minHourlyVolume", name = "Min hourly volume", description = "Minimum two-sided 1h volume", position = 2, section = market)
    default int minHourlyVolume() { return 100; }

    @ConfigItem(keyName = "quoteAge", name = "Max quote age (s)", description = "Reject items whose latest high/low trade is older than this", position = 3, section = market)
    default int quoteAge() { return 180; }

    @ConfigItem(keyName = "edgePercent", name = "Price edge %", description = "Move buy/sell offers inside the spread to improve fill priority", position = 0, section = execution)
    default double edgePercent() { return 0.02; }

    @ConfigItem(keyName = "offerTimeout", name = "Reprice after (s)", description = "Cancel and reprice plugin offers after this many seconds", position = 1, section = execution)
    default int offerTimeout() { return 300; }
}
