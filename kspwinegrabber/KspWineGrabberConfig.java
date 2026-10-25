package net.runelite.client.plugins.microbot.kspwinegrabber;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspsupport.KspBreakConfig;

@ConfigGroup("KspWineGrabber")
public interface KspWineGrabberConfig extends Config, KspBreakConfig
{
    @ConfigSection(name = "Break Handler", description = "Randomized logout breaks", position = 100)
    String breakHandlerSection = KspBreakConfig.SECTION;
}
