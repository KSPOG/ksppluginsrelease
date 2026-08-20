package net.runelite.client.plugins.microbot.kspfleshcrawlers;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(KspFleshCrawlerConfig.GROUP)
public interface KspFleshCrawlerConfig extends Config {
    String GROUP = "kspfleshcrawlers";

    @ConfigSection(name = "Training", description = "Combat goals and melee balancing", position = 0, closedByDefault = false)
    String trainingSection = "training";

    @ConfigSection(name = "Loot", description = "Ground-item looting and bones", position = 1, closedByDefault = false)
    String lootSection = "loot";

    @ConfigSection(name = "Supplies", description = "Food, healing, potions and banking", position = 2, closedByDefault = false)
    String suppliesSection = "supplies";

    @ConfigSection(name = "Navigation", description = "Stronghold travel controls", position = 3, closedByDefault = true)
    String navigationSection = "navigation";

    @ConfigItem(keyName = "trainAttack", name = "Train Attack", description = "Allow Attack training", position = 0, section = trainingSection)
    default boolean trainAttack() { return true; }

    @Range(min = 1, max = 99)
    @ConfigItem(keyName = "attackTarget", name = "Attack target", description = "Attack goal", position = 1, section = trainingSection)
    default int attackTarget() { return 50; }

    @ConfigItem(keyName = "trainStrength", name = "Train Strength", description = "Allow Strength training", position = 2, section = trainingSection)
    default boolean trainStrength() { return true; }

    @Range(min = 1, max = 99)
    @ConfigItem(keyName = "strengthTarget", name = "Strength target", description = "Strength goal", position = 3, section = trainingSection)
    default int strengthTarget() { return 50; }

    @ConfigItem(keyName = "trainDefence", name = "Train Defence", description = "Allow Defence training", position = 4, section = trainingSection)
    default boolean trainDefence() { return true; }

    @Range(min = 1, max = 99)
    @ConfigItem(keyName = "defenceTarget", name = "Defence target", description = "Defence goal", position = 5, section = trainingSection)
    default int defenceTarget() { return 50; }

    @ConfigItem(keyName = "balanceCombatLevels", name = "Balance combat levels", description = "Train the lowest enabled melee skill that remains below its goal", position = 6, section = trainingSection)
    default boolean balanceCombatLevels() { return true; }

    @ConfigItem(keyName = "avoidControlled", name = "Avoid Controlled", description = "Avoid Controlled weapon styles", position = 7, section = trainingSection)
    default boolean avoidControlled() { return true; }

    @ConfigItem(keyName = "stopAtGoals", name = "Stop at goals", description = "Stop attacking once all enabled goals are reached", position = 8, section = trainingSection)
    default boolean stopAtGoals() { return true; }

    @ConfigItem(keyName = "lootEnabled", name = "Loot items", description = "Loot items from the configured list", position = 0, section = lootSection)
    default boolean lootEnabled() { return true; }

    @ConfigItem(keyName = "lootOwnDrops", name = "Loot own drops", description = "Loot every ground item owned by the local player", position = 1, section = lootSection)
    default boolean lootOwnDrops() { return true; }

    @ConfigItem(keyName = "lootItems", name = "Loot list", description = "Comma-separated exact item names", position = 2, section = lootSection)
    default String lootItems() {
        return "Grimy ranarr weed, Grimy avantoe, Grimy kwuarm, Grimy cadantine, Grimy lantadyme, Nature rune, Dust rune, Fire rune, Iron ore, Silver bar, Coins, Bottom of sceptre, Uncut sapphire, Uncut emerald, Uncut ruby, Uncut diamond, Loop half of key, Tooth half of key";
    }

    @Range(min = 1, max = 20)
    @ConfigItem(keyName = "lootRadius", name = "Loot radius", description = "Ground-item search radius", position = 3, section = lootSection)
    default int lootRadius() { return 8; }

    @ConfigItem(keyName = "buryBones", name = "Bury bones", description = "Pick up and bury configured bones when out of combat", position = 4, section = lootSection)
    default boolean buryBones() { return false; }

    @ConfigItem(keyName = "boneItems", name = "Bone list", description = "Comma-separated bone item names", position = 5, section = lootSection)
    default String boneItems() { return "Bones, Big bones"; }

    @ConfigItem(keyName = "useHealing", name = "Use healing", description = "Eat the configured food at the HP threshold", position = 0, section = suppliesSection)
    default boolean useHealing() { return true; }

    @ConfigItem(keyName = "foodName", name = "Food name", description = "Food to eat and withdraw", position = 1, section = suppliesSection)
    default String foodName() { return "Trout"; }

    @Range(min = 1, max = 28)
    @ConfigItem(keyName = "foodAmount", name = "Food amount", description = "Food amount to withdraw when banking", position = 2, section = suppliesSection)
    default int foodAmount() { return 10; }

    @Range(min = 1, max = 99)
    @ConfigItem(keyName = "unknownFoodHeal", name = "Unknown food heal", description = "Fallback heal value if the food is not in Rs2Food", position = 3, section = suppliesSection)
    default int unknownFoodHeal() { return 12; }

    @Range(min = 2, max = 99)
    @ConfigItem(keyName = "healAtHp", name = "Heal at HP", description = "Eat at or below this HP. Flesh Crawlers have a max hit of 1, so low values are efficient.", position = 4, section = suppliesSection)
    default int healAtHp() { return 6; }

    @ConfigItem(keyName = "usePotions", name = "Use potions", description = "Use available melee boosting potions", position = 5, section = suppliesSection)
    default boolean usePotions() { return true; }

    @ConfigItem(keyName = "bankForFood", name = "Bank for food", description = "When food is depleted, use the floor-2 rope shortcut, exit the Stronghold, bank, then return", position = 6, section = suppliesSection)
    default boolean bankForFood() { return true; }

    @ConfigItem(keyName = "autoTravel", name = "Auto travel", description = "Automatically travel between the bank and the confirmed Flesh Crawler room", position = 0, section = navigationSection)
    default boolean autoTravel() { return true; }

    @ConfigItem(keyName = "useWarPortal", name = "Use War portal", description = "Use the Vault of War portal from floor 1 whenever available. This remains the deterministic floor-1 transport.", position = 1, section = navigationSection)
    default boolean useWarPortal() { return true; }

    @ConfigItem(keyName = "useWebWalker", name = "Use WebWalker", description = "Use Microbot WebWalker for known reachable room/corridor segments. Door crossings, portals, ropes and ladders stay deterministic so the walker cannot loop the wrong door.", position = 2, section = navigationSection)
    default boolean useWebWalker() { return true; }

    @Range(min = 3, max = 20)
    @ConfigItem(keyName = "fightRadius", name = "Fight radius", description = "Maximum target distance from 2040,5188", position = 3, section = navigationSection)
    default int fightRadius() { return 12; }

    @ConfigItem(keyName = "autoRetaliate", name = "Auto retaliate", description = "Keep auto-retaliate enabled", position = 4, section = navigationSection)
    default boolean autoRetaliate() { return true; }
}
