package net.runelite.client.plugins.microbot.mining;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import net.runelite.api.GameObject;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.ObjectComposition;
import net.runelite.api.ObjectID;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ItemID;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.mining.data.LocationOption;
import net.runelite.client.plugins.microbot.mining.data.MiningOreOption;
import net.runelite.client.plugins.microbot.mining.data.MiningRockLocations;
import net.runelite.client.plugins.microbot.mining.data.Rocks;
import net.runelite.client.plugins.microbot.util.antiban.Rs2Antiban;
import net.runelite.client.plugins.microbot.util.antiban.Rs2AntibanSettings;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.depositbox.Rs2DepositBox;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.math.Rs2Random;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.security.Login;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import javax.inject.Singleton;

import java.util.ArrayList;
import java.awt.event.KeyEvent;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

enum State {
    MINING,
    RESETTING,
}

@Singleton
public class AutoMiningScript extends Script {
    private static final Logger log = LoggerFactory.getLogger(AutoMiningScript.class);

    private static final int GEM_MINE_UNDERGROUND = 11410;
    private static final WorldPoint VARROCK_SOUTH_EAST_MINE_POSITION =
            new WorldPoint(3284, 3363, 0);
    private static final LocationOption VARROCK_SOUTH_EAST_MINE =
            new LocationOption(VARROCK_SOUTH_EAST_MINE_POSITION, "Varrock South East Mine", false);

    // World-object IDs are used only to identify mineable rock objects.
    private static final Set<Integer> COPPER_ROCK_IDS = new HashSet<>(Arrays.asList(
            ObjectID.COPPER_ROCKS,
            ObjectID.COPPER_ROCKS_10943,
            ObjectID.COPPER_ROCKS_11161,
            ObjectID.COPPER_ROCKS_37944
    ));
    private static final Set<Integer> TIN_ROCK_IDS = new HashSet<>(Arrays.asList(
            ObjectID.TIN_ROCKS,
            ObjectID.TIN_ROCKS_11360,
            ObjectID.TIN_ROCKS_11361,
            ObjectID.TIN_ROCKS_37945
    ));
    private static final Set<Integer> IRON_ROCK_IDS = new HashSet<>(Arrays.asList(
            ObjectID.IRON_ROCKS,
            ObjectID.IRON_ROCKS_11365,
            ObjectID.IRON_ROCKS_36203,
            ObjectID.IRON_ROCKS_42833
    ));

    // Inventory item IDs are deliberately separate from world rock IDs.
    private static final int COPPER_ORE_ITEM_ID = 436;
    private static final int TIN_ORE_ITEM_ID = 438;
    private static final int IRON_ORE_ITEM_ID = 440;

    // Name aliases are a fallback for client revisions/locations whose active
    // rock object ID is not present in the sets above. Generic "Rocks" is not
    // accepted because it cannot distinguish copper from tin.
    private static final Set<String> COPPER_ROCK_NAMES = normalizedNames(
            "Copper rocks", "Copper rock", "Copper ore rocks", "Copper ore vein", "Copper vein"
    );
    private static final Set<String> TIN_ROCK_NAMES = normalizedNames(
            "Tin rocks", "Tin rock", "Tin ore rocks", "Tin ore vein", "Tin vein"
    );
    private static final Set<String> IRON_ROCK_NAMES = normalizedNames(
            "Iron rocks", "Iron rock", "Iron ore rocks", "Iron ore vein", "Iron vein"
    );
    State state = State.MINING;
    private static final List<Rocks> PROGRESSIVE_ROCKS = buildProgressiveRocks();
    private volatile Rocks activeRock;
    private volatile LocationOption activeLocation;

    /** Synchronizes the client event thread with the mining executor. */
    private final Object targetLock = new Object();
    private volatile boolean retargetRequested;
    private volatile boolean retargetInProgress;

    /**
     * Client-thread snapshots. These values are written by RuneLite event
     * subscribers and read by the mining worker without blocking the client
     * thread through Rs2Player utility calls.
     */
    private volatile boolean playerAnimatingSnapshot;
    private volatile WorldPoint playerLocationSnapshot;

    /** Prevents any accidental overlap between mining-loop iterations. */
    private final AtomicBoolean miningLoopRunning = new AtomicBoolean(false);

    /** The exact scene object currently being mined. */
    private volatile WorldPoint currentRockLocation;
    private volatile int currentRockId = -1;

    /** Inventory state when the current rock was clicked. */
    private volatile long inventoryFingerprintAtInteraction = Long.MIN_VALUE;
    private volatile int copperCountAtInteraction;
    private volatile int tinCountAtInteraction;
    private volatile int ironCountAtInteraction;

    /**
     * Temporarily excluded after depletion/inventory gain so the script does
     * not click the same rock again before its scene object has transformed.
     */
    private volatile WorldPoint excludedRockLocation;

    /** Rock selected for the current interaction. */
    private volatile Rocks currentRockType;

    /** Determines the next rock when Copper and Tin counts are equal. */
    private volatile Rocks copperTinTiePreference = Rocks.COPPER;

    /** Overlay/session statistics. */
    private volatile long startedAt;
    private volatile int startingMiningLevel = -1;
    private volatile int currentMiningLevel;
    private volatile String currentOreDisplay = "Unknown";

    /**
     * Active rock objects observed in the scene. Spawn/despawn events keep this
     * cache current, allowing post-depletion selection without another full
     * scene scan.
     */
    private final Map<WorldPoint, GameObject> activeRockObjectCache = new ConcurrentHashMap<>();

    /** IDs discovered through the name fallback are promoted into the fast path. */
    private final Map<Rocks, Set<Integer>> discoveredRockIds = new ConcurrentHashMap<>();


