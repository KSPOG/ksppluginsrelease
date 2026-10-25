package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.crafting.inventory;

public enum CraftInventory
{
    LEATHER_GLOVES(
            "Leather gloves",
            RecipeType.LEATHER,
            ingredient("Needle", 1, true, 1733),
            ingredient("Thread", 1, true, 1734),
            ingredient("Leather", 1, false, 1741)),
    GOLD_RING(
            "Gold ring",
            RecipeType.FURNACE,
            ingredient("Ring mould", 1, true, 1592),
            ingredient("Gold bar", 1, false, 2357)),
    GOLD_NECKLACE(
            "Gold necklace",
            RecipeType.FURNACE,
            ingredient("Necklace mould", 1, true, 1597),
            ingredient("Gold bar", 1, false, 2357)),
    CUT_SAPPHIRE(
            "Sapphire",
            RecipeType.GEM_CUTTING,
            ingredient("Chisel", 1, true, 1755),
            ingredient("Uncut sapphire", 1, false, 1623)),
    SAPPHIRE_RING(
            "Sapphire ring",
            RecipeType.FURNACE,
            ingredient("Ring mould", 1, true, 1592),
            ingredient("Sapphire", 13, false, 1607),
            ingredient("Gold bar", 13, false, 2357)),
    SAPPHIRE_NECKLACE(
            "Sapphire necklace",
            RecipeType.FURNACE,
            ingredient("Necklace mould", 1, true, 1597),
            ingredient("Sapphire", 13, false, 1607),
            ingredient("Gold bar", 13, false, 2357)),
    TIARA(
            "Tiara",
            RecipeType.FURNACE,
            ingredient("Tiara mould", 1, true, 5523),
            ingredient("Silver bar", 1, false, 2355)),
    CUT_EMERALD(
            "Emerald",
            RecipeType.GEM_CUTTING,
            ingredient("Chisel", 1, true, 1755),
            ingredient("Uncut emerald", 1, false, 1621)),
    EMERALD_RING(
            "Emerald ring",
            RecipeType.FURNACE,
            ingredient("Ring mould", 1, true, 1592),
            ingredient("Emerald", 13, false, 1605),
            ingredient("Gold bar", 13, false, 2357)),
    EMERALD_NECKLACE(
            "Emerald necklace",
            RecipeType.FURNACE,
            ingredient("Necklace mould", 1, true, 1597),
            ingredient("Emerald", 13, false, 1605),
            ingredient("Gold bar", 13, false, 2357)),
    CUT_RUBY(
            "Ruby",
            RecipeType.GEM_CUTTING,
            ingredient("Chisel", 1, true, 1755),
            ingredient("Uncut ruby", 1, false, 1619)),
    RUBY_RING(
            "Ruby ring",
            RecipeType.FURNACE,
            ingredient("Ring mould", 1, true, 1592),
            ingredient("Ruby", 13, false, 1603),
            ingredient("Gold bar", 13, false, 2357)),
    RUBY_NECKLACE(
            "Ruby necklace",
            RecipeType.FURNACE,
            ingredient("Necklace mould", 1, true, 1597),
            ingredient("Ruby", 13, false, 1603),
            ingredient("Gold bar", 13, false, 2357)),
    CUT_DIAMOND(
            "Diamond",
            RecipeType.GEM_CUTTING,
            ingredient("Chisel", 1, true, 1755),
            ingredient("Uncut diamond", 1, false, 1617)),
    DIAMOND_RING(
            "Diamond ring",
            RecipeType.FURNACE,
            ingredient("Ring mould", 1, true, 1592),
            ingredient("Diamond", 13, false, 1601),
            ingredient("Gold bar", 13, false, 2357)),
    DIAMOND_NECKLACE(
            "Diamond necklace",
            RecipeType.FURNACE,
            ingredient("Necklace mould", 1, true, 1597),
            ingredient("Diamond", 13, false, 1601),
            ingredient("Gold bar", 13, false, 2357));

    private final String productName;
    private final RecipeType recipeType;
    private final Ingredient[] ingredients;

    CraftInventory(String productName, RecipeType recipeType, Ingredient... ingredients)
    {
        this.productName = productName;
        this.recipeType = recipeType;
        this.ingredients = ingredients;
    }

    public String getProductName() { return productName; }

    public RecipeType getRecipeType() { return recipeType; }

    public Ingredient[] getIngredients() { return ingredients.clone(); }

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

    public boolean requiresFurnace() { return recipeType == RecipeType.FURNACE; }

    private static Ingredient ingredient(String itemName, int amount, boolean reusable, int itemId)
    {
        return new Ingredient(itemName, amount, reusable, itemId);
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
        private final int itemId;

        private Ingredient(String itemName, int amount, boolean reusable, int itemId)
        {
            this.itemName = itemName;
            this.amount = amount;
            this.reusable = reusable;
            this.itemId = itemId;
        }

        public String getItemName() { return itemName; }

        public int getAmount() { return amount; }

        public boolean isReusable() { return reusable; }

        public int getItemId() { return itemId; }
    }
}
