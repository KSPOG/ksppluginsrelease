package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.util.*;
import javax.inject.Inject;
import net.runelite.api.*;
import net.runelite.api.gameval.InventoryID;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;

final class BankSnapshotReader {
    private static final int[] TAB_COUNTS={Varbits.BANK_TAB_ONE_COUNT,Varbits.BANK_TAB_TWO_COUNT,Varbits.BANK_TAB_THREE_COUNT,Varbits.BANK_TAB_FOUR_COUNT,Varbits.BANK_TAB_FIVE_COUNT,Varbits.BANK_TAB_SIX_COUNT,Varbits.BANK_TAB_SEVEN_COUNT,Varbits.BANK_TAB_EIGHT_COUNT,Varbits.BANK_TAB_NINE_COUNT};
    private final Client client; private final ItemManager itemManager;
    @Inject BankSnapshotReader(Client client,ItemManager itemManager){this.client=client;this.itemManager=itemManager;}
    BankSnapshot read(){return Microbot.getClientThread().runOnClientThreadOptional(this::readOnClientThread).orElseThrow(()->new IllegalStateException("Could not read bank snapshot on the client thread."));}
    private BankSnapshot readOnClientThread(){
        ItemContainer bank=client.getItemContainer(InventoryID.BANK); if(bank==null)throw new IllegalStateException("Bank interface is open, but the live bank item container is not ready yet.");
        int[] counts=new int[TAB_COUNTS.length],segments=new int[10]; for(int i=0;i<counts.length;i++)counts[i]=client.getVarbitValue(TAB_COUNTS[i]);
        List<BankSnapshot.BankStack> stacks=new ArrayList<>(); Item[] items=bank.getItems(); if(items!=null)for(int slot=0;slot<items.length;slot++){
            Item item=items[slot]; if(item==null||item.getId()==-1)continue; int tab=tabForIndex(slot,counts); ItemComposition clientComp=client.getItemDefinition(item.getId()); if(clientComp==null)continue;
            if(clientComp.getPlaceholderTemplateId()!=-1){segments[tab]++;continue;}
            ItemComposition comp=itemManager.getItemComposition(item.getId()); String name=comp.getName(); if(name==null||name.isEmpty()||"null".equalsIgnoreCase(name))name=clientComp.getName();
            stacks.add(new BankSnapshot.BankStack(item.getId(),name==null?"":name,item.getQuantity(),slot,slot,tab,segments[tab],comp.isStackable(),comp.isTradeable(),comp.isGeTradeable(),isEquipable(comp)));
        }
        return new BankSnapshot(stacks,counts,client.getVarbitValue(Varbits.CURRENT_BANK_TAB));
    }
    private static int tabForIndex(int index,int[] counts){int cursor=0;for(int i=0;i<counts.length;i++){cursor+=counts[i];if(index<cursor)return i+1;}return 0;}
    private static boolean isEquipable(ItemComposition c){String[] a=c.getInventoryActions();if(a!=null)for(String s:a)if(s!=null){s=s.toLowerCase();if(s.contains("wear")||s.contains("wield")||s.contains("equip"))return true;}return false;}
}
