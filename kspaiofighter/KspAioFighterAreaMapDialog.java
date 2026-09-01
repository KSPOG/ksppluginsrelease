package net.runelite.client.plugins.microbot.kspaiofighter;

import net.runelite.api.coords.WorldPoint;
import net.runelite.client.ui.ColorScheme;

import javax.imageio.ImageIO;
import javax.swing.BorderFactory;
import javax.swing.JButton;
import javax.swing.JComboBox;
import javax.swing.JDialog;
import javax.swing.JLabel;
import javax.swing.JPanel;
import javax.swing.SwingConstants;
import javax.swing.SwingUtilities;
import javax.swing.SwingWorker;
import javax.swing.border.EmptyBorder;
import java.awt.BasicStroke;
import java.awt.BorderLayout;
import java.awt.Color;
import java.awt.Component;
import java.awt.Desktop;
import java.awt.Dialog;
import java.awt.Dimension;
import java.awt.FlowLayout;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Point;
import java.awt.RenderingHints;
import java.awt.Window;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.awt.event.MouseWheelEvent;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.net.URI;
import java.net.URLConnection;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.BiConsumer;

/**
 * Native Swing area picker using the same public OSRS map tiles and coordinate
 * transform used by Explv's Map. This avoids requiring a browser callback or
 * clipboard parsing while keeping the familiar two-click rectangular Area flow.
 */
final class KspAioFighterAreaMapDialog extends JDialog
{
    private static final int DEFAULT_X = 3244;
    private static final int DEFAULT_Y = 3468;
    private static final int DEFAULT_PLANE = 0;

    private final ExplvMapCanvas mapCanvas;
    private final JLabel selectionLabel = new JLabel("Select two corner tiles", SwingConstants.CENTER);
    private final JButton useArea = new JButton("Use Area");
    private final BiConsumer<WorldPoint, WorldPoint> onAreaSelected;

    static void show(Component parent,
                     WorldPoint centre,
                     WorldPoint existingFirst,
                     WorldPoint existingSecond,
                     BiConsumer<WorldPoint, WorldPoint> onAreaSelected)
    {
        Window owner = SwingUtilities.getWindowAncestor(parent);
        KspAioFighterAreaMapDialog dialog = new KspAioFighterAreaMapDialog(
            owner, centre, existingFirst, existingSecond, onAreaSelected);
        dialog.setLocationRelativeTo(owner);
        dialog.setVisible(true);
    }

    private KspAioFighterAreaMapDialog(Window owner,
                                       WorldPoint centre,
                                       WorldPoint existingFirst,
                                       WorldPoint existingSecond,
                                       BiConsumer<WorldPoint, WorldPoint> onAreaSelected)
    {
        super(owner, "KSP AIO Fighter - Select Attack Area", Dialog.ModalityType.APPLICATION_MODAL);
        this.onAreaSelected = onAreaSelected;

        WorldPoint safeCentre = valid(centre)
            ? centre
            : new WorldPoint(DEFAULT_X, DEFAULT_Y, DEFAULT_PLANE);
        int initialPlane = safeCentre.getPlane();
        if (valid(existingFirst) && valid(existingSecond) && existingFirst.getPlane() == existingSecond.getPlane())
        {
            initialPlane = existingFirst.getPlane();
        }

        mapCanvas = new ExplvMapCanvas(safeCentre, initialPlane, this::refreshSelectionState);
        if (valid(existingFirst) && valid(existingSecond) && existingFirst.getPlane() == existingSecond.getPlane())
        {
            mapCanvas.setSelection(existingFirst, existingSecond);
            mapCanvas.centerOn(new WorldPoint(
                (existingFirst.getX() + existingSecond.getX()) / 2,
                (existingFirst.getY() + existingSecond.getY()) / 2,
                existingFirst.getPlane()));
        }

        buildUi(safeCentre);
        refreshSelectionState();
        setMinimumSize(new Dimension(760, 560));
        setPreferredSize(new Dimension(940, 720));
        pack();
    }

