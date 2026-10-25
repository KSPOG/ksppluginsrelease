package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.selling.buyscript;

/** Selects missing Crafting essentials before unrelated GE supplies. */
public final class CraftingSupplyPriorityPolicy {
    private CraftingSupplyPriorityPolicy() {}

    public static String choose(boolean chiselNeeded, boolean chiselPending,
                                String selectedGem, boolean gemNeeded, boolean gemPending) {
        if (chiselNeeded && !chiselPending) return "Chisel";
        if (selectedGem != null && gemNeeded && !gemPending) return selectedGem;
        return null;
    }

    /** Select one missing essential that can actually be bought. */
    public static String chooseAffordable(boolean chiselNeeded, boolean chiselPending,
            int chiselPrice, String gem, boolean gemNeeded, boolean gemPending,
            int gemPrice, long coins) {
        if (coins <= 0L) return null;
        if (chiselNeeded && !chiselPending && chiselPrice > 1 && coins >= chiselPrice)
            return "Chisel";
        if (gem != null && gemNeeded && !gemPending && gemPrice > 1 && coins >= gemPrice)
            return gem;
        return null;
    }
}
