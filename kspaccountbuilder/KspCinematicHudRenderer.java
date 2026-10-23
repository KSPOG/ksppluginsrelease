package net.runelite.client.plugins.microbot.kspaccountbuilder;

import java.awt.*;
import java.awt.geom.*;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.imageio.ImageIO;
import java.util.*;
import java.util.List;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Client;
import net.runelite.api.Experience;
import net.runelite.api.GameState;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * Reference-composition HUD: independent canvas-rendered components, real
 * Account Builder state, proportional hit testing and optional companion UI.
 *
 * No scripted actions run in the paint callback. The registered RuneLite
 * mouse listener dispatches only explicitly enabled actions.
 */
final class KspCinematicHudRenderer
{
    private static final Color INK = new Color(8, 13, 14, 244);
    private static final Color PANEL = new Color(16, 24, 25, 245);
    private static final Color RAISED = new Color(24, 33, 34, 247);
    private static final Color GOLD = new Color(201, 155, 43);
    private static final Color BRIGHT = new Color(255, 226, 138);
    private static final Color IVORY = new Color(242, 235, 221);
    private static final Color MUTED = new Color(184, 185, 174);
    private static final Color XP_BLUE = new Color(8, 126, 235);
    private static final Color TASK_GREEN = new Color(21, 207, 37);
    private static final int FULL_W = 1130, FULL_H = 405;
    private static final int MINI_W = 500, MINI_H = 76;
    private final KspAccountBuilderPlugin plugin;
    private final KspAccountBuilderScript script;
    private final KspAccountBuilderConfig config;
    private final Map<String, Rectangle> hitboxes = new LinkedHashMap<>();
    private final Map<String, BufferedImage> sceneCache = new HashMap<>();
    private final Set<String> unavailableScenes = new HashSet<>();
    private String currentPage = "Skills";
    private boolean settingsOpen;
    private boolean queueOpen;
    private int selectedQueueRow;
    private int queueScrollOffset;
    private boolean collapsed;
    private double scale = 1;
    private int surfaceWidth;
    private int surfaceHeight;
    private String hover = "";
    private Skill observedSkill;
    private long observedRuntimeStart;
    private int observedXp;
    private long observedSince;
    private static final Font TITLE = new Font("Serif", Font.BOLD, 29);
    private static final Font HEADING = new Font("Serif", Font.BOLD, 25);
    private static final Font BODY = new Font("SansSerif", Font.PLAIN, 14);
    private static final Font SMALL = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font BUTTON = new Font("Serif", Font.BOLD, 19);

    KspCinematicHudRenderer(KspAccountBuilderPlugin plugin, KspAccountBuilderScript script, KspAccountBuilderConfig config)
    {
        this.plugin = plugin;
        this.script = script;
        this.config = config;
    }

