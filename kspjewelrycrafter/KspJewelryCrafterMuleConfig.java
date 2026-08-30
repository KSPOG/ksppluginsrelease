package net.runelite.client.plugins.microbot.kspjewelrycrafter;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup("kspjewelrycrafter")
public interface KspJewelryCrafterMuleConfig extends KspJewelryCrafterConfig, KspMuleConfig
{
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;

    @Override
    default int muleMinimumTradingCapital()
    {
        return reserveCoins();
    }
}
