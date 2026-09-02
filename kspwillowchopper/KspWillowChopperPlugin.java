package net.runelite.client.plugins.microbot.kspwillowchopper;

import com.google.inject.Provides;
import net.runelite.api.ChatMessageType;
import net.runelite.api.GameObject;
import net.runelite.api.NPC;
import net.runelite.api.Point;
import net.runelite.api.coords.WorldPoint;
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
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.plugins.microbot.kspmule.KspMuleWorkerService;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.KspEggEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.KspEntlingsEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.KspFlowersEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.KspFoxEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.KspHivesEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.KspLeprechaunEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.KspRitualEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.KspRootEvent;
import net.runelite.client.plugins.microbot.kspwillowchopper.forestry.KspStrugglingSaplingEvent;
import net.runelite.client.plugins.microbot.util.inventory.InteractOrder;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.ui.overlay.OverlayManager;

import javax.inject.Inject;
import java.awt.Polygon;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@PluginDescriptor(
        name = PluginConstants.KSP + "Chopper",
        description = "Deterministic tree chopping with banking, Firemaking and optional Forestry helpers.",
        tags = {"woodcutting", "firemaking", "forestry", "ksp", "microbot"},
        authors = {"KSP"},
        version = KspWillowChopperPlugin.VERSION,
        minClientVersion = "2.1.32",
        enabledByDefault = PluginConstants.DEFAULT_ENABLED,
        isExternal = PluginConstants.IS_EXTERNAL
)
public class KspWillowChopperPlugin extends Plugin {
    /** Intentionally unchanged at the user's request. */
    public static final String VERSION = "0.1.3";

    private static final String LEGACY_FORESTRY_PACKAGE =
            "net.runelite.client.plugins.microbot.kspwillowchopper.forestry.";
    private static final Pattern ANIMA_BARK_PATTERN = Pattern.compile(
            "You've been awarded <col=[0-9a-f]+>(\\d+) Anima-infused bark</col>\\.");

    @Inject private KspWillowChopperConfig config;
    @Inject private OverlayManager overlayManager;
    @Inject private KspWillowChopperOverlay overlay;
    @Inject private KspWillowChopperScript script;
    @Inject public Rs2TileObjectCache rs2TileObjectCache;

    private final KspMuleWorkerService muleService = new KspMuleWorkerService("Willow Chopper");

    public final List<Rs2NpcModel> ritualCircles = new ArrayList<>();
    public final List<GameObject> saplingIngredients = new ArrayList<>();

    private final AtomicInteger completedForestryEvents = new AtomicInteger();
    private final AtomicInteger logsChopped = new AtomicInteger();
    private final AtomicInteger animaBarkGained = new AtomicInteger();
    private final AtomicLong lastForestryInteractionMillis = new AtomicLong();
    private final Object forestryCompletionLock = new Object();
    private final Map<KspForestryEvent, Long> forestryCompletionTimes = new EnumMap<>(KspForestryEvent.class);
    private final List<BlockingEvent> forestryHandlers = new ArrayList<>();

    private volatile long lastForestryInteractionKey = Long.MIN_VALUE;
    private volatile KspForestryEvent currentForestryEvent = KspForestryEvent.NONE;
    private volatile boolean runtimeActive;
    private KspStrugglingSaplingEvent saplingEvent;

    @Provides
    KspWillowChopperConfig provideConfig(ConfigManager manager) {
        return manager.getConfig(KspWillowChopperConfig.class);
    }

    @Override
    protected void startUp() {
        runtimeActive = true;
        currentForestryEvent = KspForestryEvent.NONE;
        completedForestryEvents.set(0);
        logsChopped.set(0);
        animaBarkGained.set(0);
        lastForestryInteractionMillis.set(0L);
        lastForestryInteractionKey = Long.MIN_VALUE;
        ritualCircles.clear();
        saplingIngredients.clear();
        synchronized (forestryCompletionLock) {
            forestryCompletionTimes.clear();
        }

        purgeLegacyGlobalForestryHandlers();
        rebuildForestryHandlers();
        muleService.start(config);
        overlayManager.add(overlay);
        script.run(config);
    }

    @Override
    protected void shutDown() {
        runtimeActive = false;
        currentForestryEvent = KspForestryEvent.NONE;
        forestryHandlers.clear();
        saplingEvent = null;

        script.shutdown();
        muleService.shutdown();
        ritualCircles.clear();
        saplingIngredients.clear();
        overlayManager.remove(overlay);
        purgeLegacyGlobalForestryHandlers();
    }

    /**
     * Older Chopper builds registered all Forestry helpers in Microbot's global
     * BlockingEventManager. A hot reload could leave one behind and freeze every
     * script. Remove only Chopper Forestry handlers; core Microbot events and
     * other plugins are untouched.
     */
    private void purgeLegacyGlobalForestryHandlers() {
        try {
            List<BlockingEvent> registered = new ArrayList<>(Microbot.getBlockingEventManager().getEvents());
            for (BlockingEvent event : registered) {
                if (event != null && event.getClass().getName().startsWith(LEGACY_FORESTRY_PACKAGE)) {
                    Microbot.getBlockingEventManager().remove(event);
                }
            }
        } catch (Exception ex) {
            Microbot.logStackTrace("KSP Chopper legacy Forestry cleanup", ex);
        }
    }

