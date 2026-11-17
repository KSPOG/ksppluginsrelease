package net.runelite.client.plugins.microbot.KSPGELooter;


import net.runelite.client.plugins.microbot.kspsupport.KspBreakConfig;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup("KSPGELooter")
public interface KSPGELooterConfig extends Config, KspMuleConfig, KspBreakConfig {
    int MIN_CLICK_DELAY_MS = 1;
    int MAX_CLICK_DELAY_MS = 250;
    int MAX_RETURN_DELAY_SECONDS = 300;

    @ConfigSection(name = "Break Handler", description = "Randomized logout breaks", position = 100)
    String breakHandlerSection = KspBreakConfig.SECTION;

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

    @Range(min = 0, max = MAX_RETURN_DELAY_SECONDS)
    @ConfigItem(
            keyName = "returnDelaySeconds",
            name = "Return Delay (seconds)",
            description = "Seconds without eligible loot before returning to the waiting area. Set to 0 to return immediately.",
            position = 1,
            section = lootingSection
    )
    default int returnDelaySeconds()
    {
        return 0;
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

    @Range(min = MIN_CLICK_DELAY_MS, max = MAX_CLICK_DELAY_MS)
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

    static int clickDelayMs(int configuredDelay)
    {
        return Math.max(MIN_CLICK_DELAY_MS, Math.min(MAX_CLICK_DELAY_MS, configuredDelay));
    }
}
