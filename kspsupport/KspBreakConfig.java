package net.runelite.client.plugins.microbot.kspsupport;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

/** Account Builder-compatible settings, stored in each plugin's own config group. */
public interface KspBreakConfig extends Config
{
    String SECTION = "breakHandler";

    @ConfigItem(keyName = "doBreaks", name = "Do Breaks", description = "Perform randomized logout breaks", position = 0, section = SECTION)
    default boolean doBreaks() { return true; }

    @Range(min = 5, max = 300)
    @ConfigItem(keyName = "breakAfterMinMinutes", name = "Break After Min (min)", description = "Minimum minutes of runtime before a break", position = 1, section = SECTION)
    default int breakAfterMinMinutes() { return 45; }

    @Range(min = 5, max = 300)
    @ConfigItem(keyName = "breakAfterMaxMinutes", name = "Break After Max (min)", description = "Maximum minutes of runtime before a break", position = 2, section = SECTION)
    default int breakAfterMaxMinutes() { return 90; }

    @Range(min = 1, max = 180)
    @ConfigItem(keyName = "breakDurationMinMinutes", name = "Break Duration Min (min)", description = "Minimum break duration in minutes", position = 3, section = SECTION)
    default int breakDurationMinMinutes() { return 5; }

    @Range(min = 1, max = 180)
    @ConfigItem(keyName = "breakDurationMaxMinutes", name = "Break Duration Max (min)", description = "Maximum break duration in minutes", position = 4, section = SECTION)
    default int breakDurationMaxMinutes() { return 15; }
}