    public boolean run(AutoMiningConfig config) {
        state = State.MINING;
        initialPlayerLocation = null;
        clearCurrentRock();
        excludedRockLocation = null;
        currentRockType = null;
        copperTinTiePreference = Rocks.COPPER;
        retargetRequested = false;
        retargetInProgress = false;
        playerAnimatingSnapshot = false;
        playerLocationSnapshot = null;
        miningLoopRunning.set(false);
        startedAt = System.currentTimeMillis();
        startingMiningLevel = -1;
        currentMiningLevel = 0;
        currentOreDisplay = getConfiguredOreDisplay(config);
        activeRockObjectCache.clear();
        discoveredRockIds.clear();
        Rs2Antiban.resetAntibanSettings();
        Rs2AntibanSettings.actionCooldownChance = 0.0;
        Rs2AntibanSettings.actionCooldownActive = false;
        mainScheduledFuture = scheduledExecutorService.scheduleAtFixedRate(() -> {
            if (!miningLoopRunning.compareAndSet(false, true)) {
                return;
            }

            try {
                if (!super.run()) return;
                if (!Microbot.isLoggedIn()) return;
                if (config.leagueMode() && Rs2Player.checkIdleLogout(Rs2Random.between(500, 1500))) {
                    int[] arrowKeys = { KeyEvent.VK_LEFT, KeyEvent.VK_RIGHT, KeyEvent.VK_UP, KeyEvent.VK_DOWN };
                    Rs2Keyboard.keyPress(arrowKeys[Rs2Random.between(0, arrowKeys.length - 1)]);
                }
                if (initialPlayerLocation == null) {
                    initialPlayerLocation = getPlayerLocationSnapshot();
                }

                // Skip cycle until client-thread snapshots are available.
                if (initialPlayerLocation == null) {
                    return;
                }
                if (currentMiningLevel <= 0) {
                    Microbot.status = "Waiting for Mining level";
                    return;
                }

                updateActiveRock(config);

                if (activeRock == null || !hasRequiredMiningLevel(activeRock)) {
                    Microbot.log("You do not have the required mining level to mine this ore.");
                    return;
                }

                // Consume depletion/inventory events before any travel check.
                // Otherwise the location guard can interrupt the replacement
                // click and send the player back to the anchor tile.
                if (state == State.MINING && processRequestedRetarget(config)) {
                    return;
                }

                // The base Script executor has multiple worker threads. While
                // the event task is replacing one rock with the next, a second
                // loop must not start location enforcement in the brief gap
                // between clearing the old target and tracking the new one.
                if (state == State.MINING && retargetInProgress) {
                    return;
                }

                boolean hasTrackedRock = currentRockLocation != null;

                // Tool and location preparation are startup/return safeguards.
                // They must never run while a valid rock interaction is tracked.
                if (state == State.MINING && !hasTrackedRock) {
                    if (!ensurePickaxeBeforeMiningTravel()) {
                        return;
                    }

                    if (shouldUseConfiguredMiningLocation(config)
                            && ensureConfiguredMiningLocation(config)) {
                        return;
                    }
                }

                Rs2AntibanSettings.actionCooldownActive = false;

                //code to change worlds if there are too many players in the distance to stray tiles
                int maxPlayers = config.maxPlayersInArea();
                if (state == State.MINING
                        && currentRockLocation == null
                        && !retargetInProgress
                        && maxPlayers > 0) {
                    WorldPoint localLocation = getPlayerLocationSnapshot();
                    long nearbyPlayers = Microbot.getClientThread().runOnClientThreadOptional(() ->
                                    Microbot.getClient().getTopLevelWorldView().players().stream()
                                            .filter(p -> p != null && p != Microbot.getClient().getLocalPlayer())
                                            .filter(p -> {
                                                if (config.distanceToStray() == 0) {
                                                    // Only count players standing on the same exact tile
                                                    return p.getWorldLocation().equals(localLocation);
                                                }
                                                // Count players within distanceToStray
                                                return p.getWorldLocation().distanceTo(localLocation) <= config.distanceToStray();
                                            })
                                            // filter if players are using mining animation
                                            .filter(p -> p.getAnimation() != -1)
                                            .count())
                            .orElse(0L);

                    if (nearbyPlayers >= maxPlayers) {
                        Microbot.status = "Too many players nearby. Hopping...";

                        int world = Login.getRandomWorld(Rs2Player.isMember());
                        boolean hopped = Microbot.hopToWorld(world);
                        if (hopped) {
                            Microbot.status = "Hopped to world: " + world;
                            return; // Exit current cycle after hop
                        }
                    }
                }


                switch (state) {
                    case MINING:
                        if (shouldResetInventory(config)) {
                            state = State.RESETTING;
                            return;
                        }

                        if (activeRock == null) {
                            return;
                        }

                        // A tracked target is held until an object/inventory event
                        // requests a retarget. Never run anchor enforcement while a
                        // rock is active; interacting with an edge rock can move the
                        // player several tiles away from the central anchor.
                        if (currentRockLocation != null) {
                            return;
                        }

                        WorldPoint miningAnchor = getMiningAnchor(config);
                        WorldPoint currentPlayerLocation = getPlayerLocationSnapshot();
                        if (miningAnchor == null || currentPlayerLocation == null) {
                            return;
                        }

                        int miningAreaRadius = getMiningAreaRadius(config);
                        int distanceFromMiningAnchor = currentPlayerLocation.distanceTo(miningAnchor);
                        if (distanceFromMiningAnchor > miningAreaRadius) {
                            Microbot.status = "Walking back to mining location...";
                            Rs2Walker.walkTo(miningAnchor, Math.max(1, miningAreaRadius / 2));
                            return;
                        }

                        GameObject rock = findNextRock(config);

                        if (rock != null) {
                            if (Rs2GameObject.interact(rock, "Mine")) {
                                trackCurrentRock(rock);
                            }
                        }
                        break;
                    case RESETTING:
                        if (config.useBank()) {
                            if (config.clayBracelet() && config.ORE() == MiningOreOption.CLAY) {
                                if (!Rs2Bank.walkToBankAndUseBank()) {
                                    return;
                                }

                                // Use the same deterministic Deposit inventory + pickaxe
                                // preparation flow for clay banking as for normal ores.
                                if (!MiningBankingHelper.depositInventoryExceptPickaxeUntilClear(8_000L)) {
                                    return;
                                }

                                if (Rs2Bank.hasItem(11074)) {
                                    Rs2Bank.withdrawAndEquip(11074);
                                }
                                else {
                                    log.info("You don't have any more bracelet of clays");
                                }

                                if (!Rs2Bank.closeBank()) {
                                    return;
                                }

                                walkToMiningAnchor(config);
                            }
                            else if (activeRock == Rocks.GEM
                                    && getPlayerLocationSnapshot() != null
                                    && getPlayerLocationSnapshot().getRegionID() == GEM_MINE_UNDERGROUND) {
                                if (Rs2DepositBox.openDepositBox()) {
                                    if (Rs2Inventory.contains("Open gem bag")) {
                                        Rs2Inventory.interact("Open gem bag", "Empty");
                                        Rs2DepositBox.depositAllExcept("Open gem bag");
                                    } else {
                                        Rs2DepositBox.depositAll();
                                    }
                                    Rs2DepositBox.closeDepositBox();
                                }
                            } else if (Rocks.BASALT == activeRock) {
                                if (Rs2Walker.walkTo(2872, 3935, 0)) {
                                    Rs2Inventory.useItemOnNpc(ItemID.BASALT, NpcID.MY2ARM_SNOWFLAKE);
                                    Rs2Walker.walkTo(2841, 10339, 0);
                                }
                            } else {
                                if (!Rs2Bank.isOpen()) {
                                    if (!Rs2Bank.walkToBankAndUseBank()) {
                                        return;
                                    }
                                    return;
                                }

                                // Always use the bank widget's Deposit inventory button.
                                // The helper equips/preserves the current pickaxe when possible,
                                // then checks the bank for a higher usable tier, equips it, and
                                // deposits the displaced outdated pickaxe before leaving.
                                if (!MiningBankingHelper.depositInventoryExceptPickaxeUntilClear(8_000L)) {
                                    return;
                                }

                                if (!Rs2Bank.closeBank())
                                    return;

                                walkToMiningAnchor(config);
                            }

                        } else {
                            MiningInventoryCleanupHelper.dropAllExceptPickaxes(config.interactOrder());
                        }

                        state = State.MINING;
                        clearCurrentRock();
                        break;
                }
            } catch (Exception ex) {
                Microbot.log(ex.getMessage());
            } finally {
                miningLoopRunning.set(false);
            }
        }, 0, 50, TimeUnit.MILLISECONDS);
        return true;
    }

