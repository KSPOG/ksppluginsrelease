package net.runelite.client.plugins.microbot.f2pprocessingfactory;

import net.runelite.api.Skill;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

public enum FactoryRecipe
{
    CHOCOLATE_DUST(
        "Chocolate dust", Skill.COOKING, 1,
        "Chocolate dust", 2_600, "Make", 1, null,
        RecipeInput.tool("Knife"),
        RecipeInput.consumed("Chocolate bar", 1)
    ),
    PIE_SHELL(
        "Pie shells", Skill.COOKING, 1,
        "Pie shell", 1_650, "Make", 1, null,
        RecipeInput.consumed("Pie dish", 1),
        RecipeInput.consumed("Pastry dough", 1)
    ),
    UNCOOKED_REDBERRY_PIE(
        "Uncooked redberry pies", Skill.COOKING, 10,
        "Uncooked berry pie", 1_650, "Make", 1, null,
        RecipeInput.consumed("Pie shell", 1),
        RecipeInput.consumed("Redberries", 1)
    ),
    UNCOOKED_MEAT_PIE(
        "Uncooked meat pies", Skill.COOKING, 20,
        "Uncooked meat pie", 1_650, "Make", 1, null,
        RecipeInput.consumed("Pie shell", 1),
        RecipeInput.consumed("Cooked meat", 1)
    ),
    UNCOOKED_APPLE_PIE(
        "Uncooked apple pies", Skill.COOKING, 30,
        "Uncooked apple pie", 1_650, "Make", 1, null,
        RecipeInput.consumed("Pie shell", 1),
        RecipeInput.consumed("Cooking apple", 1)
    ),
    MEAT_PIZZA(
        "Meat pizzas", Skill.COOKING, 45,
        "Meat pizza", 2_500, "Make", 1, null,
        RecipeInput.consumed("Plain pizza", 1),
        RecipeInput.consumed("Cooked meat", 1)
    ),
    ANCHOVY_PIZZA(
        "Anchovy pizzas", Skill.COOKING, 55,
        "Anchovy pizza", 2_500, "Make", 1, null,
        RecipeInput.consumed("Plain pizza", 1),
        RecipeInput.consumed("Anchovies", 1)
    ),
    STRING_GOLD_AMULET(
        "String gold amulets", Skill.CRAFTING, 1,
        "Gold amulet", 2_500, "String", 1, null,
        RecipeInput.consumed("Gold amulet (u)", 1),
        RecipeInput.consumed("Ball of wool", 1)
    ),
    STRING_SAPPHIRE_AMULET(
        "String sapphire amulets", Skill.CRAFTING, 1,
        "Sapphire amulet", 2_500, "String", 1, null,
        RecipeInput.consumed("Sapphire amulet (u)", 1),
        RecipeInput.consumed("Ball of wool", 1)
    ),
    STRING_EMERALD_AMULET(
        "String emerald amulets", Skill.CRAFTING, 1,
        "Emerald amulet", 2_500, "String", 1, null,
        RecipeInput.consumed("Emerald amulet (u)", 1),
        RecipeInput.consumed("Ball of wool", 1)
    ),
    STRING_RUBY_AMULET(
        "String ruby amulets", Skill.CRAFTING, 1,
        "Ruby amulet", 2_500, "String", 1, null,
        RecipeInput.consumed("Ruby amulet (u)", 1),
        RecipeInput.consumed("Ball of wool", 1)
    ),
    STRING_DIAMOND_AMULET(
        "String diamond amulets", Skill.CRAFTING, 1,
        "Diamond amulet", 2_500, "String", 1, null,
        RecipeInput.consumed("Diamond amulet (u)", 1),
        RecipeInput.consumed("Ball of wool", 1)
    ),
    CUT_SAPPHIRE(
        "Cut sapphires", Skill.CRAFTING, 20,
        "Sapphire", 2_780, "Cut", 1, null,
        RecipeInput.tool("Chisel"),
        RecipeInput.consumed("Uncut sapphire", 1)
    ),
    CUT_EMERALD(
        "Cut emeralds", Skill.CRAFTING, 27,
        "Emerald", 2_780, "Cut", 1, null,
        RecipeInput.tool("Chisel"),
        RecipeInput.consumed("Uncut emerald", 1)
    ),
    CUT_RUBY(
        "Cut rubies", Skill.CRAFTING, 34,
        "Ruby", 2_780, "Cut", 1, null,
        RecipeInput.tool("Chisel"),
        RecipeInput.consumed("Uncut ruby", 1)
    ),
    CUT_DIAMOND(
        "Cut diamonds", Skill.CRAFTING, 43,
        "Diamond", 2_780, "Cut", 1, null,
        RecipeInput.tool("Chisel"),
        RecipeInput.consumed("Uncut diamond", 1)
    ),

