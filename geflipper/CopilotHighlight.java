package net.runelite.client.plugins.microbot.geflipper;

import java.awt.Color;
import java.awt.Rectangle;
import net.runelite.api.NPC;
import net.runelite.api.widgets.Widget;

/**
 * Immutable snapshot of one active Flipping Copilot overlay.
 *
 * <p>Flipping Copilot does not publish a compile-time automation API. The bridge therefore
 * reads only the two overlay payloads that Copilot publishes through RuneLite's OverlayManager:
 * a widget plus relative bounds, or an NPC.</p>
 */
final class CopilotHighlight
{
    private final Widget widget;
    private final NPC npc;
    private final Rectangle relativeBounds;
    private final Color color;
    private final String overlayClassName;

    private CopilotHighlight(
        Widget widget,
        NPC npc,
        Rectangle relativeBounds,
        Color color,
        String overlayClassName)
    {
        this.widget = widget;
        this.npc = npc;
        this.relativeBounds = relativeBounds == null ? null : new Rectangle(relativeBounds);
        this.color = color;
        this.overlayClassName = overlayClassName;
    }

    static CopilotHighlight widget(
        Widget widget,
        Rectangle relativeBounds,
        Color color,
        String overlayClassName)
    {
        return new CopilotHighlight(widget, null, relativeBounds, color, overlayClassName);
    }

    static CopilotHighlight npc(NPC npc, String overlayClassName)
    {
        return new CopilotHighlight(null, npc, null, null, overlayClassName);
    }

    Widget getWidget()
    {
        return widget;
    }

    NPC getNpc()
    {
        return npc;
    }

    Rectangle getRelativeBounds()
    {
        return relativeBounds == null ? null : new Rectangle(relativeBounds);
    }

    Color getColor()
    {
        return color;
    }

    String getOverlayClassName()
    {
        return overlayClassName;
    }

    boolean isWidget()
    {
        return widget != null;
    }

    boolean isNpc()
    {
        return npc != null;
    }
}
