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
 * Surface/dungeon map space and RuneLite plane are deliberately separate.
 * Dungeon/high-Y space uses KSP RuneLite region imagery only, so plane 0 in a
 * dungeon can never accidentally fall back to the plane-0 surface map.
 */
final class KspAioFighterAreaMapDialog extends JDialog
{
    private static final int DEFAULT_X = 3244;
    private static final int DEFAULT_Y = 3468;
    private static final int DEFAULT_DUNGEON_Y = 9600;
    private static final int DUNGEON_Y_THRESHOLD = 6400;

    private final KspMapCanvas mapCanvas;
    private final JLabel selectionLabel = new JLabel("Select two corner tiles", SwingConstants.CENTER);
    private final JButton useArea = new JButton("Use Area");
    private final BiConsumer<WorldPoint, WorldPoint> onAreaSelected;
    private final WorldPoint playerOrFallbackCentre;

    private JComboBox<String> mapSpaceSelector;
    private JComboBox<Integer> planeSelector;
    private JTextField worldXField;
    private JTextField worldYField;
    private WorldPoint surfaceCentre;
    private WorldPoint dungeonCentre;
    private String activeMapSpace;
    private boolean syncingControls;

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
        this.playerOrFallbackCentre = valid(centre)
            ? centre
            : new WorldPoint(DEFAULT_X, DEFAULT_Y, 0);

        int initialPlane = playerOrFallbackCentre.getPlane();
        if (valid(existingFirst) && valid(existingSecond) && existingFirst.getPlane() == existingSecond.getPlane())
        {
            initialPlane = existingFirst.getPlane();
        }

        mapCanvas = new KspMapCanvas(playerOrFallbackCentre, initialPlane, this::refreshSelectionState);
        if (valid(existingFirst) && valid(existingSecond) && existingFirst.getPlane() == existingSecond.getPlane())
        {
            mapCanvas.setSelection(existingFirst, existingSecond);
            mapCanvas.centerOn(new WorldPoint(
                (existingFirst.getX() + existingSecond.getX()) / 2,
                (existingFirst.getY() + existingSecond.getY()) / 2,
                existingFirst.getPlane()));
        }

        WorldPoint visible = mapCanvas.getCentreWorldPoint();
        if (isDungeonSpace(visible))
        {
            activeMapSpace = "Dungeon";
            dungeonCentre = visible;
            surfaceCentre = new WorldPoint(DEFAULT_X, DEFAULT_Y, visible.getPlane());
        }
        else
        {
            activeMapSpace = "Surface";
            surfaceCentre = visible;
            dungeonCentre = new WorldPoint(visible.getX(), DEFAULT_DUNGEON_Y, visible.getPlane());
        }

