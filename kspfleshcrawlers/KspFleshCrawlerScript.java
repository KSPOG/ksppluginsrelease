package net.runelite.client.plugins.microbot.kspfleshcrawlers;

import net.runelite.api.Actor;
import net.runelite.api.NPC;
import net.runelite.api.Skill;
import net.runelite.api.TileObject;
import net.runelite.api.TileItem;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.plugins.grounditems.GroundItem;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.grounditem.Rs2GroundItem;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.misc.Rs2Food;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;
import net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@SuppressWarnings({"deprecation", "removal"})
public class KspFleshCrawlerScript extends Script {
    private static final String NPC_NAME = "Flesh Crawler";

    /*
     * The v1.0.1 target was the deep south-east room. That forced the generic
     * web-walker through most of Catacomb of Famine. The near-entry crawler
     * room is a much better operational target: after the floor-1 shortcut
     * portal + ladder, only the entry-room rickety door pair must be crossed.
     */
    private static final WorldPoint DEFAULT_FIGHT_POINT = new WorldPoint(2032, 5242, 0);
    private static final WorldPoint STRONGHOLD_SURFACE_ENTRANCE = new WorldPoint(3081, 3420, 0);
    private static final WorldPoint WAR_PORTAL_POINT = new WorldPoint(1863, 5238, 0);
    private static final WorldPoint WAR_LADDER_DOWN_POINT = new WorldPoint(1902, 5222, 0);

    private static final int FLOOR_1_MIN_X = 1855;
    private static final int FLOOR_1_MAX_X = 1920;
    private static final int FLOOR_1_MIN_Y = 5184;
    private static final int FLOOR_1_MAX_Y = 5248;
    private static final int FLOOR_2_MIN_X = 1983;
    private static final int FLOOR_2_MAX_X = 2048;
    private static final int FLOOR_2_MIN_Y = 5184;
    private static final int FLOOR_2_MAX_Y = 5248;

    private static final int FLOOR_1_START_MIN_X = 1855;
    private static final int FLOOR_1_START_MAX_X = 1867;
    private static final int FLOOR_1_START_MIN_Y = 5237;
    private static final int FLOOR_1_START_MAX_Y = 5247;

    private static final long MOVE_CLICK_COOLDOWN_MS = 850L;
    private static final long DOOR_RETRY_COOLDOWN_MS = 12_000L;
    private static final long DOOR_NUDGE_MIN_DELAY_MS = 350L;
    private static final long PORTAL_RETRY_COOLDOWN_MS = 4_000L;

    /*
     * Route used only when the Vault of War portal is unavailable.
     * These are the current Stronghold QuestHelper line points to the
     * Gift of Peace / ladder area. We deliberately do NOT call web-walk
     * on this floor: local waypoint clicks + our own door resolver own it.
     */
    private static final List<WorldPoint> FLOOR_1_ROUTE = Arrays.asList(
            new WorldPoint(1859, 5243, 0),
            new WorldPoint(1859, 5232, 0),
            new WorldPoint(1864, 5227, 0),
            new WorldPoint(1870, 5227, 0),
            new WorldPoint(1875, 5239, 0),
            new WorldPoint(1880, 5240, 0),
            new WorldPoint(1883, 5243, 0),
            new WorldPoint(1912, 5242, 0),
            new WorldPoint(1912, 5237, 0),
            new WorldPoint(1905, 5234, 0),
            new WorldPoint(1905, 5228, 0),
            WAR_LADDER_DOWN_POINT
    );

    /*
     * Floor 2 starts in the north-east room.  The nearest Flesh Crawler
     * group is immediately west of that start room, not south.  The old
     * v1.0.2 route therefore aimed at the wrong wall and could sit forever
     * on the first floor-2 waypoint.  These points follow the first segment
     * of Microbot QuestHelper's Catacomb of Famine route and then stop in
     * the adjacent crawler room.
     */
    private static final List<WorldPoint> FLOOR_2_ENTRY_ROUTE = Arrays.asList(
            new WorldPoint(2042, 5245, 0),
            new WorldPoint(2034, 5244, 0),
            DEFAULT_FIGHT_POINT
    );

    private static final int RICKETY_DOOR_ID_A = 16065;
    private static final int RICKETY_DOOR_ID_B = 16066;
    private static final int FLOOR_2_CRAWLER_SCAN_RADIUS = 24;

    /** Security-question answers used by the current Stronghold QuestHelper. */
    private static final String[] STRONGHOLD_CORRECT_ANSWERS = {
            "No.",
            "Me.",
            "Nobody.",
            "Talk to any banker.",
            "Nothing, it's a fake.",
            "Delete it - it's a fake!",
            "Don't give them my password.",
            "Report the player for phishing.",
            "Use the Account Recovery system.",
            "No way! I'm reporting you to Jagex!",
            "No, you should never buy an account.",
            "Secure my device and reset my password.",
            "Decline the offer and report that player.",
            "The birthday of a famous person or event.",
            "Only on the Old School RuneScape website.",
            "Read the text and follow the advice given.",
            "Virus scan my device then change my password.",
            "Report the incident and do not click any links.",
            "Don't share your information and report the player.",
            "Set up two-factor authentication with my email provider.",
            "No, you should never allow anyone to level your account.",
            "No, you should never allow anyone to use your account.",
            "Authenticator and two-step login on my registered email.",
            "No way! You'll just take my gold for your own! Reported!",
            "Don't type in my password backwards and report the player.",
            "Don't give them the information and send an 'Abuse report'.",
            "Don't tell them anything and click the 'Report Abuse' button.",
            "Politely tell them no and then use the 'Report Abuse' button.",
            "Politely tell them no, then use the 'Report Abuse' button.",
            "Don't give out your password to anyone. Not even close friends.",
            "Do not visit the website and report the player who messaged you.",
            "Report the stream as a scam. Real Jagex streams have a 'verified' mark.",
            "Two-factor authentication on your account and your registered email.",
            "Nope, you're tricking me into going somewhere dangerous.",
            "It's never used on other websites or accounts."
    };