    @Override
    public void shutdown() {
        super.shutdown();
        clearCurrentRock();
        retargetRequested = false;
        retargetInProgress = false;
        playerAnimatingSnapshot = false;
        playerLocationSnapshot = null;
        miningLoopRunning.set(false);
        activeRockObjectCache.clear();
        discoveredRockIds.clear();
        Rs2Antiban.resetAntibanSettings();
    }

    /**
     * RuneLite client event: the tracked rock object was removed.
     */
    public void onGameObjectDespawned(GameObject gameObject) {
        if (gameObject == null || gameObject.getWorldLocation() == null) {
            return;
        }

        activeRockObjectCache.computeIfPresent(gameObject.getWorldLocation(), (location, cached) ->
                cached.getId() == gameObject.getId() ? null : cached);

        boolean matchesTrackedRock;
        synchronized (targetLock) {
            matchesTrackedRock = currentRockLocation != null
                    && currentRockId >= 0
                    && currentRockId == gameObject.getId()
                    && currentRockLocation.equals(gameObject.getWorldLocation());
        }

        if (matchesTrackedRock) {
            requestImmediateRetarget();
        }
    }

    /**
     * RuneLite client event: a replacement object appeared on the tracked tile.
     * This catches rock transformations even on clients that expose them as a
     * spawn rather than a clean despawn notification.
     */
    public void onGameObjectSpawned(GameObject gameObject) {
        if (gameObject == null || gameObject.getWorldLocation() == null) {
            return;
        }

        Rocks spawnedRockType = getRockTypeForKnownId(gameObject.getId());
        if (spawnedRockType != null && hasMineActionForObjectIdOnClientThread(gameObject.getId())) {
            activeRockObjectCache.put(gameObject.getWorldLocation(), gameObject);
        } else {
            // A depleted replacement object on the same tile must evict the
            // previously cached active rock immediately.
            activeRockObjectCache.remove(gameObject.getWorldLocation());
        }

        boolean changedTrackedTile;
        synchronized (targetLock) {
            changedTrackedTile = currentRockLocation != null
                    && currentRockId >= 0
                    && currentRockLocation.equals(gameObject.getWorldLocation())
                    && currentRockId != gameObject.getId();
        }

        if (changedTrackedTile) {
            requestImmediateRetarget();
        }
    }


    private boolean hasMineActionForObjectIdOnClientThread(int objectId) {
        ObjectComposition composition = Microbot.getClient().getObjectDefinition(objectId);
        if (composition != null && composition.getImpostorIds() != null) {
            composition = composition.getImpostor();
        }
        return hasMineAction(composition);
    }