    /**
     * Build local event handlers only. Nothing here is registered globally.
     */
    private void rebuildForestryHandlers() {
        forestryHandlers.clear();
        saplingEvent = null;

        if (!runtimeActive || config == null || !config.enableForestry()) {
            return;
        }

        if (config.rootEvent()) forestryHandlers.add(new KspRootEvent(this));
        if (config.saplingEvent()) {
            saplingEvent = new KspStrugglingSaplingEvent(this);
            forestryHandlers.add(saplingEvent);
        }
        if (config.entlingsEvent()) forestryHandlers.add(new KspEntlingsEvent(this));
        if (config.hivesEvent()) forestryHandlers.add(new KspHivesEvent(this));
        if (config.eggEvent()) forestryHandlers.add(new KspEggEvent(this));
        if (config.foxEvent()) forestryHandlers.add(new KspFoxEvent(this));
        if (config.ritualEvent()) forestryHandlers.add(new KspRitualEvent(this));
        if (config.leprechaunEvent()) forestryHandlers.add(new KspLeprechaunEvent(this));
        if (config.flowersEvent()) forestryHandlers.add(new KspFlowersEvent(this));
    }

    /**
     * Called from the Chopper script thread. A Forestry event may temporarily own
     * that thread, but never Microbot's shared BlockingEvent executor.
     */
    public boolean runForestryIfNeeded() {
        if (!runtimeActive || config == null || !config.enableForestry()) {
            currentForestryEvent = KspForestryEvent.NONE;
            return false;
        }

        for (BlockingEvent handler : new ArrayList<>(forestryHandlers)) {
            if (!runtimeActive) {
                return false;
            }

            try {
                if (!handler.validate()) {
                    continue;
                }

                handler.execute();
                return true;
            } catch (Exception ex) {
                Microbot.logStackTrace("KSP Chopper Forestry", ex);
                return true;
            } finally {
                currentForestryEvent = KspForestryEvent.NONE;
            }
        }

        currentForestryEvent = KspForestryEvent.NONE;
        return false;
    }

    @Subscribe
    public void onConfigChanged(ConfigChanged event) {
        if (!KspWillowChopperConfig.GROUP.equals(event.getGroup())) {
            return;
        }

        if ("enableForestry".equals(event.getKey()) || event.getKey().endsWith("Event")) {
            currentForestryEvent = KspForestryEvent.NONE;
            rebuildForestryHandlers();
        }
    }

    @Subscribe
    public void onChatMessage(ChatMessage event) {
        if (event.getType() != ChatMessageType.SPAM
                && event.getType() != ChatMessageType.GAMEMESSAGE
                && event.getType() != ChatMessageType.MESBOX) {
            return;
        }

        String message = event.getMessage();
        String lower = message.toLowerCase(Locale.ROOT);

        if (lower.contains("the fire has burned out")) {
            script.notifyFireBurnedOut();
        }

        String selectedResource = getSelectedResourceName();
        if (lower.startsWith("you get")
                && selectedResource != null
                && lower.contains(selectedResource.toLowerCase(Locale.ROOT))) {
            logsChopped.incrementAndGet();
        }

        Matcher bark = ANIMA_BARK_PATTERN.matcher(message);
        if (bark.matches()) {
            animaBarkGained.addAndGet(Integer.parseInt(bark.group(1)));
        }

        if (message.startsWith("The sapling seems to love") && saplingEvent != null) {
            saplingEvent.learnFromChatMessage(message);
        }
    }

    @Subscribe
    public void onNpcSpawned(NpcSpawned event) {
        NPC npc = event.getNpc();
        if (isRitualCircle(npc.getId())) {
            ritualCircles.add(new Rs2NpcModel(npc));
        }
    }

    @Subscribe
    public void onNpcDespawned(NpcDespawned event) {
        NPC npc = event.getNpc();
        if (isRitualCircle(npc.getId())) {
            ritualCircles.removeIf(model -> model.getIndex() == npc.getIndex());
        }
    }

    @Subscribe
    public void onGameObjectSpawned(GameObjectSpawned event) {
        GameObject object = event.getGameObject();
        if (isSaplingIngredient(object.getId())) {
            saplingIngredients.add(object);
        }
    }

    @Subscribe
    public void onGameObjectDespawned(GameObjectDespawned event) {
        GameObject object = event.getGameObject();
        if (!isSaplingIngredient(object.getId())) {
            return;
        }

        saplingIngredients.remove(object);
        if (saplingIngredients.isEmpty() && saplingEvent != null) {
            saplingEvent.resetLearnedCombination();
        }
    }

