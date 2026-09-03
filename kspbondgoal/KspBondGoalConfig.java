package net.runelite.client.plugins.microbot.kspbondgoal;



import net.runelite.client.config.ConfigButton;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(KspBondGoalConfig.GROUP)
public interface KspBondGoalConfig extends Config
{
    String GROUP = "kspbondgoal";

    @ConfigItem(
        keyName = "extraCoins",
        name = "Extra coins after bond",
        description = "Extra GP to keep after reaching the live bond-price target.",
        position = 0
    )
    @Range(min = 0, max = 100_000_000)
    default int extraCoins()
    {
        return 1_000_000;
    }

    @ConfigItem(
        keyName = "bondPriceOverride",
        name = "Bond price override",
        description = "0 = use RuneLite's live/cached GE price for item 13190. Set a GP value to override it.",
        position = 1
    )
    @Range(min = 0, max = 100_000_000)
    default int bondPriceOverride()
    {
        return 0;
    }

    @ConfigItem(
        keyName = "includeBankCoins",
        name = "Include banked coins",
        description = "Include the last known bank coin stack. Open the bank once after login to populate the cache.",
        position = 2
    )
    default boolean includeBankCoins()
    {
        return true;
    }

    @ConfigItem(
        keyName = "saleRealizationPercent",
        name = "Sale realization %",
        description = "Percentage of displayed GE price assumed to be realized when selling outputs. This is a planning buffer for tax/slippage.",
        position = 3
    )
    @Range(min = 50, max = 100)
    default int saleRealizationPercent()
    {
        return 98;
    }

    @ConfigItem(
        keyName = "activityEfficiencyPercent",
        name = "Activity efficiency %",
        description = "Scales the advisor's baseline units/hour assumptions. 85 means 85% of the baseline throughput.",
        position = 4
    )
    @Range(min = 25, max = 125)
    default int activityEfficiencyPercent()
    {
        return 85;
    }

    @ConfigItem(
        keyName = "showAlternatives",
        name = "Show alternatives",
        description = "Show the next two eligible money-making activities below the best current advisor pick.",
        position = 5
    )
    default boolean showAlternatives()
    {
        return true;
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
