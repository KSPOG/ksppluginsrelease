package net.runelite.client.plugins.microbot.kspf2pgatheringprofit;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.Range;

@ConfigGroup(KspF2pGatheringProfitConfig.GROUP)
public interface KspF2pGatheringProfitConfig extends Config
{
    String GROUP = "ksp-f2p-gathering-profit";

    enum Objective { MAX_GP, MAX_XP, BALANCED, GP_PRIORITY, XP_PRIORITY }
    enum SkillMode { AUTO, MINING, WOODCUTTING, FISHING }
    enum MiningTarget { AUTO, IRON, COAL, MITHRIL, ADAMANTITE, RUNITE }
    enum WoodcuttingTarget { AUTO, OAK, WILLOW, YEW }
    enum FishingTarget { AUTO, TROUT_SALMON, LOBSTER, TUNA_SWORDFISH }

    @ConfigItem(keyName = "skillMode", name = "Skill", description = "AUTO can choose between supported F2P gathering skills.", position = 0)
    default SkillMode skillMode() { return SkillMode.AUTO; }

    @ConfigItem(keyName = "objective", name = "Objective", description = "How GP and XP are prioritized.", position = 1)
    default Objective objective() { return Objective.BALANCED; }

    @ConfigItem(keyName = "gpWeight", name = "GP weight", description = "GP weighting for Balanced mode.", position = 2)
    @Range(min = 0, max = 100)
    default int gpWeight() { return 60; }

    @ConfigItem(keyName = "xpWeight", name = "XP weight", description = "XP weighting for Balanced mode.", position = 3)
    @Range(min = 0, max = 100)
    default int xpWeight() { return 40; }

    @ConfigItem(keyName = "reevaluateMinutes", name = "Re-evaluate minutes", description = "Refresh market target periodically.", position = 4)
    @Range(min = 1, max = 120)
    default int reevaluateMinutes() { return 10; }

    @ConfigItem(keyName = "miningTarget", name = "Mining target", description = "Mining target or AUTO.", position = 5)
    default MiningTarget miningTarget() { return MiningTarget.AUTO; }

    @ConfigItem(keyName = "woodcuttingTarget", name = "Woodcutting target", description = "Woodcutting target or AUTO.", position = 6)
    default WoodcuttingTarget woodcuttingTarget() { return WoodcuttingTarget.AUTO; }

    @ConfigItem(keyName = "fishingTarget", name = "Fishing target", description = "Fishing target or AUTO.", position = 7)
    default FishingTarget fishingTarget() { return FishingTarget.AUTO; }

    @ConfigItem(keyName = "bankWhenFull", name = "Bank when full", description = "Bank resources rather than drop them.", position = 8)
    default boolean bankWhenFull() { return true; }

    @ConfigItem(keyName = "stopIfMembersWorld", name = "F2P world only", description = "Stop on a members world.", position = 9)
    default boolean stopIfMembersWorld() { return true; }

    @ConfigItem(keyName = "avoidWilderness", name = "Avoid Wilderness", description = "Exclude Wilderness Runite from AUTO.", position = 10)
    default boolean avoidWilderness() { return true; }

    @ConfigItem(keyName = "failureLimit", name = "Failure limit", description = "Consecutive failures before shutdown.", position = 11)
    @Range(min = 2, max = 20)
    default int failureLimit() { return 5; }

    @ConfigItem(keyName = "progressive", name = "Progressive skilling", description = "Automatically upgrade targets as levels unlock.", position = 12)
    default boolean progressive() { return true; }

    @ConfigItem(keyName = "autoLocation", name = "Automatic location", description = "Choose the closest supported location for the selected method.", position = 13)
    default boolean autoLocation() { return true; }

    @ConfigItem(keyName = "worldHop", name = "World hopping", description = "Hop F2P worlds when competition or depleted resources persist.", position = 14)
    default boolean worldHop() { return true; }

    @ConfigItem(keyName = "competitionSeconds", name = "Competition timeout", description = "Seconds without a usable resource before considering a hop.", position = 15)
    @Range(min = 10, max = 180)
    default int competitionSeconds() { return 35; }

    @ConfigItem(keyName = "respawnGraceSeconds", name = "Respawn grace", description = "Wait this long for depleted mining nodes before hopping.", position = 16)
    @Range(min = 5, max = 120)
    default int respawnGraceSeconds() { return 20; }

    @ConfigItem(keyName = "playerCompetitionRadius", name = "Competition radius", description = "Nearby player radius used by competition detection.", position = 17)
    @Range(min = 2, max = 20)
    default int playerCompetitionRadius() { return 7; }

    @ConfigItem(keyName = "wildernessThreatRadius", name = "Wilderness threat radius", description = "Player radius that triggers Runite escape state.", position = 18)
    @Range(min = 4, max = 30)
    default int wildernessThreatRadius() { return 14; }

    @ConfigItem(keyName = "wildernessEscapeHp", name = "Emergency HP %", description = "Runite escape triggers below this HP percentage.", position = 19)
    @Range(min = 10, max = 100)
    default int wildernessEscapeHp() { return 65; }
}
