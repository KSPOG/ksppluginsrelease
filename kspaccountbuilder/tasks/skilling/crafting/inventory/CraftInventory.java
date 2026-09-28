package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.inventory;

public enum CraftInventory
{
    LEATHER_GLOVES(
            "Leather gloves",
            RecipeType.LEATHER,
            ingredient("Needle", 1, true),
            ingredient("Thread", 1, true),
            ingredient("Leather", 1, false)),
    GOLD_RING(
            "Gold ring",
            RecipeType.FURNACE,
            ingredient("Ring mould", 1, true),
            ingredient("Gold bar", 1, false)),
    GOLD_NECKLACE(
            "Gold necklace",
            RecipeType.FURNACE,
            ingredient("Necklace mould", 1, true),
            ingredient("Gold bar", 1, false)),
    CUT_SAPPHIRE(
            "Sapphire",
            RecipeType.GEM_CUTTING,
            ingredient("Chisel", 1, true),
            ingredient("Uncut sapphire", 1, false)),
    SAPPHIRE_RING(
            "Sapphire ring",
            RecipeType.FURNACE,
            ingredient("Ring mould", 1, true),
            ingredient("Sapphire", 13, false),
            ingredient("Gold bar", 13, false)),
    SAPPHIRE_NECKLACE(
            "Sapphire necklace",
            RecipeType.FURNACE,
            ingredient("Necklace mould", 1, true),
            ingredient("Sapphire", 13, false),
            ingredient("Gold bar", 13, false)),
    TIARA(
            "Tiara",
            RecipeType.FURNACE,
            ingredient("Tiara mould", 1, true),
            ingredient("Silver bar", 1, false)),
    CUT_EMERALD(
            "Emerald",
            RecipeType.GEM_CUTTING,
            ingredient("Chisel", 1, true),
            ingredient("Uncut emerald", 1, false)),
    EMERALD_RING(
            "Emerald ring",
            RecipeType.FURNACE,
            ingredient("Ring mould", 1, true),
            ingredient("Emerald", 13, false),
            ingredient("Gold bar", 13, false)),
    EMERALD_NECKLACE(
            "Emerald necklace",
            RecipeType.FURNACE,
            ingredient("Necklace mould", 1, true),
            ingredient("Emerald", 13, false),
            ingredient("Gold bar", 13, false)),
    CUT_RUBY(
            "Ruby",
            RecipeType.GEM_CUTTING,
            ingredient("Chisel", 1, true),
            ingredient("Uncut ruby", 1, false)),
    RUBY_RING(
            "Ruby ring",
            RecipeType.FURNACE,
            ingredient("Ring mould", 1, true),
            ingredient("Ruby", 13, false),
            ingredient("Gold bar", 13, false)),
    RUBY_NECKLACE(
            "Ruby necklace",
            RecipeType.FURNACE,
            ingredient("Necklace mould", 1, true),
            ingredient("Ruby", 13, false),
            ingredient("Gold bar", 13, false)),
    CUT_DIAMOND(
            "Diamond",
            RecipeType.GEM_CUTTING,
            ingredient("Chisel", 1, true),
            ingredient("Uncut diamond", 1, false)),
    DIAMOND_RING(
            "Diamond ring",
            RecipeType.FURNACE,
            ingredient("Ring mould", 1, true),
            ingredient("Diamond", 13, false),
            ingredient("Gold bar", 13, false)),
    DIAMOND_NECKLACE(
            "Diamond necklace",
            RecipeType.FURNACE,
            ingredient("Necklace mould", 1, true),
            ingredient("Diamond", 13, false),
            ingredient("Gold bar", 13, false));

    private final String productName;
    private final RecipeType recipeType;
    private final Ingredient[] ingredients;

    CraftInventory(String productName, RecipeType recipeType, Ingredient... ingredients)
    {
        this.productName = productName;
        this.recipeType = recipeType;
        this.ingredients = ingredients;
    }

    public String getProductName()
    {
        return productName;
    }

    public RecipeType getRecipeType()
    {
        return recipeType;
    }

    public Ingredient[] getIngredients()
    {
        return ingredients.clone();
    }

    public Ingredient getTool()
    {
        for (Ingredient ingredient : ingredients)
        {
            if (ingredient.isReusable())
            {
                return ingredient;
            }
        }
        return null;
    }

    public boolean requiresFurnace()
    {
        return recipeType == RecipeType.FURNACE;
    }

    private static Ingredient ingredient(String itemName, int amount, boolean reusable)
    {
        return new Ingredient(itemName, amount, reusable);
    }

    public enum RecipeType
    {
        LEATHER,
        GEM_CUTTING,
        FURNACE
    }

    public static final class Ingredient
    {
        private final String itemName;
        private final int amount;
        private final boolean reusable;

        private Ingredient(String itemName, int amount, boolean reusable)
        {
            this.itemName = itemName;
            this.amount = amount;
            this.reusable = reusable;
        }

        public String getItemName()
        {
            return itemName;
        }

        public int getAmount()
        {
            return amount;
        }

        public boolean isReusable()
        {
            return reusable;
        }
    }
}