    private final CombatTrainingController trainingController = new CombatTrainingController();
    private final Map<WorldPoint, Long> recentlyClickedDoors = new HashMap<>();

    private volatile FleshCrawlerState state = FleshCrawlerState.INITIALIZING;
    private volatile String lastAction = "Starting";
    private volatile int kills;
    private volatile int itemsLooted;
    private volatile int bonesBuried;
    private volatile int foodEaten;

    private volatile WorldPoint fightAnchor;

    /*
     * The blocking web-walker is retained only for the overworld leg to the
     * Barbarian Village entrance. Stronghold floors use the local navigator.
     */
    private ExecutorService navigationExecutor;
    private volatile Future<?> navigationFuture;
    private volatile WorldPoint navigationTarget;

    private volatile int trackedNpcIndex = -1;
    private volatile boolean trackedNpcCounted;

    private int floor1RouteIndex;
    private int floor2RouteIndex;
    private long lastMoveClickMs;
    private long lastPortalAttemptMs;
    private WorldPoint lastTravelPoint;
    private long lastTravelProgressMs;

    private WorldPoint pendingDoorLocation;
    private WorldPoint pendingDoorApproachPoint;
    private WorldPoint pendingDoorWaypoint;
    private long pendingDoorClickedAtMs;

    public boolean run(KspFleshCrawlerConfig config) {
        Microbot.enableAutoRunOn = true;
        resetSession();
        ensureNavigationExecutor();

        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!Microbot.isLoggedIn() || !super.run()) {
                    return;
                }

                updateKillTracking();

                if (handleStrongholdDialogue()) {
                    return;
                }

                if (handlePendingDoorCrossing()) {
                    return;
                }

                if (config.autoRetaliate()) {
                    Rs2Combat.setAutoRetaliate(true);
                }

                if (config.stopAtGoals() && trainingController.allEnabledGoalsReached(config)) {
                    state = FleshCrawlerState.GOALS_REACHED;
                    lastAction = "All enabled combat goals reached";
                    return;
                }

                if (handleHealing(config)) {
                    return;
                }

                if (handleOutOfFood(config)) {
                    return;
                }

                if (config.usePotions() && handlePotions()) {
                    return;
                }

                if (ensureAtFleshCrawlerRoom(config)) {
                    return;
                }

                trainingController.update(config);

                if (Rs2Combat.inCombat()) {
                    trackCurrentOpponent();
                    state = FleshCrawlerState.FIGHTING;
                    lastAction = "Fighting Flesh Crawler";
                    return;
                }

                if (config.buryBones() && handleBones(config)) {
                    return;
                }

                if (config.lootOwnDrops() && handleOwnDrops(config)) {
                    return;
                }

                if (config.lootEnabled() && handleLoot(config)) {
                    return;
                }

