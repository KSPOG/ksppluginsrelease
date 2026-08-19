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

/**
 * One-time local cache of every equipable item, grouped by RuneScape equipment slot.
 * The scan is executed on the client thread because item definitions are client-thread data.
 */
@Singleton
final class BryophytaEquipmentIndex
{
    private final Client client;
    private final ItemManager itemManager;

    private volatile Map<EquipmentInventorySlot, List<BryophytaEquipmentItem>> bySlot;
    private volatile boolean loading;

    @Inject
    BryophytaEquipmentIndex(Client client, ItemManager itemManager)
    {
        this.client = client;
        this.itemManager = itemManager;
    }

    boolean isLoaded()
    {
        return bySlot != null;
    }

    List<BryophytaEquipmentItem> itemsFor(EquipmentInventorySlot slot)
    {
        Map<EquipmentInventorySlot, List<BryophytaEquipmentItem>> index = bySlot;
        if (index == null)
        {
            return Collections.emptyList();
        }
        return index.getOrDefault(slot, Collections.emptyList());
    }

    Integer findItemIdByName(EquipmentInventorySlot slot, String name)
    {
        if (name == null || name.isBlank())
        {
            return null;
        }

        for (BryophytaEquipmentItem item : itemsFor(slot))
        {
            if (item.getName().equalsIgnoreCase(name))
            {
                return item.getId();
            }
        }
        return null;
    }

    synchronized void ensureLoaded(Consumer<Boolean> completion)
    {
        if (isLoaded())
        {
            completion.accept(true);
            return;
        }

        if (loading)
        {
            new SwingWorker<Boolean, Void>()
            {
                @Override
                protected Boolean doInBackground() throws Exception
                {
                    for (int i = 0; i < 80 && !isLoaded(); i++)
                    {
                        Thread.sleep(100L);
                    }
                    return isLoaded();
                }

                @Override
                protected void done()
                {
                    completion.accept(isLoaded());
                }
            }.execute();
            return;
        }

        loading = true;
        new SwingWorker<Map<EquipmentInventorySlot, List<BryophytaEquipmentItem>>, Void>()
        {
            @Override
            protected Map<EquipmentInventorySlot, List<BryophytaEquipmentItem>> doInBackground()
            {
                return Microbot.getClientThread().invoke(BryophytaEquipmentIndex.this::scanOnClientThread);
            }

            @Override
            protected void done()
            {
                boolean success = false;
                try
                {
                    bySlot = get();
                    success = bySlot != null && bySlot.values().stream().mapToInt(List::size).sum() > 0;
                    if (!success)
                    {
                        bySlot = null;
                    }
                }
                catch (Exception ignored)
                {
                    bySlot = null;
                }
                finally
                {
                    loading = false;
                    completion.accept(success);
                }
            }
        }.execute();
    }

    private Map<EquipmentInventorySlot, List<BryophytaEquipmentItem>> scanOnClientThread()
    {
        EnumMap<EquipmentInventorySlot, LinkedHashMap<String, BryophytaEquipmentItem>> dedup =
                new EnumMap<>(EquipmentInventorySlot.class);

        for (EquipmentInventorySlot slot : BryophytaLoadout.CONFIGURABLE_SLOTS)
        {
            dedup.put(slot, new LinkedHashMap<>());
        }

        int itemCount = client.getItemCount();
        for (int itemId = 0; itemId < itemCount; itemId++)
        {
            ItemComposition composition;
            try
            {
                composition = client.getItemDefinition(itemId);
            }
            catch (Exception ex)
            {
                continue;
            }

            if (composition == null
                    || composition.getName() == null
                    || composition.getName().isBlank()
                    || "null".equalsIgnoreCase(composition.getName())
                    || composition.getNote() != -1
                    || composition.getPlaceholderTemplateId() > 0)
            {
                continue;
            }

            ItemStats stats = itemManager.getItemStats(itemId);
            if (stats == null || !stats.isEquipable())
            {
                continue;
            }

            ItemEquipmentStats equipment = stats.getEquipment();
            if (equipment == null)
            {
                continue;
            }

            EquipmentInventorySlot slot = slotFromIndex(equipment.getSlot());
            if (slot == null || !dedup.containsKey(slot))
            {
                continue;
            }

            String name = composition.getName().trim();
            String key = name.toLowerCase(Locale.ROOT);
            dedup.get(slot).putIfAbsent(key,
                    new BryophytaEquipmentItem(itemId, name, composition.isMembers(), equipment.isTwoHanded()));
        }

        EnumMap<EquipmentInventorySlot, List<BryophytaEquipmentItem>> result =
                new EnumMap<>(EquipmentInventorySlot.class);
        for (Map.Entry<EquipmentInventorySlot, LinkedHashMap<String, BryophytaEquipmentItem>> entry : dedup.entrySet())
        {
            List<BryophytaEquipmentItem> items = new ArrayList<>(entry.getValue().values());
            items.sort(Comparator.comparing(BryophytaEquipmentItem::getName, String.CASE_INSENSITIVE_ORDER));
            result.put(entry.getKey(), Collections.unmodifiableList(items));
        }

        return Collections.unmodifiableMap(result);
    }

    private static EquipmentInventorySlot slotFromIndex(int slotIndex)
    {
        for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
        {
            if (slot.getSlotIdx() == slotIndex)
            {
                return slot;
            }
        }
        return null;
    }
}
