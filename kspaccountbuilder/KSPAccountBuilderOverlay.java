package net.runelite.client.plugins.microbot.kspaccountbuilder;

import java.awt.*;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.api.GameState;
import net.runelite.api.Experience;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.widgets.Widget;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.SkillIconManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayLayer;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

/**
 * Skill-aware, canvas-native status HUD for the existing Account Builder engine.
 *
 * The sidebar remains the authoritative execution control surface. This HUD
 * deliberately does not paint non-functional buttons or intercept game input.
 * Its data comes from live RuneLite skill telemetry and BuilderScript state.
 */
public class KSPAccountBuilderOverlay extends OverlayPanel
{
    private static final Color GOLD = new Color(201, 155, 43);
    private static final Color GOLD_LIGHT = new Color(255, 226, 138);
    private static final Color IVORY = new Color(242, 235, 221);
    private static final Color MUTED = new Color(184, 185, 174);
    private static final Color DARK = new Color(8, 13, 14, 246);
    private static final Color RAISED = new Color(24, 33, 34, 239);
    private static final Color BLUE = new Color(8, 126, 235);
    private static final Color GREEN = new Color(21, 207, 37);
    private static final int WIDTH = 640;
    private static final int HEIGHT = 232;

    private final KspAccountBuilderPlugin plugin;
    private final KspAccountBuilderScript script;
    private final KspAccountBuilderConfig config;
    private final KspCinematicHudRenderer cinematic;
    private Skill sampledSkill;
    private boolean chatboxAnchored;
    private volatile boolean compactMode;
    private volatile boolean hudVisible = true;
    private volatile boolean detailsVisible = true;
    private int sampleStartXp = -1;
    private long sampleStartMs;
    private long sampleRuntimeStartMs = -1L;

    @Inject
    public KSPAccountBuilderOverlay(KspAccountBuilderPlugin plugin, KspAccountBuilderScript script,
                                    KspAccountBuilderConfig config, ItemManager itemManager, SkillIconManager skillIconManager)
    {
        super(plugin);
        this.plugin = plugin;
        this.script = script;
        this.config = config;
        this.cinematic = new KspCinematicHudRenderer(plugin, script, config, skillIconManager);
        setPosition(OverlayPosition.TOP_CENTER);
        setLayer(OverlayLayer.ALWAYS_ON_TOP);
        setMovable(!config.lockHudPosition());
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.mayRenderOverlay() || !hudVisible) return null;
        if (config.cinematicHud() && config.hudFitChatbox())
        {
            Rectangle chat = findChatboxBounds();
            if (chat != null)
            {
                if (!chatboxAnchored)
                {
                    setPosition(OverlayPosition.DYNAMIC);
                    setMovable(false);
                    chatboxAnchored = true;
                }
                Point location = chat.getLocation();
                if (!location.equals(getPreferredLocation()))
                    setPreferredLocation(location);
                return cinematic.render(graphics, chat.width, chat.height);
            }
            // If the user collapses/hides RuneLite chat, do not suddenly
            // place the 1130px HUD on top of the game viewport.
            return null;
        }
        if (chatboxAnchored)
        {
            setPreferredLocation(null);
            setPosition(OverlayPosition.TOP_CENTER);
            setMovable(!config.lockHudPosition());
            chatboxAnchored = false;
        }
        if (!config.cinematicHud()) return renderOriginalPanel(graphics);
        if (config.cinematicHud())
        {
            if (config.blockGameInput() && Microbot.isLoggedIn() && Microbot.getClient() != null)
            {
                Graphics2D dim = (Graphics2D) graphics.create();
                try
                {
                    dim.setColor(new Color(0, 0, 0, 112));
                    dim.fillRect(-getBounds().x, -getBounds().y,
                            Microbot.getClient().getCanvasWidth(), Microbot.getClient().getCanvasHeight());
                }
                finally { dim.dispose(); }
            }
            return cinematic.render(graphics);
        }

        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int requestedScale = config.hudScalePercent();
            // Validate old or externally edited configuration values.
            double scale = (requestedScale == 75 || requestedScale == 100 || requestedScale == 125 || requestedScale == 150)
                    ? requestedScale / 100.0 : 1.0;
            g.scale(scale, scale);
            int available = (int) ((Microbot.getClient() == null ? WIDTH : Microbot.getClient().getCanvasWidth()) / scale);
            if (compactMode)
            {
                int compactWidth = Math.min(500, Math.max(260, available - 20));
                paintCompact(g, compactWidth);
                return new Dimension((int) Math.ceil(compactWidth * scale), (int) Math.ceil(76 * scale));
            }
            // Do not clip the game on fixed or narrow viewports.
            int width = Math.min(WIDTH, Math.max(260, available - 20));
            int height = width < 440 || !detailsVisible ? 183 : HEIGHT;
            paintFrame(g, 0, 0, width, height);

