package net.runelite.client.plugins.microbot.kspkebabbuyer;

import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspsupport.KspBreakConfig;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;

@ConfigGroup("KspKebabBuyer")
public interface KspKebabBuyerConfig extends Config, KspBreakConfig {
    @ConfigSection(name = "Break Handler", description = "Randomized logout breaks", position = 100)
    String breakHandlerSection = KspBreakConfig.SECTION;

}
