package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import net.runelite.api.*;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.grandexchange.*;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import java.util.*;
import java.util.concurrent.Callable;
import static net.runelite.client.plugins.microbot.util.Global.*;

/** Hardened GE interaction helpers for live slot/menu verification. */
final class KspGeInteraction {
    private static final int FIRST_GE_SLOT_CHILD=7,GE_SLOT_WIDGET_COUNT=8,COLLECT_CONTAINER_CHILD=6,MENU_STABLE_POLLS=3,MENU_POLL_MIN_MS=65,MENU_POLL_MAX_MS=95,MENU_ENTRY_HEIGHT=15;
    private static final long MENU_OPEN_TIMEOUT_MS=2500,BUY_SLOT_RESOLVE_TIMEOUT_MS=3500;

    boolean ensureOverview(){if(Rs2GrandExchange.isOfferScreenOpen()){Rs2GrandExchange.backToOverview();sleepUntil(()->!Rs2GrandExchange.isOfferScreenOpen(),1800);}return Rs2GrandExchange.isOpen()&&!Rs2GrandExchange.isOfferScreenOpen();}
    GrandExchangeSlots placeBuyOffer(String name,int id,int price,int quantity){GrandExchangeSlots existing=Rs2GrandExchange.findSlotForItem(id,false);if(existing!=null)return existing;if(!ensureOverview())return null;boolean started=Rs2GrandExchange.buyItem(name,price,quantity);long deadline=System.currentTimeMillis()+BUY_SLOT_RESOLVE_TIMEOUT_MS;while(System.currentTimeMillis()<deadline&&!Thread.currentThread().isInterrupted()){GrandExchangeSlots s=Rs2GrandExchange.findSlotForItem(id,false);if(s==null&&name!=null)s=Rs2GrandExchange.findSlotForItem(name,false);if(s!=null)return s;sleep(70,120);}if(Rs2GrandExchange.isOfferScreenOpen()){Rs2GrandExchange.backToOverview();sleepUntil(()->!Rs2GrandExchange.isOfferScreenOpen(),1500);}return started?Rs2GrandExchange.findSlotForItem(id,false):null;}
    boolean collectOverviewToInventory(){if(!ensureOverview())return false;Widget w=resolveCollectWidget();if(w!=null){if(Rs2Widget.clickWidget(w)){sleep(120,220);return true;}Rectangle b=copyBounds(w);if(validBounds(b)){Microbot.getMouse().click(b);sleep(120,220);return true;}}return Rs2GrandExchange.collectAllToInventory();}
    boolean abortOfferViaStableMenu(GrandExchangeSlots slot){if(slot==null||!ensureOverview())return false;for(int attempt=0;attempt<2;attempt++){SlotTarget t=resolveSlotTarget(slot);if(t==null)return false;if(!closeOpenMenu())continue;Microbot.getMouse().click(t.clickBounds.x+t.clickBounds.width/2,t.clickBounds.y+t.clickBounds.height/2,true);MenuSnapshot s=waitForStableSlotMenu("Abort offer",t.widgetId);if(s==null||s.entry.param1!=t.widgetId){closeOpenMenu();sleep(100,180);continue;}Rectangle row=s.entryBounds();if(!validBounds(row)){closeOpenMenu();continue;}Microbot.getMouse().click(row);if(!sleepUntil(()->!isMenuOpen(),1200)){closeOpenMenu();continue;}sleep(120,220);return true;}return false;}

