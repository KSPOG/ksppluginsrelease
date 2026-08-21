package net.runelite.client.plugins.microbot.kspwillowchopper;

import com.google.inject.Provides;
import net.runelite.api.*;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.*;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.*;
import net.runelite.client.plugins.microbot.*;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.*;
import net.runelite.client.plugins.microbot.util.inventory.*;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayManager;
import javax.inject.Inject;
import java.awt.Polygon;
import java.util.*;
import java.util.concurrent.atomic.*;
import java.util.regex.*;

@PluginDescriptor(name=PluginConstants.KSP+"Chopper",description="Direct willow chopping with bank or Forester's Campfire log handling and Forestry events.",tags={"willow","woodcutting","firemaking","forestry","ksp","microbot"},authors={"KSP"},version=KspWillowChopperPlugin.VERSION,minClientVersion="2.1.32",enabledByDefault=PluginConstants.DEFAULT_ENABLED,isExternal=PluginConstants.IS_EXTERNAL)
public class KspWillowChopperPlugin extends Plugin {
    public static final String VERSION="0.1.0"; private static final Pattern ANIMA_BARK_PATTERN=Pattern.compile("You've been awarded <col=[0-9a-f]+>(\\d+) Anima-infused bark</col>\\.");
    @Inject private KspWillowChopperConfig config; @Inject private OverlayManager overlayManager; @Inject private KspWillowChopperOverlay overlay; @Inject private KspWillowChopperScript script; @Inject public Rs2TileObjectCache rs2TileObjectCache;
    public final List<Rs2NpcModel> ritualCircles=new ArrayList<>(); public final List<GameObject> saplingIngredients=new ArrayList<>();
    private final AtomicInteger completedForestryEvents=new AtomicInteger(),logsChopped=new AtomicInteger(),animaBarkGained=new AtomicInteger(); private final AtomicLong lastForestryInteractionMillis=new AtomicLong();
    private final Object forestryCompletionLock=new Object(); private final Map<KspForestryEvent,Long> forestryCompletionTimes=new EnumMap<>(KspForestryEvent.class); private final List<BlockingEvent> forestryEvents=new ArrayList<>();
    private volatile long lastForestryInteractionKey=Long.MIN_VALUE; private volatile KspForestryEvent currentForestryEvent=KspForestryEvent.NONE; private KspStrugglingSaplingEvent saplingEvent;

    public KspWillowChopperConfig getConfig(){return config;} public KspWillowChopperScript getScript(){return script;} public KspForestryEvent getCurrentForestryEvent(){return currentForestryEvent;}
    @Provides KspWillowChopperConfig provideConfig(ConfigManager m){return m.getConfig(KspWillowChopperConfig.class);}
    @Override protected void startUp(){completedForestryEvents.set(0);logsChopped.set(0);animaBarkGained.set(0);currentForestryEvent=KspForestryEvent.NONE;lastForestryInteractionMillis.set(0);lastForestryInteractionKey=Long.MIN_VALUE;synchronized(forestryCompletionLock){forestryCompletionTimes.clear();}overlayManager.add(overlay);if(config.enableForestry())addForestryEvents();script.run(config);}
    @Override protected void shutDown(){script.shutdown();removeForestryEvents();ritualCircles.clear();saplingIngredients.clear();currentForestryEvent=KspForestryEvent.NONE;overlayManager.remove(overlay);}
    private void addForestryEvents(){removeForestryEvents();if(config.rootEvent())addForestryEvent(new KspRootEvent(this));if(config.saplingEvent()){saplingEvent=new KspStrugglingSaplingEvent(this);addForestryEvent(saplingEvent);}if(config.entlingsEvent())addForestryEvent(new KspEntlingsEvent(this));if(config.hivesEvent())addForestryEvent(new KspHivesEvent(this));if(config.eggEvent())addForestryEvent(new KspEggEvent(this));if(config.foxEvent())addForestryEvent(new KspFoxEvent(this));if(config.ritualEvent())addForestryEvent(new KspRitualEvent(this));if(config.leprechaunEvent())addForestryEvent(new KspLeprechaunEvent(this));if(config.flowersEvent())addForestryEvent(new KspFlowersEvent(this));}
    private void addForestryEvent(BlockingEvent e){forestryEvents.add(e);Microbot.getBlockingEventManager().add(e);} private void removeForestryEvents(){for(BlockingEvent e:forestryEvents)Microbot.getBlockingEventManager().remove(e);forestryEvents.clear();saplingEvent=null;}

