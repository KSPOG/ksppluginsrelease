package net.runelite.client.plugins.microbot.KspBoneAshPlugin;



import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup(KspBoneAshConfig.GROUP)
public interface KspBoneAshConfig extends Config, KspMuleConfig
{
    String GROUP = "kspboneash";

    @ConfigSection(
            name = "Prayer item",
            description = "Bone or ash to process",
            position = 0
    )
    String itemSection = "itemSection";

    @ConfigSection(
            name = "Randomized interaction",
            description = "Custom inventory interaction randomization",
            position = 1
    )
    String antibanSection = "antibanSection";

    @ConfigSection(
            name = "Local Mule",
            description = "Automatic excess-GP transfer to KSP Trade Receiver",
            position = 90
    )
    String muleSection = KspMuleConfig.SECTION;

    @ConfigItem(
            keyName = "itemName",
            name = "Bone / ash name",
            description = "Exact item name to withdraw and process, for example: Bones, Big bones, Vile ashes",
            position = 0,
            section = itemSection
    )
    default String itemName()
    {
        return "Vile ashes";
    }

    @Range(min = 0, max = 1000)
    @ConfigItem(
            keyName = "minInteractionDelay",
            name = "Minimum delay (ms)",
            description = "Minimum delay before the next inventory interaction after the tracked item changes",
            position = 0,
            section = antibanSection
    )
    default int minInteractionDelay()
    {
        return 35;
    }

    @Range(min = 0, max = 1000)
    @ConfigItem(
            keyName = "maxInteractionDelay",
            name = "Maximum delay (ms)",
            description = "Maximum delay before the next inventory interaction after the tracked item changes",
            position = 1,
            section = antibanSection
    )
    default int maxInteractionDelay()
    {
        return 110;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "randomSlotChance",
            name = "Random slot chance (%)",
            description = "Chance to choose any remaining matching inventory slot instead of following the current direction",
            position = 2,
            section = antibanSection
    )
    default int randomSlotChance()
    {
        return 35;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "directionFlipChance",
            name = "Direction flip chance (%)",
            description = "Chance to reverse the preferred inventory traversal direction after an interaction",
            position = 3,
            section = antibanSection
    )
    default int directionFlipChance()
    {
        return 12;
    }

    @Range(min = 0, max = 100)
    @ConfigItem(
            keyName = "hesitationChance",
            name = "Hesitation chance (%)",
            description = "Chance to add a small extra pause before the next inventory interaction",
            position = 4,
            section = antibanSection
    )
    default int hesitationChance()
    {
        return 4;
    }

    @Range(min = 0, max = 2000)
    @ConfigItem(
            keyName = "hesitationMin",
            name = "Hesitation minimum (ms)",
            description = "Minimum extra hesitation delay",
            position = 5,
            section = antibanSection
    )
    default int hesitationMin()
    {
        return 180;
    }

    @Range(min = 0, max = 2000)
    @ConfigItem(
            keyName = "hesitationMax",
            name = "Hesitation maximum (ms)",
            description = "Maximum extra hesitation delay",
            position = 6,
            section = antibanSection
    )
    default int hesitationMax()
    {
        return 420;
    }

    @ConfigItem(
            keyName = "kspSupportDiscord",
            name = "Support",
            description = "Open the KSP Plugins support Discord.",
            position = 10_000
    )
    default ConfigButton kspSupportDiscord()
    {
        return new ConfigButton();
    }
}
