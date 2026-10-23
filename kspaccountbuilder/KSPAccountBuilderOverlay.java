package net.runelite.client.plugins.microbot.kspaccountbuilder;

import java.awt.*;
import java.awt.geom.RoundRectangle2D;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.inject.Inject;
import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.game.Experience;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;

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
    private Skill sampledSkill;
    private boolean compactMode;
    private boolean detailsVisible = true;
    private int sampleStartXp = -1;
    private long sampleStartMs;

    @Inject
    public KSPAccountBuilderOverlay(KspAccountBuilderPlugin plugin, KspAccountBuilderScript script,
                                    KspAccountBuilderConfig config, ItemManager itemManager)
    {
        super(plugin);
        this.plugin = plugin;
        this.script = script;
        this.config = config;
        setPosition(OverlayPosition.TOP_CENTER);
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!plugin.mayRenderOverlay()) return null;

        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            int available = Microbot.getClient() == null ? WIDTH : Microbot.getClient().getCanvasWidth();
            // Do not clip the game on fixed or narrow viewports.
            int width = compactMode ? Math.min(430, Math.max(260, available - 20)) : Math.min(WIDTH, Math.max(260, available - 20));
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
            if (!narrow)
            {
                g.setFont(new Font("SansSerif", Font.PLAIN, 10));
                text(g, "Start / Stop & task settings: Account Builder sidebar", 19, HEIGHT - 7, MUTED);
            }
            return new Dimension(width, height);
        }
        finally
        {
            g.dispose();
        }
    }

    /** Toggle compact HUD without restarting or modifying the running task. */
    void setCompactMode(boolean enabled) { compactMode = enabled; }
    boolean isCompactMode() { return compactMode; }
    void setDetailsVisible(boolean enabled) { detailsVisible = enabled; }

    private void paintTelemetry(Graphics2D g, int x, int y, int width, Skill skill)
    {
        g.setFont(new Font("Serif", Font.BOLD, 18));
        text(g, skill == null ? "Session" : prettify(skill), x, y + 1, GOLD_LIGHT);
        if (skill == null || Microbot.getClient() == null)
        {
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            text(g, "Skill XP: unavailable", x, y + 23, MUTED);
            text(g, "Waiting for skilling task", x, y + 42, MUTED);
            return;
        }
        try
        {
            Client client = Microbot.getClient();
            int xp = client.getSkillExperience(skill);
            int level = client.getRealSkillLevel(skill);
            long now = System.currentTimeMillis();
            if (skill != sampledSkill || xp < sampleStartXp || sampleStartXp < 0
                    || sampleStartMs <= 0L || script.getRuntimeSeconds() == 0L)
            {
                sampledSkill = skill;
                sampleStartXp = xp;
                sampleStartMs = now;
            }
            int gained = Math.max(0, xp - sampleStartXp);
            long elapsed = Math.max(1L, now - sampleStartMs);
            long hourly = elapsed < 60000L ? 0L : (gained * 3600000L) / elapsed;
            boolean capped = level >= 99;
            int floor = Experience.getXpForLevel(Math.max(1, Math.min(99, level)));
            int ceiling = capped ? floor : Experience.getXpForLevel(level + 1);
            double progress = capped ? 1.0 : clamp((double) (xp - floor) / Math.max(1, ceiling - floor));
            g.setFont(new Font("SansSerif", Font.PLAIN, 12));
            text(g, "Level " + level, x, y + 21, IVORY);
            text(g, capped ? "Max level" : formatNumber(xp) + " / " + formatNumber(ceiling) + " XP",
                    x, y + 39, MUTED);
            bar(g, x, y + 48, width, 12, progress, BLUE);
            text(g, "Session XP: " + formatNumber(gained), x, y + 79, IVORY);
            text(g, "XP/hour: " + (hourly == 0L ? "Calculating..." : formatNumber(hourly)),
                    x, y + 98, IVORY);
            String eta = capped ? "Max level" : hourly == 0L ? "Calculating..."
                    : formatDuration(((long) Math.max(0, ceiling - xp) * 3600L) / hourly);
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
