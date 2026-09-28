package net.runelite.client.plugins.microbot.kspaccountbuilder;

import java.util.ArrayList;
import java.util.List;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.areas.CombatAreas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.cooking.areas.Areas;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.clevels.CraftingLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.firemaking.fmarea.FireArea;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smithing.smithlevels.SmithLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.smelting.barlevel.BarLevels;
import net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.woodcutting.treeareas.TreeAreas;

public enum KspSingleSkillTarget
{
    NONE(null, "N/A", null),
    AUTOMATIC(null, "Automatic / Progressive", null),

    MINING_TIN_COPPER(KspTrainSingleSkillTask.MINING, "Tin / Copper",
            net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.areas.Areas.TIN_COPPER_VARROCK_EAST),
    MINING_IRON(KspTrainSingleSkillTask.MINING, "Iron",
            net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.areas.Areas.IRON_VARROCK_EAST),
    MINING_CLAY(KspTrainSingleSkillTask.MINING, "Clay",
            net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.areas.Areas.CLAY_VARROCK_WEST),
    MINING_SILVER(KspTrainSingleSkillTask.MINING, "Silver",
            net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.areas.Areas.SILVER_VARROCK_WEST),
    MINING_COAL(KspTrainSingleSkillTask.MINING, "Coal",
            net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.mining.areas.Areas.COAL_BARBARIAN_VILLAGE),

    WOODCUTTING_REGULAR(KspTrainSingleSkillTask.WOODCUTTING, "Regular",
            TreeAreas.REGULAR_TREE_VARROCK_WEST),
    WOODCUTTING_OAK_DRAYNOR(KspTrainSingleSkillTask.WOODCUTTING, "Oak Draynor",
            TreeAreas.OAK_TREE_DRAYNOR),
    WOODCUTTING_OAK_CASTLE(KspTrainSingleSkillTask.WOODCUTTING, "Oak Castle",
            TreeAreas.VCASTLE_OAKS),
    WOODCUTTING_OAK_WEST(KspTrainSingleSkillTask.WOODCUTTING, "Oak West",
            TreeAreas.VWEST_OAKS),
    WOODCUTTING_OAK_EAST(KspTrainSingleSkillTask.WOODCUTTING, "Oak East",
            TreeAreas.VEAST_OAKS),
    WOODCUTTING_WILLOW(KspTrainSingleSkillTask.WOODCUTTING, "Willow",
            TreeAreas.WILLOW_TREES_DRAYNOR),
    WOODCUTTING_YEW(KspTrainSingleSkillTask.WOODCUTTING, "Yew",
            TreeAreas.YEW_TREE_VARROCK_PALACE),

    FIREMAKING_DRAYNOR(KspTrainSingleSkillTask.FIREMAKING, "Draynor Bank", FireArea.FM_AREA_DRAYNOR_BANK),

    FISHING_SHRIMP(KspTrainSingleSkillTask.FISHING, "Shrimp",
            net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.areas.Areas.SHRIMP_ANCHOVIES),
    FISHING_SARDINE(KspTrainSingleSkillTask.FISHING, "Sardine",
            net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.areas.Areas.SARDINE_HERRING),
    FISHING_TROUT(KspTrainSingleSkillTask.FISHING, "Trout",
            net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.areas.Areas.TROUT_SALMON),
    FISHING_KARAMJA(KspTrainSingleSkillTask.FISHING, "Karamja",
            net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.areas.Areas.KARAMJA),

    COOKING_EDGEVILLE(KspTrainSingleSkillTask.COOKING, "Edgeville", Areas.EDGEVILLE_RANGE),
    COOKING_LUMBRIDGE(KspTrainSingleSkillTask.COOKING, "Lumbridge", Areas.LUMBRIDGE_KITCHEN),

    CRAFTING_LEATHER_GLOVES(KspTrainSingleSkillTask.CRAFTING, "Leather gloves", CraftingLevels.LEATHER_GLOVES),
    CRAFTING_GOLD_RING(KspTrainSingleSkillTask.CRAFTING, "Gold ring", CraftingLevels.GOLD_RING),
    CRAFTING_GOLD_NECKLACE(KspTrainSingleSkillTask.CRAFTING, "Gold necklace", CraftingLevels.GOLD_NECKLACE),
    CRAFTING_CUT_SAPPHIRE(KspTrainSingleSkillTask.CRAFTING, "Cut sapphire", CraftingLevels.CUT_SAPPHIRE),
    CRAFTING_SAPPHIRE_RING(KspTrainSingleSkillTask.CRAFTING, "Sapphire ring", CraftingLevels.SAPPHIRE_RING),
    CRAFTING_SAPPHIRE_NECKLACE(KspTrainSingleSkillTask.CRAFTING, "Sapphire necklace", CraftingLevels.SAPPHIRE_NECKLACE),
    CRAFTING_TIARA(KspTrainSingleSkillTask.CRAFTING, "Tiara", CraftingLevels.TIARA),
    CRAFTING_CUT_EMERALD(KspTrainSingleSkillTask.CRAFTING, "Cut emerald", CraftingLevels.CUT_EMERALD),
    CRAFTING_EMERALD_RING(KspTrainSingleSkillTask.CRAFTING, "Emerald ring", CraftingLevels.EMERALD_RING),
    CRAFTING_EMERALD_NECKLACE(KspTrainSingleSkillTask.CRAFTING, "Emerald necklace", CraftingLevels.EMERALD_NECKLACE),
    CRAFTING_CUT_RUBY(KspTrainSingleSkillTask.CRAFTING, "Cut ruby", CraftingLevels.CUT_RUBY),
    CRAFTING_RUBY_RING(KspTrainSingleSkillTask.CRAFTING, "Ruby ring", CraftingLevels.RUBY_RING),
    CRAFTING_RUBY_NECKLACE(KspTrainSingleSkillTask.CRAFTING, "Ruby necklace", CraftingLevels.RUBY_NECKLACE),
    CRAFTING_CUT_DIAMOND(KspTrainSingleSkillTask.CRAFTING, "Cut diamond", CraftingLevels.CUT_DIAMOND),
    CRAFTING_DIAMOND_RING(KspTrainSingleSkillTask.CRAFTING, "Diamond ring", CraftingLevels.DIAMOND_RING),
    CRAFTING_DIAMOND_NECKLACE(KspTrainSingleSkillTask.CRAFTING, "Diamond necklace", CraftingLevels.DIAMOND_NECKLACE),

