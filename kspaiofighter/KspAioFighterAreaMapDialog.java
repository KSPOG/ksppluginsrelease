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
import javax.swing.JTextField;
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
 * Native two-corner OSRS attack-area picker.
 *
 * KSP-owned RuneLite region images are preferred, followed by KSP TMS tiles.
 * Explv is retained only as an imagery fallback while kspmaps coverage is being
 * populated. Coordinate selection and the fallback grid do not depend on any
 * imagery source, so surface and high-Y dungeon coordinates remain selectable.
 */
final class KspAioFighterAreaMapDialog extends JDialog
{
    private static final int DEFAULT_X = 3244;
    private static final int DEFAULT_Y = 3468;
    private static final int DEFAULT_PLANE = 0;

    private final KspMapCanvas mapCanvas;
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

        mapCanvas = new KspMapCanvas(safeCentre, initialPlane, this::refreshSelectionState);
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
        setMinimumSize(new Dimension(820, 600));
        setPreferredSize(new Dimension(980, 760));
        pack();
    }

    private void buildUi(WorldPoint playerOrFallbackCentre)
    {
        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel north = new JPanel(new BorderLayout(8, 6));
        JLabel instructions = new JLabel(
            "<html><b>Select Attack Area</b><br>Click two corner tiles or jump directly to any WorldPoint."
                + " Surface, dungeon/high-Y and planes 0-3 are supported. Missing imagery still shows a coordinate grid.</html>");
        north.add(instructions, BorderLayout.CENTER);

        JPanel mapControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        mapControls.add(new JLabel("Plane:"));

        JComboBox<String> plane = new JComboBox<>(new String[]{"0", "1", "2", "3", "Dungeon"});
        plane.setToolTipText("Dungeon opens the high-Y dungeon map space while preserving the real RuneLite plane (0-3)");
        plane.setSelectedItem(mapCanvas.getCentreWorldPoint().getY() >= 6400
            ? "Dungeon"
            : Integer.toString(mapCanvas.getPlane()));
        plane.addActionListener(e -> {
            String selected = (String) plane.getSelectedItem();
            if (selected == null) return;

            if ("Dungeon".equals(selected))
            {
                WorldPoint centre = mapCanvas.getCentreWorldPoint();
                if (centre.getY() < 6400)
                {
                    mapCanvas.centerOn(new WorldPoint(centre.getX(), 9600, mapCanvas.getPlane()));
                }
                return;
            }

            try
            {
                mapCanvas.setPlane(Integer.parseInt(selected));
            }
            catch (NumberFormatException ignored)
            {
            }
        });
        mapControls.add(plane);

        JButton centreButton = new JButton("Player");
        centreButton.setToolTipText("Centre on your current/configured location");
        centreButton.addActionListener(e -> {
            mapCanvas.centerOn(playerOrFallbackCentre);
            plane.setSelectedItem(playerOrFallbackCentre.getY() >= 6400
                ? "Dungeon"
                : Integer.toString(mapCanvas.getPlane()));
        });
        mapControls.add(centreButton);

        JButton openExplv = new JButton("Open Explv");
        openExplv.setToolTipText("Open the same coordinates in Explv for comparison");
        openExplv.addActionListener(e -> openExplvInBrowser());
        mapControls.add(openExplv);

        WorldPoint visibleCentre = mapCanvas.getCentreWorldPoint();
        JPanel coordinateControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        coordinateControls.add(new JLabel("World X:"));
        JTextField worldX = new JTextField(Integer.toString(visibleCentre.getX()), 6);
        coordinateControls.add(worldX);
        coordinateControls.add(new JLabel("Y:"));
        JTextField worldY = new JTextField(Integer.toString(visibleCentre.getY()), 6);
        coordinateControls.add(worldY);

        JButton goToWorldPoint = new JButton("Go to WorldPoint");
        goToWorldPoint.setToolTipText("Supports surface and dungeon/high-Y coordinates, e.g. 3118, 9837, 0");
        goToWorldPoint.addActionListener(e -> {
            try
            {
                int x = Integer.parseInt(worldX.getText().trim());
                int y = Integer.parseInt(worldY.getText().trim());
                String selectedPlane = (String) plane.getSelectedItem();
                int z = mapCanvas.getPlane();
                if (selectedPlane != null && !"Dungeon".equals(selectedPlane))
                {
                    try
                    {
                        z = Integer.parseInt(selectedPlane);
                    }
                    catch (NumberFormatException ignored)
                    {
                    }
                }
                WorldPoint target = new WorldPoint(x, y, z);
                if (valid(target))
                {
                    mapCanvas.centerOn(target);
                }
            }
            catch (NumberFormatException ignored)
            {
            }
        });
        coordinateControls.add(goToWorldPoint);

        JLabel coordinateHint = new JLabel("Dungeon = high-Y map space; actual saved plane remains 0-3");
        coordinateHint.setForeground(Color.LIGHT_GRAY);
        coordinateControls.add(coordinateHint);

        north.add(mapControls, BorderLayout.EAST);
        north.add(coordinateControls, BorderLayout.SOUTH);
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
        if (!valid(first) || !valid(second) || first.getPlane() != second.getPlane())
        {
            return;
        }

        onAreaSelected.accept(first, second);
        dispose();
    }

    private void openExplvInBrowser()
    {
        if (!Desktop.isDesktopSupported())
        {
            return;
        }

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

    private static final class KspMapCanvas extends JPanel
    {
        private static final int MAX_ZOOM = 11;
        private static final int MIN_ZOOM = 4;
        private static final int TILE_SIZE = 256;
        private static final int DRAG_THRESHOLD = 4;

        // Explv-compatible projection, shared with KSPOG/kspmaps.
        private static final double MAP_HEIGHT_MAX_ZOOM_PX = 364544.0;
        private static final double RS_TILE_PX = 32.0;
        private static final int RS_OFFSET_X = 960;
        private static final int RS_OFFSET_Y = 6208;

        private static final String KSP_TILE_BASE =
            "https://raw.githubusercontent.com/KSPOG/kspmaps/main/tiles/";
        private static final String KSP_REGION_BASE =
            "https://raw.githubusercontent.com/KSPOG/kspmaps/main/data/regions/";
        private static final String EXPLV_TILE_BASE =
            "https://raw.githubusercontent.com/Explv/osrs_map_tiles/master/";

        private final Map<String, BufferedImage> tiles = new ConcurrentHashMap<>();
        private final Set<String> loading = ConcurrentHashMap.newKeySet();
        private final Set<String> failed = ConcurrentHashMap.newKeySet();
        private final Map<String, BufferedImage> regions = new ConcurrentHashMap<>();
        private final Set<String> regionLoading = ConcurrentHashMap.newKeySet();
        private final Set<String> regionFailed = ConcurrentHashMap.newKeySet();
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

        private KspMapCanvas(WorldPoint centre, int plane, Runnable selectionChanged)
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
                    if (!SwingUtilities.isLeftMouseButton(e))
                    {
                        return;
                    }
                    pressPoint = e.getPoint();
                    pressCentreX = centreMaxPixelX;
                    pressCentreY = centreMaxPixelY;
                    dragging = false;
                }

                @Override
                public void mouseDragged(MouseEvent e)
                {
                    if (pressPoint == null)
                    {
                        return;
                    }

                    int dx = e.getX() - pressPoint.x;
                    int dy = e.getY() - pressPoint.y;
                    if (!dragging && Math.hypot(dx, dy) >= DRAG_THRESHOLD)
                    {
                        dragging = true;
                    }
                    if (!dragging)
                    {
                        return;
                    }

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

                    if (!dragging)
                    {
                        select(screenToWorld(e.getX(), e.getY()));
                    }
                    pressPoint = null;
                    dragging = false;
                }

                @Override
                public void mouseMoved(MouseEvent e)
                {
                    hover = screenToWorld(e.getX(), e.getY());
                    int regionId = ((hover.getX() >> 6) << 8) | (hover.getY() >> 6);
                    String worldSpace = hover.getY() >= 6400 ? "high-Y/dungeon" : "main world";
                    setToolTipText("Tile " + format(hover)
                        + " | region " + regionId
                        + " | local " + (hover.getX() & 63) + "," + (hover.getY() & 63)
                        + " | " + worldSpace
                        + " | zoom " + zoom);
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
            if (this.plane == next)
            {
                return;
            }

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
            if (valid(first))
            {
                plane = clampPlane(first.getPlane());
            }
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
            if (!valid(point))
            {
                return;
            }

            centreMaxPixelX = worldCentreMaxPixelX(point.getX());
            centreMaxPixelY = worldCentreMaxPixelY(point.getY());
            plane = clampPlane(point.getPlane());
            repaint();
        }

        private void select(WorldPoint point)
        {
            if (!valid(point))
            {
                return;
            }

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
            if (nextZoom == zoom)
            {
                return;
            }

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
                drawKspRegions(g);
                drawCoordinateGrid(g);
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
                if (tileX < 0 || tileX >= worldTiles)
                {
                    continue;
                }

                for (int tileY = minTileY; tileY <= maxTileY; tileY++)
                {
                    if (tileY < 0 || tileY >= worldTiles)
                    {
                        continue;
                    }

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

        private void drawKspRegions(Graphics2D g)
        {
            WorldPoint a = screenToWorld(0, 0);
            WorldPoint b = screenToWorld(getWidth(), getHeight());

            int minRegionX = Math.max(0, Math.min(a.getX(), b.getX()) >> 6);
            int maxRegionX = Math.min(255, Math.max(a.getX(), b.getX()) >> 6);
            int minRegionY = Math.max(0, Math.min(a.getY(), b.getY()) >> 6);
            int maxRegionY = Math.min(255, Math.max(a.getY(), b.getY()) >> 6);

            int regionCount = (maxRegionX - minRegionX + 1) * (maxRegionY - minRegionY + 1);
            if (regionCount <= 0 || regionCount > 256)
            {
                return;
            }

            double displayPixelsPerWorldTile = RS_TILE_PX * scale();
            int displayRegionSize = Math.max(1, (int) Math.round(64.0 * displayPixelsPerWorldTile));

            for (int regionX = minRegionX; regionX <= maxRegionX; regionX++)
            {
                for (int regionY = minRegionY; regionY <= maxRegionY; regionY++)
                {
                    int regionId = (regionX << 8) | regionY;
                    BufferedImage image = getOrRequestRegion(regionId);
                    if (image == null)
                    {
                        continue;
                    }

                    int baseX = regionX << 6;
                    int baseY = regionY << 6;
                    int drawX = (int) Math.round((worldEdgeMaxPixelX(baseX) - centreMaxPixelX) * scale()
                        + getWidth() / 2.0);
                    int drawY = (int) Math.round((worldEdgeMaxPixelY(baseY + 64) - centreMaxPixelY) * scale()
                        + getHeight() / 2.0);

                    g.drawImage(image, drawX, drawY, displayRegionSize, displayRegionSize, null);
                }
            }
        }

        private BufferedImage getOrRequestRegion(int regionId)
        {
            String key = plane + "/" + regionId;
            BufferedImage cached = regions.get(key);
            if (cached != null || regionFailed.contains(key))
            {
                return cached;
            }
            if (!regionLoading.add(key))
            {
                return null;
            }

            new SwingWorker<BufferedImage, Void>()
            {
                @Override
                protected BufferedImage doInBackground()
                {
                    return readTile(KSP_REGION_BASE + key + ".png");
                }

                @Override
                protected void done()
                {
                    regionLoading.remove(key);
                    try
                    {
                        BufferedImage image = get();
                        if (image != null)
                        {
                            regions.put(key, image);
                        }
                        else
                        {
                            regionFailed.add(key);
                        }
                    }
                    catch (Exception ex)
                    {
                        regionFailed.add(key);
                    }
                    repaint();
                }
            }.execute();

            return null;
        }

        private BufferedImage getOrRequestTile(int tileX, int standardTileY)
        {
            int tmsY = ((1 << zoom) - 1) - standardTileY;
            String key = plane + "/" + zoom + "/" + tileX + "/" + tmsY;
            BufferedImage cached = tiles.get(key);
            if (cached != null || failed.contains(key))
            {
                return cached;
            }
            if (!loading.add(key))
            {
                return null;
            }

            new SwingWorker<BufferedImage, Void>()
            {
                @Override
                protected BufferedImage doInBackground()
                {
                    BufferedImage image = readTile(KSP_TILE_BASE + key + ".png");
                    return image != null ? image : readTile(EXPLV_TILE_BASE + key + ".png");
                }

                @Override
                protected void done()
                {
                    loading.remove(key);
                    try
                    {
                        BufferedImage image = get();
                        if (image != null)
                        {
                            tiles.put(key, image);
                        }
                        else
                        {
                            failed.add(key);
                        }
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

        private BufferedImage readTile(String url)
        {
            try
            {
                URLConnection connection = URI.create(url).toURL().openConnection();
                connection.setConnectTimeout(2_500);
                connection.setReadTimeout(5_000);
                connection.setRequestProperty("User-Agent", "KSP-AIO-Fighter");
                try (InputStream input = connection.getInputStream())
                {
                    return ImageIO.read(input);
                }
            }
            catch (Exception ignored)
            {
                return null;
            }
        }

        private void drawCoordinateGrid(Graphics2D g)
        {
            int step = zoom >= 10 ? 1 : zoom == 9 ? 4 : zoom == 8 ? 8 : 64;
            WorldPoint a = screenToWorld(0, 0);
            WorldPoint b = screenToWorld(getWidth(), getHeight());

            int minX = Math.min(a.getX(), b.getX()) - step;
            int maxX = Math.max(a.getX(), b.getX()) + step;
            int minY = Math.min(a.getY(), b.getY()) - step;
            int maxY = Math.max(a.getY(), b.getY()) + step;

            int startX = Math.floorDiv(minX, step) * step;
            int startY = Math.floorDiv(minY, step) * step;

            for (int x = startX; x <= maxX; x += step)
            {
                boolean regionLine = x % 64 == 0;
                g.setColor(new Color(255, 255, 255, regionLine ? 90 : 32));
                g.setStroke(new BasicStroke(regionLine ? 1.5f : 1f));
                Point p1 = worldToScreen(new WorldPoint(x, minY, plane));
                Point p2 = worldToScreen(new WorldPoint(x, maxY, plane));
                g.drawLine(p1.x, p1.y, p2.x, p2.y);
            }

            for (int y = startY; y <= maxY; y += step)
            {
                boolean regionLine = y % 64 == 0;
                g.setColor(new Color(255, 255, 255, regionLine ? 90 : 32));
                g.setStroke(new BasicStroke(regionLine ? 1.5f : 1f));
                Point p1 = worldToScreen(new WorldPoint(minX, y, plane));
                Point p2 = worldToScreen(new WorldPoint(maxX, y, plane));
                g.drawLine(p1.x, p1.y, p2.x, p2.y);
            }
        }

        private void drawSelection(Graphics2D g)
        {
            WorldPoint end = second != null ? second : hover;
            if (first == null || end == null || first.getPlane() != plane || end.getPlane() != plane)
            {
                return;
            }

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
            if (hover == null || hover.getPlane() != plane)
            {
                return;
            }

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
            WorldPoint centre = getCentreWorldPoint();
            String text = "Imagery: KSP RuneLite regions -> KSP tiles -> Explv fallback  |  "
                + (centre.getY() >= 6400 ? "high-Y/dungeon space" : "main world")
                + "  |  zoom " + zoom + "  |  plane " + plane;
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
            int y = (int) Math.round((MAP_HEIGHT_MAX_ZOOM_PX - maxPixelY + (RS_TILE_PX / 4.0) - RS_TILE_PX)
                / RS_TILE_PX) + RS_OFFSET_Y;
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

        private static double worldEdgeMaxPixelX(int worldX)
        {
            return ((worldX - RS_OFFSET_X) * RS_TILE_PX) + (RS_TILE_PX / 4.0);
        }

        private static double worldEdgeMaxPixelY(int worldY)
        {
            return MAP_HEIGHT_MAX_ZOOM_PX - ((worldY - RS_OFFSET_Y) * RS_TILE_PX);
        }

        private static int clampPlane(int value)
        {
            return Math.max(0, Math.min(3, value));
        }
    }
}
