package net.runelite.client.plugins.microbot.kspbondgoal;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import net.runelite.api.Client;
import net.runelite.api.ItemComposition;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;

/**
 * Read-only activity advisor.
 *
 * Profit estimates use RuneLite item prices plus explicit throughput assumptions.
 * They are planning estimates, not guarantees. Market buy limits, travel time,
 * banking variance, failed offers and player execution are not modeled individually.
 */
final class BondActivityAdvisor
{
    private static final int NATURE_RUNE = 561;
    private static final int LAW_RUNE = 563;
    private static final int WINE_OF_ZAMORAK = 245;

    private static final int IRON_ORE = 440;
    private static final int COAL = 453;
    private static final int SILVER_ORE = 442;
    private static final int STEEL_BAR = 2353;
    private static final int SILVER_BAR = 2355;
    private static final int GOLD_BAR = 2357;

    private static final int YEW_LOGS = 1515;

    private static final int COWHIDE = 1739;
    private static final int LEATHER = 1741;
    private static final int CLAY = 434;
    private static final int SOFT_CLAY = 1761;
    private static final int JUG = 1935;
    private static final int JUG_OF_WATER = 1937;

    private static final int UNCUT_SAPPHIRE = 1623;
    private static final int SAPPHIRE = 1607;
    private static final int UNCUT_EMERALD = 1621;
    private static final int EMERALD = 1605;
    private static final int UNCUT_RUBY = 1619;
    private static final int RUBY = 1603;
    private static final int UNCUT_DIAMOND = 1617;
    private static final int DIAMOND = 1601;

    private static final int GOLD_RING = 1635;
    private static final int SAPPHIRE_RING = 1637;
    private static final int EMERALD_RING = 1639;
    private static final int RUBY_RING = 1641;
    private static final int DIAMOND_RING = 1643;

    // Common F2P high-alchemy candidates. The advisor selects the best live margin.
    private static final int[] HIGH_ALCH_CANDIDATES = {
        1079, // Rune platelegs
        1093, // Rune plateskirt
        1113, // Rune chainbody
        1127, // Rune platebody
        1147, // Rune med helm
        1163, // Rune full helm
        1185, // Rune sq shield
        1201, // Rune kiteshield
        1303, // Rune longsword
        1319, // Rune 2h sword
        1333  // Rune scimitar
    };

    private final Client client;
    private final ItemManager itemManager;
    private final KspBondGoalConfig config;

    BondActivityAdvisor(Client client, ItemManager itemManager, KspBondGoalConfig config)
    {
        this.client = client;
        this.itemManager = itemManager;
        this.config = config;
    }

    List<ActivityEstimate> evaluate()
    {
        List<ActivityEstimate> activities = new ArrayList<>();

        addHighAlchemy(activities);
        addTelegrabWine(activities);

        addGathering(activities, "Mine iron ore", Skill.MINING, 15, IRON_ORE, 500);
        addGathering(activities, "Mine coal", Skill.MINING, 30, COAL, 280);
        addGathering(activities, "Cut yew logs", Skill.WOODCUTTING, 60, YEW_LOGS, 180);

        addConversion(activities, "Smelt steel bars", Skill.SMITHING, 30,
            STEEL_BAR, 700, new Ingredient(IRON_ORE, 1), new Ingredient(COAL, 2));
        addConversion(activities, "Smelt silver bars", Skill.SMITHING, 20,
            SILVER_BAR, 750, new Ingredient(SILVER_ORE, 1));

        addConversion(activities, "Tan cowhides", null, 0,
            LEATHER, 1_400, new Ingredient(COWHIDE, 1), new Ingredient(-1, 1));
        addConversion(activities, "Make soft clay", null, 0,
            SOFT_CLAY, 1_300, new Ingredient(CLAY, 1));
        addConversion(activities, "Fill jugs with water", null, 0,
            JUG_OF_WATER, 1_800, new Ingredient(JUG, 1));

        addConversion(activities, "Cut sapphires", Skill.CRAFTING, 20,
            SAPPHIRE, 2_400, new Ingredient(UNCUT_SAPPHIRE, 1));
        addConversion(activities, "Cut emeralds", Skill.CRAFTING, 27,
            EMERALD, 2_400, new Ingredient(UNCUT_EMERALD, 1));
        addConversion(activities, "Cut rubies", Skill.CRAFTING, 34,
            RUBY, 2_400, new Ingredient(UNCUT_RUBY, 1));
        addConversion(activities, "Cut diamonds", Skill.CRAFTING, 43,
            DIAMOND, 2_400, new Ingredient(UNCUT_DIAMOND, 1));

        addConversion(activities, "Craft gold rings", Skill.CRAFTING, 5,
            GOLD_RING, 850, new Ingredient(GOLD_BAR, 1));
        addConversion(activities, "Craft sapphire rings", Skill.CRAFTING, 20,
            SAPPHIRE_RING, 850, new Ingredient(GOLD_BAR, 1), new Ingredient(SAPPHIRE, 1));
        addConversion(activities, "Craft emerald rings", Skill.CRAFTING, 27,
            EMERALD_RING, 850, new Ingredient(GOLD_BAR, 1), new Ingredient(EMERALD, 1));
        addConversion(activities, "Craft ruby rings", Skill.CRAFTING, 34,
            RUBY_RING, 850, new Ingredient(GOLD_BAR, 1), new Ingredient(RUBY, 1));
        addConversion(activities, "Craft diamond rings", Skill.CRAFTING, 43,
            DIAMOND_RING, 850, new Ingredient(GOLD_BAR, 1), new Ingredient(DIAMOND, 1));

        activities.removeIf(a -> a.getGpPerHour() <= 0);
        activities.sort(Comparator.comparingLong(ActivityEstimate::getGpPerHour).reversed());
        return Collections.unmodifiableList(activities);
    }

