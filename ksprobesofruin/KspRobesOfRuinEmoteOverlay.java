package net.runelite.client.plugins.microbot.ksprobesofruin;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.ScriptID;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.plugins.cluescrolls.clues.emote.Emote;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;

public class KspRobesOfRuinEmoteOverlay extends Overlay
{
    private static final Color BORDER = new Color(0, 255, 120, 245);
    private static final Color FILL = new Color(0, 255, 120, 65);

    private final Client client;
    private final KspRobesOfRuinPlugin plugin;
    private final KspRobesOfRuinConfig config;
    private int lastScrolledIndex = -1;

    @Inject
    KspRobesOfRuinEmoteOverlay(Client client, KspRobesOfRuinPlugin plugin, KspRobesOfRuinConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_WIDGETS);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.highlightEmotes())
        {
            lastScrolledIndex = -1;
            return null;
        }

        Emote expected = plugin.getExpectedEmote();
        if (expected == null)
        {
            lastScrolledIndex = -1;
            return null;
        }

        Widget container = client.getWidget(InterfaceID.Emote.CONTENTS);
        Widget window = client.getWidget(InterfaceID.Emote.UNIVERSE);
        if (container == null || window == null || container.isHidden() || window.isHidden())
        {
            return null;
        }

        Widget target = null;
        Widget[] children = container.getDynamicChildren();
        if (children != null)
        {
            for (Widget widget : children)
            {
                if (widget != null && widget.getSpriteId() == expected.getSpriteId())
                {
                    target = widget;
                    break;
                }
            }
        }

        if (target == null)
        {
            return null;
        }

        int currentIndex = plugin.getEmoteIndex();
        if (lastScrolledIndex != currentIndex)
        {
            scrollTo(target);
            lastScrolledIndex = currentIndex;
        }

        Rectangle bounds = target.getBounds();
        Rectangle windowBounds = window.getBounds();
        if (bounds == null || windowBounds == null)
        {
            return null;
        }

        Rectangle visible = bounds.intersection(windowBounds);
        if (visible.isEmpty())
        {
            return null;
        }

        graphics.setColor(FILL);
        graphics.fill(visible);
        graphics.setColor(BORDER);
        graphics.setStroke(new BasicStroke(3f));
        graphics.draw(visible);

        String label = "NEXT " + (currentIndex + 1) + "/17";
        graphics.drawString(label, visible.x, Math.max(windowBounds.y + 12, visible.y - 3));
        return null;
    }

    private void scrollTo(Widget widget)
    {
        Widget parent = client.getWidget(InterfaceID.Emote.SCROLLABLE);
        if (parent == null || widget == null)
        {
            return;
        }

        int centerY = widget.getRelativeY() + widget.getHeight() / 2;
        int newScroll = Math.max(0, Math.min(parent.getScrollHeight(),
                centerY - parent.getHeight() / 2));

        client.runScript(
                ScriptID.UPDATE_SCROLLBAR,
                InterfaceID.Emote.SCROLLBAR,
                InterfaceID.Emote.SCROLLABLE,
                newScroll
        );
    }
}