    /**
     * RuneLite client event: the player inventory changed. Copper/Tin targets
     * require the expected ore to increase, preventing a delayed ore update from
     * the previous rock from cancelling the newly clicked opposite rock.
     */
    public void onInventoryChanged(ItemContainer inventory) {
        if (inventory == null) {
            return;
        }

        Item[] items = inventory.getItems();
        long fingerprint = getInventoryFingerprint(items);
        int copperCount = countContainerItemId(items, COPPER_ORE_ITEM_ID);
        int tinCount = countContainerItemId(items, TIN_ORE_ITEM_ID);
        int ironCount = countContainerItemId(items, IRON_ORE_ITEM_ID);
        boolean shouldRetarget = false;

        synchronized (targetLock) {
            if (currentRockLocation == null
                    || currentRockId < 0
                    || inventoryFingerprintAtInteraction == Long.MIN_VALUE
                    || fingerprint == inventoryFingerprintAtInteraction) {
                return;
            }

            boolean copperIncreased = copperCount > copperCountAtInteraction;
            boolean tinIncreased = tinCount > tinCountAtInteraction;
            boolean ironIncreased = ironCount > ironCountAtInteraction;

            if (currentRockType == Rocks.COPPER || currentRockType == Rocks.TIN) {
                boolean expectedOreIncreased = currentRockType == Rocks.COPPER
                        ? copperIncreased
                        : tinIncreased;
                boolean oppositeOreIncreased = currentRockType == Rocks.COPPER
                        ? tinIncreased
                        : copperIncreased;
                boolean nonOreInventoryChange = !copperIncreased && !tinIncreased && !ironIncreased;

                shouldRetarget = expectedOreIncreased || nonOreInventoryChange;

                if (oppositeOreIncreased && !shouldRetarget) {
                    // Absorb a delayed update from the preceding rock without
                    // completing the newly clicked opposite target.
                    inventoryFingerprintAtInteraction = fingerprint;
                    copperCountAtInteraction = copperCount;
                    tinCountAtInteraction = tinCount;
                    ironCountAtInteraction = ironCount;
                }
            } else {
                shouldRetarget = true;
            }
        }

        if (shouldRetarget) {
            requestImmediateRetarget();
        }
    }

    /**
     * Marks the current target for replacement. RuneLite event handlers never
     * submit a second mining worker; the single 50 ms loop consumes this flag.
     */
    private void requestImmediateRetarget() {
        synchronized (targetLock) {
            if (state != State.MINING || currentRockLocation == null || currentRockId < 0) {
                return;
            }

            // RuneLite event handlers only mark work as pending. They never
            // submit another AutoMiningScript worker, so the client thread
            // cannot be flooded by concurrent animation/skill queries.
            retargetRequested = true;
        }
    }

    /**
     * Consumes a rock-change or inventory-change event and clicks the next rock
     * as soon as the player's current mining animation has finished. There is
     * no fixed timeout, XP-drop wait, cooldown or reachability delay.
     */
    private boolean processRequestedRetarget(AutoMiningConfig config) {
        Rocks completedRock;
        WorldPoint completedLocation;

        synchronized (targetLock) {
            if (!retargetRequested || currentRockLocation == null || currentRockId < 0) {
                return false;
            }

            // Rock depletion and inventory changes can arrive before the final
            // mining animation tick has ended. Keep the event pending and let
            // the 50 ms loop consume it immediately once the player is idle.
            if (playerAnimatingSnapshot) {
                Microbot.status = "Finishing mining animation";
                return false;
            }

            retargetRequested = false;
            retargetInProgress = true;
            completedRock = currentRockType;
            completedLocation = currentRockLocation;

            if (completedRock == Rocks.COPPER) {
                copperTinTiePreference = Rocks.TIN;
            } else if (completedRock == Rocks.TIN) {
                copperTinTiePreference = Rocks.COPPER;
            }

            excludedRockLocation = completedLocation;
            clearCurrentRockLocked();
        }

        try {
            updateActiveRock(config);

            if (shouldResetInventory(config)) {
                state = State.RESETTING;
                return true;
            }

            GameObject nextRock = findNextRock(config);
            if (nextRock != null && Rs2GameObject.interact(nextRock, "Mine")) {
                trackCurrentRock(nextRock);
            }

            return true;
        } finally {
            retargetInProgress = false;
        }
    }

    /**
     * Finds the nearest active rock around the mining anchor. Copper, tin and
     * iron use object IDs because some RuneLite object compositions are exposed
     * with ore-specific names. Generic "Rocks" objects are rejected. No
     * pathfinding/reachability probe is performed
     * during target selection; that probe was the remaining post-depletion pause.
     */
    private GameObject findNextRock(AutoMiningConfig config) {
        final WorldPoint playerLocation = getPlayerLocationSnapshot();
        final WorldPoint miningAnchor = getMiningAnchor(config);
        if (playerLocation == null || miningAnchor == null || activeRock == null) {
            return null;
        }

        Microbot.status = "Finding rock";
        final int searchRadius = getRockSearchRadius(config);

        // Fast event-cache path.
        GameObject cachedRock = findNearestCachedRock(playerLocation, miningAnchor, searchRadius, true);
        if (cachedRock != null) {
            return cachedRock;
        }

        // Always repair an empty cache from the currently loaded scene. Spawn
        // events are not replayed when a plugin starts after the rocks loaded.
        refreshActiveRockCacheById(playerLocation, miningAnchor, searchRadius);
        cachedRock = findNearestCachedRock(playerLocation, miningAnchor, searchRadius, true);
        if (cachedRock != null) {
            return cachedRock;
        }

        if (excludedRockLocation != null) {
            excludedRockLocation = null;
            cachedRock = findNearestCachedRock(playerLocation, miningAnchor, searchRadius, false);
            if (cachedRock != null) {
                return cachedRock;
            }
        }

        // The batched fallback accepts only ore-specific names. Generic
        // "Rocks" objects are intentionally rejected because their ore type
        // and Mining requirement cannot be established safely before clicking.
        GameObject fallbackRock = findRockByBatchedCompositionFallback(
                playerLocation, miningAnchor, searchRadius);
        if (fallbackRock != null) {
            activeRockObjectCache.put(fallbackRock.getWorldLocation(), fallbackRock);
            return fallbackRock;
        }

        Microbot.status = "Finding " + formatRockName(activeRock) + " rock";
        return null;
    }

