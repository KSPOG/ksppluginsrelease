package net.runelite.client.plugins.microbot.kspaiofighter;

import net.runelite.api.EquipmentInventorySlot;
import net.runelite.client.config.ConfigManager;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Map;

@Singleton
final class KspAioFighterEquipmentSettings
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

    private final ConfigManager configManager;

    @Inject
    KspAioFighterEquipmentSettings(ConfigManager configManager)
    {
        this.configManager = configManager;
    }

    String get(KspAioFighterGearStyle style, EquipmentInventorySlot slot)
    {
        String value = configManager.getConfiguration(KspAioFighterConfig.GROUP, key(style, slot));
        return value == null ? "" : value.trim();
    }

    void set(KspAioFighterGearStyle style, EquipmentInventorySlot slot, String itemName)
    {
        if (itemName == null || itemName.isBlank())
        {
            configManager.unsetConfiguration(KspAioFighterConfig.GROUP, key(style, slot));
        }
        else
        {
            configManager.setConfiguration(KspAioFighterConfig.GROUP, key(style, slot), itemName.trim());
        }
        syncLegacyGearList(style);
    }

    void clear(KspAioFighterGearStyle style, EquipmentInventorySlot slot)
    {
        configManager.unsetConfiguration(KspAioFighterConfig.GROUP, key(style, slot));
        syncLegacyGearList(style);
    }

    void clearStyle(KspAioFighterGearStyle style)
    {
        for (EquipmentInventorySlot slot : CONFIGURABLE_SLOTS)
        {
            configManager.unsetConfiguration(KspAioFighterConfig.GROUP, key(style, slot));
        }
        syncLegacyGearList(style);
    }

    Map<EquipmentInventorySlot, String> equipmentFor(KspAioFighterGearStyle style)
    {
        EnumMap<EquipmentInventorySlot, String> result = new EnumMap<>(EquipmentInventorySlot.class);
        for (EquipmentInventorySlot slot : CONFIGURABLE_SLOTS)
        {
            String value = get(style, slot);
            if (!value.isBlank()) result.put(slot, value);
        }
        return result;
    }

    void importLegacyIfNeeded()
    {
        for (KspAioFighterGearStyle style : KspAioFighterGearStyle.values())
        {
            if (!equipmentFor(style).isEmpty()) continue;
            String raw = configManager.getConfiguration(KspAioFighterConfig.GROUP, style.configKey());
            if (raw == null || raw.isBlank()) continue;

            // Preserve old setups until the user edits them in the new panel. The old CSV remains
            // authoritative for imported setups because slot inference cannot be done reliably by name alone.
        }
    }

    private void syncLegacyGearList(KspAioFighterGearStyle style)
    {
        List<String> names = new ArrayList<>();
        for (EquipmentInventorySlot slot : CONFIGURABLE_SLOTS)
        {
            String name = get(style, slot);
            if (!name.isBlank()) names.add(name);
        }
        configManager.setConfiguration(KspAioFighterConfig.GROUP, style.configKey(), String.join(", ", names));
    }

    private static String key(KspAioFighterGearStyle style, EquipmentInventorySlot slot)
    {
        return "gearSelection." + style.name().toLowerCase() + "." + slot.name().toLowerCase();
    }
}
