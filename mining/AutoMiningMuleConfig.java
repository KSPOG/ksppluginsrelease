package net.runelite.client.plugins.microbot.mining;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup("Mining")
public interface AutoMiningMuleConfig extends AutoMiningConfig, KspMuleConfig
{
    @ConfigSection(name = "General", description = "General settings", position = 0)
    String generalSection = "general";
    @ConfigSection(name = "Dropping", description = "Dropping settings", position = 1)
    String droppingSection = "droppingSection";
    @ConfigSection(name = "Banking", description = "Banking settings", position = 2)
    String bankingSection = "bankingSection";
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;
}
