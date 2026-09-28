package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.fishing.helper;

import net.runelite.api.NPCComposition;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.kspaccountbuilder.KspWalkerGuard;
import net.runelite.client.plugins.microbot.util.dialogues.Rs2Dialogue;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.List;

public final class KaramjaTravelHelper
{
    private static final List<String> PORT_SARIM_TRAVEL_NPCS = List.of(
            "Seaman Thresnor",
            "Seaman Lorris",
            "Captain Tobias"
    );
    private static final List<String> CUSTOMS_OFFICER_NPCS = List.of("Customs Officer");
    private static final List<String> TRAVEL_ACTIONS = List.of(
            "Musa Point",
            "Port Sarim",
            "Pay-fare",
            "Travel",
            "Talk-to"
    );

    // Force the script to walk to the Port Sarim customs ship first.
    // Do not path directly to Musa Point from the mainland, otherwise WebWalker may choose
    // unrelated transports such as the Rimmington ship network.
    private static final WorldPoint PORT_SARIM_TRAVEL_POINT = new WorldPoint(3029, 3217, 0);
    private static final WorldPoint PORT_SARIM_DEPOSIT_POINT = new WorldPoint(3047, 3236, 0);
    private static final WorldPoint KARAMJA_CUSTOMS_POINT = new WorldPoint(2954, 3148, 0);
    private static final WorldPoint KARAMJA_FISHING_POINT = new WorldPoint(2924, 3177, 0);

    private static final int PORT_SARIM_CUSTOMS_MIN_X = 3039;
    private static final int PORT_SARIM_CUSTOMS_MAX_X = 3052;
    private static final int PORT_SARIM_CUSTOMS_MIN_Y = 3234;
    private static final int PORT_SARIM_CUSTOMS_MAX_Y = 3237;

    private static final int KARAMJA_DOCK_MIN_X = 2951;
    private static final int KARAMJA_DOCK_MAX_X = 2957;
    private static final int KARAMJA_DOCK_MIN_Y = 3144;
    private static final int KARAMJA_DOCK_MAX_Y = 3151;

    private static final int NPC_REACH_DISTANCE = 3;
    private static final int FISHING_AREA_DISTANCE = 5;
    private static final int DEPOSIT_AREA_DISTANCE = 6;
    private static final long WALK_REFIRE_COOLDOWN_MS = 3_000L;
    private static final long TRAVEL_TRANSITION_TIMEOUT_MS = 15_000L;

    private static final String WALK_KEY_TO_PORT_SARIM_TRAVEL = "ksp_fishing_karamja_port_sarim_travel";
    private static final String WALK_KEY_TO_PORT_SARIM_DEPOSIT = "ksp_fishing_karamja_port_sarim_deposit";
    private static final String WALK_KEY_TO_KARAMJA_CUSTOMS = "ksp_fishing_karamja_customs";
    private static final String WALK_KEY_TO_KARAMJA_FISHING = "ksp_fishing_karamja_fishing";
    private static final String WALK_KEY_TO_PORT_SARIM_NPC = "ksp_fishing_karamja_port_sarim_npc";
    private static final String WALK_KEY_TO_CUSTOMS_NPC = "ksp_fishing_karamja_customs_npc";

    private static volatile TravelDirection pendingTravelDirection;
    private static volatile long pendingTravelUntilMs;

    private KaramjaTravelHelper()
    {
    }

    public static boolean travelToKaramjaFishingSpot()
    {
        if (handleTravelDialogue(TravelDirection.TO_KARAMJA))
        {
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null)
        {
            return false;
        }

        if (isTravelTransitionPending(TravelDirection.TO_KARAMJA, playerLocation))
        {
            return false;
        }

        if (isNearKaramjaFishingSpot(playerLocation))
        {
            clearWalkerState();
            return true;
        }

        if (isInKaramjaArea(playerLocation))
        {
            webWalkToPoint(
                    WALK_KEY_TO_KARAMJA_FISHING,
                    KARAMJA_FISHING_POINT,
                    FISHING_AREA_DISTANCE);
            return false;
        }

        if (!isNearPortSarimTravelPoint(playerLocation))
        {
            // Important: the only mainland target before Musa Point must be the Port Sarim boat.
            // This prevents the global pathfinder from selecting Rimmington/charter-style routes.
            KspWalkerGuard.clear(WALK_KEY_TO_KARAMJA_FISHING);
            webWalkToPoint(
                    WALK_KEY_TO_PORT_SARIM_TRAVEL,
                    PORT_SARIM_TRAVEL_POINT,
                    NPC_REACH_DISTANCE);
            return false;
        }

        interactWithNearestNpc(
                PORT_SARIM_TRAVEL_NPCS,
                WALK_KEY_TO_PORT_SARIM_NPC,
                TravelDirection.TO_KARAMJA);
        return false;
    }