    private SlotTarget resolveSlotTarget(GrandExchangeSlots slot){return onClientThread(()->{int i=slot.ordinal();if(i<0||i>=GE_SLOT_WIDGET_COUNT)return null;Widget w=Microbot.getClient().getWidget(InterfaceID.GE_OFFERS,FIRST_GE_SLOT_CHILD+i);if(!isVisible(w))return null;Rectangle b=w.getBounds();return validBounds(b)?new SlotTarget(w.getId(),new Rectangle(b)):null;},null);}
    private Widget resolveCollectWidget(){return onClientThread(()->{Widget root=Microbot.getClient().getWidget(InterfaceID.GE_OFFERS,COLLECT_CONTAINER_CHILD);if(root==null)return null;for(Widget w:descendants(root))if(isVisible(w)&&hasLabel(w,"Collect"))return w;return isVisible(root)&&hasLabel(root,"Collect")?root:null;},null);}
    private MenuSnapshot waitForStableSlotMenu(String option,int widgetId){long deadline=System.currentTimeMillis()+MENU_OPEN_TIMEOUT_MS;MenuSnapshot previous=null;int stable=0;while(System.currentTimeMillis()<deadline&&!Thread.currentThread().isInterrupted()){MenuSnapshot current=readOpenSlotMenu(option,widgetId);if(current==null){previous=null;stable=0;}else if(current.sameLayoutAndEntry(previous)){if(++stable>=MENU_STABLE_POLLS)return current;}else{previous=current;stable=1;}sleep(MENU_POLL_MIN_MS,MENU_POLL_MAX_MS);}return null;}
    private MenuSnapshot readOpenSlotMenu(String option,int widgetId){return onClientThread(()->{Client c=Microbot.getClient();if(!c.isMenuOpen())return null;Menu m=c.getMenu();if(m==null)return null;int x=m.getMenuX(),y=m.getMenuY(),w=m.getMenuWidth(),h=m.getMenuHeight();MenuEntry[] entries=m.getMenuEntries();if(w<=0||h<=0||entries==null||entries.length==0)return null;for(int i=entries.length-1;i>=0;i--){MenuEntry e=entries[i];if(e!=null&&e.getParam1()==widgetId&&normalize(e.getOption()).equalsIgnoreCase(option))return new MenuSnapshot(x,y,w,h,entries.length,i,SlotMenuEntry.copyOf(e));}return null;},null);}
    private boolean closeOpenMenu(){if(!isMenuOpen())return true;Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);return sleepUntil(()->!isMenuOpen(),800);} private boolean isMenuOpen(){return onClientThread(()->Microbot.getClient().isMenuOpen(),false);}
    private List<Widget> descendants(Widget root){List<Widget> out=new ArrayList<>();collect(root,out,Collections.newSetFromMap(new IdentityHashMap<>()));return out;}
    private void collect(Widget w,List<Widget> out,Set<Widget> seen){if(w==null||!seen.add(w))return;out.add(w);collect(w.getDynamicChildren(),out,seen);collect(w.getChildren(),out,seen);collect(w.getNestedChildren(),out,seen);collect(w.getStaticChildren(),out,seen);}
    private void collect(Widget[] ws,List<Widget> out,Set<Widget> seen){if(ws!=null)for(Widget w:ws)collect(w,out,seen);}
    private boolean hasLabel(Widget w,String label){if(label.equalsIgnoreCase(normalize(w.getText()))||label.equalsIgnoreCase(normalize(w.getName())))return true;String[] a=w.getActions();if(a!=null)for(String s:a)if(label.equalsIgnoreCase(normalize(s)))return true;return false;}
    private boolean isVisible(Widget w){return w!=null&&!w.isHidden()&&validBounds(w.getBounds());} private Rectangle copyBounds(Widget w){return w==null||w.getBounds()==null?null:new Rectangle(w.getBounds());} private boolean validBounds(Rectangle r){return r!=null&&r.width>0&&r.height>0;} private static String normalize(String s){return s==null?"":s.replaceAll("<[^>]+>","").trim();}
    private <T>T onClientThread(Callable<T> c,T fallback){try{return Microbot.getClientThread().runOnClientThreadOptional(c).orElse(fallback);}catch(RuntimeException ex){return fallback;}}

    private static final class SlotTarget { private final int widgetId; private final Rectangle clickBounds; SlotTarget(int id,Rectangle b){widgetId=id;clickBounds=b;} }
    private static final class MenuSnapshot {
        private final int menuX,menuY,menuWidth,menuHeight,entryCount,entryIndex; private final SlotMenuEntry entry;
        MenuSnapshot(int x,int y,int w,int h,int count,int index,SlotMenuEntry entry){menuX=x;menuY=y;menuWidth=w;menuHeight=h;entryCount=count;entryIndex=index;this.entry=entry;}
        private boolean sameLayoutAndEntry(MenuSnapshot o){return o!=null&&menuX==o.menuX&&menuY==o.menuY&&menuWidth==o.menuWidth&&menuHeight==o.menuHeight&&entryCount==o.entryCount&&entryIndex==o.entryIndex&&entry.sameAction(o.entry);}
        private Rectangle entryBounds(){if(entryCount<=0||entryIndex<0||entryIndex>=entryCount||menuWidth<=4||menuHeight<=MENU_ENTRY_HEIGHT)return null;int y=menuY+menuHeight-8-entryIndex*MENU_ENTRY_HEIGHT;return new Rectangle(menuX+2,y-6,Math.max(1,menuWidth-4),13);}
    }
    private static final class SlotMenuEntry {
        private final String option; private final int identifier,param0,param1,itemId,worldViewId; private final MenuAction type;
        SlotMenuEntry(String o,int id,MenuAction t,int p0,int p1,int item,int world){option=o;identifier=id;type=t;param0=p0;param1=p1;itemId=item;worldViewId=world;}
        private static SlotMenuEntry copyOf(MenuEntry e){return new SlotMenuEntry(e.getOption(),e.getIdentifier(),e.getType(),e.getParam0(),e.getParam1(),e.getItemId(),e.getWorldViewId());}
        private boolean sameAction(SlotMenuEntry o){return o!=null&&identifier==o.identifier&&type==o.type&&param0==o.param0&&param1==o.param1&&itemId==o.itemId&&worldViewId==o.worldViewId&&normalize(option).equalsIgnoreCase(normalize(o.option));}
    }
}
