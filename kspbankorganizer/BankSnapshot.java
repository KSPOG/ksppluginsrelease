package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.util.*;

final class BankSnapshot {
    private final List<BankStack> items; private final int[] tabCounts; private final int currentTab;
    BankSnapshot(List<BankStack> items,int[] counts,int tab){this.items=Collections.unmodifiableList(new ArrayList<>(items));tabCounts=counts.clone();currentTab=tab;}
    List<BankStack> items(){return items;} int currentTab(){return currentTab;} int stackCount(){return items.size();}
    int tabbedStackCount(){int n=0;for(BankStack s:items)if(s.tab()>0)n++;return n;} int mainTabCount(){return stackCount()-tabbedStackCount();}

    static final class BankStack {
        private final int itemId,quantity,slot,allItemsIndex,tab,segment; private final String name; private final boolean stackable,tradeable,geTradeable,equipable;
        BankStack(int id,String name,int qty,int slot,int index,int tab,int segment,boolean stackable,boolean tradeable,boolean geTradeable,boolean equipable){itemId=id;this.name=name;quantity=qty;this.slot=slot;allItemsIndex=index;this.tab=tab;this.segment=segment;this.stackable=stackable;this.tradeable=tradeable;this.geTradeable=geTradeable;this.equipable=equipable;}
        int itemId(){return itemId;} String name(){return name;} int quantity(){return quantity;} int slot(){return slot;} int allItemsIndex(){return allItemsIndex;} int tab(){return tab;} int segment(){return segment;}
        boolean stackable(){return stackable;} boolean tradeable(){return tradeable;} boolean geTradeable(){return geTradeable;} boolean equipable(){return equipable;}
    }
}