    public static boolean returnToPortSarimDepositPoint()
    {
        if (handleTravelDialogue(TravelDirection.TO_PORT_SARIM))
        {
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        if (playerLocation == null)
        {
            return false;
        }

        if (isTravelTransitionPending(TravelDirection.TO_PORT_SARIM, playerLocation))
        {
            return false;
        }

        if (!isInKaramjaArea(playerLocation)
                && playerLocation.distanceTo(PORT_SARIM_DEPOSIT_POINT) <= DEPOSIT_AREA_DISTANCE)
        {
            clearWalkerState();
            return true;
        }

        if (isInKaramjaDockArea(playerLocation))
        {
            interactWithNearestNpc(
                    CUSTOMS_OFFICER_NPCS,
                    WALK_KEY_TO_CUSTOMS_NPC,
                    TravelDirection.TO_PORT_SARIM);
            return false;
        }

        if (isInKaramjaArea(playerLocation))
        {
            webWalkToPoint(
                    WALK_KEY_TO_KARAMJA_CUSTOMS,
                    KARAMJA_CUSTOMS_POINT,
                    NPC_REACH_DISTANCE);
            return false;
        }

        webWalkToPoint(
                WALK_KEY_TO_PORT_SARIM_DEPOSIT,
                PORT_SARIM_DEPOSIT_POINT,
                DEPOSIT_AREA_DISTANCE);
        return false;
    }

    public static boolean isNearKaramjaFishingSpot()
    {
        return isNearKaramjaFishingSpot(Rs2Player.getWorldLocation());
    }

    public static boolean isNearKaramjaFishingSpot(WorldPoint location)
    {
        return location != null
                && location.getPlane() == KARAMJA_FISHING_POINT.getPlane()
                && location.distanceTo(KARAMJA_FISHING_POINT) <= FISHING_AREA_DISTANCE;
    }

    public static boolean isInKaramjaDockArea()
    {
        return isInKaramjaDockArea(Rs2Player.getWorldLocation());
    }

    public static boolean isInKaramjaDockArea(WorldPoint location)
    {
        return isWithinBounds(
                location,
                KARAMJA_DOCK_MIN_X,
                KARAMJA_DOCK_MAX_X,
                KARAMJA_DOCK_MIN_Y,
                KARAMJA_DOCK_MAX_Y
        );
    }

    public static boolean isNearPortSarimTravelPoint()
    {
        return isNearPortSarimTravelPoint(Rs2Player.getWorldLocation());
    }

    public static boolean isNearPortSarimTravelPoint(WorldPoint location)
    {
        return location != null
                && location.getPlane() == PORT_SARIM_TRAVEL_POINT.getPlane()
                && location.distanceTo2D(PORT_SARIM_TRAVEL_POINT) <= NPC_REACH_DISTANCE;
    }

    public static boolean isInPortSarimCustomsArea()
    {
        return isInPortSarimCustomsArea(Rs2Player.getWorldLocation());
    }

    public static boolean isInPortSarimCustomsArea(WorldPoint location)
    {
        return isWithinBounds(
                location,
                PORT_SARIM_CUSTOMS_MIN_X,
                PORT_SARIM_CUSTOMS_MAX_X,
                PORT_SARIM_CUSTOMS_MIN_Y,
                PORT_SARIM_CUSTOMS_MAX_Y
        );
    }

    public static boolean isInKaramjaArea()
    {
        return isInKaramjaArea(Rs2Player.getWorldLocation());
    }

    public static boolean isInKaramjaArea(WorldPoint location)
    {
        return location != null
                && location.getPlane() == 0
                && location.getX() < 3000
                && location.getY() < 3200;
    }

    public static WorldPoint getKaramjaFishingPoint()
    {
        return KARAMJA_FISHING_POINT;
    }

    public static WorldPoint getKaramjaCustomsPoint()
    {
        return KARAMJA_CUSTOMS_POINT;
    }

    public static WorldPoint getPortSarimTravelPoint()
    {
        return PORT_SARIM_TRAVEL_POINT;
    }

    public static WorldPoint getPortSarimDepositPoint()
    {
        return PORT_SARIM_DEPOSIT_POINT;
    }

    public static void clearWalkerState()
    {
        clearPendingTravel();
        KspWalkerGuard.clear(WALK_KEY_TO_PORT_SARIM_TRAVEL);
        KspWalkerGuard.clear(WALK_KEY_TO_PORT_SARIM_DEPOSIT);
        KspWalkerGuard.clear(WALK_KEY_TO_KARAMJA_CUSTOMS);
        KspWalkerGuard.clear(WALK_KEY_TO_KARAMJA_FISHING);
        KspWalkerGuard.clear(WALK_KEY_TO_PORT_SARIM_NPC);
        KspWalkerGuard.clear(WALK_KEY_TO_CUSTOMS_NPC);
    }

    private static boolean handleTravelDialogue(TravelDirection direction)
    {
        if (Rs2Dialogue.hasContinue())
        {
            Rs2Dialogue.clickContinue();
            return true;
        }

        if (!Rs2Dialogue.hasSelectAnOption())
        {
            return false;
        }

        boolean clicked = Rs2Dialogue.clickOption(
                "Yes please",
                "Yes",
                "Musa Point",
                "Karamja",
                "Port Sarim",
                "pay",
                "travel",
                "sail"
        );
        if (clicked)
        {
            markTravelPending(direction);
        }
        return clicked;
    }

    private static void walkToPoint(String key, WorldPoint target, int arriveDistance)
    {
        KspWalkerGuard.walkFastCanvasToPoint(key, target, arriveDistance, WALK_REFIRE_COOLDOWN_MS);
    }

    private static void webWalkToPoint(String key, WorldPoint target, int arriveDistance)
    {
        KspWalkerGuard.walkToPoint(key, target, arriveDistance, WALK_REFIRE_COOLDOWN_MS);
    }

    private static boolean interactWithNearestNpc(
            List<String> npcNames,
            String walkKey,
            TravelDirection direction)
    {
        Rs2NpcModel npc = findNearestNpc(npcNames);
        if (npc == null)
        {
            return false;
        }

        WorldPoint playerLocation = Rs2Player.getWorldLocation();
        WorldPoint npcLocation = npc.getWorldLocation();
        if (playerLocation == null || npcLocation == null)
        {
            return false;
        }

        if (playerLocation.distanceTo(npcLocation) > NPC_REACH_DISTANCE)
        {
            walkToPoint(walkKey, npcLocation, NPC_REACH_DISTANCE);
            return false;
        }

        String action = getAvailableAction(npc, TRAVEL_ACTIONS);
        if (action.isEmpty())
        {
            return false;
        }

        boolean clicked = npc.click(action);
        if (clicked)
        {
            KspWalkerGuard.clear(walkKey);
            if (!"Talk-to".equalsIgnoreCase(action))
            {
                markTravelPending(direction);
            }
        }
        return clicked;
    }

    private static boolean isTravelTransitionPending(TravelDirection direction, WorldPoint playerLocation)
    {
        TravelDirection pendingDirection = pendingTravelDirection;
        if (pendingDirection == null)
        {
            return false;
        }

        boolean arrived = pendingDirection == TravelDirection.TO_KARAMJA
                ? isInKaramjaArea(playerLocation)
                : !isInKaramjaArea(playerLocation);
        if (arrived || System.currentTimeMillis() >= pendingTravelUntilMs)
        {
            clearPendingTravel();
            return false;
        }

        return pendingDirection == direction;
    }

    private static void markTravelPending(TravelDirection direction)
    {
        pendingTravelDirection = direction;
        pendingTravelUntilMs = System.currentTimeMillis() + TRAVEL_TRANSITION_TIMEOUT_MS;
    }

    private static void clearPendingTravel()
    {
        pendingTravelDirection = null;
        pendingTravelUntilMs = 0L;
    }

    private static Rs2NpcModel findNearestNpc(List<String> npcNames)
    {
        if (npcNames == null || npcNames.isEmpty())
        {
            return null;
        }

        String[] names = npcNames.toArray(new String[0]);
        return Microbot.getRs2NpcCache().query()
                .fromWorldView()
                .withNames(names)
                .nearestOnClientThread();
    }

    private static String getAvailableAction(Rs2NpcModel npc, List<String> preferredActions)
    {
        if (npc == null || preferredActions == null || preferredActions.isEmpty())
        {
            return "";
        }

        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null)
            {
                return "";
            }

            NPCComposition composition = Microbot.getClient().getNpcDefinition(npc.getId());
            if (composition == null || composition.getActions() == null)
            {
                return "";
            }

            for (String preferredAction : preferredActions)
            {
                for (String action : composition.getActions())
                {
                    if (action != null && action.equalsIgnoreCase(preferredAction))
                    {
                        return action;
                    }
                }
            }

            return "";
        }).orElse("");
    }

    private static boolean isWithinBounds(
            WorldPoint location,
            int minX,
            int maxX,
            int minY,
            int maxY)
    {
        return location != null
                && location.getPlane() == 0
                && location.getX() >= minX
                && location.getX() <= maxX
                && location.getY() >= minY
                && location.getY() <= maxY;
    }

    private enum TravelDirection
    {
        TO_KARAMJA,
        TO_PORT_SARIM
    }
}
