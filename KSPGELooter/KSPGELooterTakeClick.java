package net.runelite.client.plugins.microbot.KSPGELooter;

import java.awt.Polygon;
import java.awt.Rectangle;
import java.awt.event.KeyEvent;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilNotNull;

/** Click the loot tile directly, as in AIO Fighter's physical pickup path. */
final class KSPGELooterTakeClick
{
    private static final int UI_TIMEOUT_MS = 800;

    private KSPGELooterTakeClick() {}

    static boolean click(Rs2TileItemModel item)
    {
        LocalPoint local = item.getLocalLocation();
        if (local == null || !closeOpenMenu()) return false;

        Point tile = canvasPoint(local);
        if (tile == null)
        {
            Rs2Camera.turnTo(local);
            tile = sleepUntilNotNull(() -> canvasPoint(local), UI_TIMEOUT_MS);
        }
        if (tile == null || item.isDespawned()) return false;

        Microbot.getMouse().click(tile.getX(), tile.getY());
        return true;
    }

    private static Point canvasPoint(LocalPoint local)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Client client = Microbot.getClient();
            Polygon polygon = Perspective.getCanvasTilePoly(client, local);
            if (polygon == null) return null;

            Rectangle bounds = polygon.getBounds();
            if (bounds.isEmpty()) return null;

            int x = (int) Math.round(bounds.getCenterX());
            int y = (int) Math.round(bounds.getCenterY());
            Rectangle viewport = new Rectangle(client.getViewportXOffset(), client.getViewportYOffset(),
                    client.getViewportWidth(), client.getViewportHeight());
            return viewport.contains(x, y) ? new Point(x, y) : null;
        }).orElse(null);
    }

    private static boolean isMenuOpen()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> Microbot.getClient().isMenuOpen())
                .orElse(false);
    }

    private static boolean closeOpenMenu()
    {
        if (!isMenuOpen()) return true;
        Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        return sleepUntil(() -> !isMenuOpen(), UI_TIMEOUT_MS);
    }
}
