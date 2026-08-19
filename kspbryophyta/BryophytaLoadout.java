package net.runelite.client.plugins.microbot.kspbryophyta;

import net.runelite.api.EquipmentInventorySlot;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Built-in Bryophyta equipment presets used as defaults by the side-panel editor.
 * User overrides are stored by {@link BryophytaEquipmentSettings} and are applied
 * on top of these defaults.
 */
final class BryophytaLoadout
{
    static final EquipmentInventorySlot[] CONFIGURABLE_SLOTS = {
            EquipmentInventorySlot.HEAD,
            EquipmentInventorySlot.CAPE,
            EquipmentInventorySlot.AMULET,
            EquipmentInventorySlot.AMMO,
            EquipmentInventorySlot.WEAPON,
            EquipmentInventorySlot.BODY,
            EquipmentInventorySlot.SHIELD,
            EquipmentInventorySlot.LEGS,
            EquipmentInventorySlot.GLOVES,
            EquipmentInventorySlot.BOOTS,
            EquipmentInventorySlot.RING
    };

    private BryophytaLoadout()
    {
    }

    static Map<EquipmentInventorySlot, String> defaultEquipmentFor(BryophytaStrategy strategy)
    {
        LinkedHashMap<EquipmentInventorySlot, String> gear = new LinkedHashMap<>();

        switch (strategy)
        {
            case MELEE:
                gear.put(EquipmentInventorySlot.HEAD, "Rune full helm");
                gear.put(EquipmentInventorySlot.CAPE, "Red cape");
                gear.put(EquipmentInventorySlot.AMULET, "Amulet of strength");
                gear.put(EquipmentInventorySlot.WEAPON, "Rune scimitar");
                gear.put(EquipmentInventorySlot.BODY, "Green d'hide body");
                gear.put(EquipmentInventorySlot.SHIELD, "Rune kiteshield");
                gear.put(EquipmentInventorySlot.LEGS, "Green d'hide chaps");
                gear.put(EquipmentInventorySlot.GLOVES, "Green d'hide vambraces");
                gear.put(EquipmentInventorySlot.BOOTS, "Fancy boots");
                break;

            case RANGED:
                gear.put(EquipmentInventorySlot.HEAD, "Coif");
                gear.put(EquipmentInventorySlot.CAPE, "Red cape");
                gear.put(EquipmentInventorySlot.AMULET, "Amulet of power");
                gear.put(EquipmentInventorySlot.WEAPON, "Maple shortbow");
                gear.put(EquipmentInventorySlot.BODY, "Green d'hide body");
                gear.put(EquipmentInventorySlot.LEGS, "Green d'hide chaps");
                gear.put(EquipmentInventorySlot.GLOVES, "Green d'hide vambraces");
                gear.put(EquipmentInventorySlot.BOOTS, "Fancy boots");
                gear.put(EquipmentInventorySlot.AMMO, "Adamant arrow");
                break;

            case MAGIC_FIRE:
                gear.put(EquipmentInventorySlot.HEAD, "Blue wizard hat");
                gear.put(EquipmentInventorySlot.CAPE, "Red cape");
                gear.put(EquipmentInventorySlot.AMULET, "Amulet of magic");
                gear.put(EquipmentInventorySlot.WEAPON, "Staff of fire");
                gear.put(EquipmentInventorySlot.BODY, "Blue wizard robe");
                gear.put(EquipmentInventorySlot.SHIELD, "Anti-dragon shield");
                gear.put(EquipmentInventorySlot.LEGS, "Zamorak monk bottom");
                gear.put(EquipmentInventorySlot.GLOVES, "Leather vambraces");
                gear.put(EquipmentInventorySlot.BOOTS, "Leather boots");
                break;

            default:
                throw new IllegalStateException("Unsupported Bryophyta strategy: " + strategy);
        }

        return gear;
    }
}
