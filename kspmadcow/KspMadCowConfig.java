package net.runelite.client.plugins.microbot.kspmadcow;


import net.runelite.client.plugins.microbot.kspsupport.KspSupportConfig;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;

@ConfigGroup(KspMadCowConfig.GROUP)
public interface KspMadCowConfig extends Config, KspMuleConfig, KspSupportConfig {
    String GROUP = "kspmadcow";

    @ConfigSection(name = "Supplies", description = "Food, Cowbell and boosting-potion settings", position = 0)
    String suppliesSection = "supplies";
    @ConfigSection(name = "Combat", description = "Combat training, prayers and Brutus mechanics", position = 1)
    String combatSection = "combat";
    @ConfigSection(name = "Looting", description = "Loot and banking behaviour", position = 2)
    String lootSection = "loot";
    @ConfigSection(name = "Travel", description = "Cowbell, altar and instance behaviour", position = 3)
    String travelSection = "travel";
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;

    @ConfigItem(keyName = "food", name = "Food", description = "Food that must be present in the inventory", position = 0, section = suppliesSection)
    default Rs2Food food() { return Rs2Food.LOBSTER; }
    @ConfigItem(keyName = "foodAmount", name = "Food amount", description = "Target number of configured food items to withdraw", position = 1, section = suppliesSection)
    @Range(min = 1, max = 25) default int foodAmount() { return 20; }
    @ConfigItem(keyName = "eatAtPercent", name = "Eat at HP %", description = "Eat the configured food at or below this hitpoints percentage, including during combat", position = 2, section = suppliesSection)
    @Range(min = 10, max = 90) default int eatAtPercent() { return 45; }
    @ConfigItem(keyName = "airRunesToCarry", name = "Air runes", description = "Target bank withdrawal; every air rune present in inventory is added to the Cowbell", position = 3, section = suppliesSection)
    @Range(min = 1, max = 1000) default int airRunesToCarry() { return 100; }
    @ConfigItem(keyName = "useStatBoostingPotions", name = "Use stat-boosting potions", description = "Withdraw and drink suitable melee, ranged or magic boosting potions when available", position = 4, section = suppliesSection)
    default boolean useStatBoostingPotions() { return false; }

    @ConfigItem(keyName = "demonicBrutus", name = "Demonic Brutus", description = "Fight the Desert Treasure II hard-mode variant. One Abyssal potato is withdrawn and fed to Brutus for every attempt", position = 0, section = combatSection)
    default boolean demonicBrutus() { return false; }
    @ConfigItem(keyName = "balanceCombatStats", name = "Balance melee stats", description = "When enabled, train the lowest of Attack, Strength and Defence; when disabled, keep the current style", position = 1, section = combatSection)
    default boolean balanceCombatStats() { return true; }
    @ConfigItem(keyName = "combatSpell", name = "Spell", description = "Spell to autocast when the equipped gear is detected as Magic", position = 2, section = combatSection)
    default KspMadCowSpell combatSpell() { return KspMadCowSpell.FIRE_STRIKE; }
    @ConfigItem(keyName = "equipMooleta", name = "Equip Mooleta", description = "Equip a Mooleta from inventory or bank while training melee", position = 3, section = combatSection)
    default boolean equipMooleta() { return true; }
    @ConfigItem(keyName = "dodgeSpecials", name = "Dodge specials", description = "Use growl/snort cues and canvas-only tile movement to dodge Charge and Stomp without minimap walking", position = 4, section = combatSection)
    default boolean dodgeSpecials() { return true; }
    @ConfigItem(keyName = "autoRetaliate", name = "Enable auto-retaliate", description = "Enable auto-retaliate while the script is active", position = 5, section = combatSection)
    default boolean autoRetaliate() { return true; }
    @ConfigItem(keyName = "usePrayer", name = "Use Prayer", description = "Master Prayer toggle. When disabled, do not activate combat prayers or route to an altar to restore Prayer", position = 6, section = combatSection)
    default boolean usePrayer() { return true; }
    @ConfigItem(keyName = "useCombatPrayers", name = "Use combat prayers", description = "Automatically use the configured melee, ranged or magic prayer tier while fighting Brutus. Requires Use Prayer", position = 7, section = combatSection)
    default boolean useCombatPrayers() { return true; }
    @ConfigItem(keyName = "speed", name = "Speed", description = "After normal Brutus dies, use the Cowbell amulet's Ring option immediately to request the faster respawn", position = 8, section = combatSection)
    default boolean speed() { return false; }
    @ConfigItem(keyName = "debugLogging", name = "Debug logging", description = "Write Brutus special, movement, combat, healing, potion, altar and banking decisions to the Microbot log", position = 9, section = combatSection)
    default boolean debugLogging() { return true; }

    @ConfigItem(keyName = "lootAll", name = "Enable looting", description = "Enable non-bone ground-item looting. If Specific loot items is filled in, only those item names are taken", position = 0, section = lootSection)
    default boolean lootAll() { return true; }
    @ConfigItem(keyName = "specificLootItems", name = "Specific loot items", description = "Optional loot whitelist. Enter exact item names separated by commas, semicolons or new lines. Leave blank to loot every non-bone item", position = 1, section = lootSection)
    default String specificLootItems() { return ""; }
    @ConfigItem(keyName = "buryBones", name = "Bury bones", description = "Bury Brutus's ground bones and any bones in the inventory", position = 2, section = lootSection)
    default boolean buryBones() { return true; }
    @ConfigItem(keyName = "lootRadius", name = "Loot radius", description = "Maximum distance from the player for loot interactions", position = 3, section = lootSection)
    @Range(min = 2, max = 20) default int lootRadius() { return 12; }
    @ConfigItem(keyName = "bankWhenFull", name = "Banking", description = "Master banking toggle. When disabled, never cast Minigame Teleport, use the Lumbridge banking fallback, or leave Brutus to restock", position = 4, section = lootSection)
    default boolean bankWhenFull() { return true; }

    @ConfigItem(keyName = "autoConfirmEntry", name = "Confirm entry warning", description = "Select Yes and don't ask again when the Brutus warning is shown", position = 0, section = travelSection)
    default boolean autoConfirmEntry() { return true; }
    @ConfigItem(keyName = "autoRun", name = "Enable run", description = "Allow Microbot to automatically enable run energy", position = 1, section = travelSection)
    default boolean autoRun() { return true; }
    @ConfigItem(keyName = "restorePrayerBeforeTravel", name = "Restore Prayer before travel", description = "When Prayer points are zero, visit the Lumbridge altar before using the Cowbell", position = 2, section = travelSection)
    default boolean restorePrayerBeforeTravel() { return true; }
    @ConfigItem(keyName = "shutdownOnMissingSupplies", name = "Stop if supplies missing", description = "Disable the plugin when the bank lacks food, a Cowbell, or required air runes", position = 3, section = travelSection)
    default boolean shutdownOnMissingSupplies() { return true; }
}