    private void buildUi(WorldPoint playerOrFallbackCentre)
    {
        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel north = new JPanel(new BorderLayout(8, 6));
        JLabel instructions = new JLabel(
            "<html><b>Select Attack Area</b><br>Click two corner tiles. Drag to pan; mouse wheel zooms.</html>");
        north.add(instructions, BorderLayout.CENTER);

        JPanel mapControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        mapControls.add(new JLabel("Plane:"));
        JComboBox<Integer> plane = new JComboBox<>(new Integer[]{0, 1, 2, 3});
        plane.setSelectedItem(mapCanvas.getPlane());
        plane.addActionListener(e -> {
            Integer selected = (Integer) plane.getSelectedItem();
            if (selected != null) mapCanvas.setPlane(selected);
        });
        mapControls.add(plane);

        JButton centreButton = new JButton("Centre");
        centreButton.setToolTipText("Centre the map on your current/configured location");
        centreButton.addActionListener(e -> mapCanvas.centerOn(playerOrFallbackCentre));
        mapControls.add(centreButton);

        JButton openExplv = new JButton("Open Explv");
        openExplv.setToolTipText("Open the same location on explv.github.io in your browser");
        openExplv.addActionListener(e -> openExplvInBrowser());
        mapControls.add(openExplv);
        north.add(mapControls, BorderLayout.EAST);
        add(north, BorderLayout.NORTH);

        mapCanvas.setBorder(BorderFactory.createLineBorder(ColorScheme.DARKER_GRAY_COLOR));
        add(mapCanvas, BorderLayout.CENTER);

        JPanel south = new JPanel(new BorderLayout(8, 4));
        selectionLabel.setForeground(Color.WHITE);
        south.add(selectionLabel, BorderLayout.NORTH);

        JPanel buttons = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        JButton clear = new JButton("Clear Selection");
        clear.addActionListener(e -> mapCanvas.clearSelection());
        JButton cancel = new JButton("Cancel");
        cancel.addActionListener(e -> dispose());
        useArea.addActionListener(e -> applySelection());
        buttons.add(clear);
        buttons.add(cancel);
        buttons.add(useArea);
        south.add(buttons, BorderLayout.SOUTH);
        add(south, BorderLayout.SOUTH);
    }

    private void refreshSelectionState()
    {
        WorldPoint first = mapCanvas.getFirst();
        WorldPoint second = mapCanvas.getSecond();
        if (!valid(first))
        {
            selectionLabel.setText("Select first corner tile");
            useArea.setEnabled(false);
            return;
        }
        if (!valid(second))
        {
            selectionLabel.setText("First corner: " + format(first) + " - select opposite corner");
            useArea.setEnabled(false);
            return;
        }

        int minX = Math.min(first.getX(), second.getX());
        int maxX = Math.max(first.getX(), second.getX());
        int minY = Math.min(first.getY(), second.getY());
        int maxY = Math.max(first.getY(), second.getY());
        selectionLabel.setText("Area: (" + minX + ", " + minY + ") to (" + maxX + ", " + maxY + ")"
            + "  |  " + (maxX - minX + 1) + " x " + (maxY - minY + 1)
            + "  |  plane " + first.getPlane());
        useArea.setEnabled(first.getPlane() == second.getPlane());
    }

    private void applySelection()
    {
        WorldPoint first = mapCanvas.getFirst();
        WorldPoint second = mapCanvas.getSecond();
        if (!valid(first) || !valid(second) || first.getPlane() != second.getPlane()) return;
        onAreaSelected.accept(first, second);
        dispose();
    }

    private void openExplvInBrowser()
    {
        if (!Desktop.isDesktopSupported()) return;
        try
        {
            WorldPoint centre = mapCanvas.getCentreWorldPoint();
            String url = "https://explv.github.io/?centreX=" + centre.getX()
                + "&centreY=" + centre.getY()
                + "&centreZ=" + mapCanvas.getPlane()
                + "&zoom=" + mapCanvas.getZoom();
            Desktop.getDesktop().browse(URI.create(url));
        }
        catch (Exception ignored)
        {
        }
    }

    private static boolean valid(WorldPoint point)
    {
        return point != null && point.getX() > 0 && point.getY() > 0;
    }

    private static String format(WorldPoint point)
    {
        return "(" + point.getX() + ", " + point.getY() + ", " + point.getPlane() + ")";
    }

