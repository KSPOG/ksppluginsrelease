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
import net.runelite.client.game.SkillIconManager;

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
    private static final int CHAT_W = 500, CHAT_H = 174;
    private final KspAccountBuilderPlugin plugin;
    private final KspAccountBuilderScript script;
    private final KspAccountBuilderConfig config;
    private final SkillIconManager skillIcons;
    private final Map<Skill, BufferedImage> iconCache = new EnumMap<>(Skill.class);
    // Paint into a private map and publish an immutable hitbox snapshot.
    // Mouse callbacks and RuneLite paint can execute on different threads.
    private final Map<String, Rectangle> paintingHitboxes = new LinkedHashMap<>();
    private volatile Map<String, Rectangle> hitboxes = Collections.emptyMap();
    private final java.util.concurrent.ConcurrentMap<String, BufferedImage> sceneCache =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final java.util.concurrent.ConcurrentMap<String, Long> nextArtworkRetry =
            new java.util.concurrent.ConcurrentHashMap<>();
    private final Set<String> artworkLoading = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private final Set<String> missingArtwork = java.util.concurrent.ConcurrentHashMap.newKeySet();
    private volatile String currentPage = "Skills";
    private volatile boolean settingsOpen;
    private volatile boolean queueOpen;
    private volatile int selectedQueueRow;
    private volatile int queueScrollOffset;
    private volatile boolean collapsed;
    private volatile double scale = 1;
    private volatile int surfaceWidth;
    private volatile int surfaceHeight;
    private volatile String hover = "";
    private volatile Point hoverPoint;
    private Skill observedSkill;
    private long observedRuntimeStart;
    private int observedXp;
    private long observedSince;
    private static final Font TITLE = new Font("Serif", Font.BOLD, 29);
    private static final Font HEADING = new Font("Serif", Font.BOLD, 25);
    private static final Font BODY = new Font("SansSerif", Font.PLAIN, 14);
    private static final Font SMALL = new Font("SansSerif", Font.PLAIN, 12);
    private static final Font BUTTON = new Font("Serif", Font.BOLD, 19);

    KspCinematicHudRenderer(KspAccountBuilderPlugin plugin, KspAccountBuilderScript script,
                            KspAccountBuilderConfig config, SkillIconManager skillIcons)
    {
        this.plugin = plugin;
        this.script = script;
        this.config = config;
        this.skillIcons = skillIcons;
    }

    Dimension render(Graphics2D graphics)
    {
        return render(graphics, 0, 0);
    }

    /** Render within the live chat-container bounds when supplied. */
    Dimension render(Graphics2D graphics, int chatWidth, int chatHeight)
    {
        boolean chatbox = chatWidth > 0 && chatHeight > 0;
        int available = chatbox ? chatWidth :
                Microbot.getClient() == null ? FULL_W : Microbot.getClient().getCanvasWidth();
        boolean mini = plugin.isCompactOverlay() || available < 750;
        int contentW = chatbox ? chatWidth : mini ? MINI_W : settingsOpen ? 1395 : FULL_W;
        int contentH = chatbox ? chatHeight : mini ? (settingsOpen ? 334 : MINI_H)
                : queueOpen ? 557 : FULL_H;
        if (mini || chatbox) queueOpen = false;
        double userScale = Math.max(.75, Math.min(1.5, config.hudScalePercent() / 100.0));
        int availableH = Microbot.getClient() == null ? contentH : Microbot.getClient().getCanvasHeight();
        scale = chatbox ? 1.0 : Math.min(userScale,
                Math.min(Math.max(.4, (available - 16.0) / contentW),
                        Math.max(.4, (availableH - 16.0) / contentH)));
        surfaceWidth = contentW;
        surfaceHeight = contentH;
        paintingHitboxes.clear();
        Graphics2D g = (Graphics2D) graphics.create();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            g.setRenderingHint(RenderingHints.KEY_FRACTIONALMETRICS, RenderingHints.VALUE_FRACTIONALMETRICS_ON);
            if (chatbox)
                paintChatboxHd(g, chatWidth, chatHeight);
            else
            {
                g.scale(scale, scale);
                if (mini)
                {
                    paintMini(g);
                    if (settingsOpen) paintCompactSettings(g);
                }
                else paintFull(g);
            }
        }
        finally
        {
            hitboxes = Collections.unmodifiableMap(new LinkedHashMap<>(paintingHitboxes));
            g.dispose();
        }
        return new Dimension((int) Math.ceil(surfaceWidth * scale),
                (int) Math.ceil(surfaceHeight * scale));
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
        // Green means TASK progress, never the blue next-skill-level XP.
        // A duration-target queue is the only task type with an actual
        // completed/target metric currently exported by the builder.
        double taskProgress = Double.NaN;
        String taskProgressText = "Progress: —";
        if (script.hasOrderedQueue())
        {
            List<KspAccountBuilderTaskQueue.Entry> entries = script.getOrderedQueueSnapshot();
            int index = script.getOrderedQueuePosition() - 1;
            if (index >= 0 && index < entries.size())
            {
                long targetSeconds = entries.get(index).durationSeconds;
                long completedSeconds = script.isOrderedQueueComplete()
                        ? targetSeconds : Math.min(targetSeconds, script.getOrderedElapsedSeconds());
                if (targetSeconds > 0L)
                {
                    taskProgress = KspAccountBuilderHudMath.taskDurationProgress(completedSeconds, targetSeconds);
                    taskProgressText = String.format(Locale.ROOT, "%d / %d s (%.1f%%)",
                            completedSeconds, targetSeconds, taskProgress * 100.0);
                }
            }
        }
        drawBar(g, 256, 310, 480, 16, taskProgress, TASK_GREEN);
        g.setFont(SMALL);
        label(g, taskProgressText, 742, 323, MUTED);
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
        label(g, valid ? String.format(Locale.ROOT, "%,d / %,d XP (%.1f%%)",
                xp, next, progress * 100.0) : "Waiting for skill telemetry", 938, 115, IVORY);

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
                : script.isAnyBreakActive() ? "Break active" : time(script.getTimeUntilBreakSeconds()));
        stat(g, 318, "Queue Position:",
                script.hasOrderedQueue()
                    ? script.getOrderedQueuePosition() + " / " + script.getOrderedQueueSize()
                    : "Automatic");
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

    /**
     * Chatbox-specific composition (500x174 logical units). Keeps full
     * cinematic skill artwork and working controls without covering the
     * game viewport. Do not scale the 1130px display down to unreadable text.
     */
    /**
     * Classic Cinematic miniature: preserve the reference composition rather
     * than replacing its left rail with horizontal tabs. Fixed logical bounds
     * are scaled uniformly to the actual chatbox by render(Graphics2D,int,int).
     *
     * 0..81 navigation | 84..325 live skill scene | 329..493 telemetry.
     */
    /**
     * Native-resolution chatbox compositor. Every shape, image and glyph is
     * rendered at its final pixel coordinates; no 500x174 bitmap scaling.
     */
    private void paintChatboxHd(Graphics2D g, int w, int h)
    {
        final int p = Math.max(3, h / 65);
        final int gap = Math.max(3, h / 65);
        final int header = Math.max(24, h / 8);
        final int footer = Math.max(26, h / 6);
        final int top = header + gap;
        final int bottom = h - footer - gap;
        final int bodyH = Math.max(35, bottom - top - gap);
        final int navW = Math.max(65, Math.min(92, (int) (w * 0.12)));
        final int statsW = Math.max(145, (int) (w * 0.29));
        final int navX = p;
        final int sceneX = navX + navW + gap;
        final int sceneW = Math.max(80, w - sceneX - statsW - gap - p);
        final int statsX = sceneX + sceneW + gap;

        ornamentalFrame(g, 0, 0, w, h);
        g.setFont(new Font("Serif", Font.BOLD, Math.max(17, h / 11)));
        ellipsis(g, "KSP Account Builder", p + 7, header - 6, w - 120, BRIGHT);
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, h / 20)));
        alignRight(g, time(script.getRuntimeSeconds()), w - p - 6, header - 7, IVORY);
        rule(g, p + 3, header, w - p - 3, header);

        ornamentalFrame(g, navX, top, navW, bodyH);
        String[] pages = {"Status", "Skills", "Quests", "Items", "Settings"};
        int navGap = Math.max(2, bodyH / 65);
        int navButtonH = Math.max(10, (bodyH - 8 - navGap * 4) / 5);
        for (int i = 0; i < pages.length; i++)
        {
            int y = top + 4 + i * (navButtonH + navGap);
            uiButton(g, "nav:" + pages[i], navX + 3, y,
                    navW - 6, navButtonH, pages[i],
                    pages[i].equals(currentPage) ? new Color(96, 65, 13) : RAISED);
        }

        if ("Skills".equals(currentPage) && !settingsOpen)
        {
            String task = safe(script.getCurrentTaskName(), "Idle");
            String kind = resolveKind(task);
            ornamentalFrame(g, sceneX, top, sceneW, bodyH);
            Shape oldClip = g.getClip();
            g.clipRect(sceneX + 3, top + 3, sceneW - 6, bodyH - 6);
            BufferedImage scene = sceneArtwork(kind);
            if (scene != null)
            {
                // Cover rather than stretch: retain source aspect ratio.
                double ratio = Math.max((sceneW - 6.0) / scene.getWidth(),
                        (bodyH - 6.0) / scene.getHeight());
                int iw = (int) Math.ceil(scene.getWidth() * ratio);
                int ih = (int) Math.ceil(scene.getHeight() * ratio);
                g.drawImage(scene, sceneX + (sceneW - iw) / 2,
                        top + (bodyH - ih) / 2, iw, ih, null);
            }
            else
            {
                g.setColor(new Color(31, 53, 44));
                g.fillRect(sceneX + 3, top + 3, sceneW - 6, bodyH - 6);
            }
            g.setClip(oldClip);
            g.setColor(new Color(4, 9, 13, 220));
            g.fillRoundRect(sceneX + 5, top + 5, sceneW - 10, 22, 5, 5);
            g.setFont(new Font("Serif", Font.BOLD, Math.max(12, bodyH / 10)));
            ellipsis(g, task.replace('_', ' '), sceneX + 10, top + 20,
                    sceneW - 20, BRIGHT);
            g.setColor(new Color(4, 9, 13, 225));
            g.fillRoundRect(sceneX + 5, bottom - 27, sceneW - 10, 20, 5, 5);
            g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, bodyH / 12)));
            ellipsis(g, (script.isManualPaused() ? "PAUSED · " : "")
                    + safe(script.getOverlayTaskDetail(), "Idle"),
                    sceneX + 10, bottom - 13, sceneW - 20, IVORY);
            double progress = Double.NaN;
            if (script.hasOrderedQueue())
            {
                List<KspAccountBuilderTaskQueue.Entry> entries = script.getOrderedQueueSnapshot();
                int index = script.getOrderedQueuePosition() - 1;
                if (index >= 0 && index < entries.size())
                    progress = KspAccountBuilderHudMath.taskDurationProgress(
                            script.isOrderedQueueComplete() ? entries.get(index).durationSeconds
                                    : script.getOrderedElapsedSeconds(),
                            entries.get(index).durationSeconds);
            }
            drawBar(g, sceneX + 10, bottom - 6, sceneW - 20, 3, progress, TASK_GREEN);
            paintChatboxStatsHd(g, statsX, top, statsW, bodyH, kind);
        }
        else
        {
            ornamentalFrame(g, sceneX, top, w - sceneX - p, bodyH);
            g.setFont(new Font("Serif", Font.BOLD, Math.max(14, bodyH / 7)));
            label(g, currentPage, sceneX + 10, top + 19, BRIGHT);
            g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, bodyH / 11)));
            if ("Settings".equals(currentPage) || settingsOpen)
            {
                toggle(g, "setting:tooltips", sceneX + 10, top + 28,
                        config.hudTooltips(), "Tooltips");
                toggle(g, "setting:animation", sceneX + 10, top + 57,
                        config.showSkillAnimations(), "Animations");
                toggle(g, "setting:block", sceneX + sceneW + 8, top + 28,
                        config.blockGameInput(), "Block input");
                uiButton(g, "scale-down", w - 103, bottom - 34, 42, 26, "−", RAISED);
                uiButton(g, "scale-up", w - 55, bottom - 34, 42, 26, "+", RAISED);
            }
            else
            {
                String task = safe(script.getCurrentTaskName(), "Idle");
                String detail = safe(script.getOverlayTaskDetail(), "Waiting");
                ellipsis(g, "Task: " + task, sceneX + 10, top + 39,
                        w - sceneX - 22, IVORY);
                ellipsis(g, "Action: " + detail, sceneX + 10, top + 61,
                        w - sceneX - 22, IVORY);
                label(g, "Queue " + (script.hasOrderedQueue()
                        ? script.getOrderedQueuePosition() + "/" + script.getOrderedQueueSize()
                        : "Automatic"), sceneX + 10, top + 83, MUTED);
            }
        }

        String[] ids = {"pause", "resume", "stop", "skip", "settings", "nav:Queue"};
        String[] labels = {"Ⅱ Pause", "▶ Resume", "■ Stop", "▶▶ Skip", "⚙ Settings", "Queue"};
        Color[] colors = {new Color(124, 82, 6), new Color(4, 116, 35),
                new Color(142, 16, 19), new Color(21, 41, 59),
                new Color(21, 41, 59), RAISED};
        int btnGap = 3;
        int usable = w - 2 * p - 5 * btnGap;
        int x = p;
        for (int i = 0; i < ids.length; i++)
        {
            int width = (i == 5) ? w - p - x : usable / 6;
            uiButton(g, ids[i], x, h - footer, width, footer - p,
                    labels[i], colors[i]);
            x += width + btnGap;
        }
    }

    private void paintChatboxStatsHd(Graphics2D g, int x, int y, int w, int h, String kind)
    {
        ornamentalFrame(g, x, y, w, h);
        Client client = Microbot.getClient();
        Skill skill = skillFor(kind);
        boolean valid = client != null && skill != null
                && client.getGameState() == GameState.LOGGED_IN;
        int level = 0, xp = 0, next = 0;
        long gained = 0, rate = 0, remaining = -1;
        double progress = Double.NaN;
        if (valid)
        {
            try
            {
                xp = client.getSkillExperience(skill);
                level = client.getRealSkillLevel(skill);
                valid = xp >= 0 && level >= 1;
                if (valid)
                {
                    int startXp = Experience.getXpForLevel(Math.min(99, level));
                    next = level >= 99 ? startXp : Experience.getXpForLevel(level + 1);
                    progress = KspAccountBuilderHudMath.levelProgress(xp, startXp,
                            next, level >= 99);
                    long now = System.currentTimeMillis();
                    long runId = script.getStartedAtMillis();
                    if (skill != observedSkill || xp < observedXp
                            || observedSince == 0 || runId != observedRuntimeStart)
                    {
                        observedSkill = skill;
                        observedRuntimeStart = runId;
                        observedXp = xp;
                        observedSince = now;
                    }
                    gained = Math.max(0L, (long) xp - observedXp);
                    long elapsed = Math.max(0L, now - observedSince);
                    if (elapsed >= 60000L)
                        rate = KspAccountBuilderHudMath.hourlyXp(gained, elapsed);
                    remaining = level >= 99 ? 0 : Math.max(0, next - xp);
                }
            }
            catch (RuntimeException ex) { valid = false; }
        }
        int left = x + 7, right = x + w - 7;
        g.setFont(new Font("Serif", Font.BOLD, Math.max(12, h / 9)));
        ellipsis(g, displayKind(kind), left, y + 17, w - 14, BRIGHT);
        g.setFont(new Font("SansSerif", Font.PLAIN, Math.max(10, h / 11)));
        label(g, valid ? "Lv " + level : "XP unavailable", left, y + 33, IVORY);
        alignRight(g, valid ? briefXp(xp) + "/" + briefXp(next) : "—",
                right, y + 33, MUTED);
        drawBar(g, left, y + 39, w - 14, 7, progress, XP_BLUE);
        int lineY = y + Math.max(58, h / 2);
        int step = Math.max(12, (h - (lineY - y) - 8) / 4);
        label(g, "XP +" + String.format(Locale.ROOT, "%,d", gained), left, lineY, IVORY);
        alignRight(g, rate > 0 ? String.format(Locale.ROOT, "%,d/h", rate) : "—/h",
                right, lineY, IVORY);
        label(g, "Next:", left, lineY + step, MUTED);
        alignRight(g, !valid ? "—" : remaining == 0 ? "Max level"
                : rate > 0 ? time(KspAccountBuilderHudMath.etaSeconds(remaining, rate))
                        : "Calculating", right, lineY + step, IVORY);
        label(g, "Break:", left, lineY + step * 2, MUTED);
        alignRight(g, script.isBreakActive() ? time(script.getBreakTimeRemainingSeconds())
                : script.isAnyBreakActive() ? "Active"
                        : time(script.getTimeUntilBreakSeconds()),
                right, lineY + step * 2, IVORY);
        label(g, "Queue:", left, lineY + step * 3, MUTED);
        alignRight(g, script.hasOrderedQueue()
                ? script.getOrderedQueuePosition() + "/" + script.getOrderedQueueSize()
                : "Automatic", right, lineY + step * 3, IVORY);
    }

    private void paintChatbox(Graphics2D g)
    {
        ornamentalFrame(g, 0, 0, CHAT_W, CHAT_H);
        g.setFont(new Font("Serif", Font.BOLD, 17));
        ellipsis(g, "KSP Account Builder", 10, 20, 315, BRIGHT);
        g.setFont(new Font("SansSerif", Font.PLAIN, 10));
        alignRight(g, time(script.getRuntimeSeconds()), 488, 19, IVORY);
        rule(g, 8, 28, 492, 28);

        // Reference: persistent left-hand navigation, not a tab toolbar.
        ornamentalFrame(g, 5, 32, 76, 104);
        String[] pages = {"Status", "Skills", "Quests", "Items", "Settings"};
        for (int i = 0; i < pages.length; i++)
        {
            String page = pages[i];
            uiButton(g, "nav:" + page, 9, 35 + i * 20, 68, 18, page,
                    page.equals(currentPage) ? new Color(96, 65, 13) : RAISED);
        }

        if (settingsOpen || "Settings".equals(currentPage))
        {
            // Settings have their own page but maintain the same frame.
            ornamentalFrame(g, 84, 32, 409, 104);
            g.setFont(new Font("Serif", Font.BOLD, 14));
            label(g, "HUD SETTINGS", 92, 48, BRIGHT);
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            toggle(g, "setting:tooltips", 92, 55, config.hudTooltips(), "Tooltips");
            toggle(g, "setting:animation", 92, 83, config.showSkillAnimations(), "Animations");
            toggle(g, "setting:block", 290, 55, config.blockGameInput(), "Block input");
            label(g, "Scale " + config.hudScalePercent() + "%", 297, 109, IVORY);
            uiButton(g, "scale-down", 386, 87, 42, 30, "−", RAISED);
            uiButton(g, "scale-up", 439, 87, 42, 30, "+", RAISED);
        }
        else if (!"Skills".equals(currentPage))
        {
            // Full-size page details must stay in the content zone, leaving
            // the reference navigation and action rail fixed in position.
            paintChatboxPage(g);
        }
        else
        {
            String task = safe(script.getCurrentTaskName(), "Idle");
            String kind = resolveKind(task);
            ornamentalFrame(g, 84, 32, 241, 104);
            Shape clip = g.getClip();
            g.clipRect(87, 35, 235, 98);
            BufferedImage artwork = sceneArtwork(kind);
            if (artwork != null)
                g.drawImage(artwork, 87, 35, 235, 98, null);
            else
            {
                g.setColor(new Color(31, 53, 44));
                g.fillRect(87, 35, 235, 98);
                g.setFont(new Font("Serif", Font.BOLD, 18));
                ellipsis(g, displayKind(kind), 92, 78, 223, BRIGHT);
            }
            g.setClip(clip);

            // Overlay text stays inside the scene and is never drawn over
            // the statistics panel or the six bottom action buttons.
            g.setColor(new Color(4, 9, 13, 219));
            g.fillRoundRect(88, 37, 233, 19, 4, 4);
            g.setFont(new Font("Serif", Font.BOLD, 12));
            ellipsis(g, task.replace('_', ' '), 94, 51, 218, BRIGHT);

            g.setColor(new Color(4, 9, 13, 225));
            g.fillRoundRect(88, 108, 233, 23, 4, 4);
            g.setFont(new Font("SansSerif", Font.PLAIN, 10));
            ellipsis(g, script.isManualPaused() ? "PAUSED · " + script.getOverlayTaskDetail()
                    : script.getOverlayTaskDetail(), 94, 119, 217, IVORY);
            double taskProgress = Double.NaN;
            if (script.hasOrderedQueue())
            {
                List<KspAccountBuilderTaskQueue.Entry> entries = script.getOrderedQueueSnapshot();
                int index = script.getOrderedQueuePosition() - 1;
                if (index >= 0 && index < entries.size())
                {
                    long target = entries.get(index).durationSeconds;
                    long elapsed = script.isOrderedQueueComplete() ? target
                            : script.getOrderedElapsedSeconds();
                    taskProgress = KspAccountBuilderHudMath.taskDurationProgress(elapsed, target);
                }
            }
            drawBar(g, 94, 124, 219, 4, taskProgress, TASK_GREEN);
            paintChatboxStats(g, kind);
        }

        uiButton(g, "pause", 5, 141, 84, 27, "Ⅱ Pause", new Color(124, 82, 6));
        uiButton(g, "resume", 92, 141, 84, 27, "▶ Resume", new Color(4, 116, 35));
        uiButton(g, "stop", 179, 141, 76, 27, "■ Stop", new Color(142, 16, 19));
        uiButton(g, "skip", 258, 141, 70, 27, "▶▶ Skip", new Color(21, 41, 59));
        uiButton(g, "settings", 331, 141, 94, 27, "⚙ Settings", new Color(21, 41, 59));
        uiButton(g, "nav:Queue", 428, 141, 67, 27, "Queue", RAISED);
    }

    /** Full information categories reflowed into the chatbox footprint. */
    private void paintChatboxPage(Graphics2D g)
    {
        ornamentalFrame(g, 84, 33, 409, 103);
        g.setFont(new Font("Serif", Font.BOLD, 15));
        label(g, currentPage, 92, 49, BRIGHT);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        String task = safe(script.getCurrentTaskName(), "Idle");
        String detail = safe(script.getOverlayTaskDetail(), "Waiting for a task");
        switch (currentPage)
        {
            case "Status":
                ellipsis(g, "Execution: " + (script.isManualPaused() ? "Paused"
                        : plugin.isRuntimeActive() ? "Running" : "Stopped"), 92, 67, 190, IVORY);
                ellipsis(g, "Task: " + task, 92, 83, 190, IVORY);
                ellipsis(g, "Action: " + detail, 92, 99, 190, MUTED);
                label(g, "Account: " + time(script.getAccountPlayTimeSeconds()), 292, 67, IVORY);
                label(g, "Break: " + time(script.getTimeUntilBreakSeconds()), 292, 83, IVORY);
                label(g, "Runtime: " + time(script.getRuntimeSeconds()), 292, 99, IVORY);
                break;
            case "Quests":
                label(g, "Single quest: " + (config.runSingleQuest() ? "Enabled" : "Disabled"), 92, 70, IVORY);
                ellipsis(g, "Current task: " + task, 92, 89, 386, IVORY);
                label(g, "Quest selection: Account Builder sidebar", 92, 109, MUTED);
                break;
            case "Items":
                ellipsis(g, "Current task: " + task, 92, 70, 386, IVORY);
                ellipsis(g, "Activity: " + detail, 92, 89, 386, IVORY);
                label(g, "Inventory, banking and GE: automatic", 92, 109, MUTED);
                break;
            case "Queue":
                label(g, "Mode: " + (script.hasOrderedQueue() ? "Ordered" : "Automatic"), 92, 69, IVORY);
                label(g, "Position: " + (script.hasOrderedQueue()
                        ? script.getOrderedQueuePosition() + " / " + script.getOrderedQueueSize()
                        : "—"), 92, 89, IVORY);
                label(g, "Edit tasks in the RuneLite side panel", 92, 109, MUTED);
                break;
            default:
                ellipsis(g, task + " · " + detail, 92, 81, 380, IVORY);
        }
    }

    /** Compact telemetry: separate XP, next-level, break and queue fields. */
    private void paintChatboxStats(Graphics2D g, String kind)
    {
        ornamentalFrame(g, 329, 32, 164, 104);
        Client client = Microbot.getClient();
        Skill skill = skillFor(kind);
        boolean valid = client != null && skill != null
                && client.getGameState() == GameState.LOGGED_IN;
        g.setFont(new Font("Serif", Font.BOLD, 14));
        ellipsis(g, displayKind(kind), 337, 46, 146, BRIGHT);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        long gained = 0L, rate = 0L;
        long remaining = -1L;
        double progress = Double.NaN;
        if (valid)
        {
            try
            {
                int xp = client.getSkillExperience(skill);
                int level = client.getRealSkillLevel(skill);
                valid = xp >= 0 && level >= 1;
                if (valid)
                {
                    int levelStart = Experience.getXpForLevel(Math.min(99, level));
                    int levelEnd = level >= 99 ? levelStart : Experience.getXpForLevel(level + 1);
                    progress = KspAccountBuilderHudMath.levelProgress(xp, levelStart,
                            levelEnd, level >= 99);
                    label(g, "Lv " + level, 337, 60, IVORY);
                    alignRight(g, level >= 99 ? "MAX XP" :
                            briefXp(xp) + " / " + briefXp(levelEnd) + " XP",
                            484, 60, MUTED);
                    long now = System.currentTimeMillis();
                    long runId = script.getStartedAtMillis();
                    if (skill != observedSkill || xp < observedXp || observedSince == 0
                            || runId != observedRuntimeStart)
                    {
                        observedSkill = skill;
                        observedRuntimeStart = runId;
                        observedXp = xp;
                        observedSince = now;
                    }
                    gained = Math.max(0L, (long) xp - observedXp);
                    long elapsed = Math.max(0L, now - observedSince);
                    if (elapsed >= 60000L)
                        rate = KspAccountBuilderHudMath.hourlyXp(gained, elapsed);
                    remaining = level >= 99 ? 0L : Math.max(0, levelEnd - xp);
                }
            }
            catch (RuntimeException ignored) { valid = false; }
        }
        if (!valid) label(g, "XP unavailable", 337, 60, MUTED);
        drawBar(g, 337, 65, 147, 7, progress, XP_BLUE);
        g.setFont(new Font("SansSerif", Font.PLAIN, 11));
        label(g, "XP +" + String.format(Locale.ROOT, "%,d", gained), 337, 86, IVORY);
        alignRight(g, rate > 0L ? String.format(Locale.ROOT, "%,d/h", rate)
                : "—/h", 485, 86, IVORY);
        label(g, "Next:", 337, 99, MUTED);
        alignRight(g, !valid ? "—" : remaining == 0L ? "Max level"
                        : rate > 0L ? time(KspAccountBuilderHudMath.etaSeconds(remaining, rate))
                        : "Calculating", 485, 99, IVORY);
        label(g, "Break:", 337, 112, MUTED);
        alignRight(g, script.isBreakActive() ? time(script.getBreakTimeRemainingSeconds())
                : script.isAnyBreakActive() ? "Active"
                : time(script.getTimeUntilBreakSeconds()), 485, 112, IVORY);
        label(g, "Queue:", 337, 125, MUTED);
        alignRight(g, script.hasOrderedQueue()
                ? script.getOrderedQueuePosition() + "/" + script.getOrderedQueueSize()
                : "Automatic", 485, 125, IVORY);
        // Stats are informational in chatbox mode: they must not capture
        // synthetic game clicks. Only the six actual buttons use hitboxes.
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
        // Reserve room for a real task-progress bar, independently of XP.
        ellipsis(g, script.getOverlayTaskDetail(), 53, 63, 115, IVORY);
        double progress = Double.NaN;
        String progressLabel = "—";
        if (script.hasOrderedQueue())
        {
            List<KspAccountBuilderTaskQueue.Entry> entries = script.getOrderedQueueSnapshot();
            int index = script.getOrderedQueuePosition() - 1;
            if (index >= 0 && index < entries.size())
            {
                long target = entries.get(index).durationSeconds;
                if (target > 0)
                {
                    long completed = script.isOrderedQueueComplete()
                            ? target : Math.min(target, script.getOrderedElapsedSeconds());
                    progress = KspAccountBuilderHudMath.taskDurationProgress(completed, target);
                    progressLabel = String.format(Locale.ROOT, "%d/%ds (%.0f%%)",
                            completed, target, progress * 100.0);
                }
            }
        }
        g.setFont(SMALL);
        label(g, progressLabel, 177, 46, MUTED);
        drawBar(g, 176, 55, 139, 10, progress, TASK_GREEN);
        uiButton(g, "pause", 327, 33, 30, 34, "Ⅱ", new Color(124, 82, 6));
        uiButton(g, "resume", 362, 33, 30, 34, "▶", new Color(4, 116, 35));
        uiButton(g, "stop", 397, 33, 30, 34, "■", new Color(142, 16, 19));
        uiButton(g, "settings", 432, 33, 30, 34, "⚙", new Color(21, 41, 59));
        uiButton(g, "restore", 467, 33, 26, 34, "▴", RAISED);
    }

    private void paintCompactSettings(Graphics2D g)
    {
        ornamentalFrame(g, 0, 82, 500, 245);
        g.setFont(new Font("Serif", Font.BOLD, 23));
        label(g, "⚙ Settings", 16, 109, BRIGHT);
        uiButton(g, "settings-close", 456, 88, 32, 26, "×", RAISED);
        toggle(g, "setting:block", 20, 139, config.blockGameInput(), "Block game input");
        toggle(g, "setting:tooltips", 20, 173, config.hudTooltips(), "Show tooltips");
        toggle(g, "setting:lock", 20, 207, config.lockHudPosition(), "Lock overlay position");
        toggle(g, "setting:animation", 20, 241, config.showSkillAnimations(), "Show animations");
        g.setFont(SMALL);
        label(g, "Animation:", 235, 139, IVORY);
        uiButton(g, "animation-mode", 324, 117, 161, 29,
                config.hudAnimationIntensity() == 0 ? "Off"
                : config.hudAnimationIntensity() == 1 ? "Subtle" : "Cinematic", RAISED);
        label(g, "UI scale: " + config.hudScalePercent() + "%", 235, 195, IVORY);
        uiButton(g, "scale-down", 327, 167, 40, 34, "−", RAISED);
        uiButton(g, "scale-up", 374, 167, 40, 34, "+", RAISED);
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
                if (script.isOrderedQueueComplete()) state = "Completed";
                else if (index < script.getOrderedQueuePosition() - 1) state = "Completed";
                else if (index == script.getOrderedQueuePosition() - 1)
                    state = script.isManualPaused() ? "Paused" : "Active";
            }
            long completedSeconds = state.equals("Completed") ? item.durationSeconds
                    : state.equals("Active") || state.equals("Paused")
                    ? Math.min(item.durationSeconds, script.getOrderedElapsedSeconds()) : 0L;
            String progress = completedSeconds + " / " + item.durationSeconds + " s";
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
        if (!config.hudTooltips() || hover.isEmpty() || hoverPoint == null) return;
        List<String> lines = new ArrayList<>();
        String title = "KSP Account Builder";
        if ("xptip".equals(hover))
        {
            String kind = resolveKind(script.getCurrentTaskName());
            Skill skill = skillFor(kind);
            title = kind + " XP";
            Client c = Microbot.getClient();
            if (skill != null && c != null && c.getGameState() == GameState.LOGGED_IN)
            {
                int level = c.getRealSkillLevel(skill), xp = c.getSkillExperience(skill);
                int target = level >= 99 ? xp : Experience.getXpForLevel(level + 1);
                double levelProgress = nextLevelProgress(skill);
                long observed = skill == observedSkill ? Math.max(0, xp - observedXp) : 0;
                long rate = KspAccountBuilderHudMath.hourlyXp(observed,
                        Math.max(1, System.currentTimeMillis() - observedSince));
                lines.add("Level: " + level + (level >= 99 ? " (maximum)" : " → " + (level + 1)));
                lines.add(String.format(Locale.ROOT, "Total XP: %,d", xp));
                lines.add(String.format(Locale.ROOT, "Progress: %.1f%%", levelProgress * 100));
                lines.add(String.format(Locale.ROOT, "XP remaining: %,d", Math.max(0, target - xp)));
                lines.add("Tracked XP: " + observed);
                lines.add("XP/hour: " + (rate <= 0 ? "Calculating…" : rate));
                lines.add("ETA: " + (rate <= 0 ? "Calculating…"
                        : time(KspAccountBuilderHudMath.etaSeconds(target - xp, rate))));
            }
            else lines.add("Skill telemetry unavailable");
        }
        else if ("tasktip".equals(hover))
        {
            title = "Current task";
            lines.add("Task: " + safe(script.getCurrentTaskName(), "Idle").replace('_', ' '));
            lines.add("Activity: " + safe(script.getOverlayTaskDetail(), "—"));
            lines.add("Task actions: not reported by the current task script");
            lines.add("Elapsed: " + time(script.getRuntimeSeconds()));
            if (script.hasOrderedQueue())
            {
                lines.add("Queue: " + script.getOrderedQueuePosition() + "/" + script.getOrderedQueueSize());
                lines.add("Queued work: " + time(script.getOrderedElapsedSeconds()));
            }
        }
        else if ("queuetip".equals(hover))
        {
            title = "Breaks and queue";
            lines.add("Break: " + (script.isAnyBreakActive() ? "Active" : time(script.getTimeUntilBreakSeconds())));
            lines.add("Task selection: " + (script.hasOrderedQueue() ? "Ordered queue" : "Automatic"));
            if (script.hasOrderedQueue())
                lines.add("Queue item: " + script.getOrderedQueuePosition() + "/" + script.getOrderedQueueSize());
            lines.add("Click to view the task queue");
        }
        else if ("pause".equals(hover) && script.isManualPaused())
        {
            title = "Pause unavailable"; lines.add("The current task is already paused");
        }
        else if ("resume".equals(hover) && !script.isManualPaused())
        {
            title = "Resume unavailable"; lines.add("Pause execution before resuming");
        }
        else if ("skip".equals(hover) && !script.canSkipTask())
        {
            title = "Skip unavailable"; lines.add("The current mode does not permit a safe task handoff");
        }
        else if (plugin.isRuntimeActive() && ("queue:add".equals(hover)
                || "queue:up".equals(hover) || "queue:down".equals(hover)
                || "queue:remove".equals(hover)))
        {
            title = "Queue editing unavailable";
            lines.add("Stop the Account Builder before editing its task queue.");
            lines.add("The active queue snapshot stays unchanged.");
        }
        else if (hover.startsWith("nav:"))
        {
            title = hover.substring(4); lines.add("Open " + hover.substring(4) + " details");
        }
        else return;

        g.setFont(SMALL);
        FontMetrics metrics = g.getFontMetrics();
        int width = metrics.stringWidth(title) + 26;
        for (String line : lines) width = Math.max(width, metrics.stringWidth(line) + 26);
        width = Math.min(490, Math.max(160, width));
        int height = 35 + lines.size() * 18;
        int x = Math.min(Math.max(8, hoverPoint.x + 15), Math.max(8, surfaceWidth - width - 8));
        int y = Math.min(Math.max(7, hoverPoint.y + 16), Math.max(7, surfaceHeight - height - 6));
        ornamentalFrame(g, x, y, width, height);
        g.setFont(new Font("Serif", Font.BOLD, 16));
        label(g, title, x + 12, y + 23, BRIGHT);
        g.setFont(SMALL);
        for (int i = 0; i < lines.size(); i++)
            ellipsis(g, lines.get(i), x + 12, y + 43 + i * 18, width - 22, IVORY);
    }

    private void uiButton(Graphics2D g, String key, int x, int y, int w, int h, String text, Color fill)
    {
        inner(g, x, y, w, h);
        boolean enabled = !("pause".equals(key) && script.isManualPaused())
                && !("resume".equals(key) && !script.isManualPaused())
                && !("skip".equals(key) && !script.canSkipTask())
                && !(("queue:add".equals(key) || "queue:up".equals(key)
                        || "queue:down".equals(key) || "queue:remove".equals(key))
                        && plugin.isRuntimeActive());
        Color base = enabled ? fill : new Color(39, 43, 43);
        Paint saved = g.getPaint();
        g.setPaint(new GradientPaint(x, y + 2, base.brighter(),
                x, y + h - 3, base.darker()));
        g.fillRoundRect(x + 2, y + 2, w - 4, h - 4, 6, 6);
        g.setPaint(saved);
        g.setColor(hover.equals(key) ? BRIGHT : GOLD);
        g.drawRoundRect(x + 2, y + 2, w - 4, h - 4, 4, 4);
        g.setColor(new Color(255, 239, 172, enabled ? 130 : 35));
        g.drawLine(x + 6, y + 5, x + w - 7, y + 5);
        // Fit text to each button, especially the six chatbox actions:
        // the original fixed 19pt font clipped Resume, Settings and Full.
        float size = Math.min(19f, Math.max(10f, h * 0.48f));
        Font font = BUTTON.deriveFont(size);
        g.setFont(font);
        while (size > 9f && g.getFontMetrics().stringWidth(text) > w - 12)
        {
            size -= 0.5f;
            g.setFont(BUTTON.deriveFont(size));
        }
        int tx = x + Math.max(3, (w - g.getFontMetrics().stringWidth(text)) / 2);
        int baseline = y + (h + g.getFontMetrics().getAscent()
                - g.getFontMetrics().getDescent()) / 2;
        label(g, text, tx, baseline, enabled ? IVORY : MUTED);
        hot(key, x, y, w, h);
    }

    private void ornamentalFrame(Graphics2D g, int x, int y, int w, int h)
    {
        g.setColor(new Color(0, 0, 0, 110));
        g.fillRoundRect(x + 3, y + 5, w, h, 10, 10);
        g.setColor(INK); g.fillRoundRect(x, y, w, h, 8, 8);
        Shape oldClip = g.getClip();
        g.clip(new RoundRectangle2D.Double(x + 5, y + 5, w - 10, h - 10, 6, 6));
        for (int ix = x + 4; ix < x + w; ix += 30)
        {
            for (int iy = y + 5; iy < y + h; iy += 30)
            {
                g.setColor(new Color(198, 157, 71, 12));
                g.drawLine(ix, iy + 14, ix + 14, iy);
                g.drawLine(ix + 14, iy, ix + 29, iy + 14);
                g.drawLine(ix + 29, iy + 14, ix + 14, iy + 29);
                g.drawLine(ix + 14, iy + 29, ix, iy + 14);
            }
        }
        g.setClip(oldClip);
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
            g.drawArc(cx - 5, cy - 5, 19, 19, 8, 75);
            g.fillOval(cx + 4, cy + 4, 3, 3);
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
        BufferedImage image = null;
        Skill skill = skillFor(kind);
        if (skill != null && skillIcons != null)
        {
            try
            {
                image = iconCache.computeIfAbsent(skill, skillIcons::getSkillImage);
            }
            catch (RuntimeException ignored) { }
        }
        if (image != null)
        {
            int size = Math.max(20, (int) (radius * 1.35));
            g.drawImage(image, cx - size / 2, cy - size / 2, size, size, null);
        }
        else
        {
            g.setFont(new Font("Serif", Font.BOLD, radius < 20 ? 18 : 27));
            String symbol = emblem(kind);
            label(g, symbol, cx - g.getFontMetrics().stringWidth(symbol) / 2, cy + 9, BRIGHT);
        }
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
     * Resolve artwork without blocking RuneLite's paint callback. This also
     * recognizes the supplied reference-derived artwork ZIP in Downloads, so
     * users do not need to extract individual skill PNGs manually.
     *
     * Cache misses are retried periodically: adding the ZIP while the client
     * is running must not require a plugin restart.
     */
    private BufferedImage sceneArtwork(String kind)
    {
        final String key = kind.toLowerCase(Locale.ROOT);
        BufferedImage cached = sceneCache.get(key);
        if (cached != null) return cached;
        long now = System.currentTimeMillis();
        Long next = nextArtworkRetry.get(key);
        if (next != null && now < next) return null;
        if (!artworkLoading.add(key)) return null;
        nextArtworkRetry.put(key, now + 30000L);
        java.util.concurrent.CompletableFuture.runAsync(() ->
        {
            try
            {
                BufferedImage loaded = loadArtwork(key + ".png");
                if (loaded != null)
                {
                    sceneCache.put(key, loaded);
                    missingArtwork.remove(key);
                    nextArtworkRetry.remove(key);
                }
                else
                {
                    missingArtwork.add(key);
                }
            }
            catch (Exception ex)
            {
                // The in-game overlay can still use the themed fallback.
                org.slf4j.LoggerFactory.getLogger(KspCinematicHudRenderer.class)
                        .warn("Could not load Account Builder cinematic artwork {}", key, ex);
            }
            finally
            {
                artworkLoading.remove(key);
            }
        });
        return null;
    }

    private BufferedImage loadArtwork(String filename) throws java.io.IOException
    {
        try (InputStream stream = getClass().getResourceAsStream("cinematic/" + filename))
        {
            if (stream != null)
            {
                BufferedImage image = ImageIO.read(stream);
                if (image != null) return image;
            }
        }

        String base = System.getProperty("ksp.accountbuilder.artworkRoot",
                Paths.get(System.getProperty("user.home"), ".runelite",
                        "kspaccountbuilder", "artwork").toString());
        Path direct = Paths.get(base, filename);
        if (Files.isRegularFile(direct))
        {
            try (InputStream stream = Files.newInputStream(direct))
            {
                BufferedImage image = ImageIO.read(stream);
                if (image != null) return image;
            }
        }

        Path home = Paths.get(System.getProperty("user.home"));
        Path[] candidates = {
                Paths.get(base, "KSP_Account_Builder_Cinematic_Artwork.zip"),
                home.resolve("Downloads").resolve("KSP_Account_Builder_Cinematic_Artwork.zip"),
                home.resolve("Downloads").resolve("KSP_Account_Builder_Dev_Cinematic_Assets_OneClick.zip")
        };
        for (Path archive : candidates)
        {
            if (!Files.isRegularFile(archive)) continue;
            try (java.util.zip.ZipFile zip = new java.util.zip.ZipFile(archive.toFile()))
            {
                java.util.zip.ZipEntry entry = zip.getEntry(filename);
                if (entry == null) entry = zip.getEntry("ksp_account_builder_artwork_pack/" + filename);
                if (entry == null) entry = zip.getEntry("kspaccountbuilder/cinematic/" + filename);
                if (entry == null || entry.isDirectory() || entry.getSize() > 8L * 1024 * 1024) continue;
                try (InputStream stream = zip.getInputStream(entry))
                {
                    BufferedImage image = ImageIO.read(stream);
                    if (image != null) return image;
                }
            }
        }
        // Source Loader compiles Java sources without carrying PNG resources.
        // Fetch the immutable dev-branch artwork automatically in the worker,
        // cache it locally, and never ask the user to install an artwork ZIP.
        return downloadDevArtwork(filename, direct);
    }

    private BufferedImage downloadDevArtwork(String filename, Path localCache) throws java.io.IOException
    {
        final String prefix =
                "https://raw.githubusercontent.com/KSPOG/ksppluginsrelease/dev/kspaccountbuilder/cinematic/";
        java.net.HttpURLConnection connection = (java.net.HttpURLConnection)
                new java.net.URL(prefix + filename).openConnection();
        connection.setConnectTimeout(6000);
        connection.setReadTimeout(12000);
        connection.setRequestMethod("GET");
        connection.setInstanceFollowRedirects(false);
        try
        {
            if (connection.getResponseCode() != 200) return null;
            long declaredSize = connection.getContentLengthLong();
            if (declaredSize > 8L * 1024 * 1024) return null;
            java.io.ByteArrayOutputStream buffer = new java.io.ByteArrayOutputStream(
                    declaredSize > 0 ? (int) declaredSize : 1024 * 1024);
            try (InputStream stream = connection.getInputStream())
            {
                byte[] bytes = new byte[16384];
                int length;
                while ((length = stream.read(bytes)) != -1)
                {
                    if (buffer.size() + length > 8 * 1024 * 1024) return null;
                    buffer.write(bytes, 0, length);
                }
            }
            byte[] png = buffer.toByteArray();
            if (png.length < 24 || png[0] != (byte) 0x89 || png[1] != 0x50
                    || png[2] != 0x4e || png[3] != 0x47) return null;
            BufferedImage image = ImageIO.read(new java.io.ByteArrayInputStream(png));
            if (image == null || image.getWidth() != 1328 || image.getHeight() != 460)
                return null;
            // Failed cache writes do not stop rendering the successfully
            // downloaded artwork in memory.
            try
            {
                Path directory = localCache.getParent();
                if (directory != null) Files.createDirectories(directory);
                Path staged = Files.createTempFile(directory, "cinematic-", ".png");
                try
                {
                    Files.write(staged, png);
                    try
                    {
                        Files.move(staged, localCache,
                                java.nio.file.StandardCopyOption.ATOMIC_MOVE,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                    catch (java.nio.file.AtomicMoveNotSupportedException ex)
                    {
                        Files.move(staged, localCache,
                                java.nio.file.StandardCopyOption.REPLACE_EXISTING);
                    }
                }
                finally { Files.deleteIfExists(staged); }
            }
            catch (java.io.IOException cacheException)
            {
                org.slf4j.LoggerFactory.getLogger(KspCinematicHudRenderer.class)
                        .debug("Cinematic scene downloaded but could not be cached: {}",
                                filename, cacheException);
            }
            return image;
        }
        finally { connection.disconnect(); }
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
        // Never silently present procedural fallback shapes as the finished
        // reference artwork. The message disappears as soon as the PNG loads.
        if (missingArtwork.contains(kind.toLowerCase(Locale.ROOT)))
        {
            g.setFont(SMALL);
            g.setColor(new Color(8, 13, 14, 222));
            g.fillRoundRect(201, 250, 575, 24, 4, 4);
            label(g, "Scene artwork unavailable — retrying automatic dev artwork retrieval",
                    211, 267, BRIGHT);
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
    /** Compact XP totals fit beside skill level in the narrow chatbox. */
    private static String briefXp(long xp)
    {
        if (xp >= 1000000L) return String.format(Locale.ROOT, "%.1fm", xp / 1000000.0);
        if (xp >= 10000L) return String.format(Locale.ROOT, "%.1fk", xp / 1000.0);
        return String.format(Locale.ROOT, "%,d", xp);
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

    private void hot(String id, int x, int y, int w, int h) { paintingHitboxes.put(id, new Rectangle(x, y, w, h)); }

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
        hoverPoint = p == null || scale <= 0 ? null : new Point(
                KspAccountBuilderHudMath.scaledCoordinate(p.x, scale),
                KspAccountBuilderHudMath.scaledCoordinate(p.y, scale));
        String key = at(p);
        hover = key == null ? "" : key;
    }

    void openSettings()
    {
        settingsOpen = !settingsOpen;
        currentPage = settingsOpen ? "Settings" : "Skills";
    }
    void openQueue() { queueOpen = !queueOpen; }
    int selectedQueueRow() { return selectedQueueRow; }
    void selectQueueRow(int row) { selectedQueueRow = Math.max(0, row); }
    void scrollQueue(int direction) { queueScrollOffset = Math.max(0, queueScrollOffset + direction); }
    void toggleCollapsed() { collapsed = !collapsed; if (collapsed) { settingsOpen = false; queueOpen = false; } }
    void navigate(String page)
    {
        currentPage = page;
        settingsOpen = "Settings".equals(page);
        queueOpen = false;
    }
}