    STRING_UNBLESSED_SYMBOL(
        "String unblessed symbols", Skill.CRAFTING, 1,
        "Unblessed symbol", 2_500, "String", 1, null,
        RecipeInput.consumed("Unstrung symbol", 1),
        RecipeInput.consumed("Ball of wool", 1)
    ),
    CHOCOLATE_CAKE(
        "Chocolate cakes", Skill.COOKING, 50,
        "Chocolate cake", 2_400, "Make", 1, null,
        RecipeInput.consumed("Cake", 1),
        RecipeInput.consumed("Chocolate bar", 1)
    ),
    CHOCOLATE_CAKE_DUST(
        "Chocolate cakes (dust)", Skill.COOKING, 50,
        "Chocolate cake", 2_400, "Make", 1, null,
        RecipeInput.consumed("Cake", 1),
        RecipeInput.consumed("Chocolate dust", 1)
    ),
    PASTRY_DOUGH(
        "Pastry dough", Skill.COOKING, 1,
        "Pastry dough", 1_500, "Make", 3, "Pastry dough",
        RecipeInput.consumed("Pot of flour", 1),
        RecipeInput.consumed("Jug of water", 1)
    ),
    BREAD_DOUGH(
        "Bread dough", Skill.COOKING, 1,
        "Bread dough", 1_500, "Make", 3, "Bread dough",
        RecipeInput.consumed("Pot of flour", 1),
        RecipeInput.consumed("Jug of water", 1)
    ),
    PIZZA_BASE(
        "Pizza bases", Skill.COOKING, 35,
        "Pizza base", 1_500, "Make", 3, "Pizza base",
        RecipeInput.consumed("Pot of flour", 1),
        RecipeInput.consumed("Jug of water", 1)
    ),
    SOFT_CLAY(
        "Soft clay", Skill.CRAFTING, 1,
        "Soft clay", 1_800, "Make", 2, null,
        RecipeInput.consumed("Clay", 1),
        RecipeInput.consumed("Jug of water", 1)
    ),
    SOFT_CLAY_BUCKET(
        "Soft clay (bucket)", Skill.CRAFTING, 1,
        "Soft clay", 1_800, "Make", 2, null,
        RecipeInput.consumed("Clay", 1),
        RecipeInput.consumed("Bucket of water", 1)
    ),
    ORANGE_DYE(
        "Orange dye", Skill.CRAFTING, 1,
        "Orange dye", 2_400, "Mix", 1, null,
        RecipeInput.consumed("Red dye", 1),
        RecipeInput.consumed("Yellow dye", 1)
    ),
    GREEN_DYE(
        "Green dye", Skill.CRAFTING, 1,
        "Green dye", 2_400, "Mix", 1, null,
        RecipeInput.consumed("Blue dye", 1),
        RecipeInput.consumed("Yellow dye", 1)
    ),
    PURPLE_DYE(
        "Purple dye", Skill.CRAFTING, 1,
        "Purple dye", 2_400, "Mix", 1, null,
        RecipeInput.consumed("Red dye", 1),
        RecipeInput.consumed("Blue dye", 1)
    ),
    RED_CAPE(
        "Red capes", Skill.CRAFTING, 1,
        "Red cape", 2_400, "Dye", 1, null,
        RecipeInput.consumed("Black cape", 1),
        RecipeInput.consumed("Red dye", 1)
    ),
    BLUE_CAPE(
        "Blue capes", Skill.CRAFTING, 1,
        "Blue cape", 2_400, "Dye", 1, null,
        RecipeInput.consumed("Black cape", 1),
        RecipeInput.consumed("Blue dye", 1)
    ),
    YELLOW_CAPE(
        "Yellow capes", Skill.CRAFTING, 1,
        "Yellow cape", 2_400, "Dye", 1, null,
        RecipeInput.consumed("Black cape", 1),
        RecipeInput.consumed("Yellow dye", 1)
    ),
    ORANGE_CAPE(
        "Orange capes", Skill.CRAFTING, 1,
        "Orange cape", 2_400, "Dye", 1, null,
        RecipeInput.consumed("Black cape", 1),
        RecipeInput.consumed("Orange dye", 1)
    ),
    GREEN_CAPE(
        "Green capes", Skill.CRAFTING, 1,
        "Green cape", 2_400, "Dye", 1, null,
        RecipeInput.consumed("Black cape", 1),
        RecipeInput.consumed("Green dye", 1)
    ),
    PURPLE_CAPE(
        "Purple capes", Skill.CRAFTING, 1,
        "Purple cape", 2_400, "Dye", 1, null,
        RecipeInput.consumed("Black cape", 1),
        RecipeInput.consumed("Purple dye", 1)
    ),
    LEATHER_GLOVES(
        "Leather gloves", Skill.CRAFTING, 1,
        "Leather gloves", 1_800, "Craft", 1, "Leather gloves",
        RecipeInput.tool("Needle"),
        RecipeInput.consumed("Leather", 1),
        RecipeInput.consumedStackablePerOutputs("Thread", 1, 5)
    ),
    LEATHER_BOOTS(
        "Leather boots", Skill.CRAFTING, 7,
        "Leather boots", 1_800, "Craft", 1, "Leather boots",
        RecipeInput.tool("Needle"),
        RecipeInput.consumed("Leather", 1),
        RecipeInput.consumedStackablePerOutputs("Thread", 1, 5)
    ),
    LEATHER_COWL(
        "Leather cowl", Skill.CRAFTING, 9,
        "Leather cowl", 1_800, "Craft", 1, "Leather cowl",
        RecipeInput.tool("Needle"),
        RecipeInput.consumed("Leather", 1),
        RecipeInput.consumedStackablePerOutputs("Thread", 1, 5)
    ),
    LEATHER_VAMBRACES(
        "Leather vambraces", Skill.CRAFTING, 11,
        "Leather vambraces", 1_800, "Craft", 1, "Leather vambraces",
        RecipeInput.tool("Needle"),
        RecipeInput.consumed("Leather", 1),
        RecipeInput.consumedStackablePerOutputs("Thread", 1, 5)
    ),
    LEATHER_BODY(
        "Leather body", Skill.CRAFTING, 14,
        "Leather body", 1_800, "Craft", 1, "Leather body",
        RecipeInput.tool("Needle"),
        RecipeInput.consumed("Leather", 1),
        RecipeInput.consumedStackablePerOutputs("Thread", 1, 5)
    ),
    LEATHER_CHAPS(
        "Leather chaps", Skill.CRAFTING, 18,
        "Leather chaps", 1_800, "Craft", 1, "Leather chaps",
        RecipeInput.tool("Needle"),
        RecipeInput.consumed("Leather", 1),
        RecipeInput.consumedStackablePerOutputs("Thread", 1, 5)
    ),
    HARDLEATHER_BODY(
        "Hardleather body", Skill.CRAFTING, 28,
        "Hardleather body", 1_800, "Craft", 1, "Hardleather body",
        RecipeInput.tool("Needle"),
        RecipeInput.consumed("Hard leather", 1),
        RecipeInput.consumedStackablePerOutputs("Thread", 1, 5)
    ),

