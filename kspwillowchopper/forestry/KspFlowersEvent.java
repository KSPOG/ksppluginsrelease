package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.*;
import net.runelite.client.plugins.microbot.kspwillowchopper.*;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class KspFlowersEvent implements BlockingEvent {
    private final KspWillowChopperPlugin plugin; public KspFlowersEvent(KspWillowChopperPlugin plugin){this.plugin=plugin;}
    @Override public boolean validate(){return plugin.isForestryEventEnabled(KspForestryEvent.FLOWERING_TREE)&&Microbot.isPluginEnabled(plugin)&&Microbot.isLoggedIn()&&!Microbot.getRs2NpcCache().query().where(n->n.getName()!=null&&isFloweringBush(n.getId())).toList().isEmpty();}
    @Override public boolean execute(){plugin.setCurrentForestryEvent(KspForestryEvent.FLOWERING_TREE);plugin.ensureInventorySpace(3);while(validate()){var target=Microbot.getRs2NpcCache().query().where(n->n.getName()!=null&&isFloweringBush(n.getId())&&n.getAnimation()==-1).toList().stream().findFirst().orElse(null);if(target==null){sleepUntil(()->false,500);continue;}if(!plugin.canStartForestryInteraction(target.getHash(),"Tend-to")){sleepUntil(()->false,150);continue;}if(target.click("Tend-to")){plugin.markForestryInteraction(target.getHash(),"Tend-to");Rs2Player.waitForAnimation();sleepUntil(()->!Rs2Player.isInteracting(),8000);}else sleepUntil(()->false,300);}plugin.completeForestryEvent(KspForestryEvent.FLOWERING_TREE);return true;}
    private boolean isFloweringBush(int id){return id==NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL01||id==NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL02||id==NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL03||id==NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL04||id==NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL05||id==NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL06||id==NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL07||id==NpcID.GATHERING_EVENT_FLOWERING_TREE_BUSH_COL08;}
    @Override public BlockingEventPriority priority(){return BlockingEventPriority.NORMAL;}
}