    private static final class ExplvMapCanvas extends JPanel
    {
        // Explv Position.js constants / transform.
        private static final int MAX_ZOOM = 11;
        private static final int MIN_ZOOM = 4;
        private static final int TILE_SIZE = 256;
        private static final double MAP_HEIGHT_MAX_ZOOM_PX = 364544.0;
        private static final double RS_TILE_PX = 32.0;
        private static final int RS_OFFSET_X = 960;
        private static final int RS_OFFSET_Y = 6208;
        private static final int DRAG_THRESHOLD = 4;

        private final Map<String, BufferedImage> tiles = new ConcurrentHashMap<>();
        private final Set<String> loading = ConcurrentHashMap.newKeySet();
        private final Set<String> failed = ConcurrentHashMap.newKeySet();
        private final Runnable selectionChanged;

        private int zoom = 10;
        private int plane;
        private double centreMaxPixelX;
        private double centreMaxPixelY;
        private WorldPoint first;
        private WorldPoint second;
        private WorldPoint hover;
        private Point pressPoint;
        private double pressCentreX;
        private double pressCentreY;
        private boolean dragging;

        private ExplvMapCanvas(WorldPoint centre, int plane, Runnable selectionChanged)
        {
            this.plane = clampPlane(plane);
            this.selectionChanged = selectionChanged;
            setBackground(ColorScheme.DARKER_GRAY_COLOR);
            setPreferredSize(new Dimension(900, 600));
            centerOn(centre);

            MouseAdapter mouse = new MouseAdapter()
            {
                @Override
                public void mousePressed(MouseEvent e)
                {
                    if (!SwingUtilities.isLeftMouseButton(e)) return;
                    pressPoint = e.getPoint();
                    pressCentreX = centreMaxPixelX;
                    pressCentreY = centreMaxPixelY;
                    dragging = false;
                }

                @Override
                public void mouseDragged(MouseEvent e)
                {
                    if (pressPoint == null) return;
                    int dx = e.getX() - pressPoint.x;
                    int dy = e.getY() - pressPoint.y;
                    if (!dragging && Math.hypot(dx, dy) >= DRAG_THRESHOLD) dragging = true;
                    if (!dragging) return;
                    double scale = scale();
                    centreMaxPixelX = pressCentreX - dx / scale;
                    centreMaxPixelY = pressCentreY - dy / scale;
                    repaint();
                }

                @Override
                public void mouseReleased(MouseEvent e)
                {
                    if (pressPoint == null || !SwingUtilities.isLeftMouseButton(e))
                    {
                        pressPoint = null;
                        return;
                    }
                    if (!dragging) select(screenToWorld(e.getX(), e.getY()));
                    pressPoint = null;
                    dragging = false;
                }

                @Override
                public void mouseMoved(MouseEvent e)
                {
                    hover = screenToWorld(e.getX(), e.getY());
                    setToolTipText("Tile " + format(hover) + " | zoom " + zoom);
                    repaint();
                }

                @Override
                public void mouseExited(MouseEvent e)
                {
                    hover = null;
                    repaint();
                }

                @Override
                public void mouseWheelMoved(MouseWheelEvent e)
                {
                    zoomAt(e.getX(), e.getY(), e.getWheelRotation() < 0 ? 1 : -1);
                }
            };
            addMouseListener(mouse);
            addMouseMotionListener(mouse);
            addMouseWheelListener(mouse);
        }

        int getPlane()
        {
            return plane;
        }

        int getZoom()
        {
            return zoom;
        }

        WorldPoint getFirst()
        {
            return first;
        }

        WorldPoint getSecond()
        {
            return second;
        }

        WorldPoint getCentreWorldPoint()
        {
            return maxPixelToWorld(centreMaxPixelX, centreMaxPixelY);
        }

        void setPlane(int plane)
        {
            int next = clampPlane(plane);
            if (this.plane == next) return;
            this.plane = next;
            hover = null;
            if ((first != null && first.getPlane() != next) || (second != null && second.getPlane() != next))
            {
                first = null;
                second = null;
                selectionChanged.run();
            }
            repaint();
        }

        void setSelection(WorldPoint first, WorldPoint second)
        {
            this.first = first;
            this.second = second;
            if (valid(first)) this.plane = clampPlane(first.getPlane());
            selectionChanged.run();
            repaint();
        }

        void clearSelection()
        {
            first = null;
            second = null;
            selectionChanged.run();
            repaint();
        }

