package net.runelite.client.plugins.microbot.kspwillowchopper;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameObjectDespawned;
import net.runelite.api.events.GameObjectSpawned;
import net.runelite.api.events.NpcDespawned;
import net.runelite.api.events.NpcSpawned;
import net.runelite.api.gameval.NpcID;
import net.runelite.api.gameval.ObjectID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.microbot.BlockingEvent;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.PluginConstants;
import net.runelite.client.plugins.microbot.api.tileobject.Rs2TileObjectCache;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.KspEggEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.KspEntlingsEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.KspFlowersEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.KspFoxEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.KspHivesEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.KspLeprechaunEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.KspRitualEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.KspRootEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.KspStrugglingSaplingEvent;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.AWTException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@PluginDescriptor(
        name = PluginConstants.KSP + "Chopper",
        description = "Direct willow chopping with bank or Forester's Campfire log handling and Forestry events.",
        tags = {"willow", "woodcutting", "firemaking", "forestry", "ksp", "microbot"},
        authors = {"KSP"},
        version = KspWillowChopperPlugin.VERSION,
        minClientVersion = "2.1.32",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
@Slf4j
public class KspWillowChopperPlugin extends Plugin {
    public static final String VERSION = "1.1.4";

    private static final Pattern ANIMA_BARK_PATTERN =
            Pattern.compile("You've been awarded <col=[0-9a-f]+>(\\d+) Anima-infused bark</col>\\.");

    @Inject private KspWillowChopperConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private KspWillowChopperOverlay overlay;
    @Inject private KspWillowChopperScript script;
    @Inject public Rs2TileObjectCache rs2TileObjectCache;

    public final List<Rs2NpcModel> ritualCircles = new ArrayList<>();
    public final List<GameObject> saplingIngredients = new ArrayList<>();

    private final AtomicInteger completedForestryEvents = new AtomicInteger();
    private final AtomicInteger logsChopped = new AtomicInteger();
    private final AtomicInteger animaBarkGained = new AtomicInteger();

    private KspEggEvent eggEvent;
    private KspEntlingsEvent entlingsEvent;
    private KspFlowersEvent flowersEvent;
    private KspFoxEvent foxEvent;
    private KspHivesEvent hivesEvent;
    private KspLeprechaunEvent leprechaunEvent;
    private KspRitualEvent ritualEvent;
    private KspRootEvent rootEvent;
    private KspStrugglingSaplingEvent saplingEvent;

    private volatile KspForestryEvent currentForestryEvent = KspForestryEvent.NONE;

    @Provides
    KspWillowChopperConfig provideConfig(ConfigManager configManager) {
        return configManager.getConfig(KspWillowChopperConfig.class);
    }

    @Override
    protected void startUp() throws AWTException {
        completedForestryEvents.set(0);
        logsChopped.set(0);
        animaBarkGained.set(0);
        currentForestryEvent = KspForestryEvent.NONE;

        overlayManager.add(overlay);

        if (config.enableForestry()) {
            addForestryEvents();
        }

        script.run(config);
    }

    @Override
    protected void shutDown() {
        script.shutdown();
        removeForestryEvents();
        ritualCircles.clear();
        saplingIngredients.clear();
        currentForestryEvent = KspForestryEvent.NONE;
        overlayManager.remove(overlay);
    }

    private void addForestryEvents() {
        removeForestryEvents();

        if (config.rootEvent()) {
            rootEvent = new KspRootEvent(this);
            Microbot.getBlockingEventManager().add(rootEvent);
        }
        if (config.saplingEvent()) {
            saplingEvent = new KspStrugglingSaplingEvent(this);
            Microbot.getBlockingEventManager().add(saplingEvent);
        }
        if (config.entlingsEvent()) {
            entlingsEvent = new KspEntlingsEvent(this);
            Microbot.getBlockingEventManager().add(entlingsEvent);
        }
        if (config.hivesEvent()) {
            hivesEvent = new KspHivesEvent(this);
            Microbot.getBlockingEventManager().add(hivesEvent);
        }
        if (config.eggEvent()) {
            eggEvent = new KspEggEvent(this);
            Microbot.getBlockingEventManager().add(eggEvent);
        }
        if (config.foxEvent()) {
            foxEvent = new KspFoxEvent(this);
            Microbot.getBlockingEventManager().add(foxEvent);
        }
        if (config.ritualEvent()) {
            ritualEvent = new KspRitualEvent(this);
            Microbot.getBlockingEventManager().add(ritualEvent);
        }
        if (config.leprechaunEvent()) {
            leprechaunEvent = new KspLeprechaunEvent(this);
            Microbot.getBlockingEventManager().add(leprechaunEvent);
        }
        if (config.flowersEvent()) {
            flowersEvent = new KspFlowersEvent(this);
            Microbot.getBlockingEventManager().add(flowersEvent);
        }
    }

    private void removeForestryEvents() {
        removeEvent(rootEvent);
        removeEvent(saplingEvent);
        removeEvent(entlingsEvent);
        removeEvent(hivesEvent);
        removeEvent(eggEvent);
        removeEvent(foxEvent);
        removeEvent(ritualEvent);
        removeEvent(leprechaunEvent);
        removeEvent(flowersEvent);

        rootEvent = null;
        saplingEvent = null;
        entlingsEvent = null;
        hivesEvent = null;
        eggEvent = null;
        foxEvent = null;
        ritualEvent = null;
        leprechaunEvent = null;
        flowersEvent = null;
    }

    private void removeEvent(BlockingEvent event) {
        if (event != null) {
            Microbot.getBlockingEventManager().remove(event);
        }
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!KspWillowChopperConfig.GROUP.equals(event.getGroup())) {
            return;
        }

        if (event.getKey().equals("enableForestry") || event.getKey().endsWith("Event")) {
            if (config.enableForestry()) {
                addForestryEvents();
            } else {
                removeForestryEvents();
                currentForestryEvent = KspForestryEvent.NONE;
            }
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.SPAM
                && event.getType() != ChatMessageType.GAMEMESSAGE
                && event.getType() != ChatMessageType.MESBOX) {
            return;
        }

        String msg = event.getMessage();
        String lower = msg.toLowerCase();

        if (lower.startsWith("you get") && lower.contains("willow logs")) {
            logsChopped.incrementAndGet();
        }

        Matcher bark = ANIMA_BARK_PATTERN.matcher(msg);
        if (bark.matches()) {
            animaBarkGained.addAndGet(Integer.parseInt(bark.group(1)));
        }

        if (msg.startsWith("The sapling seems to love") && saplingEvent != null) {
            saplingEvent.learnFromChatMessage(msg);
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        NPC npc = event.getNpc();
        int id = npc.getId();

        if (id >= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1
                && id <= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_4) {
            ritualCircles.add(new Rs2NpcModel(npc));
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        int id = npc.getId();

        if (id >= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1
                && id <= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_4) {
            ritualCircles.removeIf(model -> model.getIndex() == npc.getIndex());
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject object = event.getGameObject();
        script.notifyGameObjectSpawned(object);
        if (isSaplingIngredient(object.getId())) {
            saplingIngredients.add(object);
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        GameObject object = event.getGameObject();
        script.notifyGameObjectDespawned(object);
        if (!isSaplingIngredient(object.getId())) {
            return;
        }

        saplingIngredients.remove(object);

        // Keep the learned optimal combination until the event itself has actually ended.
        if (saplingIngredients.isEmpty() && saplingEvent != null) {
            saplingEvent.resetLearnedCombination();
        }
    }

    private boolean isSaplingIngredient(int id) {
        return id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_1
                || id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_2
                || id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_3
                || id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4A
                || id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4B
                || id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_4C
                || id == ObjectID.GATHERING_EVENT_SAPLING_INGREDIENT_5;
    }

    public boolean ensureInventorySpace(int requiredSlots) {
        int free = Rs2Inventory.emptySlotCount();
        if (free >= requiredSlots) {
            return true;
        }

        int needed = requiredSlots - free;
        KspTree tree = getSelectedTree();
        int availableResources = Rs2Inventory.count(tree.getResourceId());
        int toDrop = Math.min(needed, availableResources);

        if (toDrop <= 0) {
            return false;
        }

        Rs2Inventory.dropAmount(tree.getResourceName(), toDrop, InteractOrder.EFFICIENT_ROW);
        return Rs2Inventory.emptySlotCount() >= requiredSlots;
    }

    public KspTree getSelectedTree() {
        KspTree selected = config == null ? null : config.tree();
        return selected == null ? KspTree.WILLOW : selected;
    }

    public int getSelectedResourceId() {
        return getSelectedTree().getResourceId();
    }

    public String getSelectedResourceName() {
        return getSelectedTree().getResourceName();
    }

    public boolean isSelectedResourceCampfireBurnable() {
        return getSelectedTree().isCampfireBurnable();
    }

    public void incrementForestryEventCompleted() {
        completedForestryEvents.incrementAndGet();
    }

    public int getCompletedForestryEvents() { return completedForestryEvents.get(); }
    public int getLogsChopped() { return logsChopped.get(); }
    public int getAnimaBarkGained() { return animaBarkGained.get(); }

    public KspForestryEvent getCurrentForestryEvent() { return currentForestryEvent; }

    public void setCurrentForestryEvent(KspForestryEvent event) {
        currentForestryEvent = event == null ? KspForestryEvent.NONE : event;
    }

    public KspWillowChopperConfig getConfig() { return config; }
    public KspWillowChopperScript getScript() { return script; }

    public boolean hasLearnedSaplingCombination() {
        return saplingEvent != null && saplingEvent.hasCompleteCombination();
    }

    public String[] getSaplingCombination() {
        return saplingEvent == null
                ? new String[] {"-", "-", "-"}
                : saplingEvent.getLearnedNames();
    }

    public String getObjectName(GameObject object) {
        return Rs2GameObject.getCompositionName(object).orElse("Unknown");
    }
}
