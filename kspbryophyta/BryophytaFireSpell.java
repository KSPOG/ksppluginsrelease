package net.runelite.client.plugins.microbot.kspbryophyta;

import net.runelite.client.plugins.microbot.util.magic.Rs2CombatSpells;

public enum BryophytaFireSpell
{
    FIRE_STRIKE("Fire Strike", Rs2CombatSpells.FIRE_STRIKE, 3, 2, "Mind rune"),
    FIRE_BOLT("Fire Bolt", Rs2CombatSpells.FIRE_BOLT, 3, 4, "Chaos rune"),
    FIRE_BLAST("Fire Blast", Rs2CombatSpells.FIRE_BLAST, 4, 5, "Death rune"),
    FIRE_WAVE("Fire Wave", Rs2CombatSpells.FIRE_WAVE, 5, 7, "Blood rune"),
    FIRE_SURGE("Fire Surge", Rs2CombatSpells.FIRE_SURGE, 7, 10, "Wrath rune");

    private final String displayName;
    private final Rs2CombatSpells combatSpell;
    private final int airRunesPerCast;
    private final int fireRunesPerCast;
    private final String catalystRuneName;

    BryophytaFireSpell(
            String displayName,
            Rs2CombatSpells combatSpell,
            int airRunesPerCast,
            int fireRunesPerCast,
            String catalystRuneName)
    {
        this.displayName = displayName;
        this.combatSpell = combatSpell;
        this.airRunesPerCast = airRunesPerCast;
        this.fireRunesPerCast = fireRunesPerCast;
        this.catalystRuneName = catalystRuneName;
    }

    public Rs2CombatSpells getCombatSpell()
    {
        return combatSpell;
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
