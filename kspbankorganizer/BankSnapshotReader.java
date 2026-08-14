package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.ItemComposition;
import net.runelite.api.Varbits;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2ItemModel;

/**
 * Reads the bank using the same real-tab/varbit model used by Microbot-Hub's
 * Bank Organizer implementation.
 */
final class BankSnapshotReader
{
    private static final int[] TAB_COUNT_VARBITS = {
        Varbits.BANK_TAB_ONE_COUNT,
        Varbits.BANK_TAB_TWO_COUNT,
        Varbits.BANK_TAB_THREE_COUNT,
        Varbits.BANK_TAB_FOUR_COUNT,
        Varbits.BANK_TAB_FIVE_COUNT,
        Varbits.BANK_TAB_SIX_COUNT,
        Varbits.BANK_TAB_SEVEN_COUNT,
        Varbits.BANK_TAB_EIGHT_COUNT,
        Varbits.BANK_TAB_NINE_COUNT
    };

    private final Client client;
    private final ItemManager itemManager;

    @Inject
    BankSnapshotReader(Client client, ItemManager itemManager)
    {
        this.client = client;
        this.itemManager = itemManager;
    }

    BankSnapshot read()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(this::readOnClientThread)
            .orElseThrow(() -> new IllegalStateException("Could not read bank snapshot on the client thread."));
    }

    private BankSnapshot readOnClientThread()
    {
        // Read the live BANK item container directly. Rs2Bank.bankItems() is a
        // mirrored cache and can legitimately lag the already-visible bank UI by
        // a tick. The organizer must be able to Preview/Scan an already-open bank
        // without depending on that cache or on nearest-bank pathfinding.
        ItemContainer bankContainer = client.getItemContainer(InventoryID.BANK);
        if (bankContainer == null)
        {
            throw new IllegalStateException("Bank interface is open, but the live bank item container is not ready yet.");
        }

        List<Rs2ItemModel> bankItems = new ArrayList<>();
        Item[] items = bankContainer.getItems();
        if (items != null)
        {
            for (int slot = 0; slot < items.length; slot++)
            {
                Item item = items[slot];
                if (item == null || item.getId() == -1)
                {
                    continue;
                }

                ItemComposition composition = client.getItemDefinition(item.getId());
                if (composition == null || composition.getPlaceholderTemplateId() > 0)
                {
                    continue;
                }

                bankItems.add(new Rs2ItemModel(item, composition, slot));
            }
        }
        bankItems.sort(Comparator.comparingInt(Rs2ItemModel::getSlot));

        int[] tabCounts = readTabCounts();
        List<BankSnapshot.BankStack> stacks = new ArrayList<>();
        for (int index = 0; index < bankItems.size(); index++)
        {
            Rs2ItemModel item = bankItems.get(index);
            ItemComposition composition = itemManager.getItemComposition(item.getId());
            String name = composition.getName();
            if (name == null || name.isEmpty() || "null".equalsIgnoreCase(name))
            {
                name = item.getName();
            }

            stacks.add(new BankSnapshot.BankStack(
                item.getId(),
                name == null ? "" : name,
                item.getQuantity(),
                item.getSlot(),
                index,
                tabForIndex(index, tabCounts),
                composition.isStackable(),
                composition.isTradeable(),
                composition.isGeTradeable(),
                isEquipable(composition)));
        }

        return new BankSnapshot(stacks, tabCounts, client.getVarbitValue(Varbits.CURRENT_BANK_TAB));
    }

    private int[] readTabCounts()
    {
        int[] counts = new int[TAB_COUNT_VARBITS.length];
        for (int i = 0; i < TAB_COUNT_VARBITS.length; i++)
        {
            counts[i] = client.getVarbitValue(TAB_COUNT_VARBITS[i]);
        }
        return counts;
    }

    private static int tabForIndex(int index, int[] tabCounts)
    {
        int cursor = 0;
        for (int i = 0; i < tabCounts.length; i++)
        {
            cursor += tabCounts[i];
            if (index < cursor)
            {
                return i + 1;
            }
        }
        return 0;
    }

    private static boolean isEquipable(ItemComposition composition)
    {
        String[] actions = composition.getInventoryActions();
        if (actions == null)
        {
            return false;
        }

        for (String action : actions)
        {
            if (action == null)
            {
                continue;
            }
            String lower = action.toLowerCase();
            if (lower.contains("wear") || lower.contains("wield") || lower.contains("equip"))
            {
                return true;
            }
        }
        return false;
    }
}
