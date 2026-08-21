package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import lombok.RequiredArgsConstructor;

/** Immutable view of the real bank layout at one point in time. */
final class BankSnapshot
{
    private final List<BankStack> items;
    private final int[] tabCounts;
    private final int currentTab;

    BankSnapshot(List<BankStack> items, int[] tabCounts, int currentTab)
    {
        this.items = Collections.unmodifiableList(new ArrayList<>(items));
        this.tabCounts = tabCounts.clone();
        this.currentTab = currentTab;
    }

    List<BankStack> items()
    {
        return items;
    }

    int currentTab()
    {
        return currentTab;
    }

    int stackCount()
    {
        return items.size();
    }

    int tabbedStackCount()
    {
        int total = 0;
        for (int count : tabCounts)
        {
            total += count;
        }
        return total;
    }

    int mainTabCount()
    {
        return Math.max(0, stackCount() - tabbedStackCount());
    }

    @RequiredArgsConstructor
    static final class BankStack
    {
        private final int itemId;
        private final String name;
        private final int quantity;
        private final int slot;
        private final int allItemsIndex;
        private final int tab;
        private final boolean stackable;
        private final boolean tradeable;
        private final boolean geTradeable;
        private final boolean equipable;

        int itemId() { return itemId; }
        String name() { return name; }
        int quantity() { return quantity; }
        int slot() { return slot; }
        int allItemsIndex() { return allItemsIndex; }
        int tab() { return tab; }
        boolean stackable() { return stackable; }
        boolean tradeable() { return tradeable; }
        boolean geTradeable() { return geTradeable; }
        boolean equipable() { return equipable; }
    }
}
