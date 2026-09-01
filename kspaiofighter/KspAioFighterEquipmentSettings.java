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
        syncRuntimeGearList(style);
    }

    void clear(KspAioFighterGearStyle style, EquipmentInventorySlot slot)
    {
        configManager.unsetConfiguration(KspAioFighterConfig.GROUP, key(style, slot));
        syncRuntimeGearList(style);
    }

    void clearStyle(KspAioFighterGearStyle style)
    {
        for (EquipmentInventorySlot slot : CONFIGURABLE_SLOTS)
        {
            configManager.unsetConfiguration(KspAioFighterConfig.GROUP, key(style, slot));
        }
        syncRuntimeGearList(style);
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

    /**
     * The side-panel slot selections are the authoritative gear configuration.
     *
     * The fighter script still consumes the historical per-style CSV keys internally,
     * so those keys are now treated strictly as a compatibility/runtime mirror. Never
     * import them back into the side-panel slots: an old hidden CSV must not override
     * gear the user selected in the side panel.
     */
    void syncPanelGearToRuntime()
    {
        for (KspAioFighterGearStyle style : KspAioFighterGearStyle.values())
        {
            syncRuntimeGearList(style);
        }
    }

    private void syncRuntimeGearList(KspAioFighterGearStyle style)
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
