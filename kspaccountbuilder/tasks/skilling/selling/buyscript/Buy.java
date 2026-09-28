package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.equipment.amulet.Amulet;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.equipment.armour.Armour;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.equipment.cape.Capes;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.equipment.weapon.Weapons;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.food.Food;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.equiplevels.PickaxeEquip;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.levelreqmining.MiningReq;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.tool.SmithTool;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.equiplevels.AxeEquip;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.levelreqwc.WoodCuttingReq;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.equipment.Rs2Equipment;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.function.Predicate;

public final class Buy
{
    public static final int TARGET_SMITHING_LEVEL = 15;
    public static final int TARGET_SMITHING_XP = 2411;
    public static final double BRONZE_BAR_SMITHING_XP = 6.2D;

    public static final String COINS_NAME = "Coins";
    public static final String COPPER_ORE_NAME = "Copper ore";
    public static final String TIN_ORE_NAME = "Tin ore";
    public static final String HAMMER_NAME = SmithTool.HAMMER.getDisplayName();
    public static final int HAMMER_ITEM_ID = SmithTool.HAMMER.getItemId();
    public static final String TINDERBOX_NAME = "Tinderbox";
    public static final String FISHING_BAIT_NAME = "Fishing bait";
    public static final int FISHING_BAIT_BUY_QUANTITY = 500;
    public static final String FEATHER_NAME = "Feather";
    public static final int FEATHER_BUY_QUANTITY = 1000;
    public static final String SMALL_FISHING_NET_NAME = "Small fishing net";
    public static final String FISHING_ROD_NAME = "Fishing rod";
    public static final String FLY_FISHING_ROD_NAME = "Fly fishing rod";
    public static final String HARPOON_NAME = "Harpoon";
    public static final String LOBSTER_POT_NAME = "Lobster pot";
    public static final String LEATHER_NAME = "Leather";
    public static final String THREAD_NAME = "Thread";
    public static final String NEEDLE_NAME = "Needle";
    public static final String GOLD_BAR_NAME = "Gold bar";
    public static final String RING_MOULD_NAME = "Ring mould";
    public static final String NECKLACE_MOULD_NAME = "Necklace mould";
    public static final String CHISEL_NAME = "Chisel";
    public static final String TIARA_MOULD_NAME = "Tiara mould";
    public static final int TARGET_CRAFTING_LEVEL = 5;
    public static final int TARGET_CRAFTING_XP = 388;
    public static final double LEATHER_GLOVES_CRAFTING_XP = 13.75D;

    public static final int MELEE_TARGET_FOOD_COUNT = 5;
    public static final int MELEE_FOOD_PURCHASE_QUANTITY = 20;
    public static final int MELEE_GE_PRICE_INCREASE_STEP_PERCENT = 10;

    public static final String[] PICKAXE_NAMES = {
            "Bronze pickaxe", "Iron pickaxe", "Steel pickaxe", "Black pickaxe",
            "Mithril pickaxe", "Adamant pickaxe", "Rune pickaxe"
    };

    public static final String[] AXE_NAMES = {
            "Bronze axe", "Iron axe", "Steel axe", "Black axe",
            "Mithril axe", "Adamant axe", "Rune axe"
    };

    public static final List<String> PICKAXE_NAME_LIST = Collections.unmodifiableList(Arrays.asList(PICKAXE_NAMES));
    public static final List<String> AXE_NAME_LIST = Collections.unmodifiableList(Arrays.asList(AXE_NAMES));

    private Buy()
    {
    }

    public static String resolveDesiredPickaxeNameForBuy()
    {
        int miningLevel = Microbot.getClient().getRealSkillLevel(Skill.MINING);
        return PICKAXE_NAMES[MiningReq.bestForMiningLevel(miningLevel).ordinal()];
    }

