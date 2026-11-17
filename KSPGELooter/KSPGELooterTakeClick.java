package net.runelite.client.plugins.microbot.KSPGELooter;

import java.awt.event.KeyEvent;
import net.runelite.api.Client;
import net.runelite.api.Menu;
import net.runelite.api.MenuAction;
import net.runelite.api.MenuEntry;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.api.tileitem.models.Rs2TileItemModel;
import net.runelite.client.plugins.microbot.util.camera.Rs2Camera;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;

import static net.runelite.client.plugins.microbot.util.Global.sleepUntil;
import static net.runelite.client.plugins.microbot.util.Global.sleepUntilNotNull;

/** Select the game's real Take menu entry using ordinary mouse clicks. */
final class KSPGELooterTakeClick
{
    private static final int MENU_TIMEOUT_MS = 800;
    private static final int MENU_ROW_HEIGHT = 15;
    private static final int FIRST_ROW_CENTER = 28;

    private KSPGELooterTakeClick() {}

    static boolean click(Rs2TileItemModel item)
    {
        LocalPoint local = item.getLocalLocation();
        if (local == null || !closeOpenMenu()) return false;

        int plane = item.getWorldLocation().getPlane();
        Point tile = canvasPoint(local, plane);
        if (tile == null)
        {
            Rs2Camera.turnTo(local);
            tile = sleepUntilNotNull(() -> canvasPoint(local, plane), MENU_TIMEOUT_MS);
            if (tile == null) return false;
        }

        Microbot.getMouse().click(tile, true);
        if (!sleepUntil(KSPGELooterTakeClick::isMenuOpen, MENU_TIMEOUT_MS)) return false;

        Point take = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> takeRow(Microbot.getClient(), local, item.getId()))
                .orElse(null);
        if (take == null || item.isDespawned())
        {
            closeOpenMenu();
            return false;
        }

        Microbot.getMouse().click(take);
        return sleepUntil(() -> !isMenuOpen(), MENU_TIMEOUT_MS);
    }

    private static Point canvasPoint(LocalPoint local, int plane)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
        {
            Client client = Microbot.getClient();
            Point point = Perspective.localToCanvas(client, local, plane);
            if (point == null) return null;

            int x = point.getX() - client.getViewportXOffset();
            int y = point.getY() - client.getViewportYOffset();
            return x >= 0 && x < client.getViewportWidth() && y >= 0 && y < client.getViewportHeight()
                    ? point : null;
        }).orElse(null);
    }

    private static Point takeRow(Client client, LocalPoint tile, int itemId)
    {
        if (!client.isMenuOpen()) return null;
        Menu menu = client.getMenu();
        MenuEntry[] entries = menu.getMenuEntries();
        int scroll = client.getMenuScroll();
        for (int index = entries.length - 1; index >= 0; index--)
        {
            MenuEntry entry = entries[index];
            if (entry == null || !"Take".equalsIgnoreCase(entry.getOption())
                    || entry.getIdentifier() != itemId || entry.getParam0() != tile.getSceneX()
                    || entry.getParam1() != tile.getSceneY() || entry.getWorldViewId() != tile.getWorldView()
                    || !isGroundItemOption(entry.getType())) continue;

            int row = entries.length - 1 - index - scroll;
            int yOffset = FIRST_ROW_CENTER + row * MENU_ROW_HEIGHT;
            if (row < 0 || yOffset + MENU_ROW_HEIGHT / 2 >= menu.getMenuHeight()) return null;
            int x = menu.getMenuX() + menu.getMenuWidth() / 2;
            int y = menu.getMenuY() + yOffset;
            return x >= 0 && x < client.getCanvasWidth() && y >= 0 && y < client.getCanvasHeight()
                    ? new Point(x, y) : null;
        }
        return null;
    }

    private static boolean isGroundItemOption(MenuAction action)
    {
        return action == MenuAction.GROUND_ITEM_FIRST_OPTION || action == MenuAction.GROUND_ITEM_SECOND_OPTION
                || action == MenuAction.GROUND_ITEM_THIRD_OPTION || action == MenuAction.GROUND_ITEM_FOURTH_OPTION
                || action == MenuAction.GROUND_ITEM_FIFTH_OPTION;
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
        return sleepUntil(() -> !isMenuOpen(), MENU_TIMEOUT_MS);
    }
}
