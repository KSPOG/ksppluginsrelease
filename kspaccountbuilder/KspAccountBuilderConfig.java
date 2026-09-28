package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(KspAccountBuilderConfig.CONFIG_GROUP)
@ConfigInformation(
        "<b>Before starting the plugin:</b><br />" +
                "Make sure you either have about 50K GP In inventory or Requiered<br />" +
                "Tools i.e Pickaxe, Axe, Hammer in bank<br />" +
                "start next to a bank and let it do its thing."
)
public interface KspAccountBuilderConfig extends Config
{
    String CONFIG_GROUP = "kspaccountbuilder";

    @ConfigSection(
            name = "Antiban",
            description = "General antiban controls",
            position = 0
    )
    String antibanSection = "antiban";

    @ConfigSection(
            name = "Break Handler",
            description = "Randomized logout break scheduling",
            position = 1
    )
    String breakHandlerSection = "breakHandler";

    @ConfigSection(
            name = "Activity Switch",
            description = "Randomized activity switch timing",
            position = 2
    )
    String activitySwitchSection = "activitySwitch";

    @ConfigSection(
            name = "Train Single Skill",
            description = "Train one selected skill instead of randomly switching activities",
            position = 3
    )
    String trainSingleSkillSection = "trainSingleSkill";

    @ConfigSection(
            name = "Run Single Quest",
            description = "Run one selected quest instead of training skills or switching activities",
            position = 4
    )
    String runSingleQuestSection = "runSingleQuest";

    @ConfigItem(
            keyName = "useAntiban",
            name = "Use Antiban",
            description = "Enable antiban behavior during account builder actions",
            position = 0,
            section = antibanSection
    )
    default boolean useAntiban()
    {
        return true;
    }


    @ConfigItem(
            keyName = "debugLogging",
            name = "Debug Logging",
            description = "Show debug messages in chat/logs",
            position = 1,
            section = antibanSection
    )
    default boolean debugLogging()
    {
        return false;
    }

    @ConfigItem(
            keyName = "doBreaks",
            name = "Do Breaks",
            description = "If enabled, the script will perform randomized logout breaks",
            position = 0,
            section = breakHandlerSection
    )
    default boolean doBreaks()
    {
        return true;
    }

    @Range(min = 5, max = 300)
    @ConfigItem(
            keyName = "breakAfterMinMinutes",
            name = "Break After Min (min)",
            description = "Minimum minutes of runtime before scheduling a break",
            position = 1,
            section = breakHandlerSection
    )
    default int breakAfterMinMinutes()
    {
        return 45;
    }

    @Range(min = 5, max = 300)
    @ConfigItem(
            keyName = "breakAfterMaxMinutes",
            name = "Break After Max (min)",
            description = "Maximum minutes of runtime before scheduling a break",
            position = 2,
            section = breakHandlerSection
    )
    default int breakAfterMaxMinutes()
    {
        return 90;
    }

    @Range(min = 1, max = 180)
    @ConfigItem(
            keyName = "breakDurationMinMinutes",
            name = "Break Duration Min (min)",
            description = "Minimum break duration in minutes",
            position = 3,
            section = breakHandlerSection
    )
    default int breakDurationMinMinutes()
    {
        return 5;
    }

    @Range(min = 1, max = 180)
    @ConfigItem(
            keyName = "breakDurationMaxMinutes",
            name = "Break Duration Max (min)",
            description = "Maximum break duration in minutes",
            position = 4,
            section = breakHandlerSection
    )
    default int breakDurationMaxMinutes()
    {
        return 15;
    }

    @ConfigItem(
            keyName = "enableActivitySwitchRandomization",
            name = "Randomize Activity Switch",
            description = "Enable randomized timing before switching activities. Ignored while a single skill or quest is selected.",
            position = 0,
            section = activitySwitchSection
    )
    default boolean enableActivitySwitchRandomization()
    {
        return true;
    }

    @Range(min = 5, max = 240)
    @ConfigItem(
            keyName = "activitySwitchMinMinutes",
            name = "Switch Activity Min (min)",
            description = "Minimum minutes before switching activity",
            position = 1,
            section = activitySwitchSection
    )
    default int activitySwitchMinMinutes()
    {
        return 20;
    }