                attackNextCrawler(config);
            } catch (Exception ex) {
                Microbot.logStackTrace(getClass().getSimpleName(), ex);
                state = FleshCrawlerState.WAITING;
                lastAction = "Recovered from script exception";
            }
        }, 0, 350, TimeUnit.MILLISECONDS);

        return true;
    }

    @Override
    public void shutdown() {
        cancelNavigation();
        if (navigationExecutor != null) {
            navigationExecutor.shutdownNow();
            navigationExecutor = null;
        }
        super.shutdown();
        state = FleshCrawlerState.WAITING;
        lastAction = "Stopped";
    }

    private void resetSession() {
        state = FleshCrawlerState.INITIALIZING;
        lastAction = "Waiting for Flesh Crawlers";
        kills = 0;
        itemsLooted = 0;
        bonesBuried = 0;
        foodEaten = 0;
        fightAnchor = DEFAULT_FIGHT_POINT;
        navigationTarget = null;
        navigationFuture = null;
        trackedNpcIndex = -1;
        trackedNpcCounted = false;

        floor1RouteIndex = 0;
        floor2RouteIndex = 0;
        lastMoveClickMs = 0L;
        lastPortalAttemptMs = 0L;
        lastTravelPoint = null;
        lastTravelProgressMs = System.currentTimeMillis();
        recentlyClickedDoors.clear();
        clearPendingDoor();
    }

    private boolean ensureAtFleshCrawlerRoom(KspFleshCrawlerConfig config) {
        if (!config.autoTravel()) {
            return false;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) {
            state = FleshCrawlerState.WALKING_TO_FIGHT;
            lastAction = "Waiting for player location";
            return true;
        }

        updateTravelProgress(player);

        /*
         * If the user starts the plugin already beside Flesh Crawlers on floor 2,
         * use that room rather than forcing a walk back to our preferred entry room.
         */
        if (isOnFloor2(player)) {
            Rs2NpcModel nearbyCrawler = Rs2Npc.getNpcs(npc ->
                            npc.getName() != null
                                    && NPC_NAME.equalsIgnoreCase(npc.getName())
                                    && !npc.isDead()
                                    && npc.getWorldLocation().distanceTo(player) <= 12
                                    && Rs2Walker.canReach(npc.getWorldLocation()))
                    .findFirst()
                    .orElse(null);
            if (nearbyCrawler != null) {
                fightAnchor = nearbyCrawler.getWorldLocation();
                cancelNavigationIfOwned();
                return false;
            }
        }

        int arrivalRadius = Math.min(Math.max(config.fightRadius() / 2, 4), 8);
        if (isOnFloor2(player) && player.distanceTo(DEFAULT_FIGHT_POINT) <= arrivalRadius) {
            fightAnchor = DEFAULT_FIGHT_POINT;
            cancelNavigationIfOwned();
            return false;
        }

        if (isOnFloor1(player)) {
            cancelNavigationIfOwned();
            state = FleshCrawlerState.WALKING_TO_FIGHT;
            return handleFloor1Navigation(config, player);
        }

        if (isOnFloor2(player)) {
            cancelNavigationIfOwned();
            state = FleshCrawlerState.WALKING_TO_FIGHT;
            return handleFloor2Navigation(player);
        }

        /*
         * Outside the Stronghold: web-walk only to the SURFACE entrance.
         * Once underground, control is handed to our Stronghold navigator so the
         * generic walker cannot get trapped repeatedly selecting the same gate.
         */
        state = FleshCrawlerState.WALKING_TO_FIGHT;
        if (player.distanceTo(STRONGHOLD_SURFACE_ENTRANCE) <= 5) {
            cancelNavigationIfOwned();
            lastAction = "Entering Stronghold of Security";
            if (Rs2GameObject.interact(ObjectID.SOS_DUNG_ENT_OPEN, "Climb-down")) {
                sleep(500, 800);
            } else {
                Rs2GameObject.interact(ObjectID.SOS_DUNG_ENT_OPEN);
            }
            return true;
        }

        if (isNavigationRunning()) {
            lastAction = "Web-walking to Stronghold entrance";
            return true;
        }

        lastAction = "Web-walking to Stronghold entrance";
        startNavigation(STRONGHOLD_SURFACE_ENTRANCE, 3);
        return true;
    }

    private boolean handleFloor1Navigation(KspFleshCrawlerConfig config, WorldPoint player) {
        if (player.distanceTo(WAR_LADDER_DOWN_POINT) <= 8) {
            lastAction = "Descending to Catacomb of Famine";
            if (player.distanceTo(WAR_LADDER_DOWN_POINT) > 3) {
                moveLocally(WAR_LADDER_DOWN_POINT);
                return true;
            }

            if (!Rs2Player.isMoving() && !Rs2Dialogue.isInDialogue()) {
                if (!Rs2GameObject.interact(ObjectID.SOS_WAR_LADD_DOWN, "Climb-down")) {
                    Rs2GameObject.interact(ObjectID.SOS_WAR_LADD_DOWN);
                }
                sleep(450, 700);
            }
            return true;
        }

        if (config.useStrongholdPortals()
                && isInFloor1StartRoom(player)
                && canUseWarPortal()
                && System.currentTimeMillis() - lastPortalAttemptMs >= PORTAL_RETRY_COOLDOWN_MS) {
            lastPortalAttemptMs = System.currentTimeMillis();
            lastAction = "Using Vault of War shortcut portal";

            /*
             * Interact with the portal directly rather than trying to stand on its
             * object tile. The start room is small and the portal is already in scene.
             */
            if (Rs2GameObject.interact(ObjectID.SOS_WAR_PORTAL)) {
                sleep(500, 850);
                return true;
            }
        }

        lastAction = config.useStrongholdPortals() && canUseWarPortal()
                ? "Reaching Vault of War portal"
                : "Crossing Vault of War (custom doors)";

        floor1RouteIndex = normalizeRouteIndex(FLOOR_1_ROUTE, floor1RouteIndex, player);
        if (floor1RouteIndex >= FLOOR_1_ROUTE.size()) {
            moveLocally(WAR_LADDER_DOWN_POINT);
            return true;
        }

        WorldPoint waypoint = FLOOR_1_ROUTE.get(floor1RouteIndex);
        if (player.distanceTo(waypoint) <= 3) {
            floor1RouteIndex++;
            return true;
        }

        navigateStrongholdWaypoint(player, waypoint, "Gate of War");
        return true;
    }

    private boolean handleFloor2Navigation(WorldPoint player) {
        Rs2NpcModel nearestCrawler = findNearestFloor2Crawler(player, FLOOR_2_CRAWLER_SCAN_RADIUS);
        if (nearestCrawler != null) {
            WorldPoint crawlerLocation = nearestCrawler.getWorldLocation();

            // As soon as the first rickety-door airlock has been crossed, the
            // adjacent Flesh Crawlers become locally reachable. Stop routing and
            // let the normal combat loop take over immediately.
            if (crawlerLocation != null
                    && player.distanceTo(crawlerLocation) <= 14
                    && Rs2Walker.canReach(crawlerLocation)) {
                fightAnchor = crawlerLocation;
                lastAction = "Reached Flesh Crawler room";
                floor2RouteIndex = FLOOR_2_ENTRY_ROUTE.size();
                return false;
            }
        }

        lastAction = nearestCrawler != null
                ? "Crossing rickety doors to Flesh Crawlers"
                : "Entering nearest Flesh Crawler room";

        floor2RouteIndex = normalizeRouteIndex(FLOOR_2_ENTRY_ROUTE, floor2RouteIndex, player);
        if (floor2RouteIndex >= FLOOR_2_ENTRY_ROUTE.size()) {
            fightAnchor = DEFAULT_FIGHT_POINT;
            return false;
        }

        WorldPoint waypoint = FLOOR_2_ENTRY_ROUTE.get(floor2RouteIndex);
        if (player.distanceTo(waypoint) <= 3) {
            floor2RouteIndex++;
            return true;
        }

        /*
         * Once the west-side waypoint is active and a crawler is visible, use
         * the crawler itself as the direction-of-travel hint for door scoring.
         * This prevents a south/east door from winning merely because it is a
         * little closer to the player.
         */
        WorldPoint navigationHint = waypoint;
        if (floor2RouteIndex >= 1 && nearestCrawler != null && nearestCrawler.getWorldLocation() != null) {
            navigationHint = nearestCrawler.getWorldLocation();
        }

        navigateStrongholdWaypoint(player, navigationHint, "Rickety door");
        return true;
    }

    private Rs2NpcModel findNearestFloor2Crawler(WorldPoint player, int radius) {
        if (player == null) {
            return null;
        }

        return Rs2Npc.getNpcs(npc -> npc != null
                        && npc.getName() != null
                        && NPC_NAME.equalsIgnoreCase(npc.getName())
                        && !npc.isDead()
                        && npc.getWorldLocation() != null
                        && isOnFloor2(npc.getWorldLocation())
                        && npc.getWorldLocation().distanceTo(player) <= radius)
                .min(Comparator.comparingInt(npc -> npc.getWorldLocation().distanceTo(player)))
                .orElse(null);
    }

    private int normalizeRouteIndex(List<WorldPoint> route, int currentIndex, WorldPoint player) {
        int index = Math.max(0, Math.min(currentIndex, route.size()));

        while (index < route.size() && player.distanceTo(route.get(index)) <= 3) {
            index++;
        }

        if (index == 0 && !route.isEmpty() && player.distanceTo(route.get(0)) > 10) {
            int closestIndex = 0;
            int closestDistance = Integer.MAX_VALUE;
            for (int i = 0; i < route.size(); i++) {
                int distance = player.distanceTo(route.get(i));
                if (distance < closestDistance) {
                    closestDistance = distance;
                    closestIndex = i;
                }
            }
            if (closestDistance <= 14) {
                index = closestIndex;
            }
        }

        return index;
    }

    private void navigateStrongholdWaypoint(WorldPoint player, WorldPoint waypoint, String doorName) {
        if (Rs2Dialogue.isInDialogue()) {
            return;
        }

        long stationaryFor = System.currentTimeMillis() - lastTravelProgressMs;
        boolean waypointReachable = Rs2Walker.canReach(waypoint);

        if (!waypointReachable || stationaryFor >= 1_200L) {
            if (tryOpenProgressDoor(player, waypoint, doorName)) {
                return;
            }
        }

        if (Rs2Player.isMoving()) {
            return;
        }

        moveLocally(waypoint);
    }

    private boolean tryOpenProgressDoor(WorldPoint player, WorldPoint waypoint, String expectedName) {
        pruneRecentDoors();

        List<TileObject> matching = Rs2GameObject.getAll().stream()
                .filter(obj -> obj != null && obj.getWorldLocation() != null)
                .filter(obj -> obj.getWorldLocation().distanceTo(player) <= 7)
                .filter(obj -> isExpectedStrongholdDoor(obj, expectedName))
                .filter(obj -> Rs2GameObject.hasAction(obj, "Open", false))
                .filter(obj -> !isDoorOnCooldown(obj.getWorldLocation()))
                .sorted(Comparator.comparingInt(obj -> doorScore(player, waypoint, obj.getWorldLocation())))
                .collect(Collectors.toList());

        if (matching.isEmpty()) {
            return false;
        }

        TileObject door = matching.get(0);
        WorldPoint doorLocation = door.getWorldLocation();

        /*
         * Stronghold-specific anti-loop rule:
         * once a gate/door was clicked, it is ineligible for twelve seconds.
         * The second door of the pair therefore wins the next selection instead
         * of the navigator hammering the first door over and over.
         */
        recentlyClickedDoors.put(doorLocation, System.currentTimeMillis());
        pendingDoorLocation = doorLocation;
        pendingDoorApproachPoint = player;
        pendingDoorWaypoint = waypoint;
        pendingDoorClickedAtMs = System.currentTimeMillis();

        lastAction = "Opening next " + expectedName;
        if (Rs2GameObject.interact(door, "Open")) {
            sleep(250, 400);
            return true;
        }

        clearPendingDoor();
        return false;
    }


    private boolean isExpectedStrongholdDoor(TileObject obj, String expectedName) {
        if (obj == null) {
            return false;
        }

        if ("Rickety door".equalsIgnoreCase(expectedName)
                && (obj.getId() == RICKETY_DOOR_ID_A || obj.getId() == RICKETY_DOOR_ID_B)) {
            return true;
        }

        return Rs2GameObject.getCompositionName(obj)
                .map(name -> name.equalsIgnoreCase(expectedName))
                .orElse(false);
    }

    private int doorScore(WorldPoint player, WorldPoint waypoint, WorldPoint door) {
        int fromPlayer = player.distanceTo(door);
        int toWaypoint = door.distanceTo(waypoint);
        int currentToWaypoint = player.distanceTo(waypoint);

        /*
         * Prefer nearby doors which actually advance toward the current route
         * waypoint. The penalty prevents grabbing another visible room's door.
         */
        int backwardsPenalty = toWaypoint > currentToWaypoint + 3 ? 100 : 0;
        return (fromPlayer * 5) + (toWaypoint * 2) + backwardsPenalty;
    }

    private boolean handlePendingDoorCrossing() {
        if (pendingDoorLocation == null) {
            return false;
        }

        if (Rs2Dialogue.isInDialogue()) {
            return false; // dialogue handler owns this tick
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) {
            return true;
        }

        if (player.distanceTo(pendingDoorLocation) >= 3) {
            clearPendingDoor();
            return false;
        }

        long age = System.currentTimeMillis() - pendingDoorClickedAtMs;
        if (age < DOOR_NUDGE_MIN_DELAY_MS) {
            return true;
        }

        if (age > 3_500L) {
            clearPendingDoor();
            return false;
        }

        if (Rs2Player.isMoving() || Rs2Player.isAnimating()) {
            return true;
        }

        // Do not hammer the same post-door tile every script tick. A single
        // canvas click gets time to move the player through the airlock before
        // another nudge is allowed.
        if (System.currentTimeMillis() - lastMoveClickMs < MOVE_CLICK_COOLDOWN_MS) {
            return true;
        }

        WorldPoint crossTile = chooseDoorCrossTile();
        if (crossTile != null) {
            lastAction = "Crossing opened Stronghold door";
            Rs2Walker.walkFastCanvas(crossTile);
            lastMoveClickMs = System.currentTimeMillis();
            return true;
        }

        clearPendingDoor();
        return false;
    }

    private WorldPoint chooseDoorCrossTile() {
        if (pendingDoorLocation == null || pendingDoorWaypoint == null || pendingDoorApproachPoint == null) {
            return null;
        }

        List<WorldPoint> candidates = new ArrayList<>();
        for (int dx = -2; dx <= 2; dx++) {
            for (int dy = -2; dy <= 2; dy++) {
                if (dx == 0 && dy == 0) {
                    continue;
                }
                WorldPoint point = new WorldPoint(
                        pendingDoorLocation.getX() + dx,
                        pendingDoorLocation.getY() + dy,
                        pendingDoorLocation.getPlane()
                );

                if (point.distanceTo(pendingDoorApproachPoint) <= 1) {
                    continue;
                }
                if (point.distanceTo(pendingDoorWaypoint) >= pendingDoorApproachPoint.distanceTo(pendingDoorWaypoint)) {
                    continue;
                }
                if (Rs2Walker.canReach(point)) {
                    candidates.add(point);
                }
            }
        }

        return candidates.stream()
                .min(Comparator.comparingInt(point ->
                        point.distanceTo(pendingDoorWaypoint) * 10
                                - point.distanceTo(pendingDoorApproachPoint) * 2))
                .orElse(null);
    }

    private void moveLocally(WorldPoint target) {
        if (target == null || Rs2Player.isMoving() || Rs2Dialogue.isInDialogue()) {
            return;
        }

        long now = System.currentTimeMillis();
        if (now - lastMoveClickMs < MOVE_CLICK_COOLDOWN_MS) {
            return;
        }
        lastMoveClickMs = now;

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) {
            return;
        }

        int distance = player.distanceTo(target);
        if (distance <= 8) {
            if (!Rs2Walker.walkFastCanvas(target)) {
                Rs2Walker.walkMiniMap(target);
            }
        } else {
            Rs2Walker.walkMiniMap(target);
        }
    }

    private void updateTravelProgress(WorldPoint player) {
        if (lastTravelPoint == null || !lastTravelPoint.equals(player)) {
            lastTravelPoint = player;
            lastTravelProgressMs = System.currentTimeMillis();
        }
    }

    private void pruneRecentDoors() {
        long now = System.currentTimeMillis();
        recentlyClickedDoors.entrySet().removeIf(entry -> now - entry.getValue() >= DOOR_RETRY_COOLDOWN_MS);
    }

    private boolean isDoorOnCooldown(WorldPoint location) {
        Long clickedAt = recentlyClickedDoors.get(location);
        return clickedAt != null && System.currentTimeMillis() - clickedAt < DOOR_RETRY_COOLDOWN_MS;
    }

    private void clearPendingDoor() {
        pendingDoorLocation = null;
        pendingDoorApproachPoint = null;
        pendingDoorWaypoint = null;
        pendingDoorClickedAtMs = 0L;
    }

    private boolean canUseWarPortal() {
        int combatLevel = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient().getLocalPlayer() == null) {
                return 0;
            }
            return Microbot.getClient().getLocalPlayer().getCombatLevel();
        }).orElse(0);

        return combatLevel >= 26 || Microbot.getVarbitValue(VarbitID.SOS_EMOTE_FLAP) == 1;
    }

    private boolean isInFloor1StartRoom(WorldPoint point) {
        return isInside(point,
                FLOOR_1_START_MIN_X, FLOOR_1_START_MAX_X,
                FLOOR_1_START_MIN_Y, FLOOR_1_START_MAX_Y);
    }

    private boolean isOnFloor1(WorldPoint point) {
        return isInside(point, FLOOR_1_MIN_X, FLOOR_1_MAX_X, FLOOR_1_MIN_Y, FLOOR_1_MAX_Y);
    }

    private boolean isOnFloor2(WorldPoint point) {
        return isInside(point, FLOOR_2_MIN_X, FLOOR_2_MAX_X, FLOOR_2_MIN_Y, FLOOR_2_MAX_Y);
    }

    private boolean isInside(WorldPoint point, int minX, int maxX, int minY, int maxY) {
        return point != null
                && point.getPlane() == 0
                && point.getX() >= minX && point.getX() <= maxX
                && point.getY() >= minY && point.getY() <= maxY;
    }

    private boolean handleStrongholdDialogue() {
        if (!Rs2Dialogue.isInDialogue()) {
            return false;
        }

        WorldPoint player = Rs2Player.getWorldLocation();
        boolean strongholdTravel = isNavigationRunning() || isOnFloor1(player) || isOnFloor2(player);
        if (!strongholdTravel) {
            return false;
        }

        if (Rs2Dialogue.hasSelectAnOption()) {
            for (String answer : STRONGHOLD_CORRECT_ANSWERS) {
                if (Rs2Dialogue.hasDialogueOption(answer, true)) {
                    state = FleshCrawlerState.WALKING_TO_FIGHT;
                    lastAction = "Answering Stronghold security door";
                    if (Rs2Dialogue.clickOption(true, answer)) {
                        sleep(300, 500);
                    }
                    return true;
                }
            }
            return false;
        }

        if (Rs2Dialogue.hasContinue()) {
            state = FleshCrawlerState.WALKING_TO_FIGHT;
            lastAction = "Continuing Stronghold dialogue";
            Rs2Dialogue.clickContinue();
            sleep(250, 450);
            return true;
        }

        return false;
    }

    private synchronized void ensureNavigationExecutor() {
        if (navigationExecutor != null && !navigationExecutor.isShutdown()) {
            return;
        }
        navigationExecutor = Executors.newSingleThreadExecutor(runnable -> {
            Thread thread = new Thread(runnable, "ksp-flesh-crawler-navigation");
            thread.setDaemon(true);
            return thread;
        });
    }

    private synchronized void startNavigation(WorldPoint target, int distance) {
        if (target == null || isNavigationRunning()) {
            return;
        }

        ensureNavigationExecutor();
        navigationTarget = target;
        navigationFuture = navigationExecutor.submit(() -> {
            try {
                boolean arrived = Rs2Walker.walkTo(target, distance);
                if (arrived) {
                    lastAction = "Arrived at Stronghold entrance";
                } else if (!Thread.currentThread().isInterrupted()) {
                    lastAction = "Overworld walker stopped - retrying";
                }
            } catch (Exception ex) {
                Microbot.logStackTrace("KspFleshCrawlerNavigation", ex);
                lastAction = "Overworld navigation error - retrying";
            } finally {
                navigationTarget = null;
            }
        });
    }

    private boolean isNavigationRunning() {
        Future<?> future = navigationFuture;
        return future != null && !future.isDone() && !future.isCancelled();
    }

    private synchronized void cancelNavigationIfOwned() {
        if (isNavigationRunning() || navigationTarget != null) {
            cancelNavigation();
        }
    }

    private synchronized void cancelNavigation() {
        Future<?> future = navigationFuture;
        boolean ownedActiveWalk = (future != null && !future.isDone()) || navigationTarget != null;
        if (future != null && !future.isDone()) {
            future.cancel(true);
        }
        navigationFuture = null;
        navigationTarget = null;
        if (ownedActiveWalk) {
            Rs2Walker.setTarget(null);
        }
    }

    private boolean handleHealing(KspFleshCrawlerConfig config) {
        if (!config.useHealing()) {
            return false;
        }

        int currentHp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int maxHp = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
        int foodHeal = resolveFoodHeal(config.foodName(), config.unknownFoodHeal());

        // Flesh Crawlers only chip for very small hits, so healing at one food's
        // missing-HP value wastes supplies and interrupts combat far too often.
        // Use a low absolute HP threshold instead. We still avoid sitting at 1 HP,
        // and when possible we wait until the food can heal without wasting points.
        final int fleshCrawlerMaxHit = 1;
        final int safetyFloor = fleshCrawlerMaxHit + 1;
        final int configuredThreshold = Math.min(maxHp, Math.max(safetyFloor, config.healAtHp()));
        final int noWasteThreshold = Math.max(1, maxHp - foodHeal);
        final int healAtHp = Math.max(safetyFloor, Math.min(configuredThreshold, noWasteThreshold));

        if (currentHp > healAtHp) {
            return false;
        }

        if (!Rs2Inventory.contains(config.foodName(), false)) {
            return false;
        }

        state = FleshCrawlerState.HEALING;
        lastAction = "Eating " + config.foodName();
        if (Rs2Inventory.interact(config.foodName(), "Eat", false)) {
            foodEaten++;
            sleep(450, 700);
        }
        return true;
    }

    private boolean handleOutOfFood(KspFleshCrawlerConfig config) {
        if (!config.useHealing() || Rs2Inventory.contains(config.foodName(), false)) {
            return false;
        }

        if (!config.bankForFood()) {
            int currentHp = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
            int pauseAtHp = Math.max(2, config.healAtHp());
            if (currentHp <= pauseAtHp) {
                state = FleshCrawlerState.OUT_OF_FOOD;
                lastAction = "Out of food - combat paused at " + currentHp + " HP";
                return true;
            }
            return false;
        }

        if (fightAnchor == null) {
            state = FleshCrawlerState.OUT_OF_FOOD;
            lastAction = "Cannot bank: fight anchor not captured";
            return true;
        }

        if (isNavigationRunning()) {
            cancelNavigation();
        }

        if (!Rs2Bank.isNearBank(10)) {
            state = FleshCrawlerState.WALKING_TO_BANK;
            lastAction = "Walking to bank for food";
            Rs2Bank.walkToBank();
            return true;
        }

        state = FleshCrawlerState.BANKING;
        lastAction = "Restocking " + config.foodName();
        if (!Rs2Bank.isOpen() && !Rs2Bank.openBank()) {
            return true;
        }

        List<String> keep = new ArrayList<>();
        keep.add(config.foodName());
        keep.addAll(defaultPotionBaseNames());
        Rs2Bank.depositAllExcept(keep);

        int currentFood = Rs2Inventory.count(config.foodName(), false);
        int needed = Math.max(0, config.foodAmount() - currentFood);
        if (needed > 0) {
            Rs2Bank.withdrawX(config.foodName(), needed, false);
            sleepUntil(() -> Rs2Inventory.count(config.foodName(), false) >= config.foodAmount(), 3_000);
        }

        if (!Rs2Inventory.contains(config.foodName(), false)) {
            state = FleshCrawlerState.OUT_OF_FOOD;
            lastAction = "Configured food not available in bank";
            return true;
        }

        Rs2Bank.closeBank();
        state = FleshCrawlerState.RETURNING_TO_FIGHT;
        lastAction = "Returning to Flesh Crawlers";
        /*
         * Do not web-walk directly to the underground crawler tile. On the next
         * tick ensureAtFleshCrawlerRoom() will web-walk only to the surface
         * entrance, then switch to portal/custom-door navigation underground.
         */
        return true;
    }

    private boolean handlePotions() {
        if (drinkIfUnboosted("Super combat potion", Skill.ATTACK)
                || drinkIfUnboosted("Combat potion", Skill.ATTACK)
                || drinkIfUnboosted("Attack potion", Skill.ATTACK)
                || drinkIfUnboosted("Super attack", Skill.ATTACK)
                || drinkIfUnboosted("Strength potion", Skill.STRENGTH)
                || drinkIfUnboosted("Super strength", Skill.STRENGTH)
                || drinkIfUnboosted("Defence potion", Skill.DEFENCE)
                || drinkIfUnboosted("Super defence", Skill.DEFENCE)) {
            state = FleshCrawlerState.DRINKING_POTION;
            return true;
        }
        return false;
    }

    private boolean drinkIfUnboosted(String potionBaseName, Skill skill) {
        if (Microbot.getClient().getBoostedSkillLevel(skill) > Microbot.getClient().getRealSkillLevel(skill)) {
            return false;
        }
        if (!Rs2Inventory.contains(potionBaseName, false)) {
            return false;
        }

        lastAction = "Drinking " + potionBaseName;
        if (Rs2Inventory.interact(potionBaseName, "Drink", false)) {
            sleep(450, 700);
            return true;
        }
        return false;
    }

    private boolean handleBones(KspFleshCrawlerConfig config) {
        List<String> boneNames = parseCsv(config.boneItems());
        for (String boneName : boneNames) {
            if (Rs2Inventory.contains(boneName, true)) {
                state = FleshCrawlerState.BURYING;
                lastAction = "Burying " + boneName;
                if (Rs2Inventory.interact(boneName, "Bury", true)) {
                    bonesBuried++;
                    sleep(350, 550);
                }
                return true;
            }
        }

        if (Rs2Inventory.isFull()) {
            return false;
        }

        for (String boneName : boneNames) {
            if (Rs2GroundItem.loot(boneName, config.lootRadius())) {
                state = FleshCrawlerState.BURYING;
                lastAction = "Picking up " + boneName;
                sleep(450, 700);
                return true;
            }
        }
        return false;
    }

    private boolean handleOwnDrops(KspFleshCrawlerConfig config) {
        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null) {
            return false;
        }

        GroundItem ownDrop = Rs2GroundItem.getGroundItems().values().stream()
                .filter(Objects::nonNull)
                .filter(item -> item.getOwnership() == TileItem.OWNERSHIP_SELF)
                .filter(item -> item.getLocation() != null)
                .filter(item -> item.getLocation().distanceTo(playerLocation) <= config.lootRadius())
                .filter(Rs2GroundItem::canTakeGroundItem)
                .min(Comparator.comparingInt(item -> item.getLocation().distanceTo(playerLocation)))
                .orElse(null);

        if (ownDrop == null) {
            return false;
        }

        state = FleshCrawlerState.LOOTING;
        lastAction = "Looting own drop: " + ownDrop.getName();
        if (Rs2GroundItem.interact(ownDrop)) {
            itemsLooted++;
            sleep(450, 750);
            return true;
        }
        return false;
    }

    private boolean handleLoot(KspFleshCrawlerConfig config) {
        List<String> lootNames = parseCsv(config.lootItems());
        if (lootNames.isEmpty()) {
            return false;
        }

        for (String lootName : lootNames) {
            if (Rs2Inventory.isFull(lootName)) {
                continue;
            }
            if (Rs2GroundItem.loot(lootName, config.lootRadius())) {
                state = FleshCrawlerState.LOOTING;
                lastAction = "Looting " + lootName;
                itemsLooted++;
                sleep(450, 750);
                return true;
            }
        }
        return false;
    }

    private void attackNextCrawler(KspFleshCrawlerConfig config) {
        if (fightAnchor == null) {
            fightAnchor = DEFAULT_FIGHT_POINT;
        }

        Rs2NpcModel target = Rs2Npc.getNpcs(npc -> {
                    String name = npc.getName();
                    if (name == null || !NPC_NAME.equalsIgnoreCase(name) || npc.isDead()) {
                        return false;
                    }
                    if (!npc.isWithinDistance(fightAnchor, config.fightRadius())) {
                        return false;
                    }
                    Actor interacting = npc.getInteracting();
                    return interacting == null || interacting == Microbot.getClient().getLocalPlayer();
                })
                .findFirst()
                .orElse(null);

        if (target == null) {
            state = FleshCrawlerState.WAITING;
            lastAction = "Waiting for reachable Flesh Crawler";
            return;
        }

        if (Rs2Npc.attack(target)) {
            trackedNpcIndex = target.getIndex();
            trackedNpcCounted = false;
            state = FleshCrawlerState.FIGHTING;
            lastAction = "Attacking Flesh Crawler (level " + target.getCombatLevel() + ")";
        }
    }

    private void trackCurrentOpponent() {
        Actor interacting = Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient().getLocalPlayer() == null) {
                return null;
            }
            return Microbot.getClient().getLocalPlayer().getInteracting();
        }).orElse(null);

        if (interacting instanceof NPC) {
            NPC npc = (NPC) interacting;
            if (npc.getName() != null && NPC_NAME.equalsIgnoreCase(npc.getName())) {
                if (trackedNpcIndex != npc.getIndex()) {
                    trackedNpcIndex = npc.getIndex();
                    trackedNpcCounted = false;
                }
            }
        }
    }

    private void updateKillTracking() {
        if (trackedNpcIndex < 0 || trackedNpcCounted) {
            return;
        }

        Rs2NpcModel tracked = Rs2Npc.getNpcByIndex(trackedNpcIndex);
        if (tracked != null && tracked.isDead()) {
            kills++;
            trackedNpcCounted = true;
            lastAction = "Flesh Crawler defeated";
        }
    }

    private int resolveFoodHeal(String foodName, int fallback) {
        if (foodName != null) {
            for (Rs2Food food : Rs2Food.values()) {
                if (food.getName().equalsIgnoreCase(foodName.trim())) {
                    return Math.max(1, food.getHeal());
                }
            }
        }
        return Math.max(1, fallback);
    }

    private List<String> parseCsv(String raw) {
        if (raw == null || raw.trim().isEmpty()) {
            return new ArrayList<>();
        }
        return Arrays.stream(raw.split(","))
                .map(String::trim)
                .filter(value -> !value.isEmpty())
                .distinct()
                .collect(Collectors.toList());
    }

    private List<String> defaultPotionBaseNames() {
        return Arrays.asList(
                "Super combat potion",
                "Combat potion",
                "Attack potion",
                "Super attack",
                "Strength potion",
                "Super strength",
                "Defence potion",
                "Super defence"
        );
    }

    public FleshCrawlerState getState() {
        return state;
    }

    public String getLastAction() {
        return lastAction;
    }

    public int getKills() {
        return kills;
    }

    public int getItemsLooted() {
        return itemsLooted;
    }

    public int getBonesBuried() {
        return bonesBuried;
    }

    public int getFoodEaten() {
        return foodEaten;
    }

    public WorldPoint getFightAnchor() {
        return fightAnchor;
    }

    public Skill getCurrentTrainingSkill() {
        return trainingController.getCurrentTrainingSkill();
    }
}
