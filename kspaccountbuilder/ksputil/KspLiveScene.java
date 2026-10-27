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

    /** Find the nearest portal across all known object variants in one scene pass. */
    public static TileObject nearestGameObject(int[] objectIds, WorldPoint origin, int maxDistance)
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
                            boolean matchingId = false;
                            for (int id : objectIds)
                            {
                                if (candidate.getId() == id) { matchingId = true; break; }
                            }
                            if (!matchingId) continue;
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

    /**
     * Locate a tile object regardless of its scene layer. Rune Essence portal
     * variants can be represented by a game, wall, decorative or ground object.
     * A transformed object's visible ID may also be one of its impostor IDs.
     *
     * Scan on the client thread, but interact only after returning the result.
     * Keep nearestGameObject() unchanged for callers requiring GameObjects.
     */
    public static TileObject nearestTileObject(int[] objectIds, WorldPoint origin, int maxDistance)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (Microbot.getClient() == null) return null;
            net.runelite.api.Player player = Microbot.getClient().getLocalPlayer();
            net.runelite.api.WorldView view = player == null ? null : player.getWorldView();
            net.runelite.api.Scene scene = view == null ? Microbot.getClient().getScene() : view.getScene();
            if (scene == null) return null;
            Tile[][][] tiles = scene.getTiles();
            if (tiles == null) return null;

            // In a mapped/instanced area, TileObject.getWorldLocation() can
            // use different coordinates than Rs2Player.getWorldLocation().
            // LocalPoint positions in the SAME WorldView remain comparable.
            net.runelite.api.coords.LocalPoint playerLocal =
                    player == null ? null : player.getLocalLocation();
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
                        if (tile == null) continue;
                        for (int layer = 0; layer < 3; layer++)
                        {
                            TileObject candidate = layer == 0 ? tile.getWallObject()
                                    : layer == 1 ? tile.getDecorativeObject()
                                    : tile.getGroundObject();
                            if (candidate == null
                                    || !matchesObjectOrMorph(candidate.getId(), objectIds)) continue;
                            int distance = distanceToPlayer(candidate, origin, playerLocal, view);
                            if (distance < 0 || distance > maxDistance || distance >= bestDistance) continue;
                            closest = candidate;
                            bestDistance = distance;
                        }

                        GameObject[] objects = tile.getGameObjects();
                        if (objects == null) continue;
                        for (GameObject candidate : objects)
                        {
                            if (candidate == null
                                    || !matchesObjectOrMorph(candidate.getId(), objectIds)) continue;
                            int distance = distanceToPlayer(candidate, origin, playerLocal, view);
                            if (distance < 0 || distance > maxDistance || distance >= bestDistance) continue;
                            closest = candidate;
                            bestDistance = distance;
                        }
                    }
                }
            }
            return closest;
        }).orElse(null);
    }

    /**
     * Tile distance in the current rendered scene; never mix template,
     * instance and player world coordinates. Fall back to world locations
     * only when the client has no usable local position.
     */
    private static int distanceToPlayer(TileObject candidate, WorldPoint origin,
                                        net.runelite.api.coords.LocalPoint playerLocal,
                                        net.runelite.api.WorldView playerView)
    {
        if (candidate == null) return -1;
        if (playerView != null && candidate.getPlane() != playerView.getPlane()) return -1;
        net.runelite.api.coords.LocalPoint objectLocal = candidate.getLocalLocation();
        if (playerLocal != null && objectLocal != null && playerView != null
                && objectLocal.getWorldView() == playerLocal.getWorldView())
        {
            int dx = Math.abs(objectLocal.getX() - playerLocal.getX());
            int dy = Math.abs(objectLocal.getY() - playerLocal.getY());
            return Math.max(dx, dy) / 128;
        }
        WorldPoint objectWorld = candidate.getWorldLocation();
        if (objectWorld == null) return -1;
        if (origin != null && origin.getPlane() != objectWorld.getPlane()) return -1;
        return origin == null ? 0 : origin.distanceTo(objectWorld);
    }

    private static boolean matchesObjectOrMorph(int objectId, int[] expectedIds)
    {
        for (int expected : expectedIds)
            if (objectId == expected) return true;
        // Rune Essence portal appearances are selected by varbit-backed morphs.
        // Their actual scene ID can be the untransformed base object ID.
        ObjectComposition definition = Microbot.getClient().getObjectDefinition(objectId);
        if (definition == null || definition.getImpostorIds() == null) return false;
        for (int morphId : definition.getImpostorIds())
            for (int expected : expectedIds)
                if (morphId == expected) return true;
        return false;
    }

    /**
     * Return the active varbit-selected object ID when the scene object is a
     * multiloc. The visible rune-essence portal can be 34777 even when its
     * underlying scene object uses a different base ID.
     */
    public static int resolvedObjectId(TileObject object)
    {
        if (object == null) return -1;
        int sceneId = object.getId();
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (Microbot.getClient() == null) return sceneId;
            ObjectComposition definition = Microbot.getClient().getObjectDefinition(sceneId);
            if (definition == null || definition.getImpostorIds() == null) return sceneId;
            ObjectComposition active = definition.getImpostor();
            return active == null ? sceneId : active.getId();
        }).orElse(sceneId);
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

    /**
     * Look up currently rendered tile objects by their object definition name,
     * including wall, decorative and ground objects. The older name overload
     * only examined GameObject[] and could miss bank/deposit-box scene layers.
     */
    public static TileObject nearestTileObject(String objectName, WorldPoint origin, int maxDistance)
    {
        if (objectName == null || objectName.isEmpty()) return null;
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (Microbot.getClient() == null) return null;
            net.runelite.api.Player player = Microbot.getClient().getLocalPlayer();
            net.runelite.api.WorldView view = player == null ? null : player.getWorldView();
            net.runelite.api.Scene scene = view == null ? Microbot.getClient().getScene() : view.getScene();
            if (scene == null || scene.getTiles() == null) return null;
            net.runelite.api.coords.LocalPoint local = player == null ? null : player.getLocalLocation();
            TileObject nearest = null;
            int nearestDistance = Integer.MAX_VALUE;
            for (Tile[][] plane : scene.getTiles())
            {
                if (plane == null) continue;
                for (Tile[] row : plane)
                {
                    if (row == null) continue;
                    for (Tile tile : row)
                    {
                        if (tile == null) continue;
                        TileObject[] fixed = {tile.getWallObject(), tile.getDecorativeObject(), tile.getGroundObject()};
                        for (TileObject object : fixed)
                        {
                            if (object == null || !hasObjectName(object.getId(), objectName)) continue;
                            int distance = distanceToPlayer(object, origin, local, view);
                            if (distance < 0 || distance > maxDistance || distance >= nearestDistance) continue;
                            nearest = object;
                            nearestDistance = distance;
                        }
                        GameObject[] objects = tile.getGameObjects();
                        if (objects == null) continue;
                        for (GameObject object : objects)
                        {
                            if (object == null || !hasObjectName(object.getId(), objectName)) continue;
                            int distance = distanceToPlayer(object, origin, local, view);
                            if (distance < 0 || distance > maxDistance || distance >= nearestDistance) continue;
                            nearest = object;
                            nearestDistance = distance;
                        }
                    }
                }
            }
            return nearest;
        }).orElse(null);
    }

    private static boolean hasObjectName(int id, String expected)
    {
        ObjectComposition definition = Microbot.getClient().getObjectDefinition(id);
        if (definition == null) return false;
        ObjectComposition active = definition.getImpostorIds() == null ? definition : definition.getImpostor();
        return (active != null && expected.equalsIgnoreCase(active.getName()))
                || expected.equalsIgnoreCase(definition.getName());
    }

    /**
     * Live-scene rock lookup for Account Builder Mining. Match known rock IDs
     * first; unknown variants may match their visible ore name. Only return
     * objects inside the requested mining area with a real Mine action.
     */
    public static TileObject nearestMineableRock(int[] allowedIds, String oreName,
                                                  net.runelite.api.coords.WorldArea area,
                                                  WorldPoint origin, int maxDistance)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (Microbot.getClient() == null) return null;
            net.runelite.api.Player player = Microbot.getClient().getLocalPlayer();
            net.runelite.api.WorldView view = player == null ? null : player.getWorldView();
            net.runelite.api.Scene scene = view == null ? Microbot.getClient().getScene() : view.getScene();
            if (scene == null || scene.getTiles() == null) return null;
            net.runelite.api.coords.LocalPoint local = player == null ? null : player.getLocalLocation();
            TileObject nearest = null;
            int nearestDistance = Integer.MAX_VALUE;
            for (Tile[][] plane : scene.getTiles())
            {
                if (plane == null) continue;
                for (Tile[] row : plane)
                {
                    if (row == null) continue;
                    for (Tile tile : row)
                    {
                        if (tile == null) continue;
                        TileObject[] fixed = {tile.getWallObject(), tile.getDecorativeObject(), tile.getGroundObject()};
                        for (TileObject object : fixed)
                        {
                            if (!matchesMineableRock(object, allowedIds, oreName, area)) continue;
                            int distance = distanceToPlayer(object, origin, local, view);
                            if (distance < 0 || distance > maxDistance || distance >= nearestDistance) continue;
                            nearest = object;
                            nearestDistance = distance;
                        }
                        GameObject[] objects = tile.getGameObjects();
                        if (objects == null) continue;
                        for (GameObject object : objects)
                        {
                            if (!matchesMineableRock(object, allowedIds, oreName, area)) continue;
                            int distance = distanceToPlayer(object, origin, local, view);
                            if (distance < 0 || distance > maxDistance || distance >= nearestDistance) continue;
                            nearest = object;
                            nearestDistance = distance;
                        }
                    }
                }
            }
            return nearest;
        }).orElse(null);
    }

    private static boolean matchesMineableRock(TileObject object, int[] ids, String oreName,
                                                 net.runelite.api.coords.WorldArea area)
    {
        if (object == null || object.getWorldLocation() == null
                || area != null && !area.contains(object.getWorldLocation())) return false;
        ObjectComposition definition = Microbot.getClient().getObjectDefinition(object.getId());
        if (definition == null) return false;
        ObjectComposition active = definition.getImpostorIds() == null ? definition : definition.getImpostor();
        if (active == null) active = definition;
        String name = active.getName();
        boolean matching = false;
        if (ids != null) for (int id : ids) if (id == object.getId() || id == active.getId()) { matching = true; break; }
        if (!matching && oreName != null && name != null)
        {
            String lower = name.toLowerCase(java.util.Locale.ENGLISH);
            matching = lower.contains(oreName.toLowerCase(java.util.Locale.ENGLISH)) && lower.contains("rock");
        }
        if (!matching) return false;
        String[] actions = active.getActions();
        if (actions == null) return false;
        for (String action : actions)
            if (action != null && "Mine".equalsIgnoreCase(net.runelite.client.plugins.microbot.util.misc.Rs2UiHelper.stripColTags(action)))
                return true;
        return false;
    }

    /**
     * Build a transient model from the live scene. This preserves existing
     * Rs2TileObjectModel action/reachability APIs without using an entity cache.
     * Predicates are evaluated on the client thread.
     */
    public static net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel nearestModel(
            WorldPoint origin, int maxDistance,
            java.util.function.Predicate<net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel> matches)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (Microbot.getClient() == null) return null;
            net.runelite.api.Player player = Microbot.getClient().getLocalPlayer();
            net.runelite.api.WorldView view = player == null ? null : player.getWorldView();
            net.runelite.api.Scene scene = view == null ? Microbot.getClient().getScene() : view.getScene();
            if (scene == null || scene.getTiles() == null) return null;
            net.runelite.api.coords.LocalPoint local = player == null ? null : player.getLocalLocation();
            net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel nearest = null;
            int bestDistance = Integer.MAX_VALUE;
            for (Tile[][] plane : scene.getTiles())
            {
                if (plane == null) continue;
                for (Tile[] row : plane)
                {
                    if (row == null) continue;
                    for (Tile tile : row)
                    {
                        if (tile == null) continue;
                        TileObject[] fixed = {tile.getWallObject(), tile.getDecorativeObject(), tile.getGroundObject()};
                        for (TileObject object : fixed)
                        {
                            if (object == null) continue;
                            int distance = distanceToPlayer(object, origin, local, view);
                            if (distance < 0 || distance > maxDistance || distance >= bestDistance) continue;
                            net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel model =
                                    new net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel(object);
                            if (matches != null && !matches.test(model)) continue;
                            nearest = model;
                            bestDistance = distance;
                        }
                        GameObject[] objects = tile.getGameObjects();
                        if (objects == null) continue;
                        for (GameObject object : objects)
                        {
                            if (object == null) continue;
                            int distance = distanceToPlayer(object, origin, local, view);
                            if (distance < 0 || distance > maxDistance || distance >= bestDistance) continue;
                            net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel model =
                                    new net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel(object);
                            if (matches != null && !matches.test(model)) continue;
                            nearest = model;
                            bestDistance = distance;
                        }
                    }
                }
            }
            return nearest;
        }).orElse(null);
    }

    /**
     * Live NPC model source for fishing, questing, combat and Tutorial Island.
     * Avoids the NPC cache, while retaining all Rs2NpcModel.click() behaviors.
     */
    public static net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel nearestNpcModel(
            WorldPoint origin, int maxDistance,
            java.util.function.Predicate<net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel> matches)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            if (Microbot.getClient() == null) return null;
            net.runelite.api.Player player = Microbot.getClient().getLocalPlayer();
            net.runelite.api.WorldView view = player == null ? null : player.getWorldView();
            if (view == null || view.npcs() == null) return null;
            net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel nearest = null;
            int bestDistance = Integer.MAX_VALUE;
            for (net.runelite.api.NPC npc : view.npcs())
            {
                if (npc == null || npc.getWorldLocation() == null) continue;
                if (origin != null && npc.getWorldLocation().getPlane() != origin.getPlane()) continue;
                int distance = origin == null ? 0 : origin.distanceTo(npc.getWorldLocation());
                if (distance > maxDistance || distance >= bestDistance) continue;
                net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel model =
                        new net.runelite.client.plugins.microbot.util.npc.Rs2NpcModel(npc);
                if (matches != null && !matches.test(model)) continue;
                nearest = model;
                bestDistance = distance;
            }
            return nearest;
        }).orElse(null);
    }

    public static boolean interact(String objectName, WorldPoint origin, int maxDistance, String action)
    {
        TileObject object = nearestTileObject(objectName, origin, maxDistance);
        if (object == null || !new net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel(object).hasAction(action)) return false;
        try { return Rs2GameObject.interact(object, action); }
        catch (RuntimeException exception) { return false; }
    }

    /** Safe fallback for cache misses; return false and let the task retry. */
    public static boolean interact(int objectId, WorldPoint origin, int maxDistance, String action)
    {
        TileObject object = nearestTileObject(new int[]{objectId}, origin, maxDistance);
        if (object == null) return false;
        try { return Rs2GameObject.interact(object, action); }
        catch (RuntimeException exception) { return false; }
    }
}