    @Subscribe public void onConfigChanged(ConfigChanged e){if(!KspWillowChopperConfig.GROUP.equals(e.getGroup()))return;if(e.getKey().equals("enableForestry")||e.getKey().endsWith("Event")){if(config.enableForestry())addForestryEvents();else{removeForestryEvents();currentForestryEvent=KspForestryEvent.NONE;}}}
    @Subscribe public void onChatMessage(ChatMessage e){if(e.getType()!=ChatMessageType.SPAM&&e.getType()!=ChatMessageType.GAMEMESSAGE&&e.getType()!=ChatMessageType.MESBOX)return;String msg=e.getMessage(),lower=msg.toLowerCase();if(lower.contains("the fire has burned out"))script.notifyFireBurnedOut();if(lower.startsWith("you get")&&lower.contains("willow logs")){logsChopped.incrementAndGet();script.notifyResourceChopped();}Matcher bark=ANIMA_BARK_PATTERN.matcher(msg);if(bark.matches())animaBarkGained.addAndGet(Integer.parseInt(bark.group(1)));if(msg.startsWith("The sapling seems to love")&&saplingEvent!=null)saplingEvent.learnFromChatMessage(msg);}
    @Subscribe public void onNpcSpawned(NpcSpawned e){NPC n=e.getNpc();if(isRitual(n.getId()))ritualCircles.add(new Rs2NpcModel(n));} @Subscribe public void onNpcDespawned(NpcDespawned e){NPC n=e.getNpc();if(isRitual(n.getId()))ritualCircles.removeIf(m->m.getIndex()==n.getIndex());} private boolean isRitual(int id){return id>=NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1&&id<=NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_4;}
    @Subscribe public void onGameObjectSpawned(GameObjectSpawned e){GameObject o=e.getGameObject();script.notifyGameObjectSpawned(o);if(isSaplingIngredient(o.getId()))saplingIngredients.add(o);} @Subscribe public void onGameObjectDespawned(GameObjectDespawned e){GameObject o=e.getGameObject();script.notifyGameObjectDespawned(o);if(!isSaplingIngredient(o.getId()))return;saplingIngredients.remove(o);if(saplingIngredients.isEmpty()&&saplingEvent!=null)saplingEvent.resetLearnedCombination();}
    private boolean isSaplingIngredient(int id){return id==ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_1||id==ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_2||id==ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_3||id==ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4A||id==ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4B||id==ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4C||id==ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_5;}

    public boolean ensureInventorySpace(int slots){int free=Rs2Inventory.emptySlotCount();if(free>=slots)return true;int needed=slots-free;KspTree tree=getSelectedTree();int drop=Math.min(needed,Rs2Inventory.count(tree.getResourceId()));if(drop<=0)return false;Rs2Inventory.dropAmount(tree.getResourceName(),drop,InteractOrder.EFFICIENT_ROW);return Rs2Inventory.emptySlotCount()>=slots;}
    public KspTree getSelectedTree(){KspTree t=config==null?null:config.tree();return t==null?KspTree.WILLOW:t;} public int getSelectedResourceId(){return getSelectedTree().getResourceId();} public String getSelectedResourceName(){return getSelectedTree().getResourceName();} public boolean isSelectedResourceCampfireBurnable(){return getSelectedTree().isCampfireBurnable();}
    public boolean canStartForestryInteraction(){return canStartForestryInteraction(Long.MIN_VALUE,"generic");} public boolean canStartForestryInteraction(long hash,String action){long elapsed=System.currentTimeMillis()-lastForestryInteractionMillis.get(),key=forestryInteractionKey(hash,action);return elapsed>=900&&(key!=lastForestryInteractionKey||elapsed>=2500)&&!Rs2Player.isMoving()&&!Rs2Player.isAnimating(1500)&&!Rs2Player.isInteracting();}
    public boolean moveDirectlyToForestryTarget(long hash,WorldPoint target,Point minimap,Polygon canvas){if(target==null)return false;if(target.equals(Rs2Player.getWorldLocation()))return true;if(!canStartForestryInteraction(hash,"Move"))return false;if(minimap!=null)Microbot.getMouse().click(minimap);else if(canvas!=null)Microbot.getMouse().click(canvas.getBounds());else return false;markForestryInteraction(hash,"Move");return true;}
    public void markForestryInteraction(){markForestryInteraction(Long.MIN_VALUE,"generic");} public void markForestryInteraction(long hash,String action){lastForestryInteractionKey=forestryInteractionKey(hash,action);lastForestryInteractionMillis.set(System.currentTimeMillis());} private long forestryInteractionKey(long hash,String action){return hash*31L^(long)(action==null?0:action.toLowerCase().hashCode());}
    public boolean completeForestryEvent(KspForestryEvent e){if(e==null||e==KspForestryEvent.NONE)return false;long now=System.currentTimeMillis();synchronized(forestryCompletionLock){long last=forestryCompletionTimes.getOrDefault(e,0L);if(now-last<120_000)return false;completedForestryEvents.incrementAndGet();forestryCompletionTimes.put(e,now);return true;}}
    public void incrementForestryEventCompleted(){completeForestryEvent(currentForestryEvent);} public int getCompletedForestryEvents(){return completedForestryEvents.get();} public int getLogsChopped(){return logsChopped.get();} public int getAnimaBarkGained(){return animaBarkGained.get();}
    public void setCurrentForestryEvent(KspForestryEvent e){currentForestryEvent=e==null?KspForestryEvent.NONE:e;} public boolean hasLearnedSaplingCombination(){return saplingEvent!=null&&saplingEvent.hasCompleteCombination();} public String[] getSaplingCombination(){return saplingEvent==null?new String[]{"-","-","-"}:saplingEvent.getLearnedNames();}
    public String getObjectName(GameObject o){if(o==null)return "Unknown";try{String n=new Rs2TileObjectModel(o).getName();return n==null||n.isEmpty()?"Unknown":n;}catch(Exception ex){return "Unknown";}}
}
