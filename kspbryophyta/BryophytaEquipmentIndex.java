package net.runelite.client.plugins.microbot.kspbryophyta;

import net.runelite.api.Client;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemEquipmentStats;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.microbot.Microbot;

import javax.inject.Inject;
import javax.inject.Singleton;
import javax.swing.SwingWorker;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

@Singleton
final class BryophytaEquipmentIndex {
    private static final Map<Integer, EquipmentInventorySlot> SLOT_BY_INDEX = buildSlotIndex();

    private final Client client;
    private final ItemManager itemManager;
    private final List<Consumer<Boolean>> loadCallbacks = new ArrayList<>();
    private volatile Map<EquipmentInventorySlot, List<BryophytaEquipmentItem>> bySlot;
    private volatile boolean loading;

    @Inject
    BryophytaEquipmentIndex(Client client, ItemManager itemManager) {
        this.client = client;
        this.itemManager = itemManager;
    }

    boolean isLoaded() {
        return bySlot != null;
    }

    List<BryophytaEquipmentItem> itemsFor(EquipmentInventorySlot slot) {
        Map<EquipmentInventorySlot, List<BryophytaEquipmentItem>> index = bySlot;
        return index == null ? Collections.emptyList() : index.getOrDefault(slot, Collections.emptyList());
    }

    Integer findItemIdByName(EquipmentInventorySlot slot, String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        return itemsFor(slot).stream()
                .filter(item -> item.getName().equalsIgnoreCase(name))
                .map(BryophytaEquipmentItem::getId)
                .findFirst().orElse(null);
    }

    synchronized void ensureLoaded(Consumer<Boolean> completion) {
        if (isLoaded()) {
            completion.accept(true);
            return;
        }

        loadCallbacks.add(completion);
        if (loading) {
            return;
        }
        loading = true;

        new SwingWorker<Map<EquipmentInventorySlot, List<BryophytaEquipmentItem>>, Void>() {
            @Override
            protected Map<EquipmentInventorySlot, List<BryophytaEquipmentItem>> doInBackground() {
                return Microbot.getClientThread().invoke(BryophytaEquipmentIndex.this::scanOnClientThread);
            }

            @Override
            protected void done() {
                finishLoad(readResult());
            }

            private Map<EquipmentInventorySlot, List<BryophytaEquipmentItem>> readResult() {
                try {
                    Map<EquipmentInventorySlot, List<BryophytaEquipmentItem>> result = get();
                    return result != null && result.values().stream().mapToInt(List::size).sum() > 0 ? result : null;
                } catch (Exception ignored) {
                    return null;
                }
            }
        }.execute();
    }

    private void finishLoad(Map<EquipmentInventorySlot, List<BryophytaEquipmentItem>> result) {
        List<Consumer<Boolean>> callbacks;
        synchronized (this) {
            bySlot = result;
            loading = false;
            callbacks = new ArrayList<>(loadCallbacks);
            loadCallbacks.clear();
        }
        boolean success = result != null;
        callbacks.forEach(callback -> callback.accept(success));
    }

    private Map<EquipmentInventorySlot, List<BryophytaEquipmentItem>> scanOnClientThread() {
        EnumMap<EquipmentInventorySlot, LinkedHashMap<String, BryophytaEquipmentItem>> dedup =
                new EnumMap<>(EquipmentInventorySlot.class);
        for (EquipmentInventorySlot slot : BryophytaLoadout.CONFIGURABLE_SLOTS) {
            dedup.put(slot, new LinkedHashMap<>());
        }

        for (int itemId = 0; itemId < client.getItemCount(); itemId++) {
            ItemComposition composition = safeItemDefinition(itemId);
            if (!isUsableDefinition(composition)) {
                continue;
            }

            ItemStats stats = itemManager.getItemStats(itemId);
            ItemEquipmentStats equipment = stats == null ? null : stats.getEquipment();
            EquipmentInventorySlot slot = equipment == null ? null : SLOT_BY_INDEX.get(equipment.getSlot());
            if (stats == null || !stats.isEquipable() || slot == null || !dedup.containsKey(slot)) {
                continue;
            }

            String name = composition.getName().trim();
            dedup.get(slot).putIfAbsent(name.toLowerCase(Locale.ROOT),
                    new BryophytaEquipmentItem(itemId, name, composition.isMembers(), equipment.isTwoHanded()));
        }

        EnumMap<EquipmentInventorySlot, List<BryophytaEquipmentItem>> result =
                new EnumMap<>(EquipmentInventorySlot.class);
        dedup.forEach((slot, itemsByName) -> {
            List<BryophytaEquipmentItem> items = new ArrayList<>(itemsByName.values());
            items.sort(Comparator.comparing(BryophytaEquipmentItem::getName, String.CASE_INSENSITIVE_ORDER));
            result.put(slot, Collections.unmodifiableList(items));
        });
        return Collections.unmodifiableMap(result);
    }

    private ItemComposition safeItemDefinition(int itemId) {
        try {
            return client.getItemDefinition(itemId);
        } catch (Exception ignored) {
            return null;
        }
    }

    private static boolean isUsableDefinition(ItemComposition composition) {
        return composition != null
                && composition.getName() != null
                && !composition.getName().isBlank()
                && !"null".equalsIgnoreCase(composition.getName())
                && composition.getNote() == -1
                && composition.getPlaceholderTemplateId() <= 0;
    }

    private static Map<Integer, EquipmentInventorySlot> buildSlotIndex() {
        Map<Integer, EquipmentInventorySlot> slots = new java.util.HashMap<>();
        for (EquipmentInventorySlot slot : EquipmentInventorySlot.values()) {
            slots.put(slot.getSlotIdx(), slot);
        }
        return Collections.unmodifiableMap(slots);
    }
}
