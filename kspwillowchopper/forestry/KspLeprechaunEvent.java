package net.runelite.client.plugins.microbot.kspwillowchopper.forestry;

import net.runelite.api.gameval.*;
import net.runelite.client.plugins.microbot.*;
import net.runelite.client.plugins.microbot.kspwillowchopper.*;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;

public class KspLeprechaunEvent implements BlockingEvent {
    private final KspWillowChopperPlugin plugin; public KspLeprechaunEvent(KspWillowChopperPlugin plugin){this.plugin=plugin;}
    @Override public boolean validate(){return Microbot.isPluginEnabled(plugin)&&Microbot.isLoggedIn()&&Microbot.getRs2NpcCache().query().withId(NpcID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN).nearest()!=null;}
    @Override public boolean execute(){plugin.setCurrentForestryEvent(KspForestryEvent.LEPRECHAUN);while(validate()){var rainbow=Microbot.getRs2TileObjectCache().query().withId(ObjectID.GATHERING_EVENT_WOODCUTTING_LEPRECHAUN_RAINBOW).nearest();if(rainbow==null){sleepUntil(()->false,300);continue;}if(Rs2Player.getWorldLocation().equals(rainbow.getWorldLocation())){sleepUntil(()->false,350);continue;}if(!plugin.moveDirectlyToForestryTarget(rainbow.getHash(),rainbow.getWorldLocation(),rainbow.getMinimapLocation(),rainbow.getCanvasTilePoly())){sleepUntil(()->false,150);continue;}sleepUntil(()->Rs2Player.getWorldLocation().equals(rainbow.getWorldLocation())||!validate(),2500);}plugin.completeForestryEvent(KspForestryEvent.LEPRECHAUN);return true;}
    @Override public BlockingEventPriority priority(){return BlockingEventPriority.NORMAL;}
}