        void centerOn(WorldPoint point)
        {
            if (!valid(point)) return;
            centreMaxPixelX = worldCentreMaxPixelX(point.getX());
            centreMaxPixelY = worldCentreMaxPixelY(point.getY());
            plane = clampPlane(point.getPlane());
            repaint();
        }

        private void select(WorldPoint point)
        {
            if (!valid(point)) return;
            if (first == null || second != null)
            {
                first = point;
                second = null;
            }
            else
            {
                second = point;
            }
            selectionChanged.run();
            repaint();
        }

        private void zoomAt(int screenX, int screenY, int delta)
        {
            int nextZoom = Math.max(MIN_ZOOM, Math.min(MAX_ZOOM, zoom + delta));
            if (nextZoom == zoom) return;

            double oldScale = scale();
            double maxUnderMouseX = centreMaxPixelX + (screenX - getWidth() / 2.0) / oldScale;
            double maxUnderMouseY = centreMaxPixelY + (screenY - getHeight() / 2.0) / oldScale;

            zoom = nextZoom;
            double newScale = scale();
            centreMaxPixelX = maxUnderMouseX - (screenX - getWidth() / 2.0) / newScale;
            centreMaxPixelY = maxUnderMouseY - (screenY - getHeight() / 2.0) / newScale;
            repaint();
        }

        @Override
        protected void paintComponent(Graphics graphics)
        {
            super.paintComponent(graphics);
            Graphics2D g = (Graphics2D) graphics.create();
            try
            {
                g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
                drawTiles(g);
                drawSelection(g);
                drawHover(g);
                drawAttribution(g);
            }
            finally
            {
                g.dispose();
            }
        }

        private void drawTiles(Graphics2D g)
        {
            double scale = scale();
            double centrePixelX = centreMaxPixelX * scale;
            double centrePixelY = centreMaxPixelY * scale;
            double left = centrePixelX - getWidth() / 2.0;
            double top = centrePixelY - getHeight() / 2.0;

            int minTileX = (int) Math.floor(left / TILE_SIZE) - 1;
            int maxTileX = (int) Math.floor((left + getWidth()) / TILE_SIZE) + 1;
            int minTileY = (int) Math.floor(top / TILE_SIZE) - 1;
            int maxTileY = (int) Math.floor((top + getHeight()) / TILE_SIZE) + 1;
            int worldTiles = 1 << zoom;

            for (int tileX = minTileX; tileX <= maxTileX; tileX++)
            {
                if (tileX < 0 || tileX >= worldTiles) continue;
                for (int tileY = minTileY; tileY <= maxTileY; tileY++)
                {
                    if (tileY < 0 || tileY >= worldTiles) continue;
                    int drawX = (int) Math.round(tileX * TILE_SIZE - left);
                    int drawY = (int) Math.round(tileY * TILE_SIZE - top);
                    BufferedImage image = getOrRequestTile(tileX, tileY);
                    if (image != null)
                    {
                        g.drawImage(image, drawX, drawY, TILE_SIZE, TILE_SIZE, null);
                    }
                }
            }
        }

        private BufferedImage getOrRequestTile(int tileX, int standardTileY)
        {
            int tmsY = ((1 << zoom) - 1) - standardTileY;
            String key = plane + "/" + zoom + "/" + tileX + "/" + tmsY;
            BufferedImage cached = tiles.get(key);
            if (cached != null || failed.contains(key)) return cached;
            if (!loading.add(key)) return null;

            new SwingWorker<BufferedImage, Void>()
            {
                @Override
                protected BufferedImage doInBackground() throws Exception
                {
                    String url = "https://raw.githubusercontent.com/Explv/osrs_map_tiles/master/"
                        + key + ".png";
                    URLConnection connection = URI.create(url).toURL().openConnection();
                    connection.setConnectTimeout(4_000);
                    connection.setReadTimeout(7_000);
                    connection.setRequestProperty("User-Agent", "KSP-AIO-Fighter");
                    try (InputStream input = connection.getInputStream())
                    {
                        return ImageIO.read(input);
                    }
                }

                @Override
                protected void done()
                {
                    loading.remove(key);
                    try
                    {
                        BufferedImage image = get();
                        if (image != null) tiles.put(key, image);
                        else failed.add(key);
                    }
                    catch (Exception ex)
                    {
                        failed.add(key);
                    }
                    repaint();
                }
            }.execute();
            return null;
        }

