package net.runelite.client.plugins.microbot.KSPGELooter;

import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup("KSPGELooter")
public interface KSPGELooterMuleConfig extends KSPGELooterConfig, KspMuleConfig
{
    @ConfigSection(name = "Looting", description = "Ground-item looting settings", position = 0)
    String lootingSection = "looting";
    @ConfigSection(name = "Priority Mode", description = "Temporarily pauses other Microbot scripts while eligible loot is present", position = 1)
    String prioritySection = "priority";
    @ConfigSection(name = "High Alchemy", description = "Automatic High Level Alchemy settings", position = 2)
    String alchemySection = "alchemy";
    @ConfigSection(name = "Spam Clicking", description = "Controls repeated Take interactions", position = 3)
    String spamSection = "spam";
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;
}
