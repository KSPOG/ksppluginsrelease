package net.runelite.client.plugins.microbot.kspbryophyta;

import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemComposition;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.microbot.Microbot;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Persists user-selected equipment overrides independently for each Bryophyta strategy.
 *
 * Missing override = use the strategy default.
 * -1 override      = intentionally leave the slot empty.
 * positive override = use the exact item id selected in the equipment side panel.
 */
@Singleton
final class BryophytaEquipmentSettings
{
    static final String CONFIG_GROUP = "kspbryophyta";
    static final int EXPLICIT_EMPTY = -1;

    private final ConfigManager configManager;
    private final Client client;

    @Inject
    BryophytaEquipmentSettings(ConfigManager configManager, Client client)
    {
        this.configManager = configManager;
        this.client = client;
    }

    Integer getOverrideItemId(BryophytaStrategy strategy, EquipmentInventorySlot slot)
    {
        String raw = configManager.getConfiguration(CONFIG_GROUP, key(strategy, slot));
        if (raw == null || raw.trim().isEmpty())
        {
            return null;
        }

        try
        {
            return Integer.parseInt(raw.trim());
        }
        catch (NumberFormatException ex)
        {
            return null;
        }
    }


    boolean isExplicitEmpty(BryophytaStrategy strategy, EquipmentInventorySlot slot)
    {
        Integer override = getOverrideItemId(strategy, slot);
        return override != null && override == EXPLICIT_EMPTY;
    }

    void setItem(BryophytaStrategy strategy, EquipmentInventorySlot slot, int itemId)
    {
        configManager.setConfiguration(CONFIG_GROUP, key(strategy, slot), itemId);
    }

    void setEmpty(BryophytaStrategy strategy, EquipmentInventorySlot slot)
    {
        configManager.setConfiguration(CONFIG_GROUP, key(strategy, slot), EXPLICIT_EMPTY);
    }

    void resetSlot(BryophytaStrategy strategy, EquipmentInventorySlot slot)
    {
        configManager.unsetConfiguration(CONFIG_GROUP, key(strategy, slot));
    }

    void resetStrategy(BryophytaStrategy strategy)
    {
        for (EquipmentInventorySlot slot : BryophytaLoadout.CONFIGURABLE_SLOTS)
        {
            resetSlot(strategy, slot);
        }
    }

    Map<EquipmentInventorySlot, String> equipmentFor(BryophytaStrategy strategy)
    {
        LinkedHashMap<EquipmentInventorySlot, String> resolved =
                new LinkedHashMap<>(BryophytaLoadout.defaultEquipmentFor(strategy));

        for (EquipmentInventorySlot slot : BryophytaLoadout.CONFIGURABLE_SLOTS)
        {
            Integer override = getOverrideItemId(strategy, slot);
            if (override == null)
            {
                continue;
            }

            if (override == EXPLICIT_EMPTY)
            {
                resolved.remove(slot);
                continue;
            }

            String itemName = itemName(override);
            if (itemName != null && !itemName.isBlank() && !"null".equalsIgnoreCase(itemName))
            {
                resolved.put(slot, itemName);
            }
        }

        return resolved;
    }

    String mainWeaponFor(BryophytaStrategy strategy)
    {
        return equipmentFor(strategy).getOrDefault(EquipmentInventorySlot.WEAPON, "");
    }

    String displayNameFor(BryophytaStrategy strategy, EquipmentInventorySlot slot)
    {
        Integer override = getOverrideItemId(strategy, slot);
        if (override != null)
        {
            if (override == EXPLICIT_EMPTY)
            {
                return "Empty";
            }

            String customName = itemName(override);
            if (customName != null)
            {
                return customName;
            }
        }

        return BryophytaLoadout.defaultEquipmentFor(strategy).getOrDefault(slot, "Empty");
    }

    Integer selectedCustomItemId(BryophytaStrategy strategy, EquipmentInventorySlot slot)
    {
        Integer override = getOverrideItemId(strategy, slot);
        return override != null && override > 0 ? override : null;
    }

    private String itemName(int itemId)
    {
        if (itemId <= 0)
        {
            return null;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            ItemComposition composition = client.getItemDefinition(itemId);
            return composition == null ? null : composition.getName();
        }).orElse(null);
    }

    private static String key(BryophytaStrategy strategy, EquipmentInventorySlot slot)
    {
        return "gear." + strategy.name().toLowerCase() + "." + slot.name().toLowerCase();
    }
}
