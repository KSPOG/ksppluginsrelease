package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.GameObject;
import net.runelite.api.ItemContainer;
import net.runelite.api.MenuAction;
import net.runelite.api.NPCComposition;
import net.runelite.api.Varbits;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.Global;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.menu.NewMenuEntry;
import net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

final class BankActuator {
    private static final int BANK_GROUP_ID=12,REARRANGE_CHILD=23,MOVE_VERIFY_MS=5000,OPEN_VERIFY_MS=1500,BANK_APPROACH_MS=3500,SEARCH_RADIUS=20,INTERACT_DISTANCE=4,WALK_TIMEOUT_MS=15000;
    private static final int[] TAB_COUNTS={Varbits.BANK_TAB_ONE_COUNT,Varbits.BANK_TAB_TWO_COUNT,Varbits.BANK_TAB_THREE_COUNT,Varbits.BANK_TAB_FOUR_COUNT,Varbits.BANK_TAB_FIVE_COUNT,Varbits.BANK_TAB_SIX_COUNT,Varbits.BANK_TAB_SEVEN_COUNT,Varbits.BANK_TAB_EIGHT_COUNT,Varbits.BANK_TAB_NINE_COUNT};
    private static final MenuAction[] NPC_ACTIONS={MenuAction.NPC_FIRST_OPTION,MenuAction.NPC_SECOND_OPTION,MenuAction.NPC_THIRD_OPTION,MenuAction.NPC_FOURTH_OPTION,MenuAction.NPC_FIFTH_OPTION};
    private final Client client; private final BankSnapshotReader snapshots;
    @Inject BankActuator(Client client,BankSnapshotReader snapshots){this.client=client;this.snapshots=snapshots;}

