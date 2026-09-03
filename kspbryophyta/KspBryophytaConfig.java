package net.runelite.client.plugins.microbot.kspbryophyta;


import net.runelite.client.plugins.microbot.kspsupport.KspSupportConfig;
import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;

@ConfigGroup("kspbryophyta")
public interface KspBryophytaConfig extends Config, KspMuleConfig, KspSupportConfig {
    @ConfigSection(name = "Strategy", description = "Combat strategy and automatic strategy loadout", position = 0)
    String strategySection = "strategySection";
    @ConfigSection(name = "Banking & restock", description = "Varrock banking, teleport runes and trip supplies", position = 1)
    String bankingSection = "bankingSection";
    @ConfigSection(name = "Survival", description = "Health, prayer and poison handling", position = 2)
    String survivalSection = "survivalSection";
    @ConfigSection(name = "Kills & loot", description = "Post-kill behaviour", position = 3)
    String lootSection = "lootSection";
    @ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
    String muleSection = KspMuleConfig.SECTION;

    @ConfigItem(keyName = "strategy", name = "Strategy", description = "Choose Melee, Ranged or Fire Magic. The plugin equips the matching default/custom side-panel setup automatically", position = 0, section = strategySection)
    default BryophytaStrategy strategy() { return BryophytaStrategy.MELEE; }
    @ConfigItem(keyName = "fireSpell", name = "Fire spell", description = "Spell used when Strategy is Magic - Fire", position = 1, section = strategySection)
    default BryophytaFireSpell fireSpell() { return BryophytaFireSpell.FIRE_BLAST; }
    @Range(min = 3, max = 10)
    @ConfigItem(keyName = "minimumRangeDistance", name = "Distance from Bryophyta", description = "For Ranged/Magic, actively maintain exactly this many tiles between the player and Bryophyta", position = 2, section = strategySection)
    default int minimumRangeDistance() { return 5; }
    @ConfigItem(keyName = "growthlingToolName", name = "Growthling axe", description = "Exact woodcutting axe to bank and carry. Rune axe matches the supplied Melee setup", position = 3, section = strategySection)
    default String growthlingToolName() { return "Rune axe"; }

    @ConfigItem(keyName = "foodName", name = "Food", description = "Exact food name to withdraw at Varrock East bank", position = 0, section = bankingSection)
    default String foodName() { return "Swordfish"; }
    @Range(min = 1, max = 24)
    @ConfigItem(keyName = "foodAmount", name = "Food amount", description = "Food per trip. 21 mirrors the supplied setup while leaving room for teleport runes and loot", position = 1, section = bankingSection)
    default int foodAmount() { return 21; }
    @Range(min = 1, max = 100)
    @ConfigItem(keyName = "varrockTeleportCount", name = "Varrock teleports", description = "Bank enough raw runes for this many Varrock Teleport casts (3 Air + 1 Fire + 1 Law each)", position = 2, section = bankingSection)
    default int varrockTeleportCount() { return 40; }
    @ConfigItem(keyName = "useStrengthPotion", name = "Use Strength potion", description = "For Melee, withdraw Strength potion(4) and drink when the Strength boost has fully expired", position = 3, section = bankingSection)
    default boolean useStrengthPotion() { return true; }
    @Range(min = 1, max = 4)
    @ConfigItem(keyName = "strengthPotionAmount", name = "Strength potions", description = "Number of Strength potion(4) to withdraw per Melee trip", position = 4, section = bankingSection)
    default int strengthPotionAmount() { return 1; }
    @ConfigItem(keyName = "withdrawMossyKey", name = "Bring Mossy key if banked", description = "Withdraw one Mossy key when available. It is optional and will not be treated as a missing required item", position = 5, section = bankingSection)
    default boolean withdrawMossyKey() { return true; }

    @ConfigItem(keyName = "protectFromMagic", name = "Protect from Magic", description = "Maintain Protect from Magic while Bryophyta is active", position = 0, section = survivalSection)
    default boolean protectFromMagic() { return true; }
    @Range(min = 1, max = 99)
    @ConfigItem(keyName = "eatAtPercent", name = "Eat at HP %", description = "Eat when health reaches this percentage or lower", position = 1, section = survivalSection)
    default int eatAtPercent() { return 45; }
    @Range(min = 0, max = 20)
    @ConfigItem(keyName = "teleportAtPrayerPoints", name = "Teleport at Prayer", description = "Varrock teleport and start a bank/altar cycle when Prayer reaches this many points. 0 means when depleted", position = 2, section = survivalSection)
    default int teleportAtPrayerPoints() { return 0; }
    @Range(min = 0, max = 10)
    @ConfigItem(keyName = "teleportAtFoodCount", name = "Teleport at food", description = "Varrock teleport and restock when remaining food reaches this amount", position = 3, section = survivalSection)
    default int teleportAtFoodCount() { return 0; }
    @ConfigItem(keyName = "maintainPoisonProtection", name = "Handle poison", description = "Use Microbot's anti-poison handling when poison protection is needed", position = 4, section = survivalSection)
    default boolean maintainPoisonProtection() { return true; }

    @ConfigItem(keyName = "autoEnterLair", name = "Auto navigate/enter", description = "After bank + altar, navigate through the Varrock Sewers and enter Bryophyta's lair", position = 0, section = lootSection)
    default boolean autoEnterLair() { return true; }
    @ConfigItem(keyName = "lootBossDrops", name = "Loot boss drops", description = "Loot Mossy keys, clue scrolls, ensouled giant heads and items above the configured value", position = 1, section = lootSection)
    default boolean lootBossDrops() { return true; }
    @Range(min = 0, max = 10000000)
    @ConfigItem(keyName = "lootValueThreshold", name = "Loot value", description = "Minimum GE value for generic boss-drop looting. 0 disables value-based looting", position = 2, section = lootSection)
    default int lootValueThreshold() { return 5000; }
    @ConfigItem(keyName = "openRewardChest", name = "Open reward chest", description = "After a kill, attempt Bryophyta's chest", position = 3, section = lootSection)
    default boolean openRewardChest() { return true; }
    @ConfigItem(keyName = "shutdownAfterDeath", name = "Shutdown after death", description = "Stop the script if the player dies", position = 4, section = lootSection)
    default boolean shutdownAfterDeath() { return true; }
}
