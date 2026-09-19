package net.runelite.client.plugins.microbot.ksprobesofruin;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(KspRobesOfRuinConfig.GROUP)
public interface KspRobesOfRuinConfig extends Config
{
    String GROUP = "ksprobesofruin";

    @ConfigSection(
            name = "Guide",
            description = "Robes of Ruin helper settings",
            position = 0
    )
    String guideSection = "guide";

    @ConfigItem(
            keyName = "phase",
            name = "Guide phase",
            description = "Auto follows saved progress. Use an override if you already completed an earlier step before enabling the plugin.",
            position = 0,
            section = guideSection
    )
    default KspRobesOfRuinPhase phase()
    {
        return KspRobesOfRuinPhase.AUTO;
    }

    @ConfigItem(
            keyName = "useShortestPath",
            name = "Shortest Path route",
            description = "Draw the Shortest Path route to the active Robes of Ruin location, like Quest Helper.",
            position = 1,
            section = guideSection
    )
    default boolean useShortestPath()
    {
        return true;
    }

    @ConfigItem(
            keyName = "highlightTiles",
            name = "Highlight target tiles",
            description = "Highlight the dig tile, vault-gate tile, and reward chests.",
            position = 2,
            section = guideSection
    )
    default boolean highlightTiles()
    {
        return true;
    }

    @ConfigItem(
            keyName = "highlightEmotes",
            name = "Highlight next emote",
            description = "Highlight and scroll to the next required emote while at the Varrock vault gate.",
            position = 3,
            section = guideSection
    )
    default boolean highlightEmotes()
    {
        return true;
    }

    @ConfigItem(
            keyName = "resetProgress",
            name = "Reset saved progress",
            description = "Reset the saved dig/emote progress. This switch turns itself back off.",
            position = 4,
            section = guideSection
    )
    default boolean resetProgress()
    {
        return false;
    }
}

enum KspRobesOfRuinPhase
{
    AUTO("Automatic"),
    LUMBRIDGE_DIG("Lumbridge Swamp dig"),
    VARROCK_EMOTES("Varrock vault emotes"),
    REWARD_CHESTS("Reward chests");

    private final String display;

    KspRobesOfRuinPhase(String display)
    {
        this.display = display;
    }

    @Override
    public String toString()
    {
        return display;
    }
}