    MELEE_COWS(KspTrainSingleSkillTask.MELEE, "Cows", CombatAreas.COWPEN),
    MELEE_CHICKENS(KspTrainSingleSkillTask.MELEE, "Chickens", CombatAreas.CHICKENS),
    MELEE_WARRIORS(KspTrainSingleSkillTask.MELEE, "Warriors", CombatAreas.AL_KHARID_WARRIOR),
    MELEE_HILL_GIANTS(KspTrainSingleSkillTask.MELEE, "Hill Giants", CombatAreas.HILL_GIANTS),
    MELEE_MOSS_GIANTS(KspTrainSingleSkillTask.MELEE, "Moss Giants", CombatAreas.MOSS_GIANTS),

    SMITHING_BRONZE_DAGGER(KspTrainSingleSkillTask.SMITHING, "Bronze Dagger", SmithLevels.BRONZE_DAGGER),
    SMITHING_BRONZE_SCIMITAR(KspTrainSingleSkillTask.SMITHING, "Bronze Scim", SmithLevels.BRONZE_SCIMITAR),
    SMITHING_BRONZE_WARHAMMER(KspTrainSingleSkillTask.SMITHING, "Bronze WH", SmithLevels.BRONZE_WARHAMMER),
    SMITHING_BRONZE_PLATEBODY(KspTrainSingleSkillTask.SMITHING, "Bronze Plate", SmithLevels.BRONZE_PLATEBODY),
    SMITHING_IRON_SCIMITAR(KspTrainSingleSkillTask.SMITHING, "Iron Scim", SmithLevels.IRON_SCIMITAR),
    SMITHING_IRON_WARHAMMER(KspTrainSingleSkillTask.SMITHING, "Iron WH", SmithLevels.IRON_WARHAMMER),
    SMITHING_IRON_PLATEBODY(KspTrainSingleSkillTask.SMITHING, "Iron Plate", SmithLevels.IRON_PLATEBODY),
    SMITHING_STEEL_SCIMITAR(KspTrainSingleSkillTask.SMITHING, "Steel Scim", SmithLevels.STEEL_SCIMITAR),
    SMITHING_STEEL_WARHAMMER(KspTrainSingleSkillTask.SMITHING, "Steel WH", SmithLevels.STEEL_WARHAMMER),
    SMITHING_STEEL_PLATEBODY(KspTrainSingleSkillTask.SMITHING, "Steel Plate", SmithLevels.STEEL_PLATEBODY),

    SMELTING_BRONZE(KspTrainSingleSkillTask.SMELTING, "Bronze", BarLevels.BRONZE),
    SMELTING_IRON(KspTrainSingleSkillTask.SMELTING, "Iron", BarLevels.IRON),
    SMELTING_SILVER(KspTrainSingleSkillTask.SMELTING, "Silver", BarLevels.SILVER),
    SMELTING_STEEL(KspTrainSingleSkillTask.SMELTING, "Steel", BarLevels.STEEL),
    SMELTING_GOLD(KspTrainSingleSkillTask.SMELTING, "Gold", BarLevels.GOLD),
    SMELTING_MITHRIL(KspTrainSingleSkillTask.SMELTING, "Mithril", BarLevels.MITHRIL),
    SMELTING_ADAMANT(KspTrainSingleSkillTask.SMELTING, "Adamant", BarLevels.ADAMANT),
    SMELTING_RUNE(KspTrainSingleSkillTask.SMELTING, "Rune", BarLevels.RUNE);

    private final KspTrainSingleSkillTask task;
    private final String displayName;
    private final Object value;

    KspSingleSkillTarget(KspTrainSingleSkillTask task, String displayName, Object value)
    {
        this.task = task;
        this.displayName = displayName;
        this.value = value;
    }

    public static KspSingleSkillTarget fromKey(String key)
    {
        if (key == null || key.isEmpty())
        {
            return AUTOMATIC;
        }

        try
        {
            return valueOf(key);
        }
        catch (IllegalArgumentException ignored)
        {
            return AUTOMATIC;
        }
    }

    public static KspSingleSkillTarget[] optionsFor(KspTrainSingleSkillTask task)
    {
        List<KspSingleSkillTarget> options = new ArrayList<>();
        options.add(AUTOMATIC);
        for (KspSingleSkillTarget target : values())
        {
            if (target.task == task)
            {
                options.add(target);
            }
        }
        return options.toArray(new KspSingleSkillTarget[0]);
    }

    public boolean supports(KspTrainSingleSkillTask selectedTask)
    {
        return this == NONE || this == AUTOMATIC || task == selectedTask;
    }

    public <T> T getValue(Class<T> type)
    {
        return type.isInstance(value) ? type.cast(value) : null;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