    boolean ensureBankOpen(){
        if(bankUiOpen())return Global.sleepUntil(this::bankReady,4000);
        if(openNearbyBanker())return Global.sleepUntil(this::bankReady,5000);
        GameObject booth=Rs2GameObject.findBank(SEARCH_RADIUS);
        if(booth!=null){
            if(!near(booth,INTERACT_DISTANCE)&&(!Rs2Bank.walkToBank()||!Global.sleepUntil(()->bankUiOpen()||near(booth,INTERACT_DISTANCE),WALK_TIMEOUT_MS)))return false;
            if(bankUiOpen())return Global.sleepUntil(this::bankReady,OPEN_VERIFY_MS);
            if(Rs2Bank.openBank(booth)&&Global.sleepUntil(this::bankReady,OPEN_VERIFY_MS))return true;
            if(bankReady()||!near(booth,INTERACT_DISTANCE))return bankReady();
            return (openNearbyBanker()||Rs2Bank.openBank(booth))&&Global.sleepUntil(this::bankReady,5000);
        }
        if(!Rs2Bank.walkToBank()||!Global.sleepUntil(()->bankUiOpen()||hasNearbyBanker()||localBoothAvailable(),WALK_TIMEOUT_MS))return false;
        if(bankUiOpen())return Global.sleepUntil(this::bankReady,OPEN_VERIFY_MS);
        booth=Rs2GameObject.findBank(SEARCH_RADIUS);
        boolean started=openNearbyBanker()||(booth!=null&&near(booth,INTERACT_DISTANCE)&&Rs2Bank.openBank(booth));
        if(!started&&!bankUiOpen())return false;
        if(Global.sleepUntil(this::bankReady,OPEN_VERIFY_MS))return true;
        if(waitForBankOrApproach())return true;
        started=openNearbyBanker()||Rs2Bank.openBank();
        return started&&Global.sleepUntil(this::bankReady,5000);
    }
    private boolean waitForBankOrApproach(){Global.sleepUntil(()->bankReady()||!approachingBank(),BANK_APPROACH_MS);return bankReady();}
    private boolean approachingBank(){return Rs2Player.isMoving()||Rs2Player.isInteracting()||Rs2Player.isAnimating(1200);}
    private boolean localBoothAvailable(){GameObject b=Rs2GameObject.findBank(SEARCH_RADIUS);return b!=null&&near(b,INTERACT_DISTANCE);}
    private boolean openNearbyBanker(){Rs2NpcModel b=nearbyBanker();return b!=null&&invokeBank(b);}
    private boolean hasNearbyBanker(){return nearbyBanker()!=null;}
    private Rs2NpcModel nearbyBanker(){Rs2NpcModel b=Rs2Npc.getBankerNPC();return b!=null&&near(b,INTERACT_DISTANCE)?b:null;}
    private boolean near(Rs2NpcModel n,int d){return Microbot.getClientThread().runOnClientThreadOptional(()->client.getLocalPlayer()!=null&&client.getLocalPlayer().getWorldLocation()!=null&&n.getWorldLocation()!=null&&n.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation())<=d).orElse(false);}
    private boolean near(GameObject o,int d){return Microbot.getClientThread().runOnClientThreadOptional(()->client.getLocalPlayer()!=null&&client.getLocalPlayer().getWorldLocation()!=null&&o.getWorldLocation()!=null&&o.getWorldLocation().distanceTo(client.getLocalPlayer().getWorldLocation())<=d).orElse(false);}
    private boolean invokeBank(Rs2NpcModel npc){
        NPCComposition def=Microbot.getClientThread().runOnClientThreadOptional(()->client.getNpcDefinition(npc.getId())).orElse(null); if(def==null||def.getActions()==null)return false;
        String[] actions=def.getActions();
        for(int i=0;i<actions.length&&i<NPC_ACTIONS.length;i++)if("Bank".equalsIgnoreCase(actions[i])){
            if(npc.getLocalLocation()==null)return false; if(!Rs2Camera.isTileOnScreen(npc.getLocalLocation()))Rs2Camera.turnTo(npc);
            Microbot.doInvoke(new NewMenuEntry().param0(0).param1(0).opcode(NPC_ACTIONS[i].getId()).identifier(npc.getIndex()).itemId(-1).target(npc.getName()).actor(npc).option(actions[i]),Rs2UiHelper.getActorClickbox(npc)); return true;
        }
        return false;
    }
    private boolean bankUiOpen(){return Microbot.getClientThread().runOnClientThreadOptional(()->usable(client.getWidget(BANK_GROUP_ID,1))).orElse(false);}
    private boolean bankReady(){return Microbot.getClientThread().runOnClientThreadOptional(()->{
        if(!usable(client.getWidget(BANK_GROUP_ID,1)))return false; Widget items=client.getWidget(BANK_GROUP_ID,12); if(!usable(items))items=client.getWidget(BANK_GROUP_ID,9); ItemContainer bank=client.getItemContainer(InventoryID.BANK); return usable(items)&&bank!=null;
    }).orElse(false);}
    private boolean bankUiStable(){return Microbot.getClientThread().runOnClientThreadOptional(()->usable(client.getWidget(BANK_GROUP_ID,1))&&usable(client.getWidget(Rs2Bank.BANK_ITEM_CONTAINER))).orElse(false);}

    ActuatorResult ensureBankInsertMode(){return ensureRearrangeMode(true);} ActuatorResult ensureBankSwapMode(){return ensureRearrangeMode(false);}
    private ActuatorResult ensureRearrangeMode(boolean insert){
        if(!ensureBankOpen())return ActuatorResult.fail("Bank is not open."); int wanted=insert?1:0; if(rearrangeMode()==wanted)return ActuatorResult.ok("Bank rearrange mode ready.");
        if(!Global.sleepUntil(()->rearrangeReady(insert),5000))return ActuatorResult.fail("Bank rearrange toggle did not expose "+(insert?"Insert":"Swap")+".");
        for(int i=0;i<5;i++){
            if(Thread.currentThread().isInterrupted())return ActuatorResult.fail("Interrupted while switching bank rearrangement mode.");
            if(!rearrangeReady(insert)&&!Global.sleepUntil(()->rearrangeReady(insert),1200))continue;
            if(clickRearrange(insert)&&Global.sleepUntil(()->rearrangeMode()==wanted,1500))return ActuatorResult.ok("Bank rearrange mode ready.");
            Global.sleep(100);
        }
        return ActuatorResult.fail("Could not switch bank rearrangement mode to "+(insert?"Insert":"Swap")+".");
    }
    private boolean rearrangeReady(boolean insert){return Microbot.getClientThread().runOnClientThreadOptional(()->hasAction(client.getWidget(BANK_GROUP_ID,REARRANGE_CHILD),insert?"insert":"swap")).orElse(false);}
    private boolean clickRearrange(boolean insert){return Microbot.getClientThread().runOnClientThreadOptional(()->{Widget w=client.getWidget(BANK_GROUP_ID,REARRANGE_CHILD);if(!hasAction(w,insert?"insert":"swap")||!usable(w))return false;Microbot.getMouse().click(w.getBounds());return true;}).orElse(false);}
    private static boolean hasAction(Widget w,String wanted){if(w==null||w.isHidden()||w.getActions()==null)return false;for(String a:w.getActions())if(a!=null&&a.toLowerCase(java.util.Locale.ROOT).contains(wanted))return true;return false;}

    ActuatorResult openTab(int tab){
        if(!ensureBankOpen())return ActuatorResult.fail("Bank is not open."); if(tab<0||tab>9||(tab>0&&tabCount(tab)<=0))return ActuatorResult.fail("Tab "+tab+" does not exist.");
        if(currentTab()==tab)return ActuatorResult.ok("Tab already open."); if(!invokeTab(tab))return ActuatorResult.fail("Could not invoke tab "+tab+".");
        return Global.sleepUntil(()->currentTab()==tab&&bankUiStable(),3000)?ActuatorResult.ok("Tab opened."):ActuatorResult.fail("Tab "+tab+" did not become active.");
    }
    ActuatorResult moveToMain(int id,int source){return source==0?ActuatorResult.ok("Item is already in main."):moveItem(id,source,0,"main");}
    ActuatorResult moveToExistingTab(int id,int source,int target){if(source==target)return ActuatorResult.ok("Item already in destination.");if(target<1||target>9||tabCount(target)<=0)return ActuatorResult.fail("Destination tab "+target+" does not exist.");return moveItem(id,source,target,"tab "+target);}
    ActuatorResult moveToNewTab(int id,int source){int target=realTabCount()+1;return target>9?ActuatorResult.fail("All nine real bank tabs already exist."):moveItem(id,source,target,"new tab "+target);}
    private ActuatorResult moveItem(int id,int source,int target,String name){
        for(int attempt=0;attempt<4;attempt++){
            if(Thread.currentThread().isInterrupted())return ActuatorResult.fail("Interrupted while moving item to "+name+".");
            ActuatorResult opened=openTab(source); if(!opened.success()){if(attempt==3)return opened;Global.sleep(120);continue;}
            BankSnapshot.BankStack stack=findStack(id); if(stack==null){if(attempt==3)return ActuatorResult.fail("Could not find item "+id+" in the live bank.");Global.sleep(120);continue;}
            if(!scrollTo(stack.slot())){if(attempt==3)return ActuatorResult.fail("Could not scroll item "+id+" into view.");Global.sleep(120);continue;}
            DragBounds b=itemToTabBounds(id,target); if(b==null){Global.sleep(100);b=itemToTabBounds(id,target);} if(b==null){if(attempt==3)return ActuatorResult.fail("Could not locate drag bounds for item "+id+" -> "+name+".");continue;}
            Microbot.drag(center(b.source),center(b.target));
            if(waitForItemTab(id,target,stack.quantity(),MOVE_VERIFY_MS))return ActuatorResult.ok("Moved item to "+name+".");
        }
        return ActuatorResult.fail("Move to "+name+" was not verified after 4 attempts.");
    }
    ActuatorResult moveWithinOpenTab(BankSnapshot.BankStack source,BankSnapshot.BankStack target){
        if(!scrollTo(source.slot()))return ActuatorResult.fail("Could not scroll source item into view."); DragBounds b=itemToItemBounds(source.slot(),target.slot()); if(b==null)return ActuatorResult.fail("Source or target slot bounds were unavailable.");
        Microbot.drag(b.source,b.target); return ActuatorResult.ok("Inserted "+source.name()+" before "+target.name()+".");
    }

    int realTabCount(){int n=0;for(int i=1;i<=9;i++)if(tabCount(i)>0)n=i;return n;}
    int tabCount(int tab){return tab<1||tab>TAB_COUNTS.length?0:Microbot.getClientThread().runOnClientThreadOptional(()->client.getVarbitValue(TAB_COUNTS[tab-1])).orElse(0);}
    private int rearrangeMode(){return Microbot.getClientThread().runOnClientThreadOptional(()->client.getVarbitValue(Varbits.BANK_REARRANGE_MODE)).orElse(-1);}
    private int currentTab(){return Microbot.getClientThread().runOnClientThreadOptional(Rs2Bank::getCurrentTab).orElse(-1);}
    private BankSnapshot.BankStack findStack(int id){try{return stackById(snapshots.read(),id);}catch(Throwable ignored){return null;}}
    private boolean invokeTab(int tab){return Microbot.getClientThread().runOnClientThreadOptional(()->{Widget w=Rs2Bank.getTabWidget(tab);if(!usable(w))return false;Microbot.getMouse().click(new Rectangle(w.getBounds()));return true;}).orElse(false);}
    private boolean scrollTo(int slot){return Microbot.getClientThread().runOnClientThreadOptional(()->Rs2Bank.scrollBankToSlot(slot)).orElse(false);}
    private DragBounds itemToTabBounds(int id,int tab){return Microbot.getClientThread().runOnClientThreadOptional(()->{
        Widget box=client.getWidget(Rs2Bank.BANK_ITEM_CONTAINER);if(box==null||box.isHidden()||box.getDynamicChildren()==null)return null;Rectangle source=null;
        for(Widget w:box.getDynamicChildren())if(w!=null&&!w.isHidden()&&w.getItemId()==id&&usable(w.getBounds())){source=new Rectangle(w.getBounds());break;}
        Widget target=Rs2Bank.getTabWidget(tab);return source!=null&&usable(target)?new DragBounds(source,new Rectangle(target.getBounds())):null;
    }).orElse(null);}
    private DragBounds itemToItemBounds(int source,int target){return Microbot.getClientThread().runOnClientThreadOptional(()->{Rectangle a=Rs2Bank.getItemBounds(source),b=Rs2Bank.getItemBounds(target);return usable(a)&&usable(b)?new DragBounds(new Rectangle(a),new Rectangle(b)):null;}).orElse(null);}
    private boolean waitForItemTab(int id,int tab,int quantity,int timeout){return Global.sleepUntil(()->{try{BankSnapshot.BankStack s=stackById(snapshots.read(),id);return s!=null&&s.tab()==tab&&s.quantity()==quantity;}catch(Throwable ignored){return false;}},timeout);}
    private static BankSnapshot.BankStack stackById(BankSnapshot s,int id){if(s!=null)for(BankSnapshot.BankStack x:s.items())if(x.itemId()==id)return x;return null;}
    private Rectangle center(Rectangle r){int w=Math.max(4,Math.min(20,r.width)),h=Math.max(4,Math.min(20,r.height));return new Rectangle(r.x+Math.max(0,(r.width-w)/2),r.y+Math.max(0,(r.height-h)/2),w,h);}
    private boolean usable(Widget w){return w!=null&&!w.isHidden()&&usable(w.getBounds());}
    private boolean usable(Rectangle r){return r!=null&&r.width>0&&r.height>0&&r.x>=0&&r.y>=0&&r.x<client.getCanvasWidth()&&r.y<client.getCanvasHeight();}

    private static final class DragBounds{final Rectangle source,target;DragBounds(Rectangle source,Rectangle target){this.source=source;this.target=target;}}
    static final class ActuatorResult{private final boolean success;private final String message;private ActuatorResult(boolean success,String message){this.success=success;this.message=message;}static ActuatorResult ok(String m){return new ActuatorResult(true,m);}static ActuatorResult fail(String m){return new ActuatorResult(false,m);}boolean success(){return success;}String message(){return message;}}
}