    GUAM_POTION_UNF(
        "Guam potion (unf)", Skill.HERBLORE, 3,
        "Guam potion (unf)", 2_500, "Make", 1, null, true,
        RecipeInput.consumed("Guam leaf", 1),
        RecipeInput.consumed("Vial of water", 1)
    ),
    MARRENTILL_POTION_UNF(
        "Marrentill potion (unf)", Skill.HERBLORE, 5,
        "Marrentill potion (unf)", 2_500, "Make", 1, null, true,
        RecipeInput.consumed("Marrentill", 1),
        RecipeInput.consumed("Vial of water", 1)
    ),
    TARROMIN_POTION_UNF(
        "Tarromin potion (unf)", Skill.HERBLORE, 12,
        "Tarromin potion (unf)", 2_500, "Make", 1, null, true,
        RecipeInput.consumed("Tarromin", 1),
        RecipeInput.consumed("Vial of water", 1)
    ),
    HARRALANDER_POTION_UNF(
        "Harralander potion (unf)", Skill.HERBLORE, 22,
        "Harralander potion (unf)", 2_500, "Make", 1, null, true,
        RecipeInput.consumed("Harralander", 1),
        RecipeInput.consumed("Vial of water", 1)
    ),
    RANARR_POTION_UNF(
        "Ranarr potion (unf)", Skill.HERBLORE, 30,
        "Ranarr potion (unf)", 2_500, "Make", 1, null, true,
        RecipeInput.consumed("Ranarr weed", 1),
        RecipeInput.consumed("Vial of water", 1)
    ),
    TOADFLAX_POTION_UNF(
        "Toadflax potion (unf)", Skill.HERBLORE, 34,
        "Toadflax potion (unf)", 2_500, "Make", 1, null, true,
        RecipeInput.consumed("Toadflax", 1),
        RecipeInput.consumed("Vial of water", 1)
    ),
    IRIT_POTION_UNF(
        "Irit potion (unf)", Skill.HERBLORE, 45,
        "Irit potion (unf)", 2_500, "Make", 1, null, true,
        RecipeInput.consumed("Irit leaf", 1),
        RecipeInput.consumed("Vial of water", 1)
    ),
    AVANTOE_POTION_UNF(
        "Avantoe potion (unf)", Skill.HERBLORE, 50,
        "Avantoe potion (unf)", 2_500, "Make", 1, null, true,
        RecipeInput.consumed("Avantoe", 1),
        RecipeInput.consumed("Vial of water", 1)
    ),
    KWUARM_POTION_UNF(
        "Kwuarm potion (unf)", Skill.HERBLORE, 55,
        "Kwuarm potion (unf)", 2_500, "Make", 1, null, true,
        RecipeInput.consumed("Kwuarm", 1),
        RecipeInput.consumed("Vial of water", 1)
    ),
    HUASCA_POTION_UNF(
        "Huasca potion (unf)", Skill.HERBLORE, 58,
        "Huasca potion (unf)", 2_500, "Make", 1, null, true,
        RecipeInput.consumed("Huasca", 1),
        RecipeInput.consumed("Vial of water", 1)
    ),
    SNAPDRAGON_POTION_UNF(
        "Snapdragon potion (unf)", Skill.HERBLORE, 63,
        "Snapdragon potion (unf)", 2_500, "Make", 1, null, true,
        RecipeInput.consumed("Snapdragon", 1),
        RecipeInput.consumed("Vial of water", 1)
    ),
    CADANTINE_POTION_UNF(
        "Cadantine potion (unf)", Skill.HERBLORE, 66,
        "Cadantine potion (unf)", 2_500, "Make", 1, null, true,
        RecipeInput.consumed("Cadantine", 1),
        RecipeInput.consumed("Vial of water", 1)
    ),
    LANTADYME_POTION_UNF(
        "Lantadyme potion (unf)", Skill.HERBLORE, 69,
        "Lantadyme potion (unf)", 2_500, "Make", 1, null, true,
        RecipeInput.consumed("Lantadyme", 1),
        RecipeInput.consumed("Vial of water", 1)
    ),
    DWARF_WEED_POTION_UNF(
        "Dwarf weed potion (unf)", Skill.HERBLORE, 72,
        "Dwarf weed potion (unf)", 2_500, "Make", 1, null, true,
        RecipeInput.consumed("Dwarf weed", 1),
        RecipeInput.consumed("Vial of water", 1)
    ),
    TORSTOL_POTION_UNF(
        "Torstol potion (unf)", Skill.HERBLORE, 78,
        "Torstol potion (unf)", 2_500, "Make", 1, null, true,
        RecipeInput.consumed("Torstol", 1),
        RecipeInput.consumed("Vial of water", 1)
    ),

