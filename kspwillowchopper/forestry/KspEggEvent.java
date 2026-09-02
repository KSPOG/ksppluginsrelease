package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import net.runelite.api.gameval.*;
import net.runelite.client.plugins.microbot.*;
import net.runelite.client.plugins.microbot.kspwillowchopper.*;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import java.util.Comparator;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class KspEggEvent implements BlockingEvent {
    private final KspWillowChopperPlugin plugin; public KspEggEvent(KspWillowChopperPlugin plugin){this.plugin=plugin;}
    @Override public boolean validate(){return plugin.isForestryEventEnabled(KspForestryEvent.PHEASANT)&&Microbot.isPluginEnabled(plugin)&&Microbot.isLoggedIn()&&Microbot.getRs2NpcCache().query().withId(NpcID.GATHERING_EVENT_PHEASANT_FORESTER).nearest()!=null;}
    @Override public boolean execute(){plugin.setCurrentForestryEvent(KspForestryEvent.PHEASANT);if(Rs2Inventory.isFull()){if(!plugin.ensureInventorySpace(1))return false;sleepUntil(()->!Rs2Inventory.isFull(),3000);}while(validate()){var forester=Microbot.getRs2NpcCache().query().withId(NpcID.GATHERING_EVENT_PHEASANT_FORESTER).nearest();if(forester==null)break;if(Rs2Inventory.contains("Pheasant egg")){if(!plugin.canStartForestryInteraction(forester.getHash(),"Talk-to")){sleepUntil(()->false,150);continue;}forester.click("Talk-to");plugin.markForestryInteraction(forester.getHash(),"Talk-to");sleepUntil(Rs2Dialogue::isInDialogue,5000);while(Rs2Dialogue.isInDialogue())Rs2Dialogue.clickContinue();continue;}var nests=Microbot.getRs2TileObjectCache().query().where(o->o.getId()==ObjectID.GATHERING_EVENT_PHEASANT_NEST02).toList();var pheasants=Microbot.getRs2NpcCache().query().withId(NpcID.GATHERING_EVENT_PHEASANT).toList();var target=nests.stream().filter(n->pheasants.stream().noneMatch(p->p.getWorldLocation().equals(n.getWorldLocation()))).min(Comparator.comparingInt(n->n.getWorldLocation().distanceTo(Rs2Player.getWorldLocation()))).orElse(null);if(target==null){sleepUntil(()->false,300);continue;}if(!plugin.canStartForestryInteraction(target.getHash(),"Nest")){sleepUntil(()->false,150);continue;}target.click();plugin.markForestryInteraction(target.getHash(),"Nest");Rs2Player.waitForAnimation();}plugin.completeForestryEvent(KspForestryEvent.PHEASANT);return true;}
    @Override public BlockingEventPriority priority(){return BlockingEventPriority.NORMAL;}
}
