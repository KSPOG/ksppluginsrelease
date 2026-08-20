package net.runelite.client.plugins.microbot.kspfleshcrawlers;

import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Deterministic Stronghold navigator for the exact rooms supplied by the user.
 *
 * Design rules:
 *  - WebWalker owns movement inside each known room/corridor and walks to the approach tile of the NEXT known door.
 *  - The plugin only interacts with a Stronghold door after WebWalker has brought the player next to that door.
 *  - WebWalker is never given a destination on the far side of a closed Stronghold door.
 *  - Door crossings, portals, ropes and ladders remain deterministic object interactions.
 *  - No nearest-door logic exists; every floor-2 door is bound to one specific airlock boundary.
 *  - The bank exit uses the east floor-2 rope shortcut instead of reversing the maze.
 */
@SuppressWarnings({"deprecation", "removal"})
final class StrongholdNavigator {
    static final WorldPoint FIGHT_TARGET = new WorldPoint(2040, 5188, 0);

    private static final WorldPoint SURFACE_ENTRANCE = new WorldPoint(3081, 3420, 0);
    private static final WorldPoint WAR_PORTAL = new WorldPoint(1863, 5238, 0);
    private static final WorldPoint WAR_LADDER_DOWN = new WorldPoint(1902, 5222, 0);
    private static final WorldPoint FAMINE_START_LADDER_UP = new WorldPoint(2042, 5245, 0);
    private static final WorldPoint WAR_START_LADDER_UP = new WorldPoint(1859, 5243, 0);
    private static final WorldPoint FAMINE_EAST_ROPE = new WorldPoint(2040, 5208, 0);

    // Current OSRS gamevals / Microbot transport IDs.
    private static final int SOS_ENTRANCE = 20790;
    private static final int SOS_WAR_PORTAL = 20786;
    private static final int SOS_WAR_LADDER_DOWN = 20785;
    private static final int SOS_WAR_LADDER_UP = 20784;
    private static final int SOS_FAM_LADDER_UP = 19003;
    private static final int SOS_FAM_ROPE_UP = 19001;

    // Catacomb of Famine closed/open door faces. v1 used the wrong 16065/16066 IDs.
    private static final int FAM_DOOR_CLOSED_A = 17009;
    private static final int FAM_DOOR_CLOSED_B = 17100;
    private static final int FAM_DOOR_OPEN_A = 18964;
    private static final int FAM_DOOR_OPEN_B = 18965;

    private static final long MOVE_COOLDOWN_MS = 700L;
    private static final int DOOR_APPROACH_DISTANCE = 1;
    private static final long OBJECT_RETRY_MS = 1_800L;
    private static final long DOOR_RETRY_MS = 2_400L;
    private static final int MAX_DOOR_ATTEMPTS = 6;