    CLEAN_GUAM(
        "Clean guam", Skill.HERBLORE, 3,
        "Guam leaf", 5_000, "Clean", 1, null, true,
        RecipeInput.consumed("Grimy guam leaf", 1)
    ),
    CLEAN_MARRENTILL(
        "Clean marrentill", Skill.HERBLORE, 5,
        "Marrentill", 5_000, "Clean", 1, null, true,
        RecipeInput.consumed("Grimy marrentill", 1)
    ),
    CLEAN_TARROMIN(
        "Clean tarromin", Skill.HERBLORE, 11,
        "Tarromin", 5_000, "Clean", 1, null, true,
        RecipeInput.consumed("Grimy tarromin", 1)
    ),
    CLEAN_HARRALANDER(
        "Clean harralander", Skill.HERBLORE, 20,
        "Harralander", 5_000, "Clean", 1, null, true,
        RecipeInput.consumed("Grimy harralander", 1)
    ),
    CLEAN_RANARR(
        "Clean ranarr", Skill.HERBLORE, 25,
        "Ranarr weed", 5_000, "Clean", 1, null, true,
        RecipeInput.consumed("Grimy ranarr weed", 1)
    ),
    CLEAN_TOADFLAX(
        "Clean toadflax", Skill.HERBLORE, 30,
        "Toadflax", 5_000, "Clean", 1, null, true,
        RecipeInput.consumed("Grimy toadflax", 1)
    ),
    CLEAN_IRIT(
        "Clean irit", Skill.HERBLORE, 40,
        "Irit leaf", 5_000, "Clean", 1, null, true,
        RecipeInput.consumed("Grimy irit leaf", 1)
    ),
    CLEAN_AVANTOE(
        "Clean avantoe", Skill.HERBLORE, 48,
        "Avantoe", 5_000, "Clean", 1, null, true,
        RecipeInput.consumed("Grimy avantoe", 1)
    ),
    CLEAN_KWUARM(
        "Clean kwuarm", Skill.HERBLORE, 54,
        "Kwuarm", 5_000, "Clean", 1, null, true,
        RecipeInput.consumed("Grimy kwuarm", 1)
    ),
    CLEAN_HUASCA(
        "Clean huasca", Skill.HERBLORE, 58,
        "Huasca", 5_000, "Clean", 1, null, true,
        RecipeInput.consumed("Grimy huasca", 1)
    ),
    CLEAN_SNAPDRAGON(
        "Clean snapdragon", Skill.HERBLORE, 59,
        "Snapdragon", 5_000, "Clean", 1, null, true,
        RecipeInput.consumed("Grimy snapdragon", 1)
    ),
    CLEAN_CADANTINE(
        "Clean cadantine", Skill.HERBLORE, 65,
        "Cadantine", 5_000, "Clean", 1, null, true,
        RecipeInput.consumed("Grimy cadantine", 1)
    ),
    CLEAN_LANTADYME(
        "Clean lantadyme", Skill.HERBLORE, 67,
        "Lantadyme", 5_000, "Clean", 1, null, true,
        RecipeInput.consumed("Grimy lantadyme", 1)
    ),
    CLEAN_DWARF_WEED(
        "Clean dwarf weed", Skill.HERBLORE, 70,
        "Dwarf weed", 5_000, "Clean", 1, null, true,
        RecipeInput.consumed("Grimy dwarf weed", 1)
    ),
    CLEAN_TORSTOL(
        "Clean torstol", Skill.HERBLORE, 75,
        "Torstol", 5_000, "Clean", 1, null, true,
        RecipeInput.consumed("Grimy torstol", 1)
    );

