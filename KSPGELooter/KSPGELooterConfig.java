package net.runelite.client.plugins.microbot.KSPGELooter;


import net.runelite.client.plugins.microbot.kspsupport.KspSupportConfig;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup("KSPGELooter")
public interface KSPGELooterConfig extends Config, KspMuleConfig, KspSupportConfig
{
    @ConfigSection(
            name = "Looting",
            description = "Ground-item looting settings",
            position = 0
    )
    String lootingSection = "looting";

    @ConfigSection(
            name = "Priority Mode",
            description = "Temporarily pauses other Microbot scripts while eligible loot is present",
            position = 1
    )
    String prioritySection = "priority";

    @ConfigSection(
            name = "High Alchemy",
            description = "Automatic High Level Alchemy settings",
            position = 2
    )
    String alchemySection = "alchemy";

    @ConfigSection(
            name = "Spam Clicking",
            description = "Controls repeated Take interactions",
            position = 3
    )
    String spamSection = "spam";

    @ConfigSection(
            name = "Local Mule",
            description = "Automatic excess-GP transfer to KSP Trade Receiver",
            position = 90
    )
    String muleSection = KspMuleConfig.SECTION;

    @ConfigItem(
            keyName = "minimumGeValue",
            name = "Minimum GE Value",
            description = "Loot an item stack when its total Grand Exchange value is at least this amount",
            position = 0,
            section = lootingSection
    )
    default int minimumGeValue()
    {
        return 1000;
    }

    @ConfigItem(
            keyName = "priorityMode",
            name = "Priority Mode",
            description = "When eligible loot appears, pause other Microbot scripts until no eligible loot remains",
            position = 0,
            section = prioritySection
    )
    default boolean priorityMode()
    {
        return true;
    }

    @ConfigItem(
            keyName = "highAlch",
            name = "High Alch",
            description = "Automatically High Alch inventory items when their alch value exceeds the rune cost",
            position = 0,
            section = alchemySection
    )
    default boolean highAlch()
    {
        return false;
    }

    @ConfigItem(
            keyName = "spamClicks",
            name = "Clicks Per Item",
            description = "Number of Take interactions sent rapidly for the selected ground item",
            position = 0,
            section = spamSection
    )
    default int spamClicks()
    {
        return 5;
    }

    @ConfigItem(
            keyName = "spamDelayMs",
            name = "Click Delay (ms)",
            description = "Delay in milliseconds between repeated Take interactions",
            position = 1,
            section = spamSection
    )
    default int spamDelayMs()
    {
        return 70;
    }
}
