package net.runelite.client.plugins.microbot.kspbryophyta;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import net.runelite.client.plugins.skillcalculator.skills.MagicAction;

@Getter
@RequiredArgsConstructor
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

    @Override
    public String toString()
    {
        return displayName;
    }
}
