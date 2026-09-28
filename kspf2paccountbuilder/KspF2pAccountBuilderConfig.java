package net.runelite.client.plugins.microbot.kspf2paccountbuilder;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(KspF2pAccountBuilderConfig.GROUP)
@ConfigInformation(
        "<html><h2>KSP F2P Account Builder</h2>"
                + "<p>Builds a fresh F2P account by coordinating skilling, questing, restocking, upgrades and the Grand Exchange.</p>"
                + "<p>New-account trade restrictions are tracked from persistent account playtime, quest points and total level.</p>"
                + "</html>")
public interface KspF2pAccountBuilderConfig extends Config
{
    String GROUP = "ksp-f2p-account-builder";

    enum GoalProfile
    {
        TRADE_UNLOCK,
        ALL_SOLO_F2P_QUESTS,
        CONTINUOUS
    }

    @ConfigSection(name = "Progression", description = "Account-building goals and automation", position = 0)
    String progressionSection = "progression";

    @ConfigSection(name = "Economy", description = "Grand Exchange, restocking and upgrades", position = 1)
    String economySection = "economy";

    @ConfigSection(name = "Safety", description = "Account-builder safety controls", position = 2)
    String safetySection = "safety";

    @ConfigSection(name = "Overlay", description = "Account-builder overlay", position = 3)
    String overlaySection = "overlay";

    @ConfigItem(
            keyName = "goalProfile",
            name = "Goal",
            description = "TRADE_UNLOCK builds toward unrestricted F2P GE selling. ALL_SOLO_F2P_QUESTS continues through supported solo F2P quests.",
            position = 0,
            section = progressionSection)
    default GoalProfile goalProfile()
    {
        return GoalProfile.TRADE_UNLOCK;
    }

    @ConfigItem(
            keyName = "autoQuest",
            name = "Automatic questing",
            description = "Automatically select and run supported F2P quests through Microbot Quest Helper.",
            position = 1,
            section = progressionSection)
    default boolean autoQuest()
    {
        return true;
    }

    @ConfigItem(
            keyName = "autoSkill",
            name = "Automatic skilling",
            description = "Automatically rotate F2P skilling modules to satisfy total-level and quest prerequisites.",
            position = 2,
            section = progressionSection)
    default boolean autoSkill()
    {
        return true;
    }

    @ConfigItem(
            keyName = "stopAtGoal",
            name = "Stop at goal",
            description = "Stop the builder after the selected finite goal is complete.",
            position = 3,
            section = progressionSection)
    default boolean stopAtGoal()
    {
        return true;
    }

    @ConfigItem(
            keyName = "autoSellOutputs",
            name = "Sell outputs",
            description = "Automatically sell whitelisted skilling outputs. Trade-restricted outputs are never sold before the restriction is removed.",
            position = 0,
            section = economySection)
    default boolean autoSellOutputs()
    {
        return true;
    }

    @ConfigItem(
            keyName = "sellBeforeTradeUnlock",
            name = "Sell unrestricted early",
            description = "Allow known unrestricted outputs such as normal logs to be sold before the new-account trade restriction is removed.",
            position = 1,
            section = economySection)
    default boolean sellBeforeTradeUnlock()
    {
        return true;
    }

    @ConfigItem(
            keyName = "autoBuyUpgrades",
            name = "Buy upgrades",
            description = "Automatically buy better F2P axes and pickaxes when the level and cash reserve allow it.",
            position = 2,
            section = economySection)
    default boolean autoBuyUpgrades()
    {
        return true;
    }

    @ConfigItem(
            keyName = "autoRestock",
            name = "Restock inputs",
            description = "Automatically buy missing skilling inputs such as fishing bait and feathers.",
            position = 3,
            section = economySection)
    default boolean autoRestock()
    {
        return true;
    }

    @Range(min = 0, max = 1000000)
    @ConfigItem(
            keyName = "minimumQuestCash",
            name = "Quest cash target",
            description = "Before questing, build at least this much liquid GP so Quest Helper can purchase missing quest items.",
            position = 4,
            section = economySection)
    default int minimumQuestCash()
    {
        return 5000;
    }

    @Range(min = 0, max = 1000000)
    @ConfigItem(
            keyName = "cashReserve",
            name = "Cash reserve",
            description = "Do not spend below this reserve on optional skilling upgrades.",
            position = 5,
            section = economySection)
    default int cashReserve()
    {
        return 1500;
    }

    @Range(min = 1, max = 60)
    @ConfigItem(
            keyName = "economyIntervalMinutes",
            name = "Economy interval",
            description = "Minutes between normal sell/restock/upgrade passes.",
            position = 6,
            section = economySection)
    default int economyIntervalMinutes()
    {
        return 5;
    }

    @Range(min = 5, max = 120)
    @ConfigItem(
            keyName = "geWaitSeconds",
            name = "GE wait seconds",
            description = "Maximum wait for a fast builder GE sell before returning control to progression.",
            position = 7,
            section = economySection)
    default int geWaitSeconds()
    {
        return 25;
    }

    @ConfigItem(
            keyName = "stopIfMembersWorld",
            name = "F2P world only",
            description = "Pause progression when logged into a members world.",
            position = 0,
            section = safetySection)
    default boolean stopIfMembersWorld()
    {
        return true;
    }

    @ConfigItem(
            keyName = "skipPartnerQuests",
            name = "Skip partner quests",
            description = "Skip quests that require another player, such as Shield of Arrav.",
            position = 1,
            section = safetySection)
    default boolean skipPartnerQuests()
    {
        return true;
    }

    @ConfigItem(
            keyName = "showOverlay",
            name = "Show overlay",
            description = "Show account-build progress and trade-restriction gates.",
            position = 0,
            section = overlaySection)
    default boolean showOverlay()
    {
        return true;
    }
}
