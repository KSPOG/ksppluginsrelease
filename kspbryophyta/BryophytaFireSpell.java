package net.runelite.client.plugins.microbot.kspbryophyta;

import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

public enum BryophytaFireSpell
{
    FIRE_STRIKE("Fire Strike", MagicAction.FIRE_STRIKE, 3, 2, "Mind rune"),
    FIRE_BOLT("Fire Bolt", MagicAction.FIRE_BOLT, 3, 4, "Chaos rune"),
    FIRE_BLAST("Fire Blast", MagicAction.FIRE_BLAST, 4, 5, "Death rune"),
    FIRE_WAVE("Fire Wave", MagicAction.FIRE_WAVE, 5, 7, "Blood rune"),
    FIRE_SURGE("Fire Surge", MagicAction.FIRE_SURGE, 7, 10, "Wrath rune");

    private final String displayName;
    private final MagicAction magicAction;
    private final int airRunesPerCast;
    private final int fireRunesPerCast;
    private final String catalystRuneName;

    BryophytaFireSpell(
            String displayName,
            MagicAction magicAction,
            int airRunesPerCast,
            int fireRunesPerCast,
            String catalystRuneName)
    {
        this.displayName = displayName;
        this.magicAction = magicAction;
        this.airRunesPerCast = airRunesPerCast;
        this.fireRunesPerCast = fireRunesPerCast;
        this.catalystRuneName = catalystRuneName;
    }

    public MagicAction getMagicAction()
    {
        return magicAction;
    }

    public int getAirRunesPerCast()
    {
        return airRunesPerCast;
    }

    public int getFireRunesPerCast()
    {
        return fireRunesPerCast;
    }

    public String getCatalystRuneName()
    {
        return catalystRuneName;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