    public static String resolveDesiredAxeNameForBuy()
    {
        int woodcuttingLevel = Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING);
        return AXE_NAMES[WoodCuttingReq.bestForWoodcuttingLevel(woodcuttingLevel).ordinal()];
    }

    public static String resolveDesiredPickaxeNameForGear()
    {
        int miningLevel = Microbot.getClient().getRealSkillLevel(Skill.MINING);
        int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
        return PICKAXE_NAMES[Math.min(
                MiningReq.bestForMiningLevel(miningLevel).ordinal(),
                PickaxeEquip.bestForAttackLevel(attackLevel).ordinal()
        )];
    }

    public static String resolveDesiredAxeNameForGear()
    {
        int woodcuttingLevel = Microbot.getClient().getRealSkillLevel(Skill.WOODCUTTING);
        int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
        return AXE_NAMES[Math.min(
                WoodCuttingReq.bestForWoodcuttingLevel(woodcuttingLevel).ordinal(),
                AxeEquip.bestForAttackLevel(attackLevel).ordinal()
        )];
    }

    public static boolean hasAnyGeBuyRequirementMissing()
    {
        return !hasNamedItemAnywhere(resolveDesiredPickaxeNameForBuy())
                || !hasNamedItemAnywhere(resolveDesiredAxeNameForBuy())
                || !hasHammerAnywhere()
                || !hasTinderboxAnywhere()
                || hasFishingBuyRequirementMissingInBank()
                || isCraftingBuyMissingInBank()
                || isJewelleryBuyMissingInBank()
                || isMeleeFoodBuyMissing()
                || isSmithingOreBuyMissingAnywhere();
    }

    public static boolean isMeleeFoodBuyMissing()
    {
        int attackLevel = Microbot.getClient().getRealSkillLevel(Skill.ATTACK);
        int strengthLevel = Microbot.getClient().getRealSkillLevel(Skill.STRENGTH);
        int defenceLevel = Microbot.getClient().getRealSkillLevel(Skill.DEFENCE);
        boolean trainingOnChickens = attackLevel < 15 || strengthLevel < 15 || defenceLevel < 15;

        return !trainingOnChickens && getMeleeFoodCountAvailable() < MELEE_TARGET_FOOD_COUNT;
    }

    public static boolean hasFishingBuyRequirementMissingInBank()
    {
        int fishingLevel = Microbot.getClient().getRealSkillLevel(Skill.FISHING);
        boolean needsBasicBaitFishing = fishingLevel < 20;

        return (needsBasicBaitFishing && Rs2Bank.count(FISHING_BAIT_NAME) <= 0)
                || Rs2Bank.count(FEATHER_NAME) <= 0
                || (needsBasicBaitFishing && Rs2Bank.count(SMALL_FISHING_NET_NAME) <= 0)
                || (needsBasicBaitFishing && Rs2Bank.count(FISHING_ROD_NAME) <= 0)
                || Rs2Bank.count(FLY_FISHING_ROD_NAME) <= 0
                || Rs2Bank.count(HARPOON_NAME) <= 0
                || Rs2Bank.count(LOBSTER_POT_NAME) <= 0;
    }

    public static boolean isCraftingBuyMissingInBank()
    {
        int craftingLevel = Microbot.getClient().getRealSkillLevel(Skill.CRAFTING);
        int craftingXp = Microbot.getClient().getSkillExperience(Skill.CRAFTING);
        int leatherNeeded = getRequiredLeatherGlovesForCraftingTarget(craftingLevel, craftingXp);

        return leatherNeeded > 0
                && (Rs2Bank.count(LEATHER_NAME) < leatherNeeded
                || Rs2Bank.count(THREAD_NAME) <= 0
                || Rs2Bank.count(NEEDLE_NAME) <= 0);
    }

    public static boolean isJewelleryBuyMissingInBank()
    {
        return Rs2Bank.count(GOLD_BAR_NAME) <= 0
                || Rs2Bank.count(RING_MOULD_NAME) <= 0
                || Rs2Bank.count(NECKLACE_MOULD_NAME) <= 0
                || Rs2Bank.count(CHISEL_NAME) <= 0
                || Rs2Bank.count(TIARA_MOULD_NAME) <= 0;
    }

    public static int getRequiredLeatherGlovesForCraftingTarget(int craftingLevel, int craftingXp)
    {
        if (craftingLevel >= TARGET_CRAFTING_LEVEL || craftingXp >= TARGET_CRAFTING_XP)
        {
            return 0;
        }

        int xpRemaining = TARGET_CRAFTING_XP - craftingXp;
        return (int) Math.ceil(xpRemaining / LEATHER_GLOVES_CRAFTING_XP);
    }

    public static boolean isSmithingOreBuyMissingAnywhere()
    {
        int currentSmithingLevel = Microbot.getClient().getRealSkillLevel(Skill.SMITHING);
        int currentSmithingXp = Microbot.getClient().getSkillExperience(Skill.SMITHING);
        int requiredBronzeBars = getRequiredBronzeBarsForSmithingTarget(currentSmithingLevel, currentSmithingXp);

        return requiredBronzeBars > 0
                && (countItemAnywhere(COPPER_ORE_NAME) < requiredBronzeBars
                || countItemAnywhere(TIN_ORE_NAME) < requiredBronzeBars);
    }

    public static int getRequiredBronzeBarsForSmithingTarget(int smithingLevel, int smithingXp)
    {
        if (smithingLevel >= TARGET_SMITHING_LEVEL || smithingXp >= TARGET_SMITHING_XP)
        {
            return 0;
        }

        int xpRemaining = TARGET_SMITHING_XP - smithingXp;
        return (int) Math.ceil(xpRemaining / BRONZE_BAR_SMITHING_XP);
    }

    public static int countItemAnywhere(String itemName)
    {
        return Rs2Inventory.count(itemName)
                + Rs2Inventory.count(itemName, true)
                + Math.max(0, Rs2Bank.count(itemName));
    }

    public static boolean hasNamedItemAnywhere(String itemName)
    {
        return itemName != null
                && (Rs2Equipment.isWearing(itemName)
                || Rs2Inventory.hasItem(itemName)
                || Rs2Inventory.hasItem(itemName, true)
                || Rs2Bank.count(itemName) > 0);
    }

    public static boolean hasHammerAnywhere()
    {
        return hasItemIdInInventory(HAMMER_ITEM_ID) || hasItemIdInBank(HAMMER_ITEM_ID);
    }

    public static boolean hasTinderboxAnywhere()
    {
        return hasNamedItemAnywhere(TINDERBOX_NAME);
    }

    public static boolean hasItemIdInInventory(int itemId)
    {
        return Rs2Inventory.all().stream()
                .anyMatch(item -> item != null && item.getId() == itemId);
    }

    public static boolean hasItemIdInBank(int itemId)
    {
        return Rs2Bank.bankItems().stream()
                .anyMatch(item -> item != null && item.getId() == itemId);
    }

    public static boolean isPickaxeName(String itemName)
    {
        return matchesAny(itemName, PICKAXE_NAMES);
    }

    public static boolean isAxeName(String itemName)
    {
        return matchesAny(itemName, AXE_NAMES);
    }

    public static boolean isToolName(String itemName)
    {
        return isPickaxeName(itemName) || isAxeName(itemName);
    }

    public static boolean isOutdatedToolName(String itemName, String desiredPickaxe, String desiredAxe)
    {
        if (itemName == null)
        {
            return false;
        }

        for (String pickaxeName : PICKAXE_NAMES)
        {
            if (pickaxeName.equalsIgnoreCase(itemName))
            {
                return !pickaxeName.equalsIgnoreCase(desiredPickaxe);
            }
        }

        for (String axeName : AXE_NAMES)
        {
            if (axeName.equalsIgnoreCase(itemName))
            {
                return !axeName.equalsIgnoreCase(desiredAxe);
            }
        }

        return false;
    }

    public static MeleeGearPlan buildMeleeGearPlan(
            int attackLevel,
            int defenceLevel,
            boolean dragonSlayerCompleted,
            Predicate<String> itemAvailable
    )
    {
        List<String> desired = new ArrayList<>();
        List<String> missing = new ArrayList<>();

        String availableWeapon = getBestAvailableMeleeWeapon(attackLevel, itemAvailable);
        if (availableWeapon != null)
        {
            desired.add(availableWeapon);
        }
        else
        {
            String requiredWeapon = getBestMeleeWeapon(attackLevel);
            addIfPresent(desired, requiredWeapon);
            addIfPresent(missing, requiredWeapon);
        }

        addIfPresent(desired, getBestAvailableMeleeArmour(defenceLevel, "full helm", itemAvailable));

        String shield = getBestAvailableMeleeArmour(defenceLevel, "kiteshield", itemAvailable);
        if (shield == null && itemAvailable != null && itemAvailable.test("Wooden shield"))
        {
            shield = "Wooden shield";
        }
        addIfPresent(desired, shield);

        addIfPresent(desired, getBestAvailableMeleeArmour(defenceLevel, "platelegs", itemAvailable));
        addIfPresent(desired, getBestAvailableMeleeBodyArmour(
                defenceLevel,
                dragonSlayerCompleted,
                itemAvailable));

        String amulet = Amulet.AMULET_OF_POWER.getDisplayName();
        if (itemAvailable == null || itemAvailable.test(amulet))
        {
            desired.add(amulet);
        }

        String cape = getBestAvailableCape(itemAvailable);
        addIfPresent(desired, cape);

        return new MeleeGearPlan(desired, missing);
    }

    private static String getBestAvailableMeleeWeapon(int attackLevel, Predicate<String> itemAvailable)
    {
        if (itemAvailable == null)
        {
            return getBestMeleeWeapon(attackLevel);
        }

        return Arrays.stream(Weapons.values())
                .filter(weapon -> attackLevel >= weapon.getRequiredAttackLevel())
                .sorted(Comparator.comparingInt(Weapons::getRequiredAttackLevel)
                        .thenComparingInt(Enum::ordinal)
                        .reversed())
                .map(Weapons::getDisplayName)
                .filter(itemAvailable)
                .findFirst()
                .orElse(null);
    }

    private static String getBestAvailableMeleeArmour(
            int defenceLevel,
            String marker,
            Predicate<String> itemAvailable)
    {
        if (itemAvailable == null)
        {
            return getBestMeleeArmour(defenceLevel, marker);
        }

        return Arrays.stream(Armour.values())
                .filter(armour -> defenceLevel >= armour.getRequiredDefenceLevel())
                .filter(armour -> armour.getDisplayName().toLowerCase(Locale.ENGLISH).contains(marker))
                .sorted(Comparator.comparingInt(Armour::getRequiredDefenceLevel)
                        .thenComparingInt(Enum::ordinal)
                        .reversed())
                .map(Armour::getDisplayName)
                .filter(itemAvailable)
                .findFirst()
                .orElse(null);
    }

    private static String getBestAvailableMeleeBodyArmour(
            int defenceLevel,
            boolean dragonSlayerCompleted,
            Predicate<String> itemAvailable)
    {
        if (itemAvailable == null)
        {
            return getBestMeleeBodyArmour(defenceLevel, dragonSlayerCompleted);
        }

        return Arrays.stream(Armour.values())
                .filter(armour -> defenceLevel >= armour.getRequiredDefenceLevel())
                .filter(armour -> {
                    String name = armour.getDisplayName().toLowerCase(Locale.ENGLISH);
                    return name.contains("platebody") || name.contains("chainbody");
                })
                .filter(armour -> dragonSlayerCompleted
                        || armour != Armour.RUNE_PLATEBODY)
                .sorted(Comparator.comparingInt(Armour::getRequiredDefenceLevel)
                        .thenComparingInt(Enum::ordinal)
                        .reversed())
                .map(Armour::getDisplayName)
                .filter(itemAvailable)
                .findFirst()
                .orElse(null);
    }

    private static String getBestAvailableCape(Predicate<String> itemAvailable)
    {
        if (itemAvailable == null)
        {
            return Capes.TEAM_1_CAPE.getDisplayName();
        }

        return Arrays.stream(Capes.values())
                .map(Capes::getDisplayName)
                .filter(itemAvailable)
                .findFirst()
                .orElse(null);
    }

    public static String getBestMeleeWeapon(int attackLevel)
    {
        return Arrays.stream(Weapons.values())
                .filter(weapon -> attackLevel >= weapon.getRequiredAttackLevel())
                .max(Comparator.comparingInt(Weapons::getRequiredAttackLevel).thenComparingInt(Enum::ordinal))
                .map(Weapons::getDisplayName)
                .orElse(null);
    }

    public static String getBestMeleeArmour(int defenceLevel, String marker)
    {
        return Arrays.stream(Armour.values())
                .filter(armour -> defenceLevel >= armour.getRequiredDefenceLevel())
                .filter(armour -> armour.getDisplayName().toLowerCase(Locale.ENGLISH).contains(marker))
                .max(Comparator.comparingInt(Armour::getRequiredDefenceLevel).thenComparingInt(Enum::ordinal))
                .map(Armour::getDisplayName)
                .orElse(null);
    }

    public static String getBestMeleeBodyArmour(int defenceLevel, boolean dragonSlayerCompleted)
    {
        if (defenceLevel >= Armour.RUNE_CHAINBODY.getRequiredDefenceLevel() && !dragonSlayerCompleted)
        {
            return Armour.RUNE_CHAINBODY.getDisplayName();
        }

        String platebody = getBestMeleeArmour(defenceLevel, "platebody");
        if (platebody != null)
        {
            return platebody;
        }

        return getBestMeleeArmour(defenceLevel, "chainbody");
    }

    public static Food getBestMeleeFoodInInventory()
    {
        return Arrays.stream(new Food[]{Food.TROUT, Food.SALMON})
                .filter(food -> Rs2Inventory.itemQuantity(food.getItemId()) > 0)
                .max(Comparator.comparingInt(Food::getHealAmount))
                .orElse(null);
    }

    public static Food getBestMeleeFoodAvailableInBank()
    {
        return Arrays.stream(new Food[]{Food.TROUT, Food.SALMON})
                .filter(food -> Rs2Bank.count(food.getItemId()) > 0)
                .max(Comparator.comparingInt(Food::getHealAmount))
                .orElse(null);
    }

    public static Food getBestOverallMeleeFood()
    {
        return Arrays.stream(Food.values())
                .max(Comparator.comparingInt(Food::getHealAmount))
                .orElse(Food.SALMON);
    }

    public static int getMeleeFoodCountInInventory()
    {
        return Arrays.stream(new Food[]{Food.TROUT, Food.SALMON})
                .mapToInt(food -> Rs2Inventory.itemQuantity(food.getItemId()))
                .sum();
    }

    public static int getMeleeFoodCountAvailable()
    {
        return getMeleeFoodCountInInventory()
                + Math.max(0, Rs2Bank.count(Food.TROUT.getItemId()))
                + Math.max(0, Rs2Bank.count(Food.SALMON.getItemId()));
    }

    private static void addIfPresent(List<String> items, String itemName)
    {
        if (itemName != null && !itemName.isEmpty())
        {
            items.add(itemName);
        }
    }

    private static boolean matchesAny(String itemName, String[] names)
    {
        if (itemName == null)
        {
            return false;
        }

        for (String name : names)
        {
            if (name.equalsIgnoreCase(itemName))
            {
                return true;
            }
        }

        return false;
    }

    public static final class MeleeGearPlan
    {
        private final List<String> desiredItems;
        private final List<String> missingItems;

        private MeleeGearPlan(List<String> desiredItems, List<String> missingItems)
        {
            this.desiredItems = Collections.unmodifiableList(new ArrayList<>(desiredItems));
            this.missingItems = Collections.unmodifiableList(new ArrayList<>(missingItems));
        }

        public List<String> getDesiredItems()
        {
            return desiredItems;
        }

        public List<String> getMissingItems()
        {
            return missingItems;
        }
    }
}
