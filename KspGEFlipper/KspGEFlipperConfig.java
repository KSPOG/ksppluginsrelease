package net.runelite.client.plugins.microbot.kspgeflipper;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("kspgeflipper")
public interface KspGEFlipperConfig extends Config {
    enum RiskLevel { LOW, MEDIUM, HIGH }
    enum EngineMode { AUTO, EMBEDDED, REMOTE, SERVER, LOCAL }
    enum ExecutionMode { MANUAL, AUTO }

    @ConfigSection(name = "Trading", description = "Capital, account and GE-slot allocation", position = 0)
    String trading = "trading";
    @ConfigSection(name = "Strategy", description = "Risk, timeframe and opportunity types", position = 1)
    String strategy = "strategy";
    @ConfigSection(name = "Market filter", description = "Profitability, quality and liquidity filters", position = 2)
    String market = "market";
    @ConfigSection(name = "Execution", description = "Offer pricing, reevaluation and hysteresis", position = 3)
    String execution = "execution";
    @ConfigSection(name = "Learning", description = "Persistent self-calibration from actual GE outcomes", position = 4)
    String learning = "learning";
    @ConfigSection(name = "Embedded core", description = "In-process market history, forecasting, portfolio and calibration", position = 5)
    String embedded = "embedded";
    @ConfigSection(name = "Remote backend", description = "Optional shared backend for multi-client synchronization", position = 6)
    String backend = "backend";

    @ConfigItem(keyName = "walkToGe", name = "Walk to GE", description = "Walk to the Grand Exchange when needed", position = 0, section = trading)
    default boolean walkToGe() { return true; }
    @ConfigItem(keyName = "maxSlots", name = "Max slots", description = "Maximum GE slots this plugin may use. Actual account/world availability is still respected.", position = 1, section = trading)
    default int maxSlots() { return 8; }
    @ConfigItem(keyName = "reservedSlots", name = "Reserved slots", description = "GE slots kept free for manual trading or other plugins", position = 2, section = trading)
    default int reservedSlots() { return 0; }
    @ConfigItem(keyName = "reserveCoins", name = "Coin reserve", description = "Coins kept out of new flips", position = 3, section = trading)
    default int reserveCoins() { return 100_000; }
    @ConfigItem(keyName = "maxCapitalPercent", name = "Max capital / flip %", description = "Hard cap on usable capital committed to one item", position = 4, section = trading)
    default double maxCapitalPercent() { return 30.0; }
    @ConfigItem(keyName = "customItems", name = "Item whitelist", description = "Optional comma-separated item names. Blank = automatic market scan", position = 5, section = trading)
    default String customItems() { return ""; }
    @ConfigItem(keyName = "blockedItems", name = "Blocked items", description = "Comma-separated item names that must never be selected", position = 6, section = trading)
    default String blockedItems() { return ""; }

    @ConfigItem(keyName = "riskLevel", name = "Risk level", description = "Controls confidence floor, exposure and liquidity participation", position = 0, section = strategy)
    default RiskLevel riskLevel() { return RiskLevel.MEDIUM; }
    @ConfigItem(keyName = "timeframeMinutes", name = "Timeframe (minutes)", description = "Desired strategy horizon used by forecasting, sizing and duration scoring", position = 1, section = strategy)
    default int timeframeMinutes() { return 30; }
    @ConfigItem(keyName = "sellOnly", name = "Sell only", description = "Do not open new positions; only generate exits for owned positions", position = 2, section = strategy)
    default boolean sellOnly() { return false; }
    @ConfigItem(keyName = "f2pOnly", name = "F2P items only", description = "Restrict recommendations to F2P items even on a members account/world", position = 3, section = strategy)
    default boolean f2pOnly() { return false; }
    @ConfigItem(keyName = "allowBuyAndHold", name = "Buy and hold", description = "Allow longer-horizon directional positions when forecast quality supports them", position = 4, section = strategy)
    default boolean allowBuyAndHold() { return false; }
    @ConfigItem(keyName = "enableDumpOpportunities", name = "Enable dump opportunities", description = "Enable independent dump/recovery candidate generation and dump event stream", position = 5, section = strategy)
    default boolean enableDumpOpportunities() { return false; }
    @ConfigItem(keyName = "dumpSlots", name = "Dump slots", description = "GE slots reserved for dump opportunities at short timeframes", position = 6, section = strategy)
    default int dumpSlots() { return 1; }
    @ConfigItem(keyName = "dumpDropPercent", name = "Dump drop threshold %", description = "Local fallback minimum latest-low deviation below the 1h low average", position = 7, section = strategy)
    default double dumpDropPercent() { return 2.5; }
    @ConfigItem(keyName = "dumpMinPredictedProfit", name = "Min dump profit", description = "Minimum calibrated net profit for a dump-recovery candidate", position = 8, section = strategy)
    default int dumpMinPredictedProfit() { return 5_000; }

