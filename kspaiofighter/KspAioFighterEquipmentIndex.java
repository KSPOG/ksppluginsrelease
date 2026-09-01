package net.runelite.client.plugins.microbot.kspaiofighter;

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
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.function.Consumer;

@Singleton
final class KspAioFighterEquipmentIndex
{
    private static final Map<Integer, EquipmentInventorySlot> SLOT_BY_INDEX = buildSlotIndex();

    private final Client client;
    private final ItemManager itemManager;
    private final List<Consumer<Boolean>> callbacks = new ArrayList<>();
    private volatile Map<EquipmentInventorySlot, List<KspAioFighterEquipmentItem>> bySlot;
    private volatile boolean loading;

    @Inject
    KspAioFighterEquipmentIndex(Client client, ItemManager itemManager)
    {
        this.client = client;
        this.itemManager = itemManager;
    }

    boolean isLoaded() { return bySlot != null; }

    List<KspAioFighterEquipmentItem> itemsFor(EquipmentInventorySlot slot)
    {
        Map<EquipmentInventorySlot, List<KspAioFighterEquipmentItem>> index = bySlot;
        return index == null ? Collections.emptyList() : index.getOrDefault(slot, Collections.emptyList());
    }

    synchronized void ensureLoaded(Consumer<Boolean> completion)
    {
        if (isLoaded())
        {
            completion.accept(true);
            return;
        }
        callbacks.add(completion);
        if (loading) return;
        loading = true;

        new SwingWorker<Map<EquipmentInventorySlot, List<KspAioFighterEquipmentItem>>, Void>()
        {
            @Override
            protected Map<EquipmentInventorySlot, List<KspAioFighterEquipmentItem>> doInBackground()
            {
                return Microbot.getClientThread().invoke(KspAioFighterEquipmentIndex.this::scanOnClientThread);
            }

            @Override
            protected void done()
            {
                Map<EquipmentInventorySlot, List<KspAioFighterEquipmentItem>> result = null;
                try
                {
                    result = get();
                    if (result != null && result.values().stream().mapToInt(List::size).sum() == 0) result = null;
                }
                catch (Exception ignored) {}
                finish(result);
            }
        }.execute();
    }

    private void finish(Map<EquipmentInventorySlot, List<KspAioFighterEquipmentItem>> result)
    {
        List<Consumer<Boolean>> pending;
        synchronized (this)
        {
            bySlot = result;
            loading = false;
            pending = new ArrayList<>(callbacks);
            callbacks.clear();
        }
        boolean success = result != null;
        pending.forEach(callback -> callback.accept(success));
    }

    private Map<EquipmentInventorySlot, List<KspAioFighterEquipmentItem>> scanOnClientThread()
    {
        EnumMap<EquipmentInventorySlot, LinkedHashMap<String, KspAioFighterEquipmentItem>> dedup = new EnumMap<>(EquipmentInventorySlot.class);
        for (EquipmentInventorySlot slot : KspAioFighterEquipmentSettings.CONFIGURABLE_SLOTS) dedup.put(slot, new LinkedHashMap<>());

        for (int itemId = 0; itemId < client.getItemCount(); itemId++)
        {
            ItemComposition composition;
            try { composition = client.getItemDefinition(itemId); }
            catch (Exception ignored) { continue; }

            if (composition == null || composition.getName() == null || composition.getName().isBlank()
                    || "null".equalsIgnoreCase(composition.getName()) || composition.getNote() != -1
                    || composition.getPlaceholderTemplateId() > 0) continue;

            ItemStats stats = itemManager.getItemStats(itemId);
            ItemEquipmentStats equipment = stats == null ? null : stats.getEquipment();
            EquipmentInventorySlot slot = equipment == null ? null : SLOT_BY_INDEX.get(equipment.getSlot());
            if (stats == null || !stats.isEquipable() || slot == null || !dedup.containsKey(slot)) continue;

            String name = composition.getName().trim();
            dedup.get(slot).putIfAbsent(name.toLowerCase(Locale.ROOT),
                    new KspAioFighterEquipmentItem(itemId, name, composition.isMembers(), equipment.isTwoHanded()));
        }

        EnumMap<EquipmentInventorySlot, List<KspAioFighterEquipmentItem>> result = new EnumMap<>(EquipmentInventorySlot.class);
        dedup.forEach((slot, values) -> {
            List<KspAioFighterEquipmentItem> items = new ArrayList<>(values.values());
            items.sort(Comparator.comparing(KspAioFighterEquipmentItem::getName, String.CASE_INSENSITIVE_ORDER));
            result.put(slot, Collections.unmodifiableList(items));
        });
        return Collections.unmodifiableMap(result);
    }

    private static Map<Integer, EquipmentInventorySlot> buildSlotIndex()
    {
        Map<Integer, EquipmentInventorySlot> result = new HashMap<>();
        for (EquipmentInventorySlot slot : EquipmentInventorySlot.values()) result.put(slot.getSlotIdx(), slot);
        return Collections.unmodifiableMap(result);
    }
}