            String task = safe(script.getCurrentTaskName(), "No active task");
            String detail = safe(script.getOverlayTaskDetail(), task);
            String status = normalizeStatus(Microbot.status);
            Skill skill = identifySkill(task + " " + detail);
            int inset = 16;
            boolean narrow = width < 440 || !detailsVisible;

            g.setFont(new Font("Serif", Font.BOLD, narrow ? 17 : 23));
            text(g, "KSP Account Builder", inset, 29, GOLD_LIGHT);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            text(g, "v" + KspAccountBuilderPlugin.VERSION, width - 70, 26, MUTED);
            line(g, 12, 41, width - 12, 41, GOLD);

            int sceneRight = narrow ? width - 14 : width - 221;
            frame(g, 12, 49, sceneRight - 12, narrow ? 78 : 138, RAISED);
            drawSkillEmblem(g, 28, 65, skill);
            g.setFont(new Font("Serif", Font.BOLD, narrow ? 17 : 24));
            text(g, skill == null ? "Account Progression" : prettify(skill), 78, 78, GOLD_LIGHT);
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            clippedText(g, detail, 78, 99, Math.max(40, sceneRight - 88), IVORY);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            clippedText(g, status, 25, narrow ? 117 : 166, sceneRight - 45, MUTED);

            if (!narrow)
            {
                frame(g, width - 213, 49, 201, 138, RAISED);
                paintTelemetry(g, width - 202, 64, 181, skill);
            }

            int footerY = narrow ? 135 : 193;
            frame(g, 12, footerY, width - 24, narrow ? 37 : 28, RAISED);
            g.setFont(new Font("SansSerif", Font.PLAIN, 11));
            String runtime = formatDuration(script.getRuntimeSeconds());
            text(g, "Runtime " + runtime, 22, footerY + 18, IVORY);
            String breakLabel = script.isBreakActive() ? "Break " + formatDuration(script.getBreakTimeRemainingSeconds())
                : config.doBreaks() ? "Next break " + formatDuration(script.getTimeUntilBreakSeconds()) : "Breaks off";
            clippedText(g, breakLabel, narrow ? width / 2 : width - 171, footerY + 18,
                    narrow ? width / 2 - 30 : 155, MUTED);