    private static final String[] SECURITY_ANSWERS = {
            "No.", "Me.", "Nobody.", "Talk to any banker.", "Nothing, it's a fake.",
            "Delete it - it's a fake!", "Don't give them my password.",
            "Report the player for phishing.", "Use the Account Recovery system.",
            "No way! I'm reporting you to Jagex!", "No, you should never buy an account.",
            "Secure my device and reset my password.", "Decline the offer and report that player.",
            "The birthday of a famous person or event.", "Only on the Old School RuneScape website.",
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

    /*
     * Centre-line checkpoints derived from the supplied Room 1 Exit polygon.
     * They stay inside that polygon and avoid asking the shortest-path walker to
     * solve the Catacomb maze or touch any door.
     */
    private static final List<WorldPoint> ROOM_1_CORRIDOR_ROUTE = Arrays.asList(
            new WorldPoint(2044, 5235, 0),
            new WorldPoint(2043, 5226, 0),
            new WorldPoint(2043, 5221, 0),
            new WorldPoint(2038, 5220, 0),
            new WorldPoint(2037, 5212, 0),
            new WorldPoint(2037, 5206, 0)
    );

    private static final DoorTransition START_TO_ROOM1 = new DoorTransition(
            "Start -> Room 1 airlock",
            StrongholdZones.FLOOR_2_START, StrongholdZones.ROOM_1_ENTER,
            new WorldPoint(2044, 5241, 0), new WorldPoint(2044, 5239, 0),
            2042, 2046, 5239, 5241
    );

    private static final DoorTransition ROOM1_TO_CORRIDOR = new DoorTransition(
            "Room 1 airlock -> corridor",
            StrongholdZones.ROOM_1_ENTER, StrongholdZones.ROOM_1_EXIT,
            new WorldPoint(2044, 5237, 0), new WorldPoint(2044, 5236, 0),
            2042, 2046, 5235, 5238
    );

    private static final DoorTransition LADDER_TO_ROOM2 = new DoorTransition(
            "Ladder room -> Room 2 airlock",
            StrongholdZones.FLOOR_2_LADDER_ROOM, StrongholdZones.ROOM_2_ENTER,
            new WorldPoint(2045, 5198, 0), new WorldPoint(2045, 5197, 0),
            2043, 2047, 5196, 5199
    );

    private static final DoorTransition ROOM2_TO_FLESH = new DoorTransition(
            "Room 2 airlock -> Flesh Crawlers",
            StrongholdZones.ROOM_2_ENTER, StrongholdZones.FLESH_CRAWLER_ROOM,
            new WorldPoint(2045, 5195, 0), new WorldPoint(2045, 5194, 0),
            2043, 2047, 5193, 5196
    );

    private static final DoorTransition FLESH_TO_ROOM2 = ROOM2_TO_FLESH.reverse("Flesh Crawlers -> Room 2 airlock");
    private static final DoorTransition ROOM2_TO_LADDER = LADDER_TO_ROOM2.reverse("Room 2 airlock -> ladder room");

    private String stage = "Idle";
    private String action = "Idle";
    private String error;
    private DoorTransition activeDoor;
    private long lastMoveAt;
    private long lastObjectAt;
    private long lastDoorAt;
    private int doorAttempts;
    private int corridorIndex;
    private boolean useWebWalker = true;
    private String movementMode = "Idle";

    void reset() {
        stage = "Idle";
        action = "Idle";
        error = null;
        activeDoor = null;
        lastMoveAt = 0L;
        lastObjectAt = 0L;
        lastDoorAt = 0L;
        doorAttempts = 0;
        corridorIndex = 0;
        useWebWalker = true;
        movementMode = "Idle";
    }

    boolean tickToFight(boolean useWarPortal, boolean useWebWalker) {
        this.useWebWalker = useWebWalker;
        error = null;
        if (handleDialogue()) return false;

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) {
            set("Waiting", "Waiting for player location");
            return false;
        }

        if (activeDoor != null) {
            return tickDoor(activeDoor);
        }

        if (StrongholdZones.FLESH_CRAWLER_ROOM.contains(player)) {
            stage = "Flesh Crawler room";
            if (player.distanceTo(FIGHT_TARGET) <= 4) {
                action = "At training tile";
                return true;
            }
            action = "Moving to 2040,5188";
            moveKnown(FIGHT_TARGET);
            return false;
        }

        if (!StrongholdZones.isOnFloor1(player) && !StrongholdZones.isOnFloor2(player)) {
            stage = "Overworld -> Stronghold";
            if (player.distanceTo(SURFACE_ENTRANCE) > 4) {
                action = "Walking to Barbarian Village entrance";
                movementMode = "WebWalker (overworld)";
                if (!Rs2Player.isMoving()) Rs2Walker.walkTo(SURFACE_ENTRANCE, 2);
                return false;
            }
            action = "Climbing into Stronghold";
            interactExpectedObject(SOS_ENTRANCE, SURFACE_ENTRANCE, 4, "Climb-down");
            return false;
        }

        if (StrongholdZones.FLOOR_1_START.contains(player)) {
            stage = "Floor 1 start";
            if (!useWarPortal) {
                fail("War portal disabled; v2 intentionally has no floor-1 door-maze fallback");
                return false;
            }
            action = "Using Vault of War portal";
            interactExpectedObject(SOS_WAR_PORTAL, WAR_PORTAL, 4, "Enter");
            return false;
        }

        if (StrongholdZones.FLOOR_1_TREASURE.contains(player)) {
            stage = "Floor 1 treasure";
            if (player.distanceTo(WAR_LADDER_DOWN) > 2) {
                action = "Walking to floor-2 ladder";
                moveKnown(WAR_LADDER_DOWN);
            } else {
                action = "Climbing down to floor 2";
                interactExpectedObject(SOS_WAR_LADDER_DOWN, WAR_LADDER_DOWN, 4, "Climb-down");
            }
            return false;
        }

        if (StrongholdZones.FLOOR_2_START.contains(player)) {
            corridorIndex = 0;
            beginDoor(START_TO_ROOM1);
            return false;
        }

        if (StrongholdZones.ROOM_1_ENTER.contains(player)) {
            beginDoor(ROOM1_TO_CORRIDOR);
            return false;
        }

        if (StrongholdZones.FLOOR_2_LADDER_ROOM.contains(player)) {
            beginDoor(LADDER_TO_ROOM2);
            return false;
        }

        if (StrongholdZones.ROOM_2_ENTER.contains(player)) {
            beginDoor(ROOM2_TO_FLESH);
            return false;
        }

        if (StrongholdZones.ROOM_1_EXIT.contains(player)) {
            stage = "Room 1 corridor -> ladder room";
            if (useWebWalker) {
                action = "WebWalking through room to next door";
                webWalkToKnownPoint(LADDER_TO_ROOM2.approach, 1, "WebWalker (room -> next door)");
            } else {
                action = "Following fixed corridor fallback";
                followCorridor(player);
            }
            return false;
        }

        if (StrongholdZones.isOnFloor1(player)) {
            fail("Unknown floor-1 position: " + format(player));
            return false;
        }

        if (StrongholdZones.isOnFloor2(player)) {
            fail("Outside configured floor-2 rooms: " + format(player));
            return false;
        }

        return false;
    }

