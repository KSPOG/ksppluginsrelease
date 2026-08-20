package net.runelite.client.plugins.microbot.kspfleshcrawlers;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;

@ConfigGroup(KspFleshCrawlerConfig.GROUP)
public interface KspFleshCrawlerConfig extends Config {
    String GROUP = "kspfleshcrawlers";

    @ConfigSection(
            name = "Training",
            description = "Combat training goals and balancing",
            position = 0,
            closedByDefault = false
    )
    String trainingSection = "training";

    @ConfigSection(
            name = "Loot",
            description = "Looting and bone burying",
            position = 1,
            closedByDefault = false
    )
    String lootSection = "loot";

    @ConfigSection(
            name = "Supplies",
            description = "Healing, potions and optional food restocking",
            position = 2,
            closedByDefault = false
    )
    String suppliesSection = "supplies";

    @ConfigSection(
            name = "Area",
            description = "Flesh Crawler targeting boundaries",
            position = 3,
            closedByDefault = true
    )
    String areaSection = "area";

    // -------------------- Training --------------------

    @ConfigItem(
            keyName = "trainAttack",
            name = "Train Attack",
            description = "Allow the plugin to train Attack",
            position = 0,
            section = trainingSection
    )
    default boolean trainAttack() {
        return true;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
            keyName = "attackTarget",
            name = "Attack target",
            description = "Stop selecting Attack once this real level is reached",
            position = 1,
            section = trainingSection
    )
    default int attackTarget() {
        return 50;
    }

    @ConfigItem(
            keyName = "trainStrength",
            name = "Train Strength",
            description = "Allow the plugin to train Strength",
            position = 2,
            section = trainingSection
    )
    default boolean trainStrength() {
        return true;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
            keyName = "strengthTarget",
            name = "Strength target",
            description = "Stop selecting Strength once this real level is reached",
            position = 3,
            section = trainingSection
    )
    default int strengthTarget() {
        return 50;
    }

    @ConfigItem(
            keyName = "trainDefence",
            name = "Train Defence",
            description = "Allow the plugin to train Defence",
            position = 4,
            section = trainingSection
    )
    default boolean trainDefence() {
        return true;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
            keyName = "defenceTarget",
            name = "Defence target",
            description = "Stop selecting Defence once this real level is reached",
            position = 5,
            section = trainingSection
    )
    default int defenceTarget() {
        return 50;
    }

    @ConfigItem(
            keyName = "balanceCombatLevels",
            name = "Balance combat levels",
            description = "Train the lowest enabled melee level that is still below its target",
            position = 6,
            section = trainingSection
    )
    default boolean balanceCombatLevels() {
        return true;
    }

    @ConfigItem(
            keyName = "avoidControlled",
            name = "Avoid Controlled",
            description = "Do not use Controlled styles for Attack/Strength/Defence training.",
            position = 7,
            section = trainingSection
    )
    default boolean avoidControlled() {
        return true;
    }

    @ConfigItem(
            keyName = "stopAtGoals",
            name = "Stop at goals",
            description = "Stop attacking once every enabled melee target has been reached",
            position = 8,
            section = trainingSection
    )
    default boolean stopAtGoals() {
        return true;
    }

    // -------------------- Loot --------------------

    @ConfigItem(
            keyName = "lootEnabled",
            name = "Loot items",
            description = "Pick up configured Flesh Crawler drops",
            position = 0,
            section = lootSection
    )
    default boolean lootEnabled() {
        return true;
    }

    @ConfigItem(
            keyName = "lootItems",
            name = "Loot list",
            description = "Comma-separated item names. Matching is case-insensitive and exact.",
            position = 1,
            section = lootSection
    )
    default String lootItems() {
        return "Grimy ranarr weed, Grimy avantoe, Grimy kwuarm, Grimy cadantine, Grimy lantadyme, Nature rune, Dust rune, Fire rune, Iron ore, Silver bar, Coins, Bottom of sceptre, Uncut sapphire, Uncut emerald, Uncut ruby, Uncut diamond, Loop half of key, Tooth half of key";
    }

    @Range(min = 1, max = 20)
    @ConfigItem(
            keyName = "lootRadius",
            name = "Loot radius",
            description = "Maximum tile radius used for configured ground-item looting",
            position = 2,
            section = lootSection
    )
    default int lootRadius() {
        return 8;
    }

    @ConfigItem(
            keyName = "buryBones",
            name = "Bury bones",
            description = "Pick up and bury configured bone items when safe to do so",
            position = 3,
            section = lootSection
    )
    default boolean buryBones() {
        return false;
    }

    @ConfigItem(
            keyName = "boneItems",
            name = "Bone list",
            description = "Comma-separated bone names to pick up and bury",
            position = 4,
            section = lootSection
    )
    default String boneItems() {
        return "Bones, Big bones";
    }

    // -------------------- Supplies --------------------

    @ConfigItem(
            keyName = "useHealing",
            name = "Use healing",
            description = "Eat the configured food when enough hitpoints are missing or HP is critically low",
            position = 0,
            section = suppliesSection
    )
    default boolean useHealing() {
        return true;
    }

    @ConfigItem(
            keyName = "foodName",
            name = "Food name",
            description = "Food item to eat and optionally withdraw",
            position = 1,
            section = suppliesSection
    )
    default String foodName() {
        return "Trout";
    }

    @Range(min = 1, max = 28)
    @ConfigItem(
            keyName = "foodAmount",
            name = "Food amount",
            description = "Desired number of food items after an optional bank restock",
            position = 2,
            section = suppliesSection
    )
    default int foodAmount() {
        return 10;
    }

    @Range(min = 1, max = 99)
    @ConfigItem(
            keyName = "unknownFoodHeal",
            name = "Unknown food heal",
            description = "Fallback heal amount when the configured food is not present in Microbot's Rs2Food table",
            position = 3,
            section = suppliesSection
    )
    default int unknownFoodHeal() {
        return 12;
    }

    @ConfigItem(
            keyName = "usePotions",
            name = "Use potions",
            description = "Use available melee combat/Attack/Strength/Defence potions when the corresponding boost has expired",
            position = 4,
            section = suppliesSection
    )
    default boolean usePotions() {
        return true;
    }

    @ConfigItem(
            keyName = "bankForFood",
            name = "Bank for food",
            description = "When out of food, use Rs2Bank/Rs2Walker to bank loot, withdraw Food amount, then return to the saved Flesh Crawler tile",
            position = 5,
            section = suppliesSection
    )
    default boolean bankForFood() {
        return false;
    }

    // -------------------- Area --------------------

    @Range(min = 3, max = 30)
    @ConfigItem(
            keyName = "fightRadius",
            name = "Fight radius",
            description = "Maximum distance from the captured Flesh Crawler fight anchor that targets may be selected",
            position = 0,
            section = areaSection
    )
    default int fightRadius() {
        return 14;
    }

    @ConfigItem(
            keyName = "autoRetaliate",
            name = "Auto retaliate",
            description = "Keep auto-retaliate enabled while fighting Flesh Crawlers",
            position = 1,
            section = areaSection
    )
    default boolean autoRetaliate() {
        return true;
    }
}
