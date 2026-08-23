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

@Singleton
final class BryophytaEquipmentSettings {
    static final String CONFIG_GROUP = "kspbryophyta";
    static final int EXPLICIT_EMPTY = -1;

    private final ConfigManager configManager;
    private final Client client;

    @Inject
    BryophytaEquipmentSettings(ConfigManager configManager, Client client) {
        this.configManager = configManager;
        this.client = client;
    }

    Integer getOverrideItemId(BryophytaStrategy strategy, EquipmentInventorySlot slot) {
        String raw = configManager.getConfiguration(CONFIG_GROUP, key(strategy, slot));
        if (raw == null || raw.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(raw.trim());
        } catch (NumberFormatException ignored) {
            return null;
        }
    }

    boolean isExplicitEmpty(BryophytaStrategy strategy, EquipmentInventorySlot slot) {
        return Integer.valueOf(EXPLICIT_EMPTY).equals(getOverrideItemId(strategy, slot));
    }

    void setItem(BryophytaStrategy strategy, EquipmentInventorySlot slot, int itemId) {
        configManager.setConfiguration(CONFIG_GROUP, key(strategy, slot), itemId);
    }

    void setEmpty(BryophytaStrategy strategy, EquipmentInventorySlot slot) {
        setItem(strategy, slot, EXPLICIT_EMPTY);
    }

    void resetSlot(BryophytaStrategy strategy, EquipmentInventorySlot slot) {
        configManager.unsetConfiguration(CONFIG_GROUP, key(strategy, slot));
    }

    void resetStrategy(BryophytaStrategy strategy) {
        for (EquipmentInventorySlot slot : BryophytaLoadout.CONFIGURABLE_SLOTS) {
            resetSlot(strategy, slot);
        }
    }

    Map<EquipmentInventorySlot, String> equipmentFor(BryophytaStrategy strategy) {
        LinkedHashMap<EquipmentInventorySlot, String> equipment =
                new LinkedHashMap<>(BryophytaLoadout.defaultEquipmentFor(strategy));
        for (EquipmentInventorySlot slot : BryophytaLoadout.CONFIGURABLE_SLOTS) {
            Integer override = getOverrideItemId(strategy, slot);
            if (override == null) {
                continue;
            }
            if (override == EXPLICIT_EMPTY) {
                equipment.remove(slot);
                continue;
            }
            String name = itemName(override);
            if (isValidName(name)) {
                equipment.put(slot, name);
            }
        }
        return equipment;
    }

    String mainWeaponFor(BryophytaStrategy strategy) {
        return equipmentFor(strategy).getOrDefault(EquipmentInventorySlot.WEAPON, "");
    }

    String displayNameFor(BryophytaStrategy strategy, EquipmentInventorySlot slot) {
        Integer override = getOverrideItemId(strategy, slot);
        if (override == null) {
            return BryophytaLoadout.defaultEquipmentFor(strategy).getOrDefault(slot, "Empty");
        }
        if (override == EXPLICIT_EMPTY) {
            return "Empty";
        }
        String custom = itemName(override);
        return isValidName(custom) ? custom
                : BryophytaLoadout.defaultEquipmentFor(strategy).getOrDefault(slot, "Empty");
    }

    Integer selectedCustomItemId(BryophytaStrategy strategy, EquipmentInventorySlot slot) {
        Integer override = getOverrideItemId(strategy, slot);
        return override != null && override > 0 ? override : null;
    }

    private String itemName(int itemId) {
        if (itemId <= 0) {
            return null;
        }
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            ItemComposition item = client.getItemDefinition(itemId);
            return item == null ? null : item.getName();
        }).orElse(null);
    }

    private static boolean isValidName(String name) {
        return name != null && !name.isBlank() && !"null".equalsIgnoreCase(name);
    }

    private static String key(BryophytaStrategy strategy, EquipmentInventorySlot slot) {
        return "gear." + strategy.name().toLowerCase() + "." + slot.name().toLowerCase();
    }
}