    private void addHighAlchemy(List<ActivityEstimate> activities)
    {
        if (level(Skill.MAGIC) < 55)
        {
            return;
        }

        int naturePrice = price(NATURE_RUNE);
        if (naturePrice <= 0)
        {
            return;
        }

        long bestMargin = Long.MIN_VALUE;
        String bestItem = null;

        for (int itemId : HIGH_ALCH_CANDIDATES)
        {
            int buyPrice = price(itemId);
            if (buyPrice <= 0)
            {
                continue;
            }

            ItemComposition composition = itemManager.getItemComposition(itemId);
            if (composition == null || composition.getHaPrice() <= 0)
            {
                continue;
            }

            long margin = (long) composition.getHaPrice() - buyPrice - naturePrice;
            if (margin > bestMargin)
            {
                bestMargin = margin;
                bestItem = composition.getName();
            }
        }

        if (bestMargin > 0 && bestItem != null)
        {
            long castsPerHour = scaledThroughput(1_100);
            activities.add(new ActivityEstimate(
                "High Alchemy",
                bestMargin * castsPerHour,
                bestItem + " ~" + bestMargin + " gp/cast; buy limits not modeled"
            ));
        }
    }

    private void addTelegrabWine(List<ActivityEstimate> activities)
    {
        if (level(Skill.MAGIC) < 33)
        {
            return;
        }

        int winePrice = price(WINE_OF_ZAMORAK);
        int lawPrice = price(LAW_RUNE);
        if (winePrice <= 0 || lawPrice <= 0)
        {
            return;
        }

        long unitProfit = realizedSellPrice(winePrice) - lawPrice;
        if (unitProfit <= 0)
        {
            return;
        }

        long units = scaledThroughput(350);
        activities.add(new ActivityEstimate(
            "Telegrab Wine of Zamorak",
            unitProfit * units,
            "Magic 33+; ~" + unitProfit + " gp after one law rune per wine"
        ));
    }

    private void addGathering(
        List<ActivityEstimate> activities,
        String name,
        Skill skill,
        int requiredLevel,
        int outputItemId,
        int baselineUnitsPerHour)
    {
        if (level(skill) < requiredLevel)
        {
            return;
        }

        int outputPrice = price(outputItemId);
        if (outputPrice <= 0)
        {
            return;
        }

        long units = scaledThroughput(baselineUnitsPerHour);
        long unitValue = realizedSellPrice(outputPrice);
        activities.add(new ActivityEstimate(
            name,
            unitValue * units,
            skill.getName() + " " + requiredLevel + "+; baseline " + baselineUnitsPerHour + " units/h"
        ));
    }

    private void addConversion(
        List<ActivityEstimate> activities,
        String name,
        Skill skill,
        int requiredLevel,
        int outputItemId,
        int baselineUnitsPerHour,
        Ingredient... inputs)
    {
        if (skill != null && level(skill) < requiredLevel)
        {
            return;
        }

        int outputPrice = price(outputItemId);
        if (outputPrice <= 0)
        {
            return;
        }

        long inputCost = 0;
        for (Ingredient input : inputs)
        {
            // Item id -1 represents a fixed 1 gp processing fee per quantity.
            if (input.itemId == -1)
            {
                inputCost += input.quantity;
                continue;
            }

            int inputPrice = price(input.itemId);
            if (inputPrice <= 0)
            {
                return;
            }
            inputCost += (long) inputPrice * input.quantity;
        }

        long unitProfit = realizedSellPrice(outputPrice) - inputCost;
        if (unitProfit <= 0)
        {
            return;
        }

        long units = scaledThroughput(baselineUnitsPerHour);
        String requirement = skill == null
            ? "No skill requirement"
            : skill.getName() + " " + requiredLevel + "+";

        activities.add(new ActivityEstimate(
            name,
            unitProfit * units,
            requirement + "; ~" + unitProfit + " gp/unit at current cached prices"
        ));
    }

    private int level(Skill skill)
    {
        return skill == null ? 99 : client.getRealSkillLevel(skill);
    }

    private int price(int itemId)
    {
        return itemManager.getItemPrice(itemId);
    }

    private long realizedSellPrice(int marketPrice)
    {
        return (long) marketPrice * config.saleRealizationPercent() / 100L;
    }

    private long scaledThroughput(int baseline)
    {
        return Math.max(1L, (long) baseline * config.activityEfficiencyPercent() / 100L);
    }

    static final class ActivityEstimate
    {
        private final String name;
        private final long gpPerHour;
        private final String detail;

        ActivityEstimate(String name, long gpPerHour, String detail)
        {
            this.name = name;
            this.gpPerHour = gpPerHour;
            this.detail = detail;
        }

        String getName()
        {
            return name;
        }

        long getGpPerHour()
        {
            return gpPerHour;
        }

        String getDetail()
        {
            return detail;
        }
    }

    private static final class Ingredient
    {
        private final int itemId;
        private final int quantity;

        private Ingredient(int itemId, int quantity)
        {
            this.itemId = itemId;
            this.quantity = quantity;
        }
    }
}
