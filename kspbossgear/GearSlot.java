package net.runelite.client.plugins.microbot.kspbossgear;

import java.util.Locale;

public enum GearSlot
{
    HEAD("Head"),
    AMULET("Neck"),
    CAPE("Cape"),
    BODY("Body"),
    LEGS("Legs"),
    WEAPON("Weapon"),
    SHIELD("Off-hand"),
    AMMO("Ammo"),
    GLOVES("Hands"),
    BOOTS("Feet"),
    RING("Ring"),
    SPECIAL_ATTACK("Spec"),
    UNKNOWN("Item");

    private static final GearSlot[] FALLBACK_ORDER = {
        HEAD, AMULET, CAPE, BODY, LEGS, WEAPON, SHIELD, AMMO, GLOVES, BOOTS, RING, SPECIAL_ATTACK
    };

    private final String displayName;

    GearSlot(String displayName)
    {
        this.displayName = displayName;
    }

    public String getDisplayName()
    {
        return displayName;
    }

    static GearSlot detect(String rawRow, int fallbackIndex)
    {
        String s = rawRow == null ? "" : rawRow.toLowerCase(Locale.ROOT);

        if (containsAny(s, "head slot", "helmet slot", "helm slot")) return HEAD;
        if (containsAny(s, "neck slot", "amulet slot")) return AMULET;
        if (containsAny(s, "cape slot")) return CAPE;
        if (containsAny(s, "body slot", "torso slot", "chest slot")) return BODY;
        if (containsAny(s, "leg slot", "legs slot")) return LEGS;
        if (containsAny(s, "weapon slot")) return WEAPON;
        if (containsAny(s, "shield slot", "off-hand slot", "offhand slot")) return SHIELD;
        if (containsAny(s, "ammo slot", "ammunition slot")) return AMMO;
        if (containsAny(s, "glove slot", "hands slot", "hand slot")) return GLOVES;
        if (containsAny(s, "boot slot", "feet slot", "foot slot")) return BOOTS;
        if (containsAny(s, "ring slot")) return RING;
        if (containsAny(s, "special attack", "spec weapon")) return SPECIAL_ATTACK;

        return fallbackIndex >= 0 && fallbackIndex < FALLBACK_ORDER.length
            ? FALLBACK_ORDER[fallbackIndex]
            : UNKNOWN;
    }

    private static boolean containsAny(String haystack, String... needles)
    {
        for (String needle : needles)
        {
            if (haystack.contains(needle)) return true;
        }
        return false;
    }
}
