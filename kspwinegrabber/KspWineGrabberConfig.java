package net.runelite.client.plugins.microbot.kspwinegrabber;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspsupport.KspBreakConfig;

@ConfigGroup("KspWineGrabber")
public interface KspWineGrabberConfig extends Config, KspBreakConfig
{
    @ConfigSection(name = "Break Handler", description = "Randomized logout breaks", position = 100)
    String breakHandlerSection = KspBreakConfig.SECTION;

    @ConfigItem(keyName = "useTeleport", name = "Use teleport?",
            description = "Withdraw all banked water runes and use Falador Teleport to return from the temple when possible.",
            position = 0)
    default boolean useTeleport() { return false; }
}