            return new Dimension((int) Math.ceil(width * scale), (int) Math.ceil(height * scale));
        }
        finally
        {
            g.dispose();
        }
    }

    /**
     * Use RuneLite's chat container for fixed, resizable and modern layouts.
     * The size is sourced from the active widget, never a hard-coded
     * guess about the player's chosen interface scaling.
     */
    private Rectangle findChatboxBounds()
    {
        Client client = Microbot.getClient();
        if (client == null) return null;
        int widgetId;
        if (!client.isResized())
            widgetId = InterfaceID.Toplevel.CHAT_CONTAINER;
        else if (client.getTopLevelInterfaceId() == InterfaceID.TOPLEVEL_PRE_EOC)
            widgetId = InterfaceID.ToplevelPreEoc.CHAT_CONTAINER;
        else
            widgetId = InterfaceID.ToplevelOsrsStretch.CHAT_CONTAINER;
        Widget container = client.getWidget(widgetId);
        if (container == null || container.isHidden()) return null;
        Rectangle bounds = container.getBounds();
        if (bounds == null || bounds.width < 170 || bounds.height < 80) return null;
        Rectangle canvas = new Rectangle(0, 0, client.getCanvasWidth(), client.getCanvasHeight());
        Rectangle visible = bounds.intersection(canvas);
        return visible.width >= 170 && visible.height >= 80 ? visible : null;
    }

    /** Dedicated compact layout; the same script instance supplies all values. */
    private void paintCompact(Graphics2D g, int width)
    {
        paintFrame(g, 0, 0, width, 76);
        g.setFont(new Font("Serif", Font.BOLD, 17));
        text(g, "KSP Account Builder", 12, 20, GOLD_LIGHT);
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        String elapsed = formatDuration(script.getRuntimeSeconds());
        text(g, "Runtime " + elapsed, Math.max(205, width - 116), 18, MUTED);
        line(g, 10, 27, width - 10, 27, GOLD);
        String activity = safe(script.getCurrentTaskName(), "No active task");
        Skill skill = identifySkill(activity + " " + safe(script.getOverlayTaskDetail(), ""));
        drawSkillEmblem(g, 13, 32, skill);
        g.setFont(new Font("Serif", Font.BOLD, 14));
        text(g, skill == null ? "Account Progression" : prettify(skill), 62, 47, GOLD_LIGHT);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        clippedText(g, activity, 62, 65, Math.max(25, width - 74), IVORY);
    }

    /**
     * Exact original Account Builder status fields retained as a recoverable
     * fallback. Cosmetic redevelopment must never remove existing telemetry.
     */
    private Dimension renderOriginalPanel(Graphics2D graphics)
    {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(280, 0));
        String title = "KSP Account Builder V" + KspAccountBuilderPlugin.VERSION;
        if (script.isAnyBreakActive())
            title += script.isBreakActive()
                    ? " | Break " + formatDuration(script.getBreakTimeRemainingSeconds())
                    : " | Break";
        panelComponent.getChildren().add(TitleComponent.builder()
                .text(title).color(Color.CYAN).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Current Task:").right(script.getCurrentTaskName()).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:").right(normalizeStatus(Microbot.status)).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time Running:").right(formatDuration(script.getRuntimeSeconds())).build());
        panelComponent.getChildren().add(LineComponent.builder()
                .left("Account Play Time:").right(formatDuration(script.getAccountPlayTimeSeconds())).build());
        if (config.useAntiban())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Antiban:")
                    .right(plugin.isAntibanEnabledForCurrentTask() ? "ON | Task profile" : "OFF | Task profile")
                    .build());
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Antiban Activity:").right(plugin.getAntibanActivity()).build());
        }
        if (config.doBreaks())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left(script.isBreakActive() ? "Break Remaining:" : "Time till break:")
                    .right(formatDuration(script.isBreakActive()
                            ? script.getBreakTimeRemainingSeconds()
                            : script.getTimeUntilBreakSeconds())).build());
        }
        if (config.enableActivitySwitchRandomization()
                && !config.trainSingleSkill() && !config.runSingleQuest())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time till activity change:")
                    .right(formatDuration(script.getTimeUntilActivitySwitchSeconds())).build());
        }
        return super.render(graphics);
    }

    /** Canvas positions are in RuneLite input coordinates, not UI scale coordinates. */
    void applyPositionLock(boolean locked) { setMovable(!chatboxAnchored && !locked); }

    private boolean insideHud(Point point)
    {
        return point != null && plugin.mayRenderOverlay() && hudVisible
                && config.cinematicHud() && getBounds().contains(point);
    }

    /** Only interactive controls consume mouse events; artwork never does. */
    boolean acceptsMouse(Point point)
    {
        if (!insideHud(point)) return false;
        Point relative = new Point(point.x - getBounds().x, point.y - getBounds().y);
        return cinematic.at(relative) != null;
    }

    boolean clickAt(Point point)
    {
        if (!insideHud(point)) return false;
        Point relative = new Point(point.x - getBounds().x, point.y - getBounds().y);
        String action = cinematic.at(relative);
        if (action == null) return false;
        plugin.dispatchHudAction(action, cinematic);
        return true;
    }

    void hoverAt(Point point)
    {
        if (insideHud(point))
            cinematic.hover(new Point(point.x - getBounds().x, point.y - getBounds().y));
        else cinematic.hover(null);
    }

    /** Toggle compact HUD without restarting or modifying the running task. */
    void setHudVisible(boolean visible) { hudVisible = visible; }
    boolean isHudVisible() { return hudVisible; }
    void setCompactMode(boolean enabled) { compactMode = enabled; }
    boolean isCompactMode() { return compactMode; }
    void setDetailsVisible(boolean enabled) { detailsVisible = enabled; }

    private void paintTelemetry(Graphics2D g, int x, int y, int width, Skill skill)
    {
        g.setFont(new Font("Serif", Font.BOLD, 18));
        text(g, skill == null ? "Session" : prettify(skill), x, y + 1, GOLD_LIGHT);
        if (skill == null || Microbot.getClient() == null || Microbot.getClient().getGameState() != GameState.LOGGED_IN)
        {
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            text(g, "Skill XP: unavailable", x, y + 23, MUTED);
            text(g, "Waiting for live skill telemetry", x, y + 42, MUTED);
            return;
        }
        try
        {
            Client client = Microbot.getClient();
            int xp = client.getSkillExperience(skill);
            int level = client.getRealSkillLevel(skill);
            if (xp < 0 || level < 1)
            {
                g.setFont(new Font("SansSerif", Font.PLAIN, 12));
                text(g, "Waiting for skill data", x, y + 27, MUTED);
                return;
            }
            long now = System.currentTimeMillis();
            long runtimeStart = script.getStartedAtMillis();
            if (skill != sampledSkill || xp < sampleStartXp || sampleStartXp < 0
                    || sampleStartMs <= 0L || runtimeStart != sampleRuntimeStartMs)
            {
                sampledSkill = skill;
                sampleRuntimeStartMs = runtimeStart;
                sampleStartXp = xp;
                sampleStartMs = now;
            }
            int gained = Math.max(0, xp - sampleStartXp);
            long elapsed = Math.max(1L, now - sampleStartMs);
            // A zero-rate initial sample must not be presented as a measured rate.
            // Avoid integer multiplication overflow for very large XP observations.
            long hourly = elapsed < 60000L || gained == 0 ? 0L
                    : (long) Math.min(Long.MAX_VALUE, ((double) gained * 3600000.0) / elapsed);
            boolean capped = level >= 99;
            int floor = Experience.getXpForLevel(Math.max(1, Math.min(99, level)));
            int ceiling = capped ? floor : Experience.getXpForLevel(level + 1);
            double progress = capped ? 1.0 : clamp((double) (xp - floor) / Math.max(1, ceiling - floor));
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            text(g, "Level " + level, x, y + 21, IVORY);
            text(g, capped ? "Max level" : formatNumber(xp) + " / " + formatNumber(ceiling) + " XP",
                    x, y + 39, MUTED);
            bar(g, x, y + 48, width, 12, progress, BLUE);
            text(g, "Tracked XP: " + formatNumber(gained), x, y + 79, IVORY);
            text(g, "XP/hour: " + (hourly == 0L ? "Calculating..." : formatNumber(hourly)),
                    x, y + 98, IVORY);
            String eta = capped ? "Max level" : hourly == 0L ? "Calculating..."
                    : formatDuration((long) Math.ceil(((double) Math.max(0, ceiling - xp) * 3600.0) / hourly));
            text(g, "Next level: " + eta, x, y + 117, IVORY);
        }
        catch (RuntimeException ex)
        {
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            text(g, "XP telemetry unavailable", x, y + 28, MUTED);
        }
    }

    private static Skill identifySkill(String value)
    {
        String v = value.toLowerCase(Locale.ROOT);
        if (v.contains("woodcut") || v.contains("chop") || v.contains("logs")) return Skill.WOODCUTTING;
        if (v.contains("mining") || v.contains("mine ") || v.contains("ore") || v.contains("rocks")) return Skill.MINING;
        if (v.contains("fish")) return Skill.FISHING;
        if (v.contains("runecraft") || v.contains("crafting rune")) return Skill.RUNECRAFT;
        if (v.contains("combat") || v.contains("melee") || v.contains("attack") || v.contains("sand crab")) return Skill.ATTACK;
        if (v.contains("cooking") || v.contains("cook")) return Skill.COOKING;
        if (v.contains("firemaking") || v.contains("light fire")) return Skill.FIREMAKING;
        if (v.contains("smith")) return Skill.SMITHING;
        if (v.contains("craft")) return Skill.CRAFTING;
        return null;
    }

    private static String prettify(Skill skill)
    {
        if (skill == Skill.RUNECRAFT) return "Runecrafting";
        String name = skill.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(name.charAt(0)) + name.substring(1);
    }

    private static void paintFrame(Graphics2D g, int x, int y, int w, int h)
    {
        g.setColor(DARK);
        g.fillRoundRect(x, y, w, h, 9, 9);
        g.setColor(new Color(82, 55, 9));
        g.setStroke(new BasicStroke(3f));
        g.drawRoundRect(x + 1, y + 1, w - 3, h - 3, 9, 9);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(1.5f));
        g.drawRoundRect(x + 4, y + 4, w - 9, h - 9, 7, 7);
        g.setColor(GOLD_LIGHT);
        for (int i = 0; i < 4; i++)
        {
            int xx = i % 2 == 0 ? x + 8 : x + w - 20;
            int yy = i / 2 == 0 ? y + 8 : y + h - 17;
            g.drawLine(xx, yy, xx + 12, yy);
        }
    }

    private static void frame(Graphics2D g, int x, int y, int w, int h, Color fill)
    {
        g.setColor(fill);
        g.fillRoundRect(x, y, Math.max(1, w), Math.max(1, h), 6, 6);
        g.setColor(GOLD);
        g.setStroke(new BasicStroke(1f));
        g.drawRoundRect(x, y, Math.max(1, w), Math.max(1, h), 6, 6);
    }

    private static void drawSkillEmblem(Graphics2D g, int x, int y, Skill skill)
    {
        g.setColor(new Color(20, 32, 25));
        g.fillOval(x, y, 40, 40);
        g.setColor(GOLD_LIGHT);
        g.setStroke(new BasicStroke(2f));
        g.drawOval(x, y, 40, 40);
        String symbol = skill == Skill.WOODCUTTING ? "W" : skill == Skill.MINING ? "M"
            : skill == Skill.FISHING ? "F" : skill == Skill.RUNECRAFT ? "R"
            : skill == Skill.ATTACK ? "C" : skill == null ? "K" : prettify(skill).substring(0, 1);
        g.setFont(new Font("Serif", Font.BOLD, 23));
        text(g, symbol, x + 12, y + 28, GOLD_LIGHT);
    }

    private static void bar(Graphics2D g, int x, int y, int width, int height, double progress, Color foreground)
    {
        g.setColor(new Color(5, 12, 16));
        g.fillRoundRect(x, y, width, height, 5, 5);
        int filled = Math.max(0, (int) Math.round(width * clamp(progress)));
        g.setColor(foreground);
        if (filled > 0) g.fillRoundRect(x, y, filled, height, 5, 5);
        g.setColor(GOLD);
        g.drawRoundRect(x, y, width, height, 5, 5);
    }

    private static double clamp(double n) { return Math.max(0.0, Math.min(1.0, n)); }
    private static void line(Graphics2D g, int x1, int y1, int x2, int y2, Color c)
    {
        g.setColor(c);
        g.drawLine(x1, y1, x2, y2);
    }
    private static void text(Graphics2D g, String value, int x, int y, Color color)
    {
        g.setColor(color);
        g.drawString(value, x, y);
    }
    private static void clippedText(Graphics2D g, String value, int x, int y, int width, Color color)
    {
        if (width < 10) return;
        FontMetrics metrics = g.getFontMetrics();
        String out = safe(value, "—");
        while (out.length() > 1 && metrics.stringWidth(out) > width)
            out = out.substring(0, out.length() - 1);
        if (!out.equals(value) && out.length() > 1) out = out.substring(0, out.length() - 1) + "…";
        text(g, out, x, y, color);
    }
    private static String safe(String value, String fallback)
    {
        return value == null || value.trim().isEmpty() ? fallback : value.trim();
    }
    private static String formatNumber(long n) { return String.format(Locale.ROOT, "%,d", n); }
    private static String formatDuration(long seconds)
    {
        if (seconds < 0L) return "—";
        long hours = TimeUnit.SECONDS.toHours(seconds);
        long minutes = TimeUnit.SECONDS.toMinutes(seconds) % 60;
        long remainder = seconds % 60;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, remainder);
    }
    private static String normalizeStatus(String status)
    {
        String s = safe(status, "Idle");
        if (s.toLowerCase(Locale.ROOT).startsWith("walking to closest bank")) return "Walking to Bank";
        if (s.toLowerCase(Locale.ROOT).startsWith("chopping down "))
            return "Chopping " + s.substring("Chopping Down ".length()).trim();
        return s;
    }
}
