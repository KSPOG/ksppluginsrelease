package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

/** Immutable view of one real bank layout. */
final class BankSnapshot {
    private final List<BankStack> items; private final int[] tabCounts; private final int currentTab;
    BankSnapshot(List<BankStack> items,int[] counts,int tab){this.items=Collections.unmodifiableList(new ArrayList<>(items));tabCounts=counts.clone();currentTab=tab;}
    List<BankStack> items(){return items;} int currentTab(){return currentTab;} int stackCount(){return items.size();}
    int tabbedStackCount(){int total=0;for(int count:tabCounts)total+=count;return total;}
    int mainTabCount(){return Math.max(0,stackCount()-tabbedStackCount());}

    static final class BankStack {
        private final int itemId,quantity,slot,allItemsIndex,tab; private final String name;
        private final boolean stackable,tradeable,geTradeable,equipable;
        BankStack(int id,String name,int qty,int slot,int index,int tab,boolean stackable,boolean tradeable,boolean geTradeable,boolean equipable){itemId=id;this.name=name;quantity=qty;this.slot=slot;allItemsIndex=index;this.tab=tab;this.stackable=stackable;this.tradeable=tradeable;this.geTradeable=geTradeable;this.equipable=equipable;}
        int itemId(){return itemId;} String name(){return name;} int quantity(){return quantity;} int slot(){return slot;} int allItemsIndex(){return allItemsIndex;} int tab(){return tab;}
        boolean stackable(){return stackable;} boolean tradeable(){return tradeable;} boolean geTradeable(){return geTradeable;} boolean equipable(){return equipable;}
    }
}