    private GameObject findNearestCachedRock(
            WorldPoint playerLocation,
            WorldPoint miningAnchor,
            int searchRadius,
            boolean applyExcludedLocation) {
        Set<Integer> activeIds = getActiveRockIds(activeRock);
        return activeRockObjectCache.values().stream()
                .filter(object -> object != null && object.getWorldLocation() != null)
                .filter(object -> activeIds.contains(object.getId()))
                .filter(object -> object.getWorldLocation().getPlane() == miningAnchor.getPlane())
                .filter(object -> object.getWorldLocation().distanceTo(miningAnchor) <= searchRadius)
                .filter(object -> !applyExcludedLocation
                        || excludedRockLocation == null
                        || !excludedRockLocation.equals(object.getWorldLocation()))
                .min(Comparator.comparingInt(object ->
                        playerLocation.distanceTo(object.getWorldLocation())))
                .orElse(null);
    }

    private void refreshActiveRockCacheById(
            WorldPoint playerLocation,
            WorldPoint miningAnchor,
            int searchRadius) {
        Set<Integer> activeIds = getActiveRockIds(activeRock);
        if (activeIds.isEmpty()) {
            return;
        }

        // Scan around the player, then constrain results to the managed mine.
        // This avoids a stale/unloaded anchor conversion preventing discovery.
        List<GameObject> idMatches = Rs2GameObject.getGameObjects(
                object -> object != null
                        && object.getWorldLocation() != null
                        && activeIds.contains(object.getId())
                        && object.getWorldLocation().getPlane() == miningAnchor.getPlane()
                        && object.getWorldLocation().distanceTo(miningAnchor) <= searchRadius,
                playerLocation,
                searchRadius);

        List<GameObject> mineableMatches = filterMineableObjectsOnClientThread(idMatches);
        for (GameObject object : mineableMatches) {
            activeRockObjectCache.put(object.getWorldLocation(), object);
        }
    }

