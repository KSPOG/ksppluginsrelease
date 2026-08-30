package net.runelite.client.plugins.microbot.f2pprocessingfactory;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup(F2PProcessingFactoryConfig.GROUP)
public interface F2PProcessingFactoryMuleConfig extends F2PProcessingFactoryConfig, KspMuleConfig
{
    @ConfigSection(name = "Factory", description = "Processing method and cycle settings", position = 0)
    String factorySection = "factory";
    @ConfigSection(name = "Profitability", description = "Minimum margins required before a cycle starts", position = 1)
    String profitabilitySection = "profitability";
    @ConfigSection(name = "Grand Exchange", description = "Buying, selling and offer retry settings", position = 2)
    String geSection = "grandExchange";
    @ConfigSection(name = "Buy limits", description = "Four-hour GE buy-limit safeguards", position = 3)
    String buyLimitSection = "buyLimits";
    @ConfigSection(name = "Anti-ban", description = "Factory-aware behavior variation that only runs in safe idle windows", position = 4)
    String antibanSection = "antiban";
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;

    @Override
    default int muleMinimumBankReserve()
    {
        return cashReserve();
    }
}