    private final String displayName;
    private final Skill requiredSkill;
    private final int requiredLevel;
    private final RecipeInput interactionItemA;
    private final RecipeInput interactionItemB;
    private final String outputItemName;
    private final int estimatedUnitsPerHour;
    private final String processingAction;
    private final int completionSlotsPerOutput;
    private final String productionOptionText;
    private final boolean membersOnly;
    private final List<RecipeInput> inputs;

    FactoryRecipe(
        String displayName,
        Skill requiredSkill,
        int requiredLevel,
        String outputItemName,
        int estimatedUnitsPerHour,
        String processingAction,
        int completionSlotsPerOutput,
        String productionOptionText,
        RecipeInput... inputs)
    {
        this(
            displayName,
            requiredSkill,
            requiredLevel,
            outputItemName,
            estimatedUnitsPerHour,
            processingAction,
            completionSlotsPerOutput,
            productionOptionText,
            false,
            inputs
        );
    }

    FactoryRecipe(
        String displayName,
        Skill requiredSkill,
        int requiredLevel,
        String outputItemName,
        int estimatedUnitsPerHour,
        String processingAction,
        int completionSlotsPerOutput,
        String productionOptionText,
        boolean membersOnly,
        RecipeInput... inputs)
    {
        if (inputs == null || inputs.length < 1)
        {
            throw new IllegalArgumentException("A factory recipe needs at least one interaction input");
        }
        if (completionSlotsPerOutput <= 0)
        {
            throw new IllegalArgumentException("Completion slots per output must be positive");
        }

        this.displayName = displayName;
        this.requiredSkill = requiredSkill;
        this.requiredLevel = requiredLevel;
        this.interactionItemA = inputs[0];
        this.interactionItemB = inputs.length > 1 ? inputs[1] : null;
        this.outputItemName = outputItemName;
        this.estimatedUnitsPerHour = estimatedUnitsPerHour;
        this.processingAction = processingAction;
        this.completionSlotsPerOutput = completionSlotsPerOutput;
        this.productionOptionText = productionOptionText;
        this.membersOnly = membersOnly;
        this.inputs = Collections.unmodifiableList(Arrays.asList(inputs));
    }

