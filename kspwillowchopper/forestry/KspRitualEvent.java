package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.*;
import net.runelite.client.plugins.microbot.kspwillowchopper.*;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import java.util.List;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class KspRitualEvent implements BlockingEvent {
    private final KspWillowChopperPlugin plugin; public KspRitualEvent(KspWillowChopperPlugin plugin){this.plugin=plugin;}
    @Override public boolean validate(){return plugin.isForestryEventEnabled(KspForestryEvent.ENCHANTMENT_RITUAL)&&Microbot.isPluginEnabled(plugin)&&Microbot.isLoggedIn()&&Microbot.getRs2NpcCache().query().withId(NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_DRYAD).nearest()!=null;}
    @Override public boolean execute(){plugin.setCurrentForestryEvent(KspForestryEvent.ENCHANTMENT_RITUAL);plugin.ensureInventorySpace(1);while(validate()){Rs2NpcModel target=solveCircles(plugin.ritualCircles);if(target==null){sleepUntil(()->false,350);continue;}if(Rs2Player.getWorldLocation().equals(target.getWorldLocation())){sleepUntil(()->false,400);continue;}if(!plugin.moveDirectlyToForestryTarget(target.getHash(),target.getWorldLocation(),target.getMinimapLocation(),target.getCanvasTilePoly())){sleepUntil(()->false,150);continue;}sleepUntil(()->Rs2Player.getWorldLocation().equals(target.getWorldLocation())||!validate(),2500);}plugin.completeForestryEvent(KspForestryEvent.ENCHANTMENT_RITUAL);return true;}
    private Rs2NpcModel solveCircles(List<Rs2NpcModel> circles){if(circles.size()!=5)return null;int xor=0;for(Rs2NpcModel n:circles)xor^=ritualValue(n);for(Rs2NpcModel n:circles){int v=ritualValue(n);if((v&xor)==v)return n;}return null;}
    private int ritualValue(Rs2NpcModel n){int o=n.getId()-NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1;return(16<<(o/4))|(1<<(o%4));}
    @Override public BlockingEventPriority priority(){return BlockingEventPriority.NORMAL;}
}
