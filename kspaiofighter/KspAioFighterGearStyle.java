package net.runelite.client.plugins.microbot.kspaiofighter;

import net.runelite.api.Skill;

enum KspAioFighterGearStyle
{
    ATTACK(Skill.ATTACK, "Attack"),
    STRENGTH(Skill.STRENGTH, "Strength"),
    DEFENCE(Skill.DEFENCE, "Defence"),
    RANGED(Skill.RANGED, "Ranged"),
    MAGIC(Skill.MAGIC, "Magic");

    private final Skill skill;
    private final String displayName;

    KspAioFighterGearStyle(Skill skill, String displayName)
    {
        this.skill = skill;
        this.displayName = displayName;
    }

    Skill getSkill()
    {
        return skill;
    }

    String configKey()
    {
        switch (this)
        {
            case ATTACK: return "attackGear";
            case STRENGTH: return "strengthGear";
            case DEFENCE: return "defenceGear";
            case RANGED: return "rangedGear";
            case MAGIC: return "magicGear";
            default: throw new IllegalStateException("Unhandled gear style: " + this);
        }
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
