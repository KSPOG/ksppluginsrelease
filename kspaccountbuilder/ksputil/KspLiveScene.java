package net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil;

import net.runelite.api.GameObject;
import net.runelite.api.ObjectComposition;
import net.runelite.api.Tile;
import net.runelite.api.TileObject;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;

/**
 * Reads currently loaded tiles directly, independently of the Microbot
 * tile-object cache. Interactions must be invoked after the scan, not while
 * holding the client-thread scan, to avoid nested client thread calls.
 */
public final class KspLiveScene
{
    private KspLiveScene() { }

    /**
     * Inspect live world-view NPCs rather than the NPC cache. Portal NPCs may
     * have markup in their names, so match the authoritative NPC IDs.
     */
    public static int nearestNpcId(int[] npcIds, WorldPoint origin, int maxDistance)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (Microbot.getClient() == null || Microbot.getClient().getTopLevelWorldView() == null
                    || Microbot.getClient().getTopLevelWorldView().npcs() == null) return -1;
            int closestId = -1;
            int closestDistance = Integer.MAX_VALUE;
            for (net.runelite.api.NPC npc : Microbot.getClient().getTopLevelWorldView().npcs())
            {
                if (npc == null || npc.getWorldLocation() == null) continue;
                boolean matched = false;
                for (int id : npcIds)
                {
                    if (npc.getId() == id) { matched = true; break; }
                }
                if (!matched) continue;
                WorldPoint location = npc.getWorldLocation();
                if (origin != null && location.getPlane() != origin.getPlane()) continue;
                int distance = origin == null ? 0 : origin.distanceTo(location);
                if (distance > maxDistance || distance >= closestDistance) continue;
                closestId = npc.getId();
                closestDistance = distance;
            }
            return closestId;
        }).orElse(-1);
    }

    public static TileObject nearestGameObject(int objectId, WorldPoint origin, int maxDistance)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (Microbot.getClient() == null || Microbot.getClient().getScene() == null) return null;
            Tile[][][] tiles = Microbot.getClient().getScene().getTiles();
            if (tiles == null) return null;
            TileObject closest = null;
            int bestDistance = Integer.MAX_VALUE;
            for (Tile[][] plane : tiles)
            {
                if (plane == null) continue;
                for (Tile[] row : plane)
                {
                    if (row == null) continue;
                    for (Tile tile : row)
                    {
                        if (tile == null || tile.getGameObjects() == null) continue;
                        for (GameObject candidate : tile.getGameObjects())
                        {
                            if (candidate == null || candidate.getId() != objectId) continue;
                            WorldPoint position = candidate.getWorldLocation();
                            if (position == null || origin != null && position.getPlane() != origin.getPlane()) continue;
                            int distance = origin == null ? 0 : origin.distanceTo(position);
                            if (distance > maxDistance || distance >= bestDistance) continue;
                            closest = candidate;
                            bestDistance = distance;
                        }
                    }
                }
            }
            return closest;
        }).orElse(null);
    }

    public static TileObject nearestGameObject(String objectName, WorldPoint origin, int maxDistance)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (Microbot.getClient() == null || Microbot.getClient().getScene() == null) return null;
            Tile[][][] tiles = Microbot.getClient().getScene().getTiles();
            if (tiles == null) return null;
            TileObject closest = null;
            int bestDistance = Integer.MAX_VALUE;
            for (Tile[][] plane : tiles)
            {
                if (plane == null) continue;
                for (Tile[] row : plane)
                {
                    if (row == null) continue;
                    for (Tile tile : row)
                    {
                        if (tile == null || tile.getGameObjects() == null) continue;
                        for (GameObject candidate : tile.getGameObjects())
                        {
                            if (candidate == null) continue;
                            WorldPoint position = candidate.getWorldLocation();
                            if (position == null || origin != null && position.getPlane() != origin.getPlane()) continue;
                            int distance = origin == null ? 0 : origin.distanceTo(position);
                            if (distance > maxDistance || distance >= bestDistance) continue;
                            ObjectComposition definition = Microbot.getClient().getObjectDefinition(candidate.getId());
                            if (definition == null || !objectName.equalsIgnoreCase(definition.getName())) continue;
                            closest = candidate;
                            bestDistance = distance;
                        }
                    }
                }
            }
            return closest;
        }).orElse(null);
    }

    public static boolean interact(String objectName, WorldPoint origin, int maxDistance, String action)
    {
        TileObject object = nearestGameObject(objectName, origin, maxDistance);
        if (object == null) return false;
        try { return Rs2GameObject.interact(object, action); }
        catch (RuntimeException exception) { return false; }
    }

    /** Safe fallback for cache misses; return false and let the task retry. */
    public static boolean interact(int objectId, WorldPoint origin, int maxDistance, String action)
    {
        TileObject object = nearestGameObject(objectId, origin, maxDistance);
        if (object == null) return false;
        try { return Rs2GameObject.interact(object, action); }
        catch (RuntimeException exception) { return false; }
    }
}
