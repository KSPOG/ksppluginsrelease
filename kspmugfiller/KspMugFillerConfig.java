package net.runelite.client.plugins.microbot.kspmugfiller;

import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspsupport.KspBreakConfig;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;

@ConfigGroup("KspMugFiller")
public interface KspMugFillerConfig extends Config, KspBreakConfig {
    @ConfigSection(name = "Break Handler", description = "Randomized logout breaks", position = 100)
    String breakHandlerSection = KspBreakConfig.SECTION;

}