    @ConfigItem(keyName = "minNetRoi", name = "Min net ROI %", description = "Minimum ROI after GE tax for the local fallback", position = 0, section = market)
    default double minNetRoi() { return 0.30; }
    @ConfigItem(keyName = "minTradeProfit", name = "Min trade profit", description = "Minimum expected profit for a normal flip", position = 1, section = market)
    default int minTradeProfit() { return 1_000; }
    @ConfigItem(keyName = "minExpectedGpPerHour", name = "Min expected GP/h", description = "Optional execution-adjusted GP/hour floor. Zero disables this filter.", position = 2, section = market)
    default int minExpectedGpPerHour() { return 0; }
    @ConfigItem(keyName = "minHourlyVolume", name = "Min hourly volume", description = "Minimum matched two-sided 1h volume", position = 3, section = market)
    default int minHourlyVolume() { return 100; }
    @ConfigItem(keyName = "quoteAge", name = "Max quote age (s)", description = "Reject items whose latest high/low trade is older than this", position = 4, section = market)
    default int quoteAge() { return 180; }

    @ConfigItem(keyName = "executionMode", name = "Execution mode", description = "MANUAL shows backend recommendations only. AUTO executes them through a separated Microbot executor.", position = 0, section = execution)
    default ExecutionMode executionMode() { return ExecutionMode.AUTO; }
    @ConfigItem(keyName = "edgePercent", name = "Price edge %", description = "Local fallback base amount moved inside the latest spread", position = 1, section = execution)
    default double edgePercent() { return 0.02; }
    @ConfigItem(keyName = "offerTimeout", name = "Reevaluate after (s)", description = "Local fallback reevaluation interval", position = 2, section = execution)
    default int offerTimeout() { return 300; }
    @ConfigItem(keyName = "modifyImprovementPercent", name = "Modify improvement %", description = "Required utility improvement before modifying an offer", position = 3, section = execution)
    default double modifyImprovementPercent() { return 10.0; }
    @ConfigItem(keyName = "abortDeteriorationPercent", name = "Abort/replacement %", description = "Required replacement advantage before aborting a weaker offer", position = 4, section = execution)
    default double abortDeteriorationPercent() { return 20.0; }
    @ConfigItem(keyName = "sellRepricePercent", name = "Sell reprice delta %", description = "Minimum tax-safe sell-target change before relisting", position = 5, section = execution)
    default double sellRepricePercent() { return 0.10; }

    @ConfigItem(keyName = "enableSelfCalibration", name = "Enable local self-calibration", description = "Use completed/aborted local flips to calibrate execution heuristics when running the local engine", position = 0, section = learning)
    default boolean enableSelfCalibration() { return true; }
    @ConfigItem(keyName = "calibrationWarmupSamples", name = "Warm-up flips", description = "Finished outcomes required before local learned corrections affect recommendations", position = 1, section = learning)
    default int calibrationWarmupSamples() { return 8; }
    @ConfigItem(keyName = "calibrationLearningRate", name = "Learning rate", description = "EWMA update rate for local outcomes", position = 2, section = learning)
    default double calibrationLearningRate() { return 0.12; }
    @ConfigItem(keyName = "calibrationMaxAdjustmentPercent", name = "Max learned adjustment %", description = "Hard local bound around deterministic execution estimates", position = 3, section = learning)
    default double calibrationMaxAdjustmentPercent() { return 35.0; }

    @ConfigItem(keyName = "embeddedMarketPollSeconds", name = "Market refresh (s)", description = "How often embedded mode refreshes Wiki market snapshots", position = 0, section = embedded)
    default int embeddedMarketPollSeconds() { return 30; }
    @ConfigItem(keyName = "embeddedHistoryPoints", name = "History points / item", description = "Bounded 5-minute market-history points retained per liquid item in embedded mode (288 = about 24h)", position = 1, section = embedded)
    default int embeddedHistoryPoints() { return 288; }

    @ConfigItem(keyName = "engineMode", name = "Engine mode", description = "EMBEDDED runs the full backend inside the plugin. REMOTE/SERVER uses the optional shared service. AUTO prefers embedded and falls back to local.", position = 0, section = backend)
    default EngineMode engineMode() { return EngineMode.EMBEDDED; }
    @ConfigItem(keyName = "backendUrl", name = "Backend URL", description = "Optional KSP GE Flipper remote server base URL", position = 1, section = backend)
    default String backendUrl() { return "http://127.0.0.1:8181"; }
    @ConfigItem(keyName = "backendApiKey", name = "Backend API key", description = "Optional X-KSP-API-Key configured on the server", position = 2, section = backend)
    default String backendApiKey() { return ""; }
    @ConfigItem(keyName = "accountKey", name = "Account key", description = "Stable backend account identifier. Blank uses the current display name.", position = 3, section = backend)
    default String accountKey() { return ""; }
    @ConfigItem(keyName = "backendPollSeconds", name = "Recommendation poll (s)", description = "How often account state is synchronized and a new recommendation is requested", position = 4, section = backend)
    default int backendPollSeconds() { return 3; }
    @ConfigItem(keyName = "backendFallback", name = "Auto fallback", description = "Use the legacy local engine if the selected embedded/remote engine cannot start", position = 5, section = backend)
    default boolean backendFallback() { return true; }
}
