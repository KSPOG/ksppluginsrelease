package net.runelite.client.plugins.microbot.kspmadcow;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup(KspMadCowConfig.GROUP)
public interface KspMadCowMuleConfig extends KspMadCowConfig, KspMuleConfig
{
    @ConfigSection(name = "Supplies", description = "Food, Cowbell and boosting-potion settings", position = 0)
    String suppliesSection = "supplies";
    @ConfigSection(name = "Combat", description = "Combat training, prayers and Brutus mechanics", position = 1)
    String combatSection = "combat";
    @ConfigSection(name = "Looting", description = "Loot and banking behaviour", position = 2)
    String lootSection = "loot";
    @ConfigSection(name = "Travel", description = "Cowbell, altar and instance behaviour", position = 3)
    String travelSection = "travel";
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;
}
