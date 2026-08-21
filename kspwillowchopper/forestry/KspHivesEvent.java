package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.*;
import net.runelite.client.plugins.microbot.kspwillowchopper.*;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;
import java.awt.event.KeyEvent;
import java.util.*;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class KspHivesEvent implements BlockingEvent {
    private final KspWillowChopperPlugin plugin; private final Set<Integer> completed=new HashSet<>(); public KspHivesEvent(KspWillowChopperPlugin plugin){this.plugin=plugin;}
    private boolean hive(int id){return id==NpcID.GATHERING_EVENT_BEES_BEEBOX_1||id==NpcID.GATHERING_EVENT_BEES_BEEBOX_2;}
    @Override public boolean validate(){return Microbot.isPluginEnabled(plugin)&&Microbot.isLoggedIn()&&!Microbot.getRs2NpcCache().query().where(x->hive(x.getId())).toList().isEmpty()&&plugin.isSelectedResourceCampfireBurnable()&&Rs2Inventory.count(plugin.getSelectedResourceId())>1;}
    @Override public boolean execute(){plugin.setCurrentForestryEvent(KspForestryEvent.BEEHIVE);completed.clear();while(validate()){if(Rs2Widget.findWidget("How many logs would you like to add",null,false)!=null){Rs2Keyboard.keyPress(KeyEvent.VK_SPACE);plugin.markForestryInteraction(0L,"Beehive-space");sleepUntil(()->Rs2Widget.findWidget("How many logs would you like to add",null,false)==null,3000);sleepUntil(()->!Rs2Player.isInteracting()&&!Rs2Player.isAnimating(),6000);continue;}var h=Microbot.getRs2NpcCache().query().where(x->hive(x.getId())&&!completed.contains(x.getIndex())).nearest();if(h==null||Rs2Inventory.count(plugin.getSelectedResourceId())<=1)break;if(!plugin.canStartForestryInteraction(h.getHash(),"Build")){sleepUntil(()->false,150);continue;}if(h.click("Build")){plugin.markForestryInteraction(h.getHash(),"Build");sleepUntil(()->Rs2Player.isInteracting()||Rs2Player.isAnimating(),3000);sleepUntil(()->!Rs2Player.isInteracting()&&!Rs2Player.isAnimating(),15000);if(Microbot.getRs2NpcCache().query().where(x->x.getIndex()==h.getIndex()).count()==0)completed.add(h.getIndex());}else sleepUntil(()->false,1000);}plugin.completeForestryEvent(KspForestryEvent.BEEHIVE);return true;}
    @Override public BlockingEventPriority priority(){return BlockingEventPriority.NORMAL;}
}
