package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.runeessence.runeessscript;

final class EssenceBankCompletionPolicy
{
    private EssenceBankCompletionPolicy() {}

    static boolean shouldContinueBanking(EssenceState state, boolean inventoryFull, boolean hasPickaxe)
    {
        return state == EssenceState.BANKING || inventoryFull || !hasPickaxe;
    }

    static boolean readyToClose(int emptySlots, boolean pickaxeInInventory)
    {
        return emptySlots >= (pickaxeInInventory ? 27 : 28);
    }
}
