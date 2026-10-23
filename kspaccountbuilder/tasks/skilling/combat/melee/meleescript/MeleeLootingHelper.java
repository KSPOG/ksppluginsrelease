package net.runelite.client.plugins.microbot.kspaccountbuilder.tasks.skilling.combat.melee.meleescript;

import java.awt.Polygon;
import java.awt.Rectangle;
import net.runelite.api.Perspective;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import org.slf4j.Logger;

final class MeleeLootingHelper
{
    private MeleeLootingHelper() {}

    static boolean take(Rs2TileItemModel loot, Logger log, boolean debugLogging)
    {
        if (loot == null) return false;

        try
        {
            if (loot.pickup()) return true;
        }
        catch (Exception ex)
        {
            if (debugLogging && log != null)
            {
                log.info("Melee loot targeted pickup failed | item={} id={} world={} error={}:{}",
                        loot.getName(), loot.getId(), loot.getWorldLocation(),
                        ex.getClass().getSimpleName(), ex.getMessage());
            }
        }

        LocalPoint localPoint = loot.getLocalLocation();
        if (localPoint == null) return false;

        Polygon tileBounds = Perspective.getCanvasTilePoly(Microbot.getClient(), localPoint);
        if (tileBounds == null) return false;

        Rectangle bounds = tileBounds.getBounds();
        if (bounds == null || bounds.width <= 0 || bounds.height <= 0) return false;

        int clickX = clamp((int) Math.round(bounds.getCenterX()), 1, Microbot.getClient().getCanvasWidth() - 2);
        int clickY = clamp((int) Math.round(bounds.getCenterY()), 1, Microbot.getClient().getCanvasHeight() - 2);
        Microbot.getMouse().click(clickX, clickY);
        return true;
    }

    private static int clamp(int value, int min, int max)
    {
        return Math.max(min, Math.min(max, value));
    }
}
