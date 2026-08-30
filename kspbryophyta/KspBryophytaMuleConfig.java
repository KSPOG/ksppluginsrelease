package net.runelite.client.plugins.microbot.kspbryophyta;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup("kspbryophyta")
public interface KspBryophytaMuleConfig extends KspBryophytaConfig, KspMuleConfig
{
    @ConfigSection(name = "Strategy", description = "Combat strategy and automatic strategy loadout", position = 0)
    String strategySection = "strategySection";
    @ConfigSection(name = "Banking & restock", description = "Varrock banking, teleport runes and trip supplies", position = 1)
    String bankingSection = "bankingSection";
    @ConfigSection(name = "Survival", description = "Health, prayer and poison handling", position = 2)
    String survivalSection = "survivalSection";
    @ConfigSection(name = "Kills & loot", description = "Post-kill behaviour", position = 3)
    String lootSection = "lootSection";
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;
}
