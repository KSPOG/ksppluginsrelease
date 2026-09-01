package net.runelite.client.plugins.microbot.kspaiofighter;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigInformation;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;
import net.runelite.client.config.Range;
import net.runelite.client.plugins.microbot.kspmule.KspMuleConfig;
import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;

@ConfigGroup(KspAioFighterConfig.GROUP)
@ConfigInformation("Configure combat, training, supplies and loot here. Equipment loadouts and attack areas are managed from the AIO Fighter side panel.")
public interface KspAioFighterConfig extends Config, KspMuleConfig
{
	String GROUP = "kspaiofighter";

	@ConfigSection(name = "Combat", description = "NPC and combat settings", position = 0)
	String combatSection = "combat";

	@ConfigSection(name = "Area", description = "Safe spot settings. Attack areas are selected from the AIO Fighter side-panel map.", position = 1)
	String areaSection = "area";

	@ConfigSection(name = "Training", description = "Combat skill targets", position = 2)
	String trainingSection = "training";

	@ConfigSection(name = "Supplies", description = "Healing and potion settings", position = 4)
	String suppliesSection = "supplies";

	@ConfigSection(name = "Loot", description = "Looting, burying, and alching", position = 5)
	String lootSection = "loot";

	@ConfigSection(name = "Paint", description = "RuneScape-style fighter paint shown over the chatbox", position = 6)
	String paintSection = "paint";

	@ConfigSection(name = "Local Mule", description = "Automatic excess-GP transfer to KSP Trade Receiver", position = 90)
	String muleSection = KspMuleConfig.SECTION;

	@ConfigItem(keyName = "showPaint", name = "Show paint", description = "Show the RuneScape-style KSP AIO Fighter paint over the chatbox.", position = 0, section = paintSection)
	default boolean showPaint()
	{
		return true;
	}

	@Deprecated
	default boolean showOverlay()
	{
		return showPaint();
	}

	@ConfigItem(keyName = "npcNames", name = "NPC names", description = "Comma-separated NPC names to fight.", position = 0, section = combatSection)
	default String npcNames()
	{
		return "";
	}

	@Range(min = 1, max = 50)
	@ConfigItem(keyName = "attackRadius", name = "Attack radius", description = "Square tile radius from your player to attack NPCs.", position = 1, section = combatSection)
	default int attackRadius()
	{
		return 10;
	}

	@ConfigItem(keyName = "bankForGear", name = "Bank for gear", description = "Walk to the nearest bank and withdraw/equip missing configured gear.", position = 2, section = combatSection)
	default boolean bankForGear()
	{
		return false;
	}

	@ConfigItem(keyName = "useSafeSpot", name = "Use safe spot", description = "Return to the saved safe spot before attacking NPCs. When enabled, right-click a tile and choose Set Safe Spot to update the tile.", position = 0, section = areaSection)
	default boolean useSafeSpot()
	{
		return false;
	}

	default int safeSpotX()
	{
		return 0;
	}

	default int safeSpotY()
	{
		return 0;
	}

	default int safeSpotPlane()
	{
		return 0;
	}

	@ConfigItem(keyName = "useAttackArea", name = "Use attack area", description = "Backing setting managed by the AIO Fighter side-panel map.", hidden = true)
	default boolean useAttackArea()
	{
		return false;
	}

	@ConfigItem(keyName = "resetArea", name = "Reset Area", description = "Legacy backing action. Attack areas are cleared from the AIO Fighter side panel.", hidden = true)
	default void resetArea()
	{
		KspAioFighterPlugin.requestAreaReset();
	}

	@ConfigItem(keyName = "resetAreas", name = "Reset Areas", description = "Legacy backing setting for clearing the saved attack area.", hidden = true)
	default boolean resetAreas()
	{
		return false;
	}

	default int attackAreaTile1X()
	{
		return 0;
	}

	default int attackAreaTile1Y()
	{
		return 0;
	}

	default int attackAreaTile1Plane()
	{
		return 0;
	}

	default int attackAreaTile2X()
	{
		return 0;
	}

	default int attackAreaTile2Y()
	{
		return 0;
	}

	default int attackAreaTile2Plane()
	{
		return 0;
	}

	@ConfigItem(keyName = "trainAttack", name = "Train Attack", description = "Train Attack until the target level is reached.", position = 0, section = trainingSection)
	default boolean trainAttack()
	{
		return true;
	}

	@Range(min = 1, max = 99)
	@ConfigItem(keyName = "attackTarget", name = "Attack target", description = "Target Attack level.", position = 1, section = trainingSection)
	default int attackTarget()
	{
		return 99;
	}

	@ConfigItem(keyName = "trainStrength", name = "Train Strength", description = "Train Strength until the target level is reached.", position = 2, section = trainingSection)
	default boolean trainStrength()
	{
		return true;
	}

	@Range(min = 1, max = 99)
	@ConfigItem(keyName = "strengthTarget", name = "Strength target", description = "Target Strength level.", position = 3, section = trainingSection)
	default int strengthTarget()
	{
		return 99;
	}

	@ConfigItem(keyName = "trainDefence", name = "Train Defence", description = "Train Defence until the target level is reached.", position = 4, section = trainingSection)
	default boolean trainDefence()
	{
		return true;
	}

