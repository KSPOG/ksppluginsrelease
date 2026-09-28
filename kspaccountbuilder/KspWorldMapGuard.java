package net.runelite.client.plugins.microbot.kspaccountbuilder;

import java.awt.event.KeyEvent;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.keyboard.Rs2Keyboard;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

public final class KspWorldMapGuard
{
    private static final int WORLD_MAP_KEY_WIDGET_ID = 38_993_938;
    private static final long CLOSE_COOLDOWN_MS = 1_500L;
    private static long lastCloseAtMs;

    private KspWorldMapGuard()
    {
    }

    public static synchronized boolean closeIfOpen()
    {
        if (!Microbot.isLoggedIn() || !isOpen())
        {
            return false;
        }

        long now = System.currentTimeMillis();
        if (now - lastCloseAtMs >= CLOSE_COOLDOWN_MS)
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
        if (keyWidget != null
                && keyWidget.getText() != null
                && keyWidget.getText().contains("Key"))
        {
            return true;
        }

        return Rs2Widget.findWidget("Game features") != null;
    }
}