    private boolean isRitualCircle(int id) {
        return id >= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_A_1
                && id <= NpcID.GATHERING_EVENT_ENCHANTED_RITUAL_D_4;
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

    public boolean ensureInventorySpace(int slots) {
        int free = Rs2Inventory.emptySlotCount();
        if (free >= slots) {
            return true;
        }

        int needed = slots - free;
        KspTree tree = getSelectedTree();
        int amount = Math.min(needed, Rs2Inventory.count(tree.getResourceId()));
        if (amount <= 0) {
            return false;
        }

        Rs2Inventory.dropAmount(tree.getResourceName(), amount, InteractOrder.EFFICIENT_ROW);
        return Rs2Inventory.emptySlotCount() >= slots;
    }

    public boolean canStartForestryInteraction() {
        return canStartForestryInteraction(Long.MIN_VALUE, "generic");
    }

    public boolean canStartForestryInteraction(long hash, String action) {
        long elapsed = System.currentTimeMillis() - lastForestryInteractionMillis.get();
        long key = forestryInteractionKey(hash, action);
        return runtimeActive
                && elapsed >= 900L
                && (key != lastForestryInteractionKey || elapsed >= 2500L)
                && !Rs2Player.isMoving()
                && !Rs2Player.isAnimating(1500)
                && !Rs2Player.isInteracting();
    }

    public boolean moveDirectlyToForestryTarget(long hash, WorldPoint target, Point minimap, Polygon canvas) {
        if (!runtimeActive || target == null) {
            return false;
        }
        if (target.equals(Rs2Player.getWorldLocation())) {
            return true;
        }
        if (!canStartForestryInteraction(hash, "Move")) {
            return false;
        }

        if (minimap != null) {
            Microbot.getMouse().click(minimap);
        } else if (canvas != null) {
            Microbot.getMouse().click(canvas.getBounds());
        } else {
            return false;
        }

        markForestryInteraction(hash, "Move");
        return true;
    }

    public void markForestryInteraction() {
        markForestryInteraction(Long.MIN_VALUE, "generic");
    }

    public void markForestryInteraction(long hash, String action) {
        lastForestryInteractionKey = forestryInteractionKey(hash, action);
        lastForestryInteractionMillis.set(System.currentTimeMillis());
    }

    private long forestryInteractionKey(long hash, String action) {
        return hash * 31L ^ (long) (action == null ? 0 : action.toLowerCase(Locale.ROOT).hashCode());
    }

    public boolean completeForestryEvent(KspForestryEvent event) {
        if (event == null || event == KspForestryEvent.NONE) {
            return false;
        }

        long now = System.currentTimeMillis();
        synchronized (forestryCompletionLock) {
            long last = forestryCompletionTimes.getOrDefault(event, 0L);
            if (now - last < 120_000L) {
                return false;
            }
            forestryCompletionTimes.put(event, now);
            completedForestryEvents.incrementAndGet();
            return true;
        }
    }

    public boolean isForestryRuntimeActive() {
        return runtimeActive;
    }

    public boolean isForestryEventEnabled(KspForestryEvent event) {
        if (!runtimeActive || config == null || !config.enableForestry()
                || event == null || event == KspForestryEvent.NONE) {
            return false;
        }

        switch (event) {
            case RISING_ROOTS: return config.rootEvent();
            case STRUGGLING_SAPLING: return config.saplingEvent();
            case FRIENDLY_ENTLINGS: return config.entlingsEvent();
            case BEEHIVE: return config.hivesEvent();
            case PHEASANT: return config.eggEvent();
            case POACHERS: return config.foxEvent();
            case ENCHANTMENT_RITUAL: return config.ritualEvent();
            case LEPRECHAUN: return config.leprechaunEvent();
            case FLOWERING_TREE: return config.flowersEvent();
            default: return false;
        }
    }

    public KspWillowChopperConfig getConfig() { return config; }
    public KspWillowChopperScript getScript() { return script; }
    public KspForestryEvent getCurrentForestryEvent() { return currentForestryEvent; }
    public void setCurrentForestryEvent(KspForestryEvent event) {
        currentForestryEvent = event == null ? KspForestryEvent.NONE : event;
    }
    public KspTree getSelectedTree() {
        KspTree tree = config == null ? null : config.tree();
        return tree == null ? KspTree.WILLOW : tree;
    }
    public int getSelectedResourceId() { return getSelectedTree().getResourceId(); }
    public String getSelectedResourceName() { return getSelectedTree().getResourceName(); }
    public boolean isSelectedResourceCampfireBurnable() { return getSelectedTree().isCampfireBurnable(); }
    public int getCompletedForestryEvents() { return completedForestryEvents.get(); }
    public int getLogsChopped() { return logsChopped.get(); }
    public int getAnimaBarkGained() { return animaBarkGained.get(); }
    public boolean hasLearnedSaplingCombination() {
        return saplingEvent != null && saplingEvent.hasCompleteCombination();
    }
    public String[] getSaplingCombination() {
        return saplingEvent == null ? new String[]{"-", "-", "-"} : saplingEvent.getLearnedNames();
    }
    public String getObjectName(GameObject object) {
        if (object == null) return "Unknown";
        try {
            String name = new Rs2TileObjectModel(object).getName();
            return name == null || name.isEmpty() ? "Unknown" : name;
        } catch (Exception ex) {
            return "Unknown";
        }
    }
}
