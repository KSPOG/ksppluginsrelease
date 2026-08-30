package net.runelite.client.plugins.microbot.kspsmartsuperheat;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup("kspSmartSuperheat")
public interface KspSmartSuperheatMuleConfig extends KspSmartSuperheatConfig, KspMuleConfig
{
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;

    @Override
    default int muleMinimumTradingCapital()
    {
        return cashReserve();
    }
}
