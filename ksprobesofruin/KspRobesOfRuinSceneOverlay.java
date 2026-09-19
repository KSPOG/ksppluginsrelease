package net.runelite.client.plugins.microbot.ksprobesofruin;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Polygon;
import java.util.HashSet;
import java.util.Set;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Perspective;
import net.runelite.api.Point;
import net.runelite.api.coords.LocalPoint;
import net.runelite.api.coords.WorldPoint;
import net.runelite.client.plugins.microbot.api.tileobject.models.Rs2TileObjectModel;
import net.runelite.client.ui.overlay.Overlay;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.OverlayUtil;

public class KspRobesOfRuinSceneOverlay extends Overlay
{
    private static final Color TARGET_BORDER = new Color(0, 255, 255, 230);
    private static final Color TARGET_FILL = new Color(0, 255, 255, 55);
    private static final Color CHEST_BORDER = new Color(255, 215, 0, 235);
    private static final Color CHEST_FILL = new Color(255, 215, 0, 55);

    private final Client client;
    private final KspRobesOfRuinPlugin plugin;
    private final KspRobesOfRuinConfig config;

    @Inject
    KspRobesOfRuinSceneOverlay(Client client, KspRobesOfRuinPlugin plugin, KspRobesOfRuinConfig config)
    {
        this.client = client;
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.DYNAMIC);
        setLayer(OverlayLayer.ABOVE_SCENE);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.highlightTiles() || client.getLocalPlayer() == null)
        {
            return null;
        }

        WorldPoint target = plugin.getSceneTarget();
        if (target != null)
        {
            drawTile(graphics, target, plugin.getSceneLabel(), TARGET_BORDER, TARGET_FILL);
        }

        if (plugin.resolveStage() == KspRobesOfRuinPlugin.GuideStage.SEARCH_REWARDS)
        {
            Set<WorldPoint> rendered = new HashSet<>();
            for (Rs2TileObjectModel chest : plugin.getVisibleRewardChests())
            {
                if (chest == null || chest.getWorldLocation() == null || !rendered.add(chest.getWorldLocation()))
                {
                    continue;
                }
                drawTile(graphics, chest.getWorldLocation(), "Search", CHEST_BORDER, CHEST_FILL);
            }
        }

        return null;
    }

    private void drawTile(Graphics2D graphics, WorldPoint worldPoint, String label, Color border, Color fill)
    {
        if (worldPoint == null || worldPoint.getPlane() != client.getPlane())
        {
            return;
        }

        LocalPoint local = LocalPoint.fromWorld(client, worldPoint);
        if (local == null)
        {
            return;
        }

        Polygon polygon = Perspective.getCanvasTilePoly(client, local);
        if (polygon == null)
        {
            return;
        }

        graphics.setColor(fill);
        graphics.fill(polygon);
        OverlayUtil.renderPolygon(graphics, polygon, border);

        if (label != null && !label.isBlank())
        {
            Point text = Perspective.getCanvasTextLocation(client, graphics, local, label, 0);
            if (text != null)
            {
                OverlayUtil.renderTextLocation(graphics, text, label, border);
            }
        }
    }
}
