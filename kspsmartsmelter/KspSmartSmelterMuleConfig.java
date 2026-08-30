package net.runelite.client.plugins.microbot.kspsmartsmelter;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup("kspSmartSmelter")
public interface KspSmartSmelterMuleConfig extends KspSmartSmelterConfig, KspMuleConfig
{
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;
}