    private List<GameObject> filterMineableObjectsOnClientThread(List<GameObject> objects) {
        if (objects == null || objects.isEmpty()) {
            return java.util.Collections.emptyList();
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            List<GameObject> mineable = new ArrayList<>();
            for (GameObject object : objects) {
                if (object == null || object.getWorldLocation() == null) {
                    continue;
                }

                ObjectComposition composition = Microbot.getClient().getObjectDefinition(object.getId());
                if (composition != null && composition.getImpostorIds() != null) {
                    composition = composition.getImpostor();
                }
                if (hasMineAction(composition)) {
                    mineable.add(object);
                }
            }
            return mineable;
        }).orElse(java.util.Collections.emptyList());
    }

    private GameObject findRockByBatchedCompositionFallback(
            WorldPoint playerLocation,
            WorldPoint miningAnchor,
            int searchRadius) {
        List<GameObject> sceneObjects = Rs2GameObject.getGameObjects(
                object -> object != null
                        && object.getWorldLocation() != null
                        && object.getWorldLocation().getPlane() == miningAnchor.getPlane()
                        && object.getWorldLocation().distanceTo(miningAnchor) <= searchRadius
                        && (excludedRockLocation == null
                            || !excludedRockLocation.equals(object.getWorldLocation())),
                playerLocation,
                searchRadius);

        if (sceneObjects.isEmpty()) {
            return null;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            GameObject nearestSpecific = null;
            int nearestSpecificDistance = Integer.MAX_VALUE;

            for (GameObject object : sceneObjects) {
                ObjectComposition composition = Microbot.getClient().getObjectDefinition(object.getId());
                if (composition != null && composition.getImpostorIds() != null) {
                    composition = composition.getImpostor();
                }
                if (!hasMineAction(composition)) {
                    continue;
                }

                Rocks classifiedType = getRockTypeForKnownId(object.getId());
                String objectName = composition == null ? null : composition.getName();

                // Safety rule: never click an unclassified generic "Rocks"
                // object. A generic name does not identify the ore type or its
                // Mining requirement. Only known IDs or ore-specific names are
                // eligible for interaction.
                if (classifiedType != activeRock && !matchesActiveRockName(activeRock, objectName)) {
                    continue;
                }

                if (classifiedType == null) {
                    learnRockType(object.getId(), activeRock);
                }

                int distance = playerLocation.distanceTo(object.getWorldLocation());
                if (distance < nearestSpecificDistance) {
                    nearestSpecific = object;
                    nearestSpecificDistance = distance;
                }
            }

            return nearestSpecific;
        }).orElse(null);
    }

    private static boolean hasMineAction(ObjectComposition composition) {
        if (composition == null || composition.getActions() == null) {
            return false;
        }
        for (String action : composition.getActions()) {
            if (action != null && "Mine".equalsIgnoreCase(action)) {
                return true;
            }
        }
        return false;
    }

    private void learnRockType(int objectId, Rocks rockType) {
        if (objectId < 0 || rockType == null) {
            return;
        }

        for (Set<Integer> ids : discoveredRockIds.values()) {
            ids.remove(objectId);
        }
        discoveredRockIds
                .computeIfAbsent(rockType, ignored -> ConcurrentHashMap.newKeySet())
                .add(objectId);
    }

    private Rocks getRockTypeForKnownId(int objectId) {
        if (COPPER_ROCK_IDS.contains(objectId)
                || discoveredRockIds.getOrDefault(Rocks.COPPER, java.util.Collections.emptySet()).contains(objectId)) {
            return Rocks.COPPER;
        }
        if (TIN_ROCK_IDS.contains(objectId)
                || discoveredRockIds.getOrDefault(Rocks.TIN, java.util.Collections.emptySet()).contains(objectId)) {
            return Rocks.TIN;
        }
        if (IRON_ROCK_IDS.contains(objectId)
                || discoveredRockIds.getOrDefault(Rocks.IRON, java.util.Collections.emptySet()).contains(objectId)) {
            return Rocks.IRON;
        }

        for (Map.Entry<Rocks, Set<Integer>> entry : discoveredRockIds.entrySet()) {
            if (entry.getValue().contains(objectId)) {
                return entry.getKey();
            }
        }
        return null;
    }

    private Set<Integer> getActiveRockIds(Rocks rock) {
        Set<Integer> ids = new HashSet<>();
        if (rock == Rocks.COPPER) {
            ids.addAll(COPPER_ROCK_IDS);
        } else if (rock == Rocks.TIN) {
            ids.addAll(TIN_ROCK_IDS);
        } else if (rock == Rocks.IRON) {
            ids.addAll(IRON_ROCK_IDS);
        }

        ids.addAll(discoveredRockIds.getOrDefault(rock, java.util.Collections.emptySet()));
        return ids;
    }

    private boolean matchesActiveRockName(Rocks rock, String objectName) {
        if (objectName == null || rock == null) {
            return false;
        }

        String normalizedName = normalizeRockName(objectName);
        if (rock == Rocks.COPPER) {
            return COPPER_ROCK_NAMES.contains(normalizedName);
        }
        if (rock == Rocks.TIN) {
            return TIN_ROCK_NAMES.contains(normalizedName);
        }
        if (rock == Rocks.IRON) {
            return IRON_ROCK_NAMES.contains(normalizedName);
        }

        return normalizeRockName(rock.getName()).equals(normalizedName);
    }

    private static Set<String> normalizedNames(String... names) {
        Set<String> normalized = new HashSet<>();
        for (String name : names) {
            normalized.add(normalizeRockName(name));
        }
        return normalized;
    }

    private static String normalizeRockName(String name) {
        return name == null
                ? ""
                : name.trim().toLowerCase(java.util.Locale.ROOT).replaceAll("\\s+", " " );
    }

    private int getRockSearchRadius(AutoMiningConfig config) {
        if (usesVarrockSouthEastMine(config)) {
            return Math.max(30, config.distanceToStray());
        }
        return config.distanceToStray();
    }

    private void trackCurrentRock(GameObject rock) {
        if (rock == null || rock.getWorldLocation() == null) {
            return;
        }

        long inventoryFingerprint = getInventoryFingerprint();
        int copperCount = countInventoryItemId(COPPER_ORE_ITEM_ID);
        int tinCount = countInventoryItemId(TIN_ORE_ITEM_ID);
        int ironCount = countInventoryItemId(IRON_ORE_ITEM_ID);

        synchronized (targetLock) {
            currentRockLocation = rock.getWorldLocation();
            currentRockId = rock.getId();
            inventoryFingerprintAtInteraction = inventoryFingerprint;
            copperCountAtInteraction = copperCount;
            tinCountAtInteraction = tinCount;
            ironCountAtInteraction = ironCount;
            currentRockType = activeRock;
            retargetRequested = false;

            if (excludedRockLocation != null
                    && !excludedRockLocation.equals(currentRockLocation)) {
                excludedRockLocation = null;
            }
        }
        Microbot.status = "Mining";
    }

    private void clearCurrentRock() {
        synchronized (targetLock) {
            clearCurrentRockLocked();
        }
    }

    private void clearCurrentRockLocked() {
        currentRockLocation = null;
        currentRockId = -1;
        inventoryFingerprintAtInteraction = Long.MIN_VALUE;
        copperCountAtInteraction = 0;
        tinCountAtInteraction = 0;
        ironCountAtInteraction = 0;
        currentRockType = null;
        retargetRequested = false;
    }

    /**
     * Includes slot, item ID and quantity so stackable mining resources also
     * trigger an immediate switch when their quantity increases.
     */
    private long getInventoryFingerprint() {
        return Rs2Inventory.items()
                .mapToLong(item -> ((long) item.getSlot() << 48)
                        ^ ((long) item.getId() << 16)
                        ^ (item.getQuantity() & 0xFFFFL))
                .sorted()
                .reduce(1125899906842597L, (hash, value) -> 31L * hash + value);
    }

    private long getInventoryFingerprint(Item[] items) {
        if (items == null) {
            return 1125899906842597L;
        }

        long hash = 1125899906842597L;
        for (int slot = 0; slot < items.length; slot++) {
            Item item = items[slot];
            if (item == null || item.getId() < 0) {
                continue;
            }

            long value = ((long) slot << 48)
                    ^ ((long) item.getId() << 16)
                    ^ (item.getQuantity() & 0xFFFFL);
            hash = 31L * hash + value;
        }
        return hash;
    }

    private int countContainerItemId(Item[] items, int itemId) {
        if (items == null) {
            return 0;
        }

        int count = 0;
        for (Item item : items) {
            if (item != null && item.getId() == itemId) {
                count += Math.max(1, item.getQuantity());
            }
        }
        return count;
    }

    private static List<Rocks> buildProgressiveRocks() {
        List<Rocks> rocks = new ArrayList<>(Arrays.asList(
                Rocks.IRON,
                Rocks.COAL,
                Rocks.GOLD,
                Rocks.MITHRIL,
                Rocks.ADAMANTITE,
                Rocks.RUNITE
        ));
        return rocks;
    }

    private void updateActiveRock(AutoMiningConfig config) {
        Rocks previousRock = activeRock;

        if (!config.progressiveMode()) {
            MiningOreOption selectedOre = config.ORE();
            activeRock = selectedOre.isCopperAndTin()
                    ? selectBalancedCopperOrTinRock()
                    : selectedOre.getRock();

            if (usesVarrockSouthEastMine(config)) {
                activeLocation = VARROCK_SOUTH_EAST_MINE;
            } else if (activeLocation == null || previousRock != activeRock) {
                // Location accessibility checks may query client state. Only
                // perform them when the selected ore actually changes.
                activeLocation = MiningRockLocations.getBestAccessibleLocation(activeRock);
            }

            updateStatus(config);
            return;
        }

        Rocks unlockedRock = PROGRESSIVE_ROCKS.stream()
                .filter(this::hasRequiredMiningLevel)
                .max(Comparator.comparingInt(Rocks::getMiningLevel))
                .orElse(null);

        // Progressive levels 1-14 mine Copper & Tin as one balanced stage.
        // Once Iron is unlocked, progression continues with the highest
        // available single ore from PROGRESSIVE_ROCKS.
        if (unlockedRock == null) {
            activeRock = selectBalancedCopperOrTinRock();
            activeLocation = VARROCK_SOUTH_EAST_MINE;
        } else {
            activeRock = unlockedRock;
            if (activeRock == Rocks.IRON) {
                activeLocation = VARROCK_SOUTH_EAST_MINE;
            } else if (activeLocation == null || previousRock != activeRock) {
                activeLocation = MiningRockLocations.getBestAccessibleLocation(activeRock);
            }
        }

        updateStatus(config);
    }

    /**
     * Guarantees that mining travel never starts without a pickaxe.
     *
     * Existing equipment/inventory is checked first so the plugin does not
     * unnecessarily visit a bank. When neither contains a pickaxe, the script
     * opens the nearest bank and withdraws the best pickaxe the account can
     * use for mining. A pickaxe that cannot be equipped because of Attack level
     * is retained in inventory and is still considered ready.
     */
    private boolean ensurePickaxeBeforeMiningTravel() {
        if (PickaxeUpgradeHelper.hasPickaxeEquippedOrInInventory()) {
            if (Rs2Bank.isOpen()) {
                Microbot.status = "Pickaxe ready - closing bank";
                return Rs2Bank.closeBank();
            }
            return true;
        }

        clearCurrentRock();
        Microbot.status = "Getting pickaxe";

        if (!Rs2Bank.isOpen()) {
            Rs2Bank.walkToBankAndUseBank();
            return false;
        }

        if (!PickaxeUpgradeHelper.withdrawBestPickaxeForMining()) {
            Microbot.status = "No usable pickaxe found";
            return false;
        }

        if (!PickaxeUpgradeHelper.hasPickaxeEquippedOrInInventory()) {
            return false;
        }

        Microbot.status = "Pickaxe ready - closing bank";
        return Rs2Bank.closeBank();
    }

    private boolean shouldUseConfiguredMiningLocation(AutoMiningConfig config) {
        return config.progressiveMode() || usesVarrockSouthEastMine(config);
    }

    private boolean usesVarrockSouthEastMine(AutoMiningConfig config) {
        if (config.progressiveMode()) {
            return activeRock == Rocks.COPPER
                    || activeRock == Rocks.TIN
                    || activeRock == Rocks.IRON;
        }

        MiningOreOption selectedOre = config.ORE();
        return selectedOre == MiningOreOption.COPPER_AND_TIN
                || selectedOre == MiningOreOption.IRON;
    }

    private boolean ensureConfiguredMiningLocation(AutoMiningConfig config) {
        // Location enforcement is only allowed when no rock interaction is
        // active. This prevents the central anchor from overriding a valid
        // Copper/Tin/Iron rock click near the edge of the mining area.
        if (currentRockLocation != null) {
            return false;
        }

        WorldPoint targetPoint = getMiningAnchor(config);
        if (targetPoint == null) {
            return false;
        }

        WorldPoint playerLocation = getPlayerLocationSnapshot();
        if (playerLocation == null) {
            return true;
        }

        int acceptableDistance = getMiningAreaRadius(config);
        int distanceToTarget = playerLocation.distanceTo(targetPoint);
        if (distanceToTarget > acceptableDistance) {
            Microbot.status = "Walking to mining location";
            if (Rs2Player.isMoving()) {
                return true;
            }

            Rs2Walker.walkTo(targetPoint, Math.max(1, acceptableDistance / 2));
            return true;
        }

        return false;
    }

    /**
     * Radius of the whole mine, not the radius of the single anchor tile.
     * The fixed Varrock mine contains valid rocks several tiles from
     * (3284, 3363, 0), so using a five-tile leash interrupted mining.
     */
    private int getMiningAreaRadius(AutoMiningConfig config) {
        return Math.max(3, getRockSearchRadius(config));
    }

    /**
     * Returns the only location that mining navigation, rock searches and
     * post-bank returns are allowed to use.
     */
    private WorldPoint getMiningAnchor(AutoMiningConfig config) {
        // Location-managed modes must never fall back to the tile where the
        // plugin was started. That fallback caused the script to arrive at the
        // configured mine and immediately create a second walk back to start.
        if (usesVarrockSouthEastMine(config)) {
            return VARROCK_SOUTH_EAST_MINE_POSITION;
        }

        if (shouldUseConfiguredMiningLocation(config)) {
            return activeLocation == null ? null : activeLocation.getWorldPoint();
        }

        // Non-managed modes intentionally mine around the tile where the
        // plugin was started, preserving the original behaviour for ores that
        // do not have an enforced destination.
        return initialPlayerLocation;
    }

    private void walkToMiningAnchor(AutoMiningConfig config) {
        WorldPoint miningAnchor = getMiningAnchor(config);
        if (miningAnchor != null) {
            Rs2Walker.walkTo(miningAnchor, config.distanceToStray());
        }
    }

    /**
     * Resets early when Copper & Tin mode has an equal pair count but fewer
     * than two free slots. This avoids mining an unavoidable unmatched 27th
     * ore when a carried pickaxe occupies one inventory slot.
     */
    private boolean shouldResetInventory(AutoMiningConfig config) {
        if (Rs2Inventory.isFull()) {
            return true;
        }

        if (!isCopperAndTinStage(config)) {
            return false;
        }

        int copperCount = countInventoryItemId(COPPER_ORE_ITEM_ID);
        int tinCount = countInventoryItemId(TIN_ORE_ITEM_ID);
        int occupiedSlots = (int) Rs2Inventory.items().count();
        int freeSlots = Math.max(0, 28 - occupiedSlots);

        return copperCount == tinCount && freeSlots < 2;
    }

    /**
     * True when the active mining stage represents the combined Copper & Tin
     * mode, including the level 1-14 stage used by Progressive mode.
     */
    private boolean isCopperAndTinStage(AutoMiningConfig config) {
        if (config == null) {
            return false;
        }

        if (!config.progressiveMode()) {
            MiningOreOption selectedOre = config.ORE();
            return selectedOre != null && selectedOre.isCopperAndTin();
        }

        return activeRock == Rocks.COPPER || activeRock == Rocks.TIN;
    }

    /**
     * Selects the ore currently present in the lower quantity. When counts are
     * equal, the preference alternates after each completed rock interaction.
     */
    private Rocks selectBalancedCopperOrTinRock() {
        int copperCount = countInventoryItemId(COPPER_ORE_ITEM_ID);
        int tinCount = countInventoryItemId(TIN_ORE_ITEM_ID);

        if (copperCount < tinCount) {
            return Rocks.COPPER;
        }
        if (tinCount < copperCount) {
            return Rocks.TIN;
        }

        return copperTinTiePreference;
    }

    private int countInventoryItemId(int itemId) {
        return Rs2Inventory.items()
                .filter(item -> item != null && item.getId() == itemId)
                .mapToInt(item -> Math.max(1, item.getQuantity()))
                .sum();
    }

    private void updateStatus(AutoMiningConfig config) {
        currentOreDisplay = getConfiguredOreDisplay(config);

        if (state == State.MINING && currentRockLocation != null) {
            Microbot.status = "Mining";
        } else if (state == State.MINING) {
            Microbot.status = "Finding rock";
        } else {
            Microbot.status = config.useBank() ? "Banking" : "Dropping inventory";
        }
    }

    private String getConfiguredOreDisplay(AutoMiningConfig config) {
        if (config == null) {
            return "Unknown";
        }

        MiningOreOption selectedOre = config.ORE();
        if (selectedOre == null) {
            return "Unknown";
        }

        if (!config.progressiveMode()) {
            return selectedOre.toString();
        }

        // Current Ore describes what is actually being mined, not the mode.
        // Progressive levels 1-14 are one combined Copper & Tin stage.
        if (activeRock == null || isCopperAndTinStage(config)) {
            return "Copper & Tin";
        }

        return formatRockName(activeRock);
    }

    private static String formatRockName(Rocks rock) {
        if (rock == null) {
            return "Unknown";
        }

        String value = rock.getName();
        if (value == null || value.trim().isEmpty()) {
            return "Unknown";
        }

        String normalized = value.trim();
        if (normalized.toLowerCase(java.util.Locale.ROOT).endsWith(" rocks")) {
            normalized = normalized.substring(0, normalized.length() - " rocks".length());
        }
        return Character.toUpperCase(normalized.charAt(0)) + normalized.substring(1);
    }

    /**
     * Called from RuneLite's client thread on every ClientTick. Reading the
     * local player directly in the subscriber is non-blocking; the mining
     * worker only consumes these volatile snapshots.
     */
    public void onClientTick(WorldPoint playerLocation, boolean playerAnimating) {
        playerLocationSnapshot = playerLocation;
        playerAnimatingSnapshot = playerAnimating;
    }

    /**
     * Called from RuneLite's client thread on GameTick. Progressive selection
     * and the overlay use this cached value instead of repeatedly calling
     * Rs2Player.getRealSkillLevel from a worker thread.
     */
    public void onMiningLevelSnapshot(int level) {
        if (level <= 0) {
            return;
        }

        currentMiningLevel = level;
        if (startingMiningLevel < 0) {
            startingMiningLevel = level;
        }
    }

    private boolean hasRequiredMiningLevel(Rocks rock) {
        return rock != null
                && currentMiningLevel > 0
                && currentMiningLevel >= rock.getMiningLevel();
    }

    private WorldPoint getPlayerLocationSnapshot() {
        return playerLocationSnapshot;
    }

    public String getFormattedRuntime() {
        if (startedAt <= 0L) {
            return "00:00:00";
        }

        long elapsedSeconds = Math.max(0L, System.currentTimeMillis() - startedAt) / 1000L;
        long hours = elapsedSeconds / 3600L;
        long minutes = (elapsedSeconds % 3600L) / 60L;
        long seconds = elapsedSeconds % 60L;
        return String.format(java.util.Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }

    public int getCurrentMiningLevel() {
        return currentMiningLevel;
    }

    public int getMiningLevelsGained() {
        if (startingMiningLevel < 0 || currentMiningLevel <= 0) {
            return 0;
        }
        return Math.max(0, currentMiningLevel - startingMiningLevel);
    }

    public String getCurrentOreDisplay() {
        return currentOreDisplay;
    }
}
