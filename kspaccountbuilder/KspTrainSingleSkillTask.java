package net.runelite.client.plugins.microbot.kspaccountbuilder;

public enum KspTrainSingleSkillTask
{
    RUNE_ESSENCE("Rune Essence"),
    MINING("Mining"),
    WOODCUTTING("Woodcutting"),
    FIREMAKING("Firemaking"),
    FISHING("Fishing"),
    COOKING("Cooking"),
    CRAFTING("Crafting"),
    MELEE("Melee"),
    SMITHING("Smithing"),
    SMELTING("Smelting");

    private final String displayName;

    KspTrainSingleSkillTask(String displayName)
    {
        this.displayName = displayName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
