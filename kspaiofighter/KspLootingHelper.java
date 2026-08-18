package net.runelite.client.plugins.microbot.kspaiofighter;

import java.awt.Polygon;
import java.awt.Rectangle;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import org.slf4j.Logger;

public final class KspLootingHelper
{
    private KspLootingHelper()
    {
    }

    /**
     * Loot helper.
     *
     * Prefer the tile-item targeted pickup path first. A raw canvas click can hit doors,
     * NPCs, or other objects when the ground item is visually overlapped by camera angle.
     * The physical click remains as a fallback for builds where the targeted menu path is
     * temporarily unavailable.
     */
    public static boolean take(Rs2TileItemModel loot, Logger log, boolean debugLogging, String debugPrefix)
    {
        if (loot == null)
        {
            debug(log, debugLogging, debugPrefix, "loot pickup skipped | loot=null");
            return false;
        }

        if (tryTargetedPickup(loot, log, debugLogging, debugPrefix))
        {
            return true;
        }

        LocalPoint localPoint = loot.getLocalLocation();
        if (localPoint == null)
        {
            debug(log, debugLogging, debugPrefix, "loot click skipped | localPoint=null");
            return false;
        }

        Rectangle clickBounds = getClickBounds(localPoint);
        if (clickBounds == null || clickBounds.isEmpty())
        {
            debug(log, debugLogging, debugPrefix,
                    "loot click skipped | tile not drawable item={} id={} loc={}",
                    loot.getName(), loot.getId(), loot.getWorldLocation());
            return false;
        }

        int clickX = clamp((int) Math.round(clickBounds.getCenterX()), 1, Microbot.getClient().getCanvasWidth() - 2);
        int clickY = clamp((int) Math.round(clickBounds.getCenterY()), 1, Microbot.getClient().getCanvasHeight() - 2);
        Microbot.getMouse().click(clickX, clickY);

        debug(log, debugLogging, debugPrefix,
                "loot physical click | item={} id={} qty={} world={} scene={},{} click={},{}",
                loot.getName(), loot.getId(), loot.getQuantity(), loot.getWorldLocation(),
                localPoint.getSceneX(), localPoint.getSceneY(), clickX, clickY);
        return true;
    }

    private static boolean tryTargetedPickup(Rs2TileItemModel loot, Logger log, boolean debugLogging, String debugPrefix)
    {
        try
        {
            boolean pickedUp = loot.pickup();
            if (pickedUp)
            {
                debug(log, debugLogging, debugPrefix,
                        "loot targeted pickup | item={} id={} qty={} world={}",
                        loot.getName(), loot.getId(), loot.getQuantity(), loot.getWorldLocation());
                return true;
            }
        }
        catch (Exception ex)
        {
            debug(log, debugLogging, debugPrefix,
                    "loot targeted pickup failed | item={} id={} world={} error={}:{}",
                    loot.getName(), loot.getId(), loot.getWorldLocation(),
                    ex.getClass().getSimpleName(), ex.getMessage());
        }

        return false;
    }

    private static Rectangle getClickBounds(LocalPoint localPoint)
    {
        Polygon tileBounds = Perspective.getCanvasTilePoly(Microbot.getClient(), localPoint);
        if (tileBounds == null)
        {
            return null;
        }

        Rectangle bounds = tileBounds.getBounds();
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0)
        {
            return null;
        }
        return bounds;
    }

    private static int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }

    private static void debug(Logger log, boolean debugLogging, String debugPrefix, String message, Object... args)
    {
        if (debugLogging && log != null)
        {
            log.info("{} | " + message, prepend(debugPrefix, args));
        }
    }

    private static Object[] prepend(String first, Object[] rest)
    {
        Object[] values = new Object[rest.length + 1];
        values[0] = first;
        System.arraycopy(rest, 0, values, 1, rest.length);
        return values;
    }
}
