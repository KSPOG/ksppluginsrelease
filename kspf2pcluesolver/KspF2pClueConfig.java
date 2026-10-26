package net.runelite.client.plugins.microbot.kspf2pcluesolver;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup("KspF2pClueSolver")
public interface KspF2pClueConfig extends Config
{
    @ConfigItem(keyName = "autoBuy", name = "Buy missing items", description = "Buy required tradeable F2P items at the Grand Exchange", position = 0)
    default boolean autoBuy() { return true; }

    @Range(min = 100, max = 1000000)
    @ConfigItem(keyName = "maxUnitPrice", name = "Maximum GP per item", description = "Never place an offer above this unit price", position = 1)
    default int maxUnitPrice() { return 10000; }

    @Range(min = 100, max = 10000000)
    @ConfigItem(keyName = "maxSessionSpend", name = "Maximum GP per session", description = "Stop making new offers after this much committed spending", position = 2)
    default int maxSessionSpend() { return 50000; }

    @Range(min = 0, max = 100)
    @ConfigItem(keyName = "pricePremium", name = "GE buy premium (%)", description = "Amount above recent instant-buy price, capped by limits", position = 3)
    default int pricePremium() { return 15; }

    @ConfigItem(keyName = "openCaskets", name = "Open beginner caskets", description = "Open completed beginner reward caskets", position = 4)
    default boolean openCaskets() { return true; }
}