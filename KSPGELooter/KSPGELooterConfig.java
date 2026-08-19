package net.runelite.client.plugins.microbot.KSPGELooter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("KSPGELooter")
public interface KSPGELooterConfig extends Config
{
    @ConfigSection(
            name = "Looting",
            description = "Ground-item looting settings",
            position = 0
    )
    String lootingSection = "looting";

    @ConfigSection(
            name = "High Alchemy",
            description = "Automatic High Level Alchemy settings",
            position = 1
    )
    String alchemySection = "alchemy";

    @ConfigSection(
            name = "Spam Clicking",
            description = "Controls repeated Take interactions",
            position = 2
    )
    String spamSection = "spam";

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