        private void drawSelection(Graphics2D g)
        {
            WorldPoint end = second != null ? second : hover;
            if (first == null || end == null || first.getPlane() != plane || end.getPlane() != plane) return;

            double tileScreenSize = RS_TILE_PX * scale();
            Point a = worldToScreen(first);
            Point b = worldToScreen(end);
            int x = (int) Math.round(Math.min(a.x, b.x) - tileScreenSize / 2.0);
            int y = (int) Math.round(Math.min(a.y, b.y) - tileScreenSize / 2.0);
            int width = Math.max(1, (int) Math.round(Math.abs(a.x - b.x) + tileScreenSize));
            int height = Math.max(1, (int) Math.round(Math.abs(a.y - b.y) + tileScreenSize));

            g.setColor(new Color(51, 181, 229, 70));
            g.fillRect(x, y, width, height);
            g.setColor(new Color(51, 181, 229));
            g.setStroke(new BasicStroke(2f));
            g.drawRect(x, y, width, height);
        }

        private void drawHover(Graphics2D g)
        {
            if (hover == null || hover.getPlane() != plane) return;
            double tileScreenSize = RS_TILE_PX * scale();
            Point p = worldToScreen(hover);
            int x = (int) Math.round(p.x - tileScreenSize / 2.0);
            int y = (int) Math.round(p.y - tileScreenSize / 2.0);
            int size = Math.max(1, (int) Math.round(tileScreenSize));
            g.setColor(new Color(255, 255, 255, 180));
            g.drawRect(x, y, size, size);
        }

        private void drawAttribution(Graphics2D g)
        {
            String text = "Map tiles: Explv/osrs_map_tiles  |  zoom " + zoom + "  |  plane " + plane;
            int width = g.getFontMetrics().stringWidth(text) + 10;
            int y = getHeight() - 8;
            g.setColor(new Color(0, 0, 0, 150));
            g.fillRect(4, y - g.getFontMetrics().getHeight(), width, g.getFontMetrics().getHeight() + 4);
            g.setColor(Color.WHITE);
            g.drawString(text, 9, y);
        }

        private Point worldToScreen(WorldPoint point)
        {
            double scale = scale();
            double x = (worldCentreMaxPixelX(point.getX()) - centreMaxPixelX) * scale + getWidth() / 2.0;
            double y = (worldCentreMaxPixelY(point.getY()) - centreMaxPixelY) * scale + getHeight() / 2.0;
            return new Point((int) Math.round(x), (int) Math.round(y));
        }

        private WorldPoint screenToWorld(int screenX, int screenY)
        {
            double scale = scale();
            double maxPixelX = centreMaxPixelX + (screenX - getWidth() / 2.0) / scale;
            double maxPixelY = centreMaxPixelY + (screenY - getHeight() / 2.0) / scale;
            return maxPixelToWorld(maxPixelX, maxPixelY);
        }

        private WorldPoint maxPixelToWorld(double maxPixelX, double maxPixelY)
        {
            int x = (int) Math.round((maxPixelX - RS_TILE_PX) / RS_TILE_PX) + RS_OFFSET_X;
            int y = (int) Math.round((MAP_HEIGHT_MAX_ZOOM_PX - maxPixelY + (RS_TILE_PX / 4.0) - RS_TILE_PX) / RS_TILE_PX) + RS_OFFSET_Y;
            return new WorldPoint(x, y, plane);
        }

        private double scale()
        {
            return Math.pow(2.0, zoom - MAX_ZOOM);
        }

        private static double worldCentreMaxPixelX(int worldX)
        {
            return ((worldX + 0.5 - RS_OFFSET_X) * RS_TILE_PX) + (RS_TILE_PX / 4.0);
        }

        private static double worldCentreMaxPixelY(int worldY)
        {
            return MAP_HEIGHT_MAX_ZOOM_PX - ((worldY + 0.5 - RS_OFFSET_Y) * RS_TILE_PX);
        }

        private static int clampPlane(int value)
        {
            return Math.max(0, Math.min(3, value));
        }
    }
}