    @Range(min = 5, max = 240)
    @ConfigItem(
            keyName = "activitySwitchMaxMinutes",
            name = "Switch Activity Max (min)",
            description = "Maximum minutes before switching activity",
            position = 2,
            section = activitySwitchSection
    )
    default int activitySwitchMaxMinutes()
    {
        return 45;
    }

    @ConfigItem(
            keyName = "trainSingleSkill",
            name = "Train Single Skill",
            description = "Force the account builder to train only the selected task",
            position = 0,
            section = trainSingleSkillSection
    )
    default boolean trainSingleSkill()
    {
        return false;
    }

    @ConfigItem(
            keyName = "singleSkillTask",
            name = "Skill",
            description = "Skill to train while Train Single Skill is enabled",
            position = 1,
            section = trainSingleSkillSection
    )
    default KspTrainSingleSkillTask singleSkillTask()
    {
        return KspTrainSingleSkillTask.MINING;
    }

    @ConfigItem(
            keyName = "singleSkillMiningTarget",
            name = "Mining",
            description = "Ore and mining location",
            position = 2,
            section = trainSingleSkillSection
    )
    default KspSingleSkillConfigTarget.Mining singleSkillMiningTarget()
    {
        return KspSingleSkillConfigTarget.Mining.NONE;
    }

    @ConfigItem(
            keyName = "singleSkillWoodcuttingTarget",
            name = "Woodcutting",
            description = "Tree type and woodcutting location",
            position = 3,
            section = trainSingleSkillSection
    )
    default KspSingleSkillConfigTarget.Woodcutting singleSkillWoodcuttingTarget()
    {
        return KspSingleSkillConfigTarget.Woodcutting.NONE;
    }

    @ConfigItem(
            keyName = "singleSkillFishingTarget",
            name = "Fishing",
            description = "Fish and fishing location",
            position = 4,
            section = trainSingleSkillSection
    )
    default KspSingleSkillConfigTarget.Fishing singleSkillFishingTarget()
    {
        return KspSingleSkillConfigTarget.Fishing.NONE;
    }

    @ConfigItem(
            keyName = "singleSkillCookingTarget",
            name = "Cooking",
            description = "Cooking location",
            position = 5,
            section = trainSingleSkillSection
    )
    default KspSingleSkillConfigTarget.Cooking singleSkillCookingTarget()
    {
        return KspSingleSkillConfigTarget.Cooking.NONE;
    }

    @ConfigItem(
            keyName = "singleSkillCraftingTarget",
            name = "Crafting",
            description = "Crafting recipe",
            position = 6,
            section = trainSingleSkillSection
    )
    default KspSingleSkillConfigTarget.Crafting singleSkillCraftingTarget()
    {
        return KspSingleSkillConfigTarget.Crafting.NONE;
    }

    @ConfigItem(
            keyName = "singleSkillMeleeTarget",
            name = "Melee",
            description = "NPC and combat location",
            position = 7,
            section = trainSingleSkillSection
    )
    default KspSingleSkillConfigTarget.Melee singleSkillMeleeTarget()
    {
        return KspSingleSkillConfigTarget.Melee.NONE;
    }

    @ConfigItem(
            keyName = "singleSkillSmithingTarget",
            name = "Smithing",
            description = "Product to smith",
            position = 8,
            section = trainSingleSkillSection
    )
    default KspSingleSkillConfigTarget.Smithing singleSkillSmithingTarget()
    {
        return KspSingleSkillConfigTarget.Smithing.NONE;
    }

    @ConfigItem(
            keyName = "singleSkillSmeltingTarget",
            name = "Smelting",
            description = "Bar to smelt",
            position = 9,
            section = trainSingleSkillSection
    )
    default KspSingleSkillConfigTarget.Smelting singleSkillSmeltingTarget()
    {
        return KspSingleSkillConfigTarget.Smelting.NONE;
    }

    @ConfigItem(
            keyName = "runSingleQuest",
            name = "Run Single Quest",
            description = "Force the account builder to run only the selected quest. Takes priority over Train Single Skill.",
            position = 0,
            section = runSingleQuestSection
    )
    default boolean runSingleQuest()
    {
        return false;
    }

    @ConfigItem(
            keyName = "singleQuestTask",
            name = "Quest",
            description = "Quest to run while Run Single Quest is enabled",
            position = 1,
            section = runSingleQuestSection
    )
    default KspQuestTask singleQuestTask()
    {
        return KspQuestTask.COOKS_ASSISTANT;
    }
}