	@Range(min = 1, max = 99)
	@ConfigItem(keyName = "defenceTarget", name = "Defence target", description = "Target Defence level.", position = 5, section = trainingSection)
	default int defenceTarget()
	{
		return 99;
	}

	@ConfigItem(keyName = "trainRanged", name = "Train Ranged", description = "Train Ranged until the target level is reached.", position = 6, section = trainingSection)
	default boolean trainRanged()
	{
		return false;
	}

	@Range(min = 1, max = 99)
	@ConfigItem(keyName = "rangedTarget", name = "Ranged target", description = "Target Ranged level.", position = 7, section = trainingSection)
	default int rangedTarget()
	{
		return 99;
	}

	@ConfigItem(keyName = "trainMagic", name = "Train Magic", description = "Train Magic until the target level is reached.", position = 8, section = trainingSection)
	default boolean trainMagic()
	{
		return false;
	}

	@Range(min = 1, max = 99)
	@ConfigItem(keyName = "magicTarget", name = "Magic target", description = "Target Magic level.", position = 9, section = trainingSection)
	default int magicTarget()
	{
		return 99;
	}

	@ConfigItem(keyName = "magicSpell", name = "Magic spell", description = "Spell to autocast when Magic is selected.", position = 10, section = trainingSection)
	default Rs2CombatSpells magicSpell()
	{
		return Rs2CombatSpells.WIND_STRIKE;
	}

	default boolean withdrawMagicRunes()
	{
		return true;
	}

	default int magicRuneCasts()
	{
		return 1;
	}

	@ConfigItem(keyName = "walkToBankAndLogoutWhenGoalsReached", name = "Walk bank + logout", description = "When all enabled training goals are reached, walk to the nearest bank, logout, and stop the plugin.", position = 11, section = trainingSection)
	default boolean walkToBankAndLogoutWhenGoalsReached()
	{
		return false;
	}

	@ConfigItem(keyName = "attackGear", name = "Attack gear", description = "Backing gear list managed by the AIO Fighter Gear side panel.", hidden = true)
	default String attackGear()
	{
		return "";
	}

	@ConfigItem(keyName = "strengthGear", name = "Strength gear", description = "Backing gear list managed by the AIO Fighter Gear side panel.", hidden = true)
	default String strengthGear()
	{
		return "";
	}

	@ConfigItem(keyName = "defenceGear", name = "Defence gear", description = "Backing gear list managed by the AIO Fighter Gear side panel.", hidden = true)
	default String defenceGear()
	{
		return "";
	}

	@ConfigItem(keyName = "rangedGear", name = "Ranged gear", description = "Backing gear list managed by the AIO Fighter Gear side panel.", hidden = true)
	default String rangedGear()
	{
		return "";
	}

	@ConfigItem(keyName = "magicGear", name = "Magic gear", description = "Backing gear list managed by the AIO Fighter Gear side panel.", hidden = true)
	default String magicGear()
	{
		return "";
	}

	@ConfigItem(keyName = "useHealing", name = "Use healing", description = "Eat configured food and bank only when no configured food remains.", position = 0, section = suppliesSection)
	default boolean useHealing()
	{
		return true;
	}

	@ConfigItem(keyName = "foodName", name = "Food name", description = "Exact food name to eat and withdraw from the bank.", position = 1, section = suppliesSection)
	default String foodName()
	{
		return "Lobster";
	}

	@Range(min = 0, max = 28)
	@ConfigItem(keyName = "foodAmount", name = "Food amount", description = "Amount of food to withdraw when restocking at the bank.", position = 2, section = suppliesSection)
	default int foodAmount()
	{
		return 10;
	}

	@Range(min = 1, max = 99)
	@ConfigItem(keyName = "unknownFoodHeal", name = "Unknown food heal", description = "Heal amount to use if the food is not known by Microbot.", position = 3, section = suppliesSection)
	default int unknownFoodHeal()
	{
		return 12;
	}

	@ConfigItem(keyName = "usePotions", name = "Use potions", description = "Drink combat potions only when the selected skill has no active boost.", position = 4, section = suppliesSection)
	default boolean usePotions()
	{
		return true;
	}

	@ConfigItem(keyName = "lootItems", name = "Loot items", description = "Enable looting of configured item names.", position = 0, section = lootSection)
	default boolean lootItems()
	{
		return true;
	}

	@ConfigItem(keyName = "itemsToLoot", name = "Items to loot", description = "Comma-separated item names to loot.", position = 1, section = lootSection)
	default String itemsToLoot()
	{
		return "";
	}

	@ConfigItem(keyName = "lootOwnership", name = "Loot ownership", description = "Loot only your drops or all matching drops.", position = 2, section = lootSection)
	default KspLootOwnership lootOwnership()
	{
		return KspLootOwnership.LOOT_OWN;
	}

	@ConfigItem(keyName = "buryBones", name = "Bury bones", description = "Loot and bury bones.", position = 3, section = lootSection)
	default boolean buryBones()
	{
		return false;
	}

	@ConfigItem(keyName = "highAlchLoot", name = "High alch loot", description = "High alch profitable items in inventory.", position = 4, section = lootSection)
	default boolean highAlchLoot()
	{
		return false;
	}

	default int highAlchRuneCasts()
	{
		return 1;
	}
}
