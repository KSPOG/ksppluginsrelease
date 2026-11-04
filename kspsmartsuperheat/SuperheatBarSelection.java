package net.runelite.client.plugins.microbot.kspsmartsuperheat;

/** User-selected recipe lock. Automatic preserves market-based selection. */
public enum SuperheatBarSelection
{
    AUTOMATIC("Automatic (best available)", null),
    BRONZE("Bronze bar", SuperheatRecipe.BRONZE),
    IRON("Iron bar", SuperheatRecipe.IRON),
    SILVER("Silver bar", SuperheatRecipe.SILVER),
    LEAD("Lead bar (members)", SuperheatRecipe.LEAD),
    STEEL("Steel bar", SuperheatRecipe.STEEL),
    GOLD("Gold bar", SuperheatRecipe.GOLD),
    MITHRIL("Mithril bar", SuperheatRecipe.MITHRIL),
    ADAMANTITE("Adamantite bar", SuperheatRecipe.ADAMANTITE),
    CUPRONICKEL("Cupronickel bar (members)", SuperheatRecipe.CUPRONICKEL),
    RUNITE("Runite bar", SuperheatRecipe.RUNITE);

    private final String label;
    private final SuperheatRecipe recipe;

    SuperheatBarSelection(String label, SuperheatRecipe recipe)
    {
        this.label = label;
        this.recipe = recipe;
    }

    public SuperheatRecipe getRecipe() { return recipe; }
    public boolean isAutomatic() { return recipe == null; }

    @Override
    public String toString() { return label; }
}