    public String getDisplayName()
    {
        return displayName;
    }

    public Skill getRequiredSkill()
    {
        return requiredSkill;
    }

    public int getRequiredLevel()
    {
        return requiredLevel;
    }

    public RecipeInput getInteractionItemA()
    {
        return interactionItemA;
    }

    public RecipeInput getInteractionItemB()
    {
        return interactionItemB;
    }

    /**
     * True for recipes such as herb cleaning where the processing action is
     * performed directly on one inventory item instead of combining two items.
     */
    public boolean isSingleItemInteraction()
    {
        return interactionItemB == null;
    }

    public String getOutputItemName()
    {
        return outputItemName;
    }

    /**
     * Secondary tradeable outputs produced alongside the primary processed item.
     * These are real outputs of the processing action and are sold by the factory
     * after the primary output instead of being left behind in the bank.
     */
    public List<String> getSecondaryOutputItemNames()
    {
        switch (this)
        {
            case PASTRY_DOUGH:
            case BREAD_DOUGH:
            case PIZZA_BASE:
                return Arrays.asList("Pot", "Jug");
            case SOFT_CLAY:
                return Collections.singletonList("Jug");
            case SOFT_CLAY_BUCKET:
                return Collections.singletonList("Bucket");
            default:
                return Collections.emptyList();
        }
    }

    /**
     * All tradeable outputs that should be drained/sold for this recipe.
     * The primary output is always first, followed by any container byproducts.
     */
    public List<String> getSaleOutputItemNames()
    {
        List<String> outputs = new ArrayList<>();
        outputs.add(outputItemName);
        outputs.addAll(getSecondaryOutputItemNames());
        return Collections.unmodifiableList(outputs);
    }

    public int getEstimatedUnitsPerHour()
    {
        return estimatedUnitsPerHour;
    }

    public String getProcessingAction()
    {
        return processingAction;
    }

    public String getProductionOptionText()
    {
        return productionOptionText;
    }

    public boolean requiresProductionOptionSelection()
    {
        return productionOptionText != null && !productionOptionText.isBlank();
    }

    public boolean isMembersOnly()
    {
        return membersOnly;
    }

    public List<RecipeInput> getInputs()
    {
        return inputs;
    }

    public int getMaximumInventoryBatch()
    {
        int maximum = 0;
        for (int units = 1; units <= 28; units++)
        {
            int startSlots = 0;
            int persistentSlots = 0;
            for (RecipeInput input : inputs)
            {
                startSlots += input.getInventorySlotsForUnits(units);
                if (!input.isConsumed() || input.isStackable())
                {
                    persistentSlots++;
                }
            }
            int completionSlots = persistentSlots + (completionSlotsPerOutput * units);

            if (startSlots > 28 || completionSlots > 28)
            {
                break;
            }
            maximum = units;
        }
        return maximum;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