    /**
     * Exit route deliberately does not reverse the Room 1 maze:
     * Flesh room -> two known airlock doors -> ladder room -> east rope shortcut
     * -> floor-2 start -> ladder to floor 1 -> surface ladder.
     */
    boolean tickToSurface(boolean useWebWalker) {
        this.useWebWalker = useWebWalker;
        error = null;
        if (handleDialogue()) return false;

        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) {
            set("Waiting", "Waiting for player location");
            return false;
        }

        if (activeDoor != null) {
            tickDoor(activeDoor);
            return false;
        }

        if (!StrongholdZones.isOnFloor1(player) && !StrongholdZones.isOnFloor2(player)) {
            set("Surface", "Stronghold exit complete");
            return true;
        }

        if (StrongholdZones.FLESH_CRAWLER_ROOM.contains(player)) {
            beginDoor(FLESH_TO_ROOM2);
            return false;
        }

        if (StrongholdZones.ROOM_2_ENTER.contains(player)) {
            beginDoor(ROOM2_TO_LADDER);
            return false;
        }

        if (StrongholdZones.FLOOR_2_LADDER_ROOM.contains(player)) {
            stage = "Floor 2 ladder room";
            if (player.distanceTo(FAMINE_EAST_ROPE) > 2) {
                action = "Walking to east rope shortcut";
                moveKnown(FAMINE_EAST_ROPE);
            } else {
                action = "Using floor-2 rope shortcut";
                interactExpectedObject(SOS_FAM_ROPE_UP, FAMINE_EAST_ROPE, 3, "Climb-up");
            }
            return false;
        }

        if (StrongholdZones.FLOOR_2_START.contains(player)) {
            stage = "Floor 2 start";
            if (player.distanceTo(FAMINE_START_LADDER_UP) > 2) {
                action = "Walking to floor-2 start ladder";
                moveKnown(FAMINE_START_LADDER_UP);
            } else {
                action = "Climbing to floor 1";
                interactExpectedObject(SOS_FAM_LADDER_UP, FAMINE_START_LADDER_UP, 4, "Climb-up");
            }
            return false;
        }

