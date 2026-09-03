package net.runelite.client.plugins.microbot.kspjewelrycrafter;


import net.runelite.client.plugins.microbot.kspsupport.KspSupportConfig;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup("kspjewelrycrafter")
public interface KspJewelryCrafterConfig extends Config, KspMuleConfig, KspSupportConfig
{
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;

    @ConfigItem(keyName = "selectionMode", name = "Recipe selection", description = "How a profitable eligible recipe is selected", position = 0)
    default JewelrySelectionMode selectionMode() { return JewelrySelectionMode.BEST_PROFIT; }
    @ConfigItem(keyName = "fixedRecipe", name = "Fixed recipe", description = "Used only when Recipe selection is Fixed recipe", position = 1)
    default JewelryRecipe fixedRecipe() { return JewelryRecipe.GOLD_RING; }
    @Range(min = 1, max = 1_000_000)
    @ConfigItem(keyName = "minimumProfitPerItem", name = "Minimum profit / item", description = "Hard net-profit floor after estimated GE tax and configured price margins", position = 2)
    default int minimumProfitPerItem() { return 10; }
    @Range(min = 0, max = 100)
    @ConfigItem(keyName = "minimumRoiPercent", name = "Minimum ROI %", description = "Hard ROI floor for a recipe to be considered profitable", position = 3)
    default int minimumRoiPercent() { return 1; }
    @Range(min = 0, max = 25)
    @ConfigItem(keyName = "buyMarkupPercent", name = "GE buy markup %", description = "Markup applied to current instant-buy prices when restocking", position = 4)
    default int buyMarkupPercent() { return 2; }
    @Range(min = 0, max = 25)
    @ConfigItem(keyName = "sellDiscountPercent", name = "GE sell discount %", description = "Discount applied to current instant-sell prices when liquidating output", position = 5)
    default int sellDiscountPercent() { return 2; }
    @Range(min = 1, max = 10_000)
    @ConfigItem(keyName = "maxRestockUnits", name = "Max restock units", description = "Maximum units bought in one restock cycle", position = 6)
    default int maxRestockUnits() { return 750; }
    @Range(min = 10, max = 100)
    @ConfigItem(keyName = "capitalUsagePercent", name = "Capital usage %", description = "Maximum percentage of spendable coins committed to one restock", position = 7)
    default int capitalUsagePercent() { return 90; }
    @Range(min = 0, max = 100_000_000)
    @ConfigItem(keyName = "reserveCoins", name = "Reserve coins", description = "Coins never committed to input restocking", position = 8)
    default int reserveCoins() { return 10_000; }
    @Range(min = 15, max = 300)
    @ConfigItem(keyName = "offerTimeoutSeconds", name = "GE offer timeout", description = "Abort and retry an unfilled plugin offer after this many seconds", position = 9)
    default int offerTimeoutSeconds() { return 60; }
    @Range(min = 0, max = 10)
    @ConfigItem(keyName = "maxOfferRetries", name = "Max GE retries", description = "Maximum repricing retries for a buy or sell before re-evaluating", position = 10)
    default int maxOfferRetries() { return 4; }
    @ConfigItem(keyName = "showOverlay", name = "Show overlay", description = "Show status, membership, recipe and live profitability", position = 11)
    default boolean showOverlay() { return true; }

    @Override
    @Range(min = 0, max = 2_000_000_000)
    @ConfigItem(
            keyName = "muleMinimumTradingCapital",
            name = "Jewelry Mule Trading Floor",
            description = "Internal mule operating-cash floor derived from Reserve coins.",
            hidden = true,
            section = KspMuleConfig.SECTION
    )
    default int muleMinimumTradingCapital() { return reserveCoins(); }
}
