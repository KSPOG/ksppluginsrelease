package net.runelite.client.plugins.microbot.kspaccountbuilder;

import java.awt.event.KeyEvent;
import net.runelite.api.Player;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

/**
 * Shared Account Builder scene/world-map guard.
 *
 * <p>RuneLite can briefly expose a logged-in client while the local player or
 * its WorldPoint is unavailable during scene/world transitions. Scene based
 * helpers such as Rs2Bank/Rs2GameObject ultimately call LocalPoint.fromWorld,
 * which cannot accept a null WorldPoint. Treat that transient state exactly
 * like another blocking UI state and let the next scheduler tick retry.</p>
 */
public final class KspWorldMapGuard
{
    private static final int WORLD_MAP_KEY_WIDGET_ID = 38_993_938;
    private static final long CLOSE_RETRY_CEILING_MS = 1_500L;
    private static long lastCloseAtMs;

    private KspWorldMapGuard() {}

    /**
     * True only when scene-dependent Account Builder work is safe to query.
     * Player/world-location access is performed on RuneLite's client thread;
     * direct access from Account Builder scheduler threads can throw and must
     * never silently lock the builder in its pre-task guard.
     */
    public static boolean isSceneReady()
    {
        if (!Microbot.isLoggedIn()
                || Microbot.getClient() == null
                || Microbot.getClientThread() == null)
        {
            return false;
        }

        try
        {
            return Microbot.getClientThread()
                    .runOnClientThreadOptional(() ->
                    {
                        Player localPlayer = Microbot.getClient().getLocalPlayer();
                        return localPlayer != null && localPlayer.getWorldLocation() != null;
                    })
                    .orElse(false);
        }
        catch (RuntimeException ignored)
        {
            return false;
        }
    }

    /**
     * Returns true while Account Builder should yield this tick. Besides an
     * open world map this also covers the transient no-player/no-WorldPoint
     * scene state seen in the runtime logs.
     */
    public static synchronized boolean closeIfOpen()
    {
        if (!Microbot.isLoggedIn())
        {
            return false;
        }

        if (!isSceneReady())
        {
            return true;
        }

        if (!isOpen())
        {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastCloseAtMs >= CLOSE_RETRY_CEILING_MS)
        {
            lastCloseAtMs = now;
            Microbot.status = "Closing world map";
            Rs2Keyboard.keyPress(KeyEvent.VK_ESCAPE);
        }

        return true;
    }

    private static boolean isOpen()
    {
        Widget keyWidget = Rs2Widget.getWidget(WORLD_MAP_KEY_WIDGET_ID);
        return keyWidget != null
                && !keyWidget.isHidden()
                && keyWidget.getText() != null
                && keyWidget.getText().contains("Key");
    }
}