        if (StrongholdZones.FLOOR_1_START.contains(player)) {
            stage = "Floor 1 start";
            if (player.distanceTo(WAR_START_LADDER_UP) > 2) {
                action = "Walking to surface ladder";
                moveKnown(WAR_START_LADDER_UP);
            } else {
                action = "Climbing to surface";
                interactExpectedObject(SOS_WAR_LADDER_UP, WAR_START_LADDER_UP, 4, "Climb-up");
            }
            return false;
        }

        // If a logout/reload happens inside the corridor during a bank trip,
        // recover toward the ladder room with the same fixed corridor route.
        if (StrongholdZones.ROOM_1_EXIT.contains(player)) {
            stage = "Floor 2 corridor recovery";
            if (useWebWalker) {
                action = "WebWalking to ladder-room door";
                webWalkToKnownPoint(LADDER_TO_ROOM2.approach, 1, "WebWalker (recovery -> next door)");
            } else {
                action = "Recovering with fixed checkpoints";
                followCorridor(player);
            }
            return false;
        }

        if (StrongholdZones.isOnFloor2(player)) {
            fail("Unknown floor-2 bank-exit position: " + format(player));
        } else if (StrongholdZones.isOnFloor1(player)) {
            fail("Unknown floor-1 bank-exit position: " + format(player));
        }
        return false;
    }

    private void followCorridor(WorldPoint player) {
        while (corridorIndex < ROOM_1_CORRIDOR_ROUTE.size() - 1
                && player.distanceTo(ROOM_1_CORRIDOR_ROUTE.get(corridorIndex)) <= 2) {
            corridorIndex++;
        }

        WorldPoint waypoint = ROOM_1_CORRIDOR_ROUTE.get(Math.min(corridorIndex, ROOM_1_CORRIDOR_ROUTE.size() - 1));
        if (player.distanceTo(waypoint) <= 2 && StrongholdZones.FLOOR_2_LADDER_ROOM.contains(player)) {
            return;
        }
        moveKnown(waypoint);
    }

    private void beginDoor(DoorTransition transition) {
        activeDoor = transition;
        doorAttempts = 0;
        lastDoorAt = 0L;
        stage = transition.name;
        action = "WebWalking to next door";
    }

    private boolean tickDoor(DoorTransition transition) {
        WorldPoint player = Rs2Player.getWorldLocation();
        if (player == null) return false;

        stage = transition.name;

        if (transition.to.contains(player)) {
            activeDoor = null;
            doorAttempts = 0;
            action = "Airlock crossed";
            return true;
        }

        if (handleDialogue()) return false;

        // Room-state rule: while we are in room X, WebWalker first walks to the
        // approach tile of room X's NEXT known door.  The plugin does not even
        // search/click the door until the player has actually arrived there.
        if (player.distanceTo(transition.approach) > DOOR_APPROACH_DISTANCE) {
            action = "WebWalking to next door";
            if (useWebWalker) {
                webWalkToKnownPoint(transition.approach, DOOR_APPROACH_DISTANCE,
                        "WebWalker (room -> next door)");
            } else {
                moveKnown(transition.approach);
            }
            return false;
        }

        if (Rs2Player.isMoving()) {
            action = "Arriving at next door";
            return false;
        }

        TileObject closed = findDoor(transition, true);
        TileObject open = findDoor(transition, false);

        if (open != null) {
            action = "Crossing fixed airlock";
            moveDoorCrossing(transition.cross);
            return false;
        }

        if (closed == null) {
            action = "Waiting for fixed door model update";
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastDoorAt < DOOR_RETRY_MS) {
            action = "Waiting for door/dialogue";
            return false;
        }

        if (doorAttempts >= MAX_DOOR_ATTEMPTS) {
            fail("Door did not open: " + transition.name);
            return false;
        }

        action = "Opening fixed Rickety door";
        movementMode = "Fixed door interaction";
        if (Rs2GameObject.interact(closed, "Open")) {
            doorAttempts++;
            lastDoorAt = now;
        } else {
            // A failed invocation gets a short retry but is never hammered every script tick.
            lastDoorAt = now - (DOOR_RETRY_MS - 700L);
        }
        return false;
    }

    private TileObject findDoor(DoorTransition transition, boolean closed) {
        int idA = closed ? FAM_DOOR_CLOSED_A : FAM_DOOR_OPEN_A;
        int idB = closed ? FAM_DOOR_CLOSED_B : FAM_DOOR_OPEN_B;
        WorldPoint player = Rs2Player.getWorldLocation();

        return Rs2GameObject.getAll().stream()
                .filter(obj -> obj != null && obj.getWorldLocation() != null)
                .filter(obj -> obj.getId() == idA || obj.getId() == idB)
                .filter(obj -> transition.containsBoundary(obj.getWorldLocation()))
                .min(Comparator.comparingInt(obj -> player == null ? 0 : player.distanceTo(obj.getWorldLocation())))
                .orElse(null);
    }

    private boolean interactExpectedObject(int id, WorldPoint expected, int expectedRadius, String actionName) {
        WorldPoint player = Rs2Player.getWorldLocation();
        TileObject object = Rs2GameObject.getAll().stream()
                .filter(obj -> obj != null && obj.getWorldLocation() != null && obj.getId() == id)
                .filter(obj -> obj.getWorldLocation().distanceTo(expected) <= expectedRadius)
                .min(Comparator.comparingInt(obj -> obj.getWorldLocation().distanceTo(expected)))
                .orElse(null);

        if (object == null) {
            if (player != null && player.distanceTo(expected) > 2) moveKnown(expected);
            return false;
        }

        if (player != null && player.distanceTo(object.getWorldLocation()) > 2) {
            moveKnown(object.getWorldLocation());
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastObjectAt < OBJECT_RETRY_MS) return false;
        movementMode = "Fixed object interaction";
        if (Rs2GameObject.interact(object, actionName)) {
            lastObjectAt = now;
            return true;
        }
        lastObjectAt = now - (OBJECT_RETRY_MS - 600L);
        return false;
    }

    boolean handleDialogue() {
        if (!Rs2Dialogue.isInDialogue()) return false;

        if (Rs2Dialogue.hasSelectAnOption()) {
            for (String answer : SECURITY_ANSWERS) {
                if (Rs2Dialogue.hasDialogueOption(answer, true)) {
                    action = "Answering Stronghold security question";
                    Rs2Dialogue.clickOption(true, answer);
                    return true;
                }
            }

            // Portal warning/confirmation fallback. Security answers are checked first,
            // so this cannot accidentally replace a known security answer.
            if (Rs2Dialogue.clickOption(false, "Yes", "Enter", "Proceed", "Continue")) {
                action = "Confirming Stronghold travel";
                return true;
            }
            action = "Waiting for unrecognised dialogue option";
            return true;
        }

        if (Rs2Dialogue.hasContinue()) {
            action = "Continuing Stronghold dialogue";
            Rs2Dialogue.clickContinue();
            return true;
        }

        return false;
    }

    /**
     * Move toward a target that is known to be in the player's current reachable room/corridor.
     *
     * Generic movement helper for non-door targets. Door approaches use webWalkToKnownPoint()
     * instead, because v2.0.2 explicitly requires WebWalker to bring the player to the next door.
     * For other room-local targets WebWalker is only used when Rs2Walker.canReach(target)
     * is already true. This prevents the shortest-path transport/door resolver from being
     * asked to solve a closed Stronghold airlock. If the target is not locally reachable,
     * we keep control and fall back to a direct canvas/minimap click instead.
     */
    /**
     * WebWalk to a point that is known to be on the CURRENT side of the next closed door.
     *
     * Unlike the old generic maze routing, this method is never given a tile beyond the
     * door.  That means the WebWalker only has to solve normal floor movement inside the
     * current room/corridor.  Door interaction remains owned by this navigator.
     */
    private void webWalkToKnownPoint(WorldPoint target, int distance, String mode) {
        if (target == null) return;
        long now = System.currentTimeMillis();
        if (now - lastMoveAt < MOVE_COOLDOWN_MS || Rs2Player.isMoving()) return;

        movementMode = mode;
        boolean moved = Rs2Walker.walkTo(target, Math.max(0, distance));
        if (moved) {
            lastMoveAt = now;
        }
    }

    private void moveKnown(WorldPoint target) {
        if (target == null) return;
        long now = System.currentTimeMillis();
        if (now - lastMoveAt < MOVE_COOLDOWN_MS || Rs2Player.isMoving()) return;

        boolean moved = false;
        if (useWebWalker && Rs2Walker.canReach(target)) {
            movementMode = "WebWalker (known segment)";
            moved = Rs2Walker.walkTo(target, 1);
        }

        if (!moved) {
            movementMode = useWebWalker
                    ? "Direct fallback (segment blocked)"
                    : "Direct room movement";
            moved = Rs2Walker.walkFastCanvas(target);
            if (!moved) moved = Rs2Walker.walkMiniMap(target);
        }

        if (moved) lastMoveAt = now;
    }

    /**
     * After the fixed door is open, make only the tiny crossing ourselves. As soon as the
     * destination room polygon is entered, the next tick maps that room to its NEXT door and
     * WebWalker takes over the approach again. The destination is deliberately
     * only one or two tiles through the exact door that we just opened, which avoids the
     * old behaviour where the generic walker could re-select a door/transport.
     */
    private void moveDoorCrossing(WorldPoint target) {
        if (target == null) return;
        long now = System.currentTimeMillis();
        if (now - lastMoveAt < MOVE_COOLDOWN_MS || Rs2Player.isMoving()) return;

        movementMode = "Direct fixed-door crossing";
        boolean moved = Rs2Walker.walkFastCanvas(target);
        if (!moved) moved = Rs2Walker.walkMiniMap(target);
        if (moved) lastMoveAt = now;
    }

    private void set(String stage, String action) {
        this.stage = stage;
        this.action = action;
    }

    private void fail(String message) {
        this.stage = "Navigation error";
        this.action = message;
        this.error = message;
    }

    String getStage() { return stage; }
    String getAction() { return action; }
    String getError() { return error; }
    String getMovementMode() { return movementMode; }

    String getCurrentZoneName() {
        StrongholdZones.PolygonZone zone = StrongholdZones.locate(Rs2Player.getWorldLocation());
        return zone == null ? "Outside configured room" : zone.getName();
    }

    private String format(WorldPoint point) {
        return point.getX() + "," + point.getY() + "," + point.getPlane();
    }

    private static final class DoorTransition {
        private final String name;
        private final StrongholdZones.PolygonZone from;
        private final StrongholdZones.PolygonZone to;
        private final WorldPoint approach;
        private final WorldPoint cross;
        private final int minX;
        private final int maxX;
        private final int minY;
        private final int maxY;

        private DoorTransition(String name,
                               StrongholdZones.PolygonZone from,
                               StrongholdZones.PolygonZone to,
                               WorldPoint approach,
                               WorldPoint cross,
                               int minX, int maxX, int minY, int maxY) {
            this.name = name;
            this.from = from;
            this.to = to;
            this.approach = approach;
            this.cross = cross;
            this.minX = minX;
            this.maxX = maxX;
            this.minY = minY;
            this.maxY = maxY;
        }

        private DoorTransition reverse(String reverseName) {
            return new DoorTransition(reverseName, to, from, cross, approach, minX, maxX, minY, maxY);
        }

        private boolean containsBoundary(WorldPoint point) {
            return point != null && point.getPlane() == 0
                    && point.getX() >= minX && point.getX() <= maxX
                    && point.getY() >= minY && point.getY() <= maxY;
        }
    }
}