    Dimension render(Graphics2D graphics)
    {
        int available = Microbot.getClient() == null ? 1130 : Microbot.getClient().getCanvasWidth();
        boolean mini = plugin.isCompactOverlay() || available < 750;
        int contentW = mini ? MINI_W : settingsOpen ? 1395 : FULL_W;
        if (mini) { settingsOpen = false; queueOpen = false; }
        int contentH = mini ? MINI_H : queueOpen ? 557 : FULL_H;
        // Client canvas coordinates and rendered geometry use the same scale.
        int availableH = Microbot.getClient() == null ? contentH : Microbot.getClient().getCanvasHeight();
        double userScale = Math.max(.75, Math.min(1.5, config.hudScalePercent() / 100.0));
        scale = Math.min(userScale, Math.min(Math.max(.4, (available - 16.0) / contentW),
                Math.max(.4, (availableH - 16.0) / contentH)));
        surfaceWidth = contentW;
        surfaceHeight = contentH;
        hitboxes.clear();
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.scale(scale, scale);
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            if (mini) paintMini(g);
            else paintFull(g);
        }
        finally { g.dispose(); }
        return new Dimension((int) Math.ceil(surfaceWidth * scale), (int) Math.ceil(surfaceHeight * scale));
    }

    private void paintFull(Graphics2D g)
    {
        ornamentalFrame(g, 0, 0, FULL_W, FULL_H);
        g.setFont(TITLE);
        label(g, "▥", 17, 34, BRIGHT);
        label(g, "KSP Account Builder", 66, 34, BRIGHT);
        g.setFont(SMALL);
        label(g, "Runtime: " + time(script.getRuntimeSeconds()), 814, 29, IVORY);
        uiButton(g, "minimize", 964, 7, 48, 29, "−", new Color(16, 24, 25));
        uiButton(g, "collapse", 1018, 7, 48, 29, collapsed ? "▾" : "▴", new Color(16, 24, 25));
        uiButton(g, "close", 1072, 7, 48, 29, "×", new Color(16, 24, 25));
        rule(g, 6, 43, 1124, 43);

        ornamentalFrame(g, 8, 49, 158, 292);
        String[] pages = {"Status", "Skills", "Quests", "Items", "Settings"};
        String[] icons = {"▤", "▥", "▧", "◆", "⚙"};
        for (int i = 0; i < pages.length; i++)
        {
            boolean chosen = pages[i].equals(currentPage);
            uiButton(g, "nav:" + pages[i], 16, 62 + i * 48, 142, 40,
                    icons[i] + "   " + pages[i], chosen ? new Color(96, 65, 13) : RAISED);
        }

        ornamentalFrame(g, 170, 49, 664, 292);
        ornamentalFrame(g, 838, 49, 284, 292);
        String task = safe(script.getCurrentTaskName(), "Idle");
        String detail = safe(script.getOverlayTaskDetail(), "Waiting for a task");
        String kind = resolveKind(task);
        if ("Skills".equals(currentPage)) paintSkill(g, task, detail, kind);
        else paintSection(g, currentPage, task, detail, kind);
        paintStats(g, kind);
        uiButton(g, "pause", 39, 351, 187, 39, "Ⅱ   Pause", new Color(124, 82, 6));
        uiButton(g, "resume", 242, 351, 194, 39, "▶   Resume", new Color(4, 116, 35));
        uiButton(g, "stop", 450, 351, 201, 39, "■   Stop", new Color(142, 16, 19));
        uiButton(g, "skip", 668, 351, 205, 39, "▶▶   Skip Task", new Color(21, 41, 59));
        uiButton(g, "settings", 890, 351, 194, 39, "⚙   Settings", new Color(21, 41, 59));
        if (settingsOpen) paintSettings(g);
        if (queueOpen) paintQueue(g);
        paintHover(g);
    }

    private void paintSkill(Graphics2D g, String task, String detail, String kind)
    {
        // The shared layout stays fixed; the visual scene changes with the task.
        Paint previous = g.getPaint();
        g.setPaint(new GradientPaint(173, 52, sceneColor(kind, 0), 830, 338, sceneColor(kind, 1)));
        g.fillRect(174, 52, 656, 285);
        g.setPaint(previous);
        paintScene(g, kind);
        g.setPaint(new GradientPaint(172, 50, new Color(0, 0, 0, 165),
                172, 157, new Color(0, 0, 0, 0)));
        g.fillRect(174, 52, 656, 121);
        g.setPaint(previous);

        medal(g, 220, 95, kind, 39);
        g.setFont(HEADING);
        label(g, displayKind(kind), 270, 88, BRIGHT);
        g.setFont(new Font("Serif", Font.PLAIN, 18));
        ellipsis(g, displayActivity(detail, task), 270, 116, 340, IVORY);

        inner(g, 180, 285, 641, 48);
        g.setFont(new Font("Serif", Font.BOLD, 15));
        ellipsis(g, "Current Task: " + displayActivity(detail, task), 254, 302, 520, IVORY);
        double levelProgress = nextLevelProgress(skillFor(kind));
        drawBar(g, 256, 310, 480, 16, levelProgress, TASK_GREEN);
        g.setFont(SMALL);
        label(g, Double.isFinite(levelProgress)
                ? String.format(Locale.ROOT, "Level: %.1f%%", levelProgress * 100.0)
                : "Progress: —", 742, 323, MUTED);
        circle(g, 213, 309, 23, new Color(69, 47, 12));
        g.setFont(new Font("Serif", Font.BOLD, 21));
        label(g, emblem(kind), 205, 317, BRIGHT);
        hot("tasktip", 180, 285, 641, 48);
    }

    private double nextLevelProgress(Skill skill)
    {
        Client client = Microbot.getClient();
        if (skill == null || client == null || client.getGameState() != GameState.LOGGED_IN)
            return Double.NaN;
        try
        {
            int level = client.getRealSkillLevel(skill), currentXp = client.getSkillExperience(skill);
            if (level < 1 || currentXp < 0) return Double.NaN;
            if (level >= 99) return 1.0;
            int start = Experience.getXpForLevel(level), target = Experience.getXpForLevel(level + 1);
            return KspAccountBuilderHudMath.levelProgress(currentXp, start, target, false);
        }
        catch (RuntimeException e) { return Double.NaN; }
    }

    private void paintSection(Graphics2D g, String page, String task, String detail, String kind)
    {
        g.setFont(HEADING);
        label(g, page, 194, 84, BRIGHT);
        rule(g, 195, 97, 810, 97);
        g.setFont(BODY);
        if ("Status".equals(page))
        {
            label(g, "Execution: " + (script.isManualPaused() ? "Paused" : plugin.isRuntimeActive() ? "Running" : "Stopped"), 199, 132, IVORY);
            label(g, "Task: " + task, 199, 164, IVORY);
            ellipsis(g, "Action: " + detail, 199, 197, 590, IVORY);
            label(g, "Account play time: " + time(script.getAccountPlayTimeSeconds()), 199, 232, MUTED);
        }
        else if ("Quests".equals(page))
        {
            label(g, "Selected quest mode: " + (config.runSingleQuest() ? "Enabled" : "Disabled"), 199, 134, IVORY);
            label(g, "Quest selection is available in the Account Builder sidebar.", 199, 169, MUTED);
            label(g, "Existing quest-completion state is preserved.", 199, 203, MUTED);
        }
        else if ("Items".equals(page))
        {
            label(g, "Current task: " + task, 199, 134, IVORY);
            label(g, "Banking and Grand Exchange remain managed by the existing engine.", 199, 168, MUTED);
        }
        else if ("Settings".equals(page))
        {
            label(g, "HUD scale: " + config.hudScalePercent() + "%", 199, 134, IVORY);
            label(g, "Breaks: " + (config.doBreaks() ? "Enabled" : "Disabled"), 199, 164, IVORY);
            label(g, "Use the Settings button to open the companion panel.", 199, 195, MUTED);
        }
    }

    private void paintStats(Graphics2D g, String kind)
    {
        Skill skill = skillFor(kind);
        medal(g, 884, 93, kind, 33);
        g.setFont(HEADING);
        Client client = Microbot.getClient();
        boolean valid = skill != null && client != null && client.getGameState() == GameState.LOGGED_IN;
        int level = 0, xp = 0, next = 0, previous = 0, gained = 0;
        long rate = 0;
        double progress = Double.NaN;
        if (valid)
        {
            try
            {
                level = client.getRealSkillLevel(skill);
                xp = client.getSkillExperience(skill);
                valid = level >= 1 && xp >= 0;
                if (valid)
                {
                    previous = Experience.getXpForLevel(Math.max(1, Math.min(99, level)));
                    next = level >= 99 ? previous : Experience.getXpForLevel(level + 1);
                    progress = KspAccountBuilderHudMath.levelProgress(xp, previous, next, level >= 99);
                    long now = System.currentTimeMillis(), runId = script.getStartedAtMillis();
                    if (skill != observedSkill || xp < observedXp || observedSince == 0 || runId != observedRuntimeStart)
                    {
                        observedSkill = skill;
                        observedRuntimeStart = runId;
                        observedXp = xp;
                        observedSince = now;
                    }
                    gained = Math.max(0, xp - observedXp);
                    long elapsed = now - observedSince;
                    if (elapsed >= 60000 && gained > 0)
                        rate = KspAccountBuilderHudMath.hourlyXp(gained, elapsed);
                }
            }
            catch (RuntimeException e) { valid = false; }
        }
        label(g, valid ? "Level " + level : "Skill unavailable", 929, 87, BRIGHT);
        drawBar(g, 929, 100, 182, 19, progress, XP_BLUE);
        g.setFont(SMALL);
        label(g, valid ? String.format(Locale.ROOT, "%,d / %,d XP", xp, next)
                : "Waiting for skill telemetry", 938, 115, IVORY);

        inner(g, 849, 132, 262, 132);
        g.setFont(BODY);
        stat(g, 151, "XP Gained:", valid ? String.format(Locale.ROOT, "%,d", gained) : "—");
        stat(g, 178, "XP/Hour:", rate > 0 ? String.format(Locale.ROOT, "%,d", rate) : "Calculating…");
        long remaining = valid ? Math.max(0, next - xp) : -1;
        stat(g, 205, "Time to Level:", level >= 99 ? "Max level" : rate > 0 && remaining >= 0
                ? time(KspAccountBuilderHudMath.etaSeconds(remaining, rate)) : "Calculating…");
        stat(g, 232, activityCounter(kind) + ":", "—");

        inner(g, 849, 273, 262, 57);
        stat(g, 292, "Break Timer:", script.isBreakActive() ? time(script.getBreakTimeRemainingSeconds())
                : time(script.getTimeUntilBreakSeconds()));
        stat(g, 318, "Queue Position:", "Not available");
        hot("xptip", 850, 72, 260, 188);
        hot("queuetip", 850, 274, 260, 58);
    }

    private void stat(Graphics2D g, int y, String caption, String value)
    {
        g.setFont(BODY);
        label(g, "✦ " + caption, 861, y, IVORY);
        g.setFont(SMALL);
        alignRight(g, value, 1102, y, IVORY);
    }

    private void paintMini(Graphics2D g)
    {
        ornamentalFrame(g, 0, 0, MINI_W, MINI_H);
        g.setFont(new Font("Serif", Font.BOLD, 17));
        label(g, "KSP Account Builder", 14, 21, BRIGHT);
        g.setFont(SMALL);
        alignRight(g, "Runtime " + time(script.getRuntimeSeconds()), 405, 20, IVORY);
        rule(g, 7, 26, 493, 26);
        String task = safe(script.getCurrentTaskName(), "Idle");
        String kind = resolveKind(task);
        medal(g, 26, 50, kind, 17);
        g.setFont(new Font("Serif", Font.BOLD, 14));
        label(g, displayKind(kind), 53, 43, BRIGHT);
        g.setFont(SMALL);
        ellipsis(g, script.getOverlayTaskDetail(), 53, 63, 255, IVORY);
        uiButton(g, "restore", 407, 31, 35, 35, "▴", new Color(16, 45, 24));
        uiButton(g, "close", 449, 31, 36, 35, "×", new Color(112, 16, 19));
    }

    private void paintSettings(Graphics2D g)
    {
        // The separate companion panel stays adjacent to the right border.
        int x = 1146, y = 120;
        ornamentalFrame(g, x, y, 224, 276);
        g.setFont(new Font("Serif", Font.BOLD, 23));
        label(g, "⚙ Settings", x + 16, y + 30, BRIGHT);
        uiButton(g, "settings-close", x + 190, y + 7, 27, 28, "×", RAISED);
        toggle(g, "setting:block", x + 16, y + 62, config.blockGameInput(), "Block game input");
        toggle(g, "setting:tooltips", x + 16, y + 96, config.hudTooltips(), "Show tooltips");
        toggle(g, "setting:lock", x + 16, y + 130, config.lockHudPosition(), "Lock overlay position");
        toggle(g, "setting:animation", x + 16, y + 164, config.showSkillAnimations(), "Show skill animations");
        g.setFont(SMALL);
        label(g, "Animation intensity:", x + 16, y + 192, IVORY);
        uiButton(g, "animation-mode", x + 16, y + 198, 190, 26,
                config.hudAnimationIntensity() == 0 ? "Off" :
                config.hudAnimationIntensity() == 1 ? "Subtle" : "Cinematic", RAISED);
        label(g, "UI scale: " + config.hudScalePercent() + "%", x + 16, y + 242, IVORY);
        uiButton(g, "scale-down", x + 142, y + 231, 30, 31, "−", RAISED);
        uiButton(g, "scale-up", x + 179, y + 231, 30, 31, "+", RAISED);
    }

    private void paintQueue(Graphics2D g)
    {
        int y = 412;
        ornamentalFrame(g, 295, y, 540, 138);
        g.setFont(new Font("Serif", Font.BOLD, 19));
        label(g, "TASK QUEUE", 311, y + 25, BRIGHT);
        boolean editing = !plugin.isRuntimeActive();
        uiButton(g, "queue:add", 531, y + 7, 106, 30, "+ Add Task", editing ? RAISED : new Color(30, 32, 32));
        uiButton(g, "queue:up", 644, y + 7, 37, 30, "▲", editing ? RAISED : new Color(30, 32, 32));
        uiButton(g, "queue:down", 686, y + 7, 37, 30, "▼", editing ? RAISED : new Color(30, 32, 32));
        uiButton(g, "queue:remove", 727, y + 7, 42, 30, "−", editing ? RAISED : new Color(30, 32, 32));
        uiButton(g, "queue-close", 794, y + 7, 29, 30, "×", RAISED);
        List<KspAccountBuilderTaskQueue.Entry> items = plugin.isRuntimeActive()
                ? script.getOrderedQueueSnapshot()
                : KspAccountBuilderTaskQueue.parse(config.orderedTaskQueue());
        int count = items.size();
        g.setFont(SMALL);
        if (count == 0)
        {
            label(g, "No queued tasks. Add tasks to schedule sequential skilling.", 313, y + 79, IVORY);
            return;
        }
        queueScrollOffset = Math.max(0, Math.min(queueScrollOffset, Math.max(0, count - 3)));
        for (int visible = 0; visible < 3; visible++)
        {
            int index = visible + queueScrollOffset;
            if (index >= count) break;
            KspAccountBuilderTaskQueue.Entry item = items.get(index);
            int yy = y + 44 + visible * 29;
            g.setColor(index == selectedQueueRow ? new Color(75, 56, 15, 210) : RAISED);
            g.fillRoundRect(307, yy, 514, 27, 4, 4);
            g.setColor(GOLD); g.drawRoundRect(307, yy, 514, 27, 4, 4);
            label(g, (index + 1) + ". " + item.taskName.replace('_', ' '), 319, yy + 18, IVORY);
            String state = "Queued";
            if (plugin.isRuntimeActive() && script.hasOrderedQueue())
            {
                if (index < script.getOrderedQueuePosition() - 1) state = "Completed";
                else if (index == script.getOrderedQueuePosition() - 1)
                    state = script.isManualPaused() ? "Paused" : "Active";
            }
            String progress = state.equals("Active") || state.equals("Paused")
                    ? script.getOrderedElapsedSeconds() + " / " + item.durationSeconds + " s"
                    : "0 / " + item.durationSeconds + " s";
            label(g, progress, 557, yy + 18, MUTED);
            alignRight(g, state, 810, yy + 18, state.equals("Active") ? TASK_GREEN : MUTED);
            hot("queue:row:" + index, 308, yy, 513, 27);
        }
        if (count > 3)
        {
            // Page with the same small controls when the queue exceeds three entries.
            label(g, (queueScrollOffset + 1) + "–" + Math.min(count, queueScrollOffset + 3)
                    + " / " + count, 762, y + 134, MUTED);
        }
    }

    private void toggle(Graphics2D g, String key, int x, int y, boolean value, String title)
    {
        inner(g, x, y - 16, 19, 19);
        if (value) { g.setFont(new Font("SansSerif", Font.BOLD, 15)); label(g, "✓", x + 2, y - 2, XP_BLUE); }
        g.setFont(BODY); label(g, title, x + 29, y - 2, IVORY);
        hot(key, x, y - 18, 225, 25);
    }

    private void paintHover(Graphics2D g)
    {
        if (!config.hudTooltips() || hover.isEmpty()) return;
        String tooltip = hover.equals("xptip") ? "Live XP • rate measured from task observations"
                : hover.equals("tasktip") ? "Current task • progress unavailable unless tracked by script"
                : hover.equals("queuetip") ? "Break schedule • dynamic task selection, no editable queue"
                : hover.startsWith("nav:") ? "Open " + hover.substring(4) + " information" : "";
        if ("pause".equals(hover) && script.isManualPaused()) tooltip = "Already paused";
        if ("resume".equals(hover) && !script.isManualPaused()) tooltip = "Pause before resuming";
        if ("skip".equals(hover) && !script.canSkipTask()) tooltip = "Skip unavailable for this task or mode";
        if (tooltip.isEmpty()) return;
        inner(g, 360, 6, 424, 29);
        g.setFont(SMALL);
        label(g, tooltip, 370, 25, BRIGHT);
    }

    private void uiButton(Graphics2D g, String key, int x, int y, int w, int h, String text, Color fill)
    {
        inner(g, x, y, w, h);
        boolean enabled = !("pause".equals(key) && script.isManualPaused())
                && !("resume".equals(key) && !script.isManualPaused())
                && !("skip".equals(key) && !script.canSkipTask());
        g.setColor(enabled ? fill : new Color(39, 43, 43));
        g.fillRoundRect(x + 2, y + 2, w - 4, h - 4, 6, 6);
        g.setColor(hover.equals(key) ? BRIGHT : GOLD);
        g.drawRoundRect(x + 2, y + 2, w - 4, h - 4, 4, 4);
        g.setFont(BUTTON);
        int tx = x + Math.max(8, (w - g.getFontMetrics().stringWidth(text)) / 2);
        label(g, text, tx, y + h / 2 + 6, enabled ? IVORY : MUTED);
        hot(key, x, y, w, h);
    }

    private void ornamentalFrame(Graphics2D g, int x, int y, int w, int h)
    {
        g.setColor(INK); g.fillRoundRect(x, y, w, h, 8, 8);
        for (int step = 0; step < 3; step++)
        {
            g.setColor(step == 0 ? new Color(82, 55, 9) : step == 1 ? GOLD : BRIGHT);
            g.setStroke(new BasicStroke(step == 2 ? 0.7f : 1.4f));
            g.drawRoundRect(x + step * 2 + 1, y + step * 2 + 1,
                    w - step * 4 - 3, h - step * 4 - 3, 7, 7);
        }
        g.setColor(BRIGHT);
        int[] xs = {x + 5, x + w - 19}, ys = {y + 6, y + h - 19};
        for (int cx : xs) for (int cy : ys)
        {
            g.drawLine(cx, cy, cx + 13, cy);
            g.drawLine(cx, cy, cx, cy + 13);
        }
    }

    private void inner(Graphics2D g, int x, int y, int w, int h)
    {
        g.setColor(RAISED); g.fillRoundRect(x, y, w, h, 6, 6);
        g.setColor(GOLD); g.setStroke(new BasicStroke(1.1f));
        g.drawRoundRect(x, y, w, h, 6, 6);
    }

    private void drawBar(Graphics2D g, int x, int y, int w, int h, double progress, Color color)
    {
        g.setColor(new Color(7, 12, 16));
        g.fillRoundRect(x, y, w, h, 5, 5);
        if (Double.isFinite(progress))
        {
            g.setColor(color);
            int amount = (int) Math.round(w * Math.min(1, Math.max(0, progress)));
            if (amount > 0) g.fillRoundRect(x + 1, y + 1, Math.max(0, amount - 2), h - 2, 5, 5);
        }
        g.setColor(GOLD); g.drawRoundRect(x, y, w, h, 5, 5);
    }

    private void medal(Graphics2D g, int cx, int cy, String kind, int radius)
    {
        circle(g, cx, cy, radius, new Color(14, 25, 19));
        g.setColor(BRIGHT); g.setStroke(new BasicStroke(2f));
        g.drawOval(cx - radius, cy - radius, radius * 2, radius * 2);
        g.setFont(new Font("Serif", Font.BOLD, radius < 20 ? 18 : 27));
        String symbol = emblem(kind);
        label(g, symbol, cx - g.getFontMetrics().stringWidth(symbol) / 2, cy + 9, BRIGHT);
    }

    private void circle(Graphics2D g, int cx, int cy, int radius, Color fill)
    {
        g.setColor(fill); g.fillOval(cx - radius, cy - radius, radius * 2, radius * 2);
    }

    private void rule(Graphics2D g, int x1, int y1, int x2, int y2)
    {
        g.setColor(GOLD); g.drawLine(x1, y1, x2, y2);
    }

    private static void label(Graphics2D g, String t, int x, int y, Color c)
    {
        g.setColor(c); g.drawString(t == null ? "—" : t, x, y);
    }
    private static void alignRight(Graphics2D g, String t, int x, int y, Color c)
    {
        label(g, t, x - g.getFontMetrics().stringWidth(t), y, c);
    }
    private static void ellipsis(Graphics2D g, String text, int x, int y, int width, Color c)
    {
        String t = safe(text, "—");
        FontMetrics f = g.getFontMetrics();
        if (f.stringWidth(t) > width)
        {
            while (t.length() > 1 && f.stringWidth(t + "…") > width) t = t.substring(0, t.length() - 1);
            t += "…";
        }
        label(g, t, x, y, c);
    }

    /**
     * Read cached local artwork, never network resources from the canvas paint
     * callback. JAR-bundled artwork is preferred; source-loaded plugins may use
     * the account-scoped artwork directory. The fallback remains functional.
     */
    private BufferedImage sceneArtwork(String kind)
    {
        String key = kind.toLowerCase(Locale.ROOT);
        if (sceneCache.containsKey(key)) return sceneCache.get(key);
        if (unavailableScenes.contains(key)) return null;
        String filename = key + ".png";
        BufferedImage image = null;
        try (InputStream stream = getClass().getResourceAsStream("cinematic/" + filename))
        {
            if (stream != null) image = ImageIO.read(stream);
        }
        catch (Exception ignored) { }
        if (image == null)
        {
            String base = System.getProperty("ksp.accountbuilder.artworkRoot",
                    Paths.get(System.getProperty("user.home"), ".runelite",
                            "kspaccountbuilder", "artwork").toString());
            Path file = Paths.get(base, filename);
            if (Files.isRegularFile(file))
            {
                try (InputStream stream = Files.newInputStream(file))
                {
                    image = ImageIO.read(stream);
                }
                catch (Exception ignored) { }
            }
        }
        if (image != null) sceneCache.put(key, image);
        else unavailableScenes.add(key);
        return image;
    }

    private void paintScene(Graphics2D g, String kind)
    {
        // Themed locally rendered scene fallback. Future PNG sprites can replace
        // individual scene layers without replacing any surrounding UI/control.
        Shape oldClip = g.getClip();
        g.setClip(174, 52, 656, 230);
        BufferedImage artwork = sceneArtwork(kind);
        if (artwork != null)
        {
            g.drawImage(artwork, 174, 52, 656, 230, null);
            g.setClip(oldClip);
            return;
        }
        java.util.Random random = new java.util.Random(kind.hashCode());
        if ("Woodcutting".equals(kind) || "Fishing".equals(kind))
        {
            for (int i = 0; i < 18; i++)
            {
                int x = 170 + random.nextInt(700), y = 163 + random.nextInt(92);
                g.setColor(new Color(30, 52, 18, 130)); g.fillOval(x, y, 90, 60);
            }
        }
        if ("Fishing".equals(kind))
        {
            g.setColor(new Color(27, 105, 156, 170)); g.fillRoundRect(175, 191, 655, 91, 30, 30);
            for (int i = 0; i < 13; i++) { int y = 200 + i * 7; rule(g, 182, y, 809, y); }
        }
        else if ("Runecrafting".equals(kind))
        {
            for (int i = 0; i < 8; i++)
            {
                int x = 220 + i * 81, y = 200 - (i % 2) * 45;
                g.setColor(new Color(18, 125, 249, 140));
                g.fillPolygon(new int[]{x, x + 22, x + 12, x - 13}, new int[]{y + 42, y - 10, y - 42, y}, 4);
            }
        }
        else if ("Mining".equals(kind))
        {
            for (int i = 0; i < 10; i++)
            {
                int x = 180 + i * 72, y = 240 - random.nextInt(36);
                g.setColor(new Color(88, 79, 63));
                g.fillPolygon(new int[]{x, x + 41, x + 76}, new int[]{y + 41, y - 39, y + 41}, 3);
            }
        }
        else if ("Combat".equals(kind))
        {
            g.setColor(new Color(196, 172, 113, 210)); g.fillRect(175, 213, 655, 67);
            for (int i = 0; i < 3; i++)
            {
                int x = 440 + i * 109, y = 222;
                circle(g, x, y, 23, new Color(215, 195, 136));
                g.setColor(new Color(111, 68, 40)); g.drawOval(x - 20, y - 20, 40, 40);
            }
        }
        else
        {
            g.setColor(new Color(58, 86, 50, 130)); g.fillRect(175, 212, 655, 70);
        }
        // Stylized adventurer silhouette, with a cape and matching tool.
        g.setColor(new Color(45, 72, 34));
        g.fillPolygon(new int[]{485, 459, 488, 510}, new int[]{174, 274, 265, 180}, 4);
        circle(g, 481, 165, 13, new Color(211, 170, 108));
        g.setColor(new Color(98, 66, 29)); g.fillRoundRect(476, 137, 31, 8, 7, 7);
        g.setStroke(new BasicStroke(8f));
        g.setColor(new Color(110, 91, 53));
        g.drawLine(488, 185, 476, 225);
        g.drawLine(499, 188, 526, 207);
        g.drawLine(485, 252, 471, 276);
        g.drawLine(497, 250, 515, 276);
        g.setStroke(new BasicStroke(5f));
        g.drawLine(527, 207, 552, 174);
        if ("Woodcutting".equals(kind))
        {
            g.setColor(new Color(102, 68, 34)); g.fillRect(570, 98, 65, 185);
            g.setColor(new Color(30, 91, 32, 170));
            for (int i = 0; i < 10; i++)
                g.fillOval(529 + (i % 5) * 31, 83 + (i / 5) * 39, 84, 65);
            g.setColor(new Color(220, 121, 30)); g.fillOval(557, 185, 26, 17);
        }
        else if ("Mining".equals(kind))
        {
            g.setColor(new Color(157, 169, 177));
            g.fillPolygon(new int[]{536, 572, 553}, new int[]{175, 157, 192}, 3);
        }
        if (config.showSkillAnimations() && config.hudAnimationIntensity() > 0)
        {
            long phase = System.currentTimeMillis() / (config.hudAnimationIntensity() > 1 ? 180L : 500L);
            java.util.Random sparks = new java.util.Random(kind.hashCode() + phase);
            for (int i = 0; i < (config.hudAnimationIntensity() > 1 ? 9 : 3); i++)
            {
                int sx = 530 + sparks.nextInt(80), sy = 135 + sparks.nextInt(100);
                circle(g, sx, sy, 1 + sparks.nextInt(3), new Color(255, 204, 72, 205));
            }
        }
        g.setClip(oldClip);
    }

    private static Color sceneColor(String kind, int index)
    {
        switch (kind)
        {
            case "Woodcutting": return index == 0 ? new Color(80, 118, 27) : new Color(42, 62, 18);
            case "Mining": return index == 0 ? new Color(115, 80, 47) : new Color(41, 37, 32);
            case "Fishing": return index == 0 ? new Color(55, 133, 146) : new Color(22, 66, 72);
            case "Runecrafting": return index == 0 ? new Color(23, 58, 148) : new Color(21, 31, 67);
            case "Combat": return index == 0 ? new Color(143, 134, 86) : new Color(77, 61, 42);
            default: return index == 0 ? new Color(53, 72, 44) : new Color(20, 30, 27);
        }
    }

    private static String resolveKind(String task)
    {
        if (task == null) return "Status";
        String s = task.toUpperCase(Locale.ROOT);
        if (s.contains("WOODCUT")) return "Woodcutting";
        if (s.contains("MINING") || s.contains("RUNE_ESSENCE")) return "Mining";
        if (s.contains("FISH")) return "Fishing";
        if (s.contains("RUNECRAFT")) return "Runecrafting";
        if (s.contains("MELEE") || s.contains("COMBAT")) return "Combat";
        if (s.contains("CRAFT")) return "Crafting";
        if (s.contains("COOK")) return "Cooking";
        if (s.contains("FIRE")) return "Firemaking";
        if (s.contains("SMITH") || s.contains("SMELT")) return "Smithing";
        return "Status";
    }
    private static Skill skillFor(String k)
    {
        switch (k)
        {
            case "Woodcutting": return Skill.WOODCUTTING;
            case "Mining": return Skill.MINING;
            case "Fishing": return Skill.FISHING;
            case "Runecrafting": return Skill.RUNECRAFT;
            case "Combat": return Skill.ATTACK;
            case "Crafting": return Skill.CRAFTING;
            case "Cooking": return Skill.COOKING;
            case "Firemaking": return Skill.FIREMAKING;
            case "Smithing": return Skill.SMITHING;
            default: return null;
        }
    }
    private static String activityCounter(String k)
    {
        switch (k)
        {
            case "Woodcutting": return "Logs Cut";
            case "Mining": return "Rocks Mined";
            case "Fishing": return "Fish Caught";
            case "Runecrafting": return "Runes Crafted";
            case "Combat": return "NPC Kills";
            default: return "Actions";
        }
    }
    private static String emblem(String kind)
    {
        switch (kind)
        {
            case "Woodcutting": return "♣";
            case "Mining": return "⚒";
            case "Fishing": return "♒";
            case "Runecrafting": return "✧";
            case "Combat": return "⚔";
            default: return "✦";
        }
    }
    private static String displayKind(String k) { return "Status".equals(k) ? "Account Progression" : k; }
    private static String displayActivity(String detail, String task)
    {
        String d = safe(detail, task);
        return d.equalsIgnoreCase("Idle") ? task.replace('_', ' ') : d;
    }
    private static String safe(String v, String fallback) { return v == null || v.trim().isEmpty() ? fallback : v.trim(); }
    private static String time(long total)
    {
        if (total < 0) return "—";
        return String.format(Locale.ROOT, "%02d:%02d:%02d",
                TimeUnit.SECONDS.toHours(total), TimeUnit.SECONDS.toMinutes(total) % 60, total % 60);
    }

    private void hot(String id, int x, int y, int w, int h) { hitboxes.put(id, new Rectangle(x, y, w, h)); }

    /**
     * Pass canvas coordinates relative to the overlay bounds. MouseManager
     * already translates stretched mouse coordinates into canvas space.
     */
    String at(Point relativePoint)
    {
        if (relativePoint == null || scale <= 0) return null;
        int x = KspAccountBuilderHudMath.scaledCoordinate(relativePoint.x, scale);
        int y = KspAccountBuilderHudMath.scaledCoordinate(relativePoint.y, scale);
        String selected = null;
        for (Map.Entry<String, Rectangle> entry : hitboxes.entrySet())
            if (entry.getValue().contains(x, y)) selected = entry.getKey();
        return selected;
    }

    void hover(Point p)
    {
        String key = at(p);
        hover = key == null ? "" : key;
    }

    void openSettings() { settingsOpen = !settingsOpen; }
    void openQueue() { queueOpen = !queueOpen; }
    int selectedQueueRow() { return selectedQueueRow; }
    void selectQueueRow(int row) { selectedQueueRow = Math.max(0, row); }
    void scrollQueue(int direction) { queueScrollOffset = Math.max(0, queueScrollOffset + direction); }
    void toggleCollapsed() { collapsed = !collapsed; if (collapsed) { settingsOpen = false; queueOpen = false; } }
    void navigate(String page) { currentPage = page; if ("Settings".equals(page)) settingsOpen = true; }
}
