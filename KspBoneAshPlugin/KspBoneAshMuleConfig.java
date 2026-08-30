package net.runelite.client.plugins.microbot.KspBoneAshPlugin;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup(KspBoneAshConfig.GROUP)
public interface KspBoneAshMuleConfig extends KspBoneAshConfig, KspMuleConfig
{
    @ConfigSection(name = "Prayer item", description = "Bone or ash to process", position = 0)
    String itemSection = "itemSection";
    @ConfigSection(name = "Randomized interaction", description = "Custom inventory interaction randomization", position = 1)
    String antibanSection = "antibanSection";
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;
}
