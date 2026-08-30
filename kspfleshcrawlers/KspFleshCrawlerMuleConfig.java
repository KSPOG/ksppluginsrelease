package net.runelite.client.plugins.microbot.kspfleshcrawlers;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup(KspFleshCrawlerConfig.GROUP)
public interface KspFleshCrawlerMuleConfig extends KspFleshCrawlerConfig, KspMuleConfig
{
    @ConfigSection(name = "Training", description = "Combat goals and melee balancing", position = 0, closedByDefault = false)
    String trainingSection = "training";
    @ConfigSection(name = "Loot", description = "Ground-item looting and bones", position = 1, closedByDefault = false)
    String lootSection = "loot";
    @ConfigSection(name = "Supplies", description = "Food, healing, potions and banking", position = 2, closedByDefault = false)
    String suppliesSection = "supplies";
    @ConfigSection(name = "Navigation", description = "Stronghold travel controls", position = 3, closedByDefault = true)
    String navigationSection = "navigation";
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;
}
