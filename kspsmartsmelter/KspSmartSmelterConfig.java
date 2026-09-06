package net.runelite.client.plugins.microbot.kspsmartsmelter;


import net.runelite.client.plugins.microbot.kspsupport.KspSupportConfig;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.FurnaceLocation;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.RankingMode;

@ConfigGroup("kspSmartSmelter")
@ConfigInformation(
        "<html><body>" +
        "<b>KSP Smart Smelter</b><br>" +
        "Chooses a profitable smelting/processing route using live market prices, " +
        "your Smithing level and membership status.<br><br>" +
        "Iron is disabled by default because ordinary furnace smelting has a failure chance. " +
        "Cannonballs require an ammo mould or double ammo mould." +
        "</body></html>"
)
public interface KspSmartSmelterConfig extends Config, KspMuleConfig, KspSupportConfig {
    @ConfigSection(name = "Anti-ban", description = "Task-aware behavior variation that only runs in safe idle windows", position = 50)
    String antibanSection = "antiban";

    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;

    @ConfigItem(keyName = "furnaceLocation", name = "Work location", description = "Furnace/bank location used for normal production", position = 1)
    default FurnaceLocation furnaceLocation() { return FurnaceLocation.EDGEVILLE; }
    @ConfigItem(keyName = "rankingMode", name = "Ranking", description = "How profitable routes are ranked", position = 2)
    default RankingMode rankingMode() { return RankingMode.TRIP_PROFIT; }
    @ConfigItem(keyName = "minProfitPerCycle", name = "Min profit / cycle", description = "Minimum expected GP profit per bar/processing cycle after GE tax", position = 3)
    default int minProfitPerCycle() { return 10; }
    @ConfigItem(keyName = "minRoiPercent", name = "Minimum ROI %", description = "Minimum expected return on input cost", position = 4)
    default double minRoiPercent() { return 1.0; }
    @ConfigItem(keyName = "priceRefreshSeconds", name = "Price refresh (sec)", description = "How often profitability is rescanned", position = 5)
    default int priceRefreshSeconds() { return 60; }
    @ConfigItem(keyName = "switchAdvantagePercent", name = "Route switch advantage %", description = "New route must beat the current route by this much before switching", position = 6)
    default int switchAdvantagePercent() { return 5; }
    @ConfigItem(keyName = "allowIron", name = "Allow risky iron", description = "Allow ordinary iron-bar smelting using a conservative 50% expected yield", position = 7)
    default boolean allowIron() { return false; }
    @ConfigItem(keyName = "allowCannonballs", name = "Allow cannonballs", description = "Consider Steel bar -> Cannonballs when a mould is available", position = 8)
    default boolean allowCannonballs() { return true; }
    @ConfigItem(keyName = "autoRestock", name = "Auto restock GE", description = "Walk to the Grand Exchange and buy inputs when bank stock is exhausted", position = 9)
    default boolean autoRestock() { return true; }
    @ConfigItem(keyName = "autoSellOutput", name = "Sell outputs on restock", description = "Sell banked output of the selected route before buying more inputs", position = 10)
    default boolean autoSellOutput() { return true; }
    @ConfigItem(keyName = "offerWaitSeconds", name = "GE wait (sec)", description = "How long to wait for restock offers before collecting and rechecking", position = 11)
    default int offerWaitSeconds() { return 15; }
    @ConfigItem(keyName = "showOverlay", name = "Overlay", description = "Show the Smart Smelter overlay", position = 12)
    default boolean showOverlay() { return true; }

    @ConfigItem(keyName = "customAntiban", name = "Custom anti-ban", description = "Enable Smart Smelter's safe, task-aware humanization layer", position = 0, section = antibanSection)
    default boolean customAntiban() { return true; }

    @ConfigItem(keyName = "antibanProfile", name = "Anti-ban profile", description = "Light keeps delays small, Balanced is the default, High adds longer pauses and more mouse-away behavior", position = 1, section = antibanSection)
    default SmartSmelterAntibanProfile antibanProfile() { return SmartSmelterAntibanProfile.BALANCED; }
}
