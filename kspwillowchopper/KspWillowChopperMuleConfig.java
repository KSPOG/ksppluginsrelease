package net.runelite.client.plugins.microbot.kspwillowchopper;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup(KspWillowChopperConfig.GROUP)
public interface KspWillowChopperMuleConfig extends KspWillowChopperConfig, KspMuleConfig
{
    @ConfigSection(name = "General", description = "Core chopping behavior", position = 0)
    String generalSection = "general";
    @ConfigSection(name = "Forestry", description = "Forestry random event handling", position = 1, closedByDefault = true)
    String forestrySection = "forestry";
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;
}