        buildUi();
        refreshSelectionState();
        setMinimumSize(new Dimension(840, 610));
        setPreferredSize(new Dimension(1000, 770));
        pack();
    }

    private void buildUi()
    {
        setLayout(new BorderLayout(8, 8));
        getRootPane().setBorder(new EmptyBorder(8, 8, 8, 8));

        JPanel north = new JPanel(new BorderLayout(8, 6));
        JLabel instructions = new JLabel(
            "<html><b>Select Attack Area</b><br>Surface and Dungeon are separate map spaces. "
                + "Plane 0-3 always remains the real RuneLite plane.</html>");
        north.add(instructions, BorderLayout.CENTER);

        JPanel mapControls = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        mapControls.add(new JLabel("Map:"));
        mapSpaceSelector = new JComboBox<>(new String[]{"Surface", "Dungeon"});
        mapSpaceSelector.setSelectedItem(activeMapSpace);
        mapSpaceSelector.setToolTipText("Switch between normal-world and high-Y dungeon coordinate space");
        mapSpaceSelector.addActionListener(e -> switchMapSpace());
        mapControls.add(mapSpaceSelector);

        mapControls.add(new JLabel("Plane:"));
        planeSelector = new JComboBox<>(new Integer[]{0, 1, 2, 3});
        planeSelector.setSelectedItem(mapCanvas.getPlane());
        planeSelector.addActionListener(e -> {
            if (syncingControls) return;
            Integer selected = (Integer) planeSelector.getSelectedItem();
            if (selected != null)
            {
                mapCanvas.setPlane(selected);
                rememberCurrentCentre();
            }
        });
        mapControls.add(planeSelector);

        JButton playerButton = new JButton("Player");
        playerButton.setToolTipText("Centre on the current/configured player location");
        playerButton.addActionListener(e -> centerOnAndSync(playerOrFallbackCentre));
        mapControls.add(playerButton);

        JButton openExplv = new JButton("Open Explv");
        openExplv.addActionListener(e -> openExplvInBrowser());
        mapControls.add(openExplv);
        north.add(mapControls, BorderLayout.EAST);

        WorldPoint visible = mapCanvas.getCentreWorldPoint();
        JPanel coordinateControls = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 0));
        coordinateControls.add(new JLabel("World X:"));
        worldXField = new JTextField(Integer.toString(visible.getX()), 6);
        coordinateControls.add(worldXField);
        coordinateControls.add(new JLabel("Y:"));
        worldYField = new JTextField(Integer.toString(visible.getY()), 6);
        coordinateControls.add(worldYField);

        JButton goButton = new JButton("Go to WorldPoint");
        goButton.setToolTipText("Example dungeon point: 3118, 9837, 0");
        goButton.addActionListener(e -> goToWorldPoint());
        coordinateControls.add(goButton);

        JLabel hint = new JLabel("Dungeon uses high-Y coordinates; Plane is still 0-3");
        hint.setForeground(Color.LIGHT_GRAY);
        coordinateControls.add(hint);
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

    private void switchMapSpace()
    {
        if (syncingControls) return;
        String selected = (String) mapSpaceSelector.getSelectedItem();
        if (selected == null || selected.equals(activeMapSpace)) return;

        rememberCurrentCentre();
        activeMapSpace = selected;
        WorldPoint remembered = "Dungeon".equals(selected) ? dungeonCentre : surfaceCentre;
        WorldPoint target = new WorldPoint(remembered.getX(), remembered.getY(), mapCanvas.getPlane());
        mapCanvas.centerOn(target);
        updateCoordinateFields(target);
    }

    private void rememberCurrentCentre()
    {
        WorldPoint current = mapCanvas.getCentreWorldPoint();
        if ("Dungeon".equals(activeMapSpace)) dungeonCentre = current;
        else surfaceCentre = current;
    }

    private void goToWorldPoint()
    {
        try
        {
            int x = Integer.parseInt(worldXField.getText().trim());
            int y = Integer.parseInt(worldYField.getText().trim());
            Integer selectedPlane = (Integer) planeSelector.getSelectedItem();
            int plane = selectedPlane == null ? mapCanvas.getPlane() : selectedPlane;
            WorldPoint target = new WorldPoint(x, y, plane);
            if (valid(target)) centerOnAndSync(target);
        }
        catch (NumberFormatException ignored)
        {
        }
    }

    private void centerOnAndSync(WorldPoint target)
    {
        if (!valid(target)) return;
        mapCanvas.centerOn(target);
        activeMapSpace = isDungeonSpace(target) ? "Dungeon" : "Surface";
        if (isDungeonSpace(target)) dungeonCentre = target;
        else surfaceCentre = target;

        syncingControls = true;
        try
        {
            mapSpaceSelector.setSelectedItem(activeMapSpace);
            planeSelector.setSelectedItem(target.getPlane());
        }
        finally
        {
            syncingControls = false;
        }
        updateCoordinateFields(target);
    }

    private void updateCoordinateFields(WorldPoint point)
    {
        worldXField.setText(Integer.toString(point.getX()));
        worldYField.setText(Integer.toString(point.getY()));
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
        String space = isDungeonSpace(first) ? "Dungeon" : "Surface";
        selectionLabel.setText("Area: (" + minX + ", " + minY + ") to (" + maxX + ", " + maxY + ")"
            + "  |  " + (maxX - minX + 1) + " x " + (maxY - minY + 1)
            + "  |  " + space + "  |  plane " + first.getPlane());
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

    private static boolean isDungeonSpace(WorldPoint point)
    {
        return point != null && point.getY() >= DUNGEON_Y_THRESHOLD;
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
        private final Set<String> loadingTiles = ConcurrentHashMap.newKeySet();
        private final Set<String> failedTiles = ConcurrentHashMap.newKeySet();
        private final Map<String, BufferedImage> regions = new ConcurrentHashMap<>();
        private final Set<String> loadingRegions = ConcurrentHashMap.newKeySet();
        private final Set<String> failedRegions = ConcurrentHashMap.newKeySet();
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
                    int regionId = ((hover.getX() >> 6) << 8) | (hover.getY() >> 6);
                    setToolTipText("Tile " + format(hover)
                        + " | region " + regionId
                        + " | local " + (hover.getX() & 63) + "," + (hover.getY() & 63)
                        + " | " + (isDungeonSpace(hover) ? "Dungeon" : "Surface")
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

        int getPlane() { return plane; }
        int getZoom() { return zoom; }
        WorldPoint getFirst() { return first; }
        WorldPoint getSecond() { return second; }
        WorldPoint getCentreWorldPoint() { return maxPixelToWorld(centreMaxPixelX, centreMaxPixelY); }

        void setPlane(int value)
        {
            int next = clampPlane(value);
            if (plane == next) return;
            plane = next;
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
            if (valid(first)) plane = clampPlane(first.getPlane());
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
            double maxX = centreMaxPixelX + (screenX - getWidth() / 2.0) / oldScale;
            double maxY = centreMaxPixelY + (screenY - getHeight() / 2.0) / oldScale;
            zoom = nextZoom;
            double newScale = scale();
            centreMaxPixelX = maxX - (screenX - getWidth() / 2.0) / newScale;
            centreMaxPixelY = maxY - (screenY - getHeight() / 2.0) / newScale;
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
                if (!isDungeonSpace(getCentreWorldPoint()))
                {
                    drawSurfaceTiles(g);
                }
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

        private void drawSurfaceTiles(Graphics2D g)
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
                    if (image != null) g.drawImage(image, drawX, drawY, TILE_SIZE, TILE_SIZE, null);
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
            if (regionCount <= 0 || regionCount > 256) return;

            int displayRegionSize = Math.max(1, (int) Math.round(64.0 * RS_TILE_PX * scale()));
            for (int rx = minRegionX; rx <= maxRegionX; rx++)
            {
                for (int ry = minRegionY; ry <= maxRegionY; ry++)
                {
                    int regionId = (rx << 8) | ry;
                    BufferedImage image = getOrRequestRegion(regionId);
                    if (image == null) continue;
                    int baseX = rx << 6;
                    int baseY = ry << 6;
                    int drawX = (int) Math.round((worldEdgeMaxPixelX(baseX) - centreMaxPixelX) * scale() + getWidth() / 2.0);
                    int drawY = (int) Math.round((worldEdgeMaxPixelY(baseY + 64) - centreMaxPixelY) * scale() + getHeight() / 2.0);
                    g.drawImage(image, drawX, drawY, displayRegionSize, displayRegionSize, null);
                }
            }
        }

        private BufferedImage getOrRequestRegion(int regionId)
        {
            String key = plane + "/" + regionId;
            BufferedImage cached = regions.get(key);
            if (cached != null || failedRegions.contains(key)) return cached;
            if (!loadingRegions.add(key)) return null;

            new SwingWorker<BufferedImage, Void>()
            {
                @Override
                protected BufferedImage doInBackground()
                {
                    return readImage(KSP_REGION_BASE + key + ".png");
                }

                @Override
                protected void done()
                {
                    loadingRegions.remove(key);
                    try
                    {
                        BufferedImage image = get();
                        if (image != null) regions.put(key, image);
                        else failedRegions.add(key);
                    }
                    catch (Exception ex)
                    {
                        failedRegions.add(key);
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
            if (cached != null || failedTiles.contains(key)) return cached;
            if (!loadingTiles.add(key)) return null;

            new SwingWorker<BufferedImage, Void>()
            {
                @Override
                protected BufferedImage doInBackground()
                {
                    BufferedImage image = readImage(KSP_TILE_BASE + key + ".png");
                    return image != null ? image : readImage(EXPLV_TILE_BASE + key + ".png");
                }

                @Override
                protected void done()
                {
                    loadingTiles.remove(key);
                    try
                    {
                        BufferedImage image = get();
                        if (image != null) tiles.put(key, image);
                        else failedTiles.add(key);
                    }
                    catch (Exception ex)
                    {
                        failedTiles.add(key);
                    }
                    repaint();
                }
            }.execute();
            return null;
        }

        private BufferedImage readImage(String url)
        {
            try
            {
                URLConnection connection = URI.create(url).toURL().openConnection();
                connection.setConnectTimeout(2500);
                connection.setReadTimeout(5000);
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
            if (first == null || end == null || first.getPlane() != plane || end.getPlane() != plane) return;
            double tileSize = RS_TILE_PX * scale();
            Point a = worldToScreen(first);
            Point b = worldToScreen(end);
            int x = (int) Math.round(Math.min(a.x, b.x) - tileSize / 2.0);
            int y = (int) Math.round(Math.min(a.y, b.y) - tileSize / 2.0);
            int width = Math.max(1, (int) Math.round(Math.abs(a.x - b.x) + tileSize));
            int height = Math.max(1, (int) Math.round(Math.abs(a.y - b.y) + tileSize));
            g.setColor(new Color(51, 181, 229, 70));
            g.fillRect(x, y, width, height);
            g.setColor(new Color(51, 181, 229));
            g.setStroke(new BasicStroke(2f));
            g.drawRect(x, y, width, height);
        }

        private void drawHover(Graphics2D g)
        {
            if (hover == null || hover.getPlane() != plane) return;
            double tileSize = RS_TILE_PX * scale();
            Point p = worldToScreen(hover);
            int x = (int) Math.round(p.x - tileSize / 2.0);
            int y = (int) Math.round(p.y - tileSize / 2.0);
            int size = Math.max(1, (int) Math.round(tileSize));
            g.setColor(new Color(255, 255, 255, 180));
            g.drawRect(x, y, size, size);
        }

        private void drawAttribution(Graphics2D g)
        {
            WorldPoint centre = getCentreWorldPoint();
            boolean dungeon = isDungeonSpace(centre);
            String text = dungeon
                ? "Map: Dungeon | Imagery: KSP RuneLite regions | zoom " + zoom + " | plane " + plane
                : "Map: Surface | Imagery: KSP regions -> KSP tiles -> Explv fallback | zoom " + zoom + " | plane " + plane;
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
