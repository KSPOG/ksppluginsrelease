package net.runelite.client.plugins.microbot.kspaccountbuilder;

import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import net.runelite.client.input.MouseAdapter;
import net.runelite.client.input.MouseWheelListener;

/**
 * RuneLite input arbiter. Consume clicks before the client's menu/camera/game
 * handlers, but ONLY inside the currently rendered Account Builder HUD.
 * Mouse movement is observed for tooltips and never blocked.
 */
final class KspClassicHudInput extends MouseAdapter implements MouseWheelListener
{
    private final KSPAccountBuilderOverlay overlay;

    KspClassicHudInput(KSPAccountBuilderOverlay overlay)
    {
        this.overlay = overlay;
    }

    private MouseEvent intercept(MouseEvent event)
    {
        overlay.hudPointer(event.getPoint());
        overlay.hudMouseEvent(event);
        return event;
    }

    @Override
    public MouseEvent mousePressed(MouseEvent event) { return intercept(event); }

    @Override
    public MouseEvent mouseReleased(MouseEvent event) { return intercept(event); }

    @Override
    public MouseEvent mouseClicked(MouseEvent event) { return intercept(event); }

    @Override
    public MouseEvent mouseDragged(MouseEvent event) { return intercept(event); }

    @Override
    public MouseEvent mouseMoved(MouseEvent event)
    {
        overlay.hudPointer(event.getPoint());
        return event;
    }

    @Override
    public MouseEvent mouseExited(MouseEvent event)
    {
        overlay.hudPointer(new java.awt.Point(-999, -999));
        return event;
    }

    @Override
    public MouseWheelEvent mouseWheelMoved(MouseWheelEvent event)
    {
        if (overlay.hudCaptures(event.getPoint())) event.consume();
        return event;
    }
}
