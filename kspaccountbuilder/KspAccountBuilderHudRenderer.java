package net.runelite.client.plugins.microbot.kspaccountbuilder;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.AlphaComposite;
import java.awt.RadialGradientPaint;
import java.awt.geom.Point2D;
import java.awt.image.BufferedImage;
import net.runelite.client.game.ItemManager;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.RoundRectangle2D;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * Fully rendered HUD themes. No mockup images or synthetic statistics.
 * The active script task determines the artwork, accent and skill progress.
 */
final class KspAccountBuilderHudRenderer
{
    private final KspAccountBuilderScript script;
    private final KspAccountBuilderConfig config;
    private final KspAccountBuilderPlugin plugin;
    private final ItemManager itemManager;
    private Graphics2D g;
    private String task;
    private Color accent;
    private Skill skill;
    private int level;
    private double progress;
    private int xpToLevel;
    private String detail;

    KspAccountBuilderHudRenderer(KspAccountBuilderScript script, KspAccountBuilderConfig config,
                                 KspAccountBuilderPlugin plugin, ItemManager itemManager)
    {
        this.itemManager = itemManager;
        this.script = script;
        this.config = config;
        this.plugin = plugin;
    }

    Dimension render(Graphics2D graphics, KspAccountBuilderOverlayStyle style)
    {
        g = (Graphics2D) graphics.create();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            task = script.getCurrentTaskName();
            if (task == null || task.isEmpty()) task = "IDLE";
            detail = script.getOverlayTaskDetail();
            if (detail == null || detail.isEmpty() || "IDLE".equalsIgnoreCase(detail))
                detail = "IDLE".equals(task) ? "Waiting for task" : "Training " + task.replace('_', ' ').toLowerCase();
            skill = resolveSkill(task);
            accent = taskColor(task);
            level = 0;
            progress = -1;
            xpToLevel = -1;
            if (skill != null && Microbot.getClient() != null)
            {
                final Skill selectedSkill = skill;
                int[] stats = Microbot.getClientThread().runOnClientThreadOptional(() -> {
                    if (Microbot.getClient() == null) return null;
                    return new int[]{
                        Microbot.getClient().getRealSkillLevel(selectedSkill),
                        Microbot.getClient().getSkillExperience(selectedSkill)
                    };
                }).orElse(null);
                if (stats != null)
                {
                    level = stats[0];
                    if (level >= 1 && level < 99)
                    {
                        int start = Experience.getXpForLevel(level);
                        int end = Experience.getXpForLevel(level + 1);
                        progress = Math.max(0, Math.min(1, (double)(stats[1] - start) / Math.max(1, end - start)));
                        xpToLevel = Math.max(0, end - stats[1]);
                    }
                }
            }
            switch (style)
            {
                case NEXUS: nexus(); return new Dimension(324, 278);
                case PRESTIGE: prestige(); return new Dimension(334, 283);
                case COMPACT: compact(); return new Dimension(294, 153);
                case HORIZON: horizon(); return new Dimension(550, 119);
                case VERTICAL_PRO: vertical(); return new Dimension(314, 347);
                case ROADMAP: roadmap(); return new Dimension(396, 288);
                case ELEMENTAL: elemental(); return new Dimension(334, 309);
                case CLASSIC_PLUS: classicPlus(); return new Dimension(318, 286);
                default: return new Dimension(280, 0);
            }
        }
        finally { g.dispose(); }
    }

    private void nexus()
    {
        base(324, 278, new Color(13, 11, 31, 241), new Color(151, 67, 247), 17);
        outline(4, 4, 316, 270, 16, new Color(60, 200, 255, 115), 1);
        strip(9, 9, 306, 39, new Color(43, 21, 81));
        text("KSP / ACCOUNT BUILDER", 21, 32, 15, true, new Color(235, 201, 255), 270);
        icon(18, 66, 66, accent);
        text(niceTask(), 95, 91, 19, true, accent, 203);
        text(detail, 95, 110, 11, false, Color.WHITE, 206);
        pill(246, 120, 67, 19, state(), new Color(30, 77, 61), new Color(163, 253, 204));
        text(skillLabel(), 18, 154, 12, true, new Color(214, 222, 250), 287);
        progressBar(18, 163, 288, 10, accent);
        metricBlock(18, 188, 137, "SESSION", duration(script.getRuntimeSeconds()));
        metricBlock(168, 188, 138, "ACCOUNT PLAY", duration(script.getAccountPlayTimeSeconds()));
        separator(18, 236, 306, new Color(119, 92, 171));
        text("BREAK " + breakTime(), 19, 254, 11, true, new Color(255, 199, 117), 139);
        text("SWITCH " + switchTime(), 172, 254, 11, true, new Color(124, 222, 236), 134);
        text(mode() + "   •   " + (config.useAntiban() ? "ANTI-BAN ON" : "ANTI-BAN OFF"),
                19, 270, 9, false, new Color(175, 162, 206), 288);
    }

    private void prestige()
    {
        base(334, 283, new Color(60, 45, 29, 246), new Color(218, 167, 76), 9);
        frame(8, 8, 318, 267, new Color(130, 94, 45));
        strip(14, 13, 306, 44, new Color(90, 62, 32));
        text("KSP ACCOUNT BUILDER", 32, 39, 18, true, new Color(255, 222, 142), 275);
        icon(21, 78, 63, new Color(234, 185, 95));
        text(niceTask(), 100, 102, 19, true, new Color(255, 232, 166), 218);
        text(detail, 100, 123, 12, false, new Color(248, 236, 216), 215);
        separator(19, 146, 315, new Color(183, 142, 79));
        text("SKILL MILESTONE", 22, 163, 10, true, new Color(223, 180, 105), 280);
        text(skillLabel(), 22, 183, 12, true, Color.WHITE, 287);
        progressBar(22, 192, 289, 10, new Color(220, 162, 71));
        text("SESSION  " + duration(script.getRuntimeSeconds()), 22, 222, 12, true, new Color(249, 218, 165), 285);
        text("ACCOUNT  " + duration(script.getAccountPlayTimeSeconds()), 22, 242, 12, true, new Color(249, 218, 165), 285);
        text("BREAK " + breakTime() + "   /   NEXT " + switchTime(),
                22, 266, 11, false, new Color(214, 189, 143), 290);
    }

    private void compact()
    {
        base(294, 153, new Color(8, 22, 32, 245), new Color(34, 205, 241), 12);
        strip(5, 5, 284, 27, new Color(17, 48, 59));
        text("KSP  /  ACCOUNT BUILDER", 13, 24, 13, true, new Color(103, 233, 253), 267);
        icon(13, 40, 40, accent);
        text(niceTask(), 65, 57, 16, true, accent, 210);
        text(detail, 65, 74, 10, false, Color.WHITE, 208);
        text(skillLabel(), 14, 96, 10, true, new Color(208, 227, 233), 260);
        progressBar(14, 102, 266, 7, accent);
        separator(11, 119, 282, new Color(55, 106, 125));
        text("RUN " + duration(script.getRuntimeSeconds()), 14, 137, 10, true, Color.WHITE, 128);
        text("BREAK " + breakTime(), 155, 137, 10, true, new Color(241, 196, 100), 130);
    }

    private void horizon()
    {
        base(550, 119, new Color(10, 22, 35, 244), new Color(53, 200, 238), 13);
        strip(6, 6, 538, 30, new Color(13, 48, 67));
        text("KSP ACCOUNT BUILDER", 15, 27, 14, true, new Color(150, 235, 253), 244);
        pill(457, 10, 80, 21, state(), new Color(28, 72, 55), new Color(158, 243, 187));
        icon(14, 44, 53, accent);
        text(niceTask(), 82, 62, 17, true, accent, 169);
        text(detail, 82, 80, 11, false, Color.WHITE, 165);
        text(skillLabel(), 270, 57, 11, true, Color.WHITE, 259);
        progressBar(270, 64, 261, 8, accent);
        separator(15, 91, 537, new Color(59, 110, 127));
        text("RUN " + duration(script.getRuntimeSeconds()), 16, 109, 10, true, Color.WHITE, 119);
        text("PLAY " + duration(script.getAccountPlayTimeSeconds()), 147, 109, 10, true, Color.WHITE, 119);
        text("BREAK " + breakTime(), 279, 109, 10, true, new Color(248, 203, 120), 118);
        text("SWITCH " + switchTime(), 411, 109, 10, true, new Color(128, 219, 232), 129);
    }

    private void vertical()
    {
        base(314, 347, new Color(10, 16, 39, 244), new Color(148, 77, 238), 13);
        strip(8, 8, 298, 40, new Color(24, 30, 75));
        text("KSP  /  PROGRESS TRACKER", 18, 32, 15, true, new Color(167, 203, 255), 273);
        icon(17, 58, 72, accent);
        text(niceTask(), 106, 85, 18, true, accent, 186);
        text(detail, 106, 104, 11, false, Color.WHITE, 191);
        pill(207, 113, 85, 19, state(), new Color(31, 67, 56), new Color(143, 242, 187));
        separator(14, 144, 299, new Color(96, 91, 155));
        text("SKILL PROGRESSION", 18, 161, 11, true, new Color(152, 182, 244), 274);
        text(skillLabel(), 18, 185, 13, true, Color.WHITE, 279);
        progressBar(18, 195, 277, 12, accent);
        metricBlock(17, 220, 132, "SESSION", duration(script.getRuntimeSeconds()));
        metricBlock(163, 220, 132, "ACCOUNT TIME", duration(script.getAccountPlayTimeSeconds()));
        separator(17, 270, 296, new Color(96, 91, 155));
        text("NEXT BREAK", 19, 289, 11, false, new Color(180, 195, 237), 130);
        text(breakTime(), 203, 289, 12, true, new Color(244, 191, 99), 83);
        text("TASK ROTATION", 19, 308, 11, false, new Color(180, 195, 237), 130);
        text(switchTime(), 203, 308, 12, true, new Color(117, 225, 237), 83);
        text(mode() + "  /  " + (config.useAntiban() ? "ANTIBAN ACTIVE" : "ANTIBAN OFF"),
                18, 334, 10, true, new Color(180, 172, 236), 275);
    }

    private void roadmap()
    {
        base(396, 288, new Color(27, 25, 24, 247), new Color(201, 152, 67), 11);
        strip(8, 8, 380, 38, new Color(70, 49, 29));
        text("KSP / ACCOUNT ROADMAP", 22, 33, 18, true, new Color(255, 213, 127), 350);
        panel(12, 57, 184, 157, new Color(46, 39, 30), new Color(122, 96, 59));
        panel(204, 57, 180, 157, new Color(35, 33, 32), new Color(122, 96, 59));
        text("CURRENT OBJECTIVE", 22, 75, 10, true, new Color(223, 181, 103), 161);
        icon(22, 84, 50, accent);
        text(niceTask(), 22, 157, 15, true, new Color(255, 222, 156), 163);
        text(detail, 22, 175, 10, false, Color.WHITE, 159);
        text("SESSION " + duration(script.getRuntimeSeconds()), 22, 200, 10, true, new Color(228, 192, 138), 167);
        text("ACCOUNT DEVELOPMENT", 216, 75, 10, true, new Color(223, 181, 103), 163);
        text(skillLabel(), 216, 104, 12, true, Color.WHITE, 157);
        progressBar(216, 113, 156, 9, accent);
        text("MODE  " + mode(), 216, 144, 11, true, Color.WHITE, 154);
        text("STATE  " + state(), 216, 164, 11, true, new Color(154, 239, 194), 154);
        text("ACCOUNT " + duration(script.getAccountPlayTimeSeconds()), 216, 200, 10, true, new Color(228, 192, 138), 156);
        panel(12, 223, 372, 52, new Color(52, 41, 31), new Color(118, 91, 59));
        text("NEXT AUTOMATION EVENTS", 23, 239, 10, true, new Color(241, 201, 122), 348);
        text("BREAK  " + breakTime(), 24, 258, 12, true, Color.WHITE, 162);
        text("ROTATION  " + switchTime(), 209, 258, 12, true, Color.WHITE, 162);
    }

    private void elemental()
    {
        Color glow = accent.brighter();
        base(334, 309, new Color(5, 18, 33, 243), glow, 17);
        outline(5, 5, 324, 299, 17, new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 100), 2);
        strip(12, 10, 310, 40, new Color(15, 38, 59));
        text("KSP  /  ELEMENTAL", 26, 36, 18, true, new Color(207, 244, 255), 279);
        // Animated geometric shimmer is synchronized to time but never changes
        // task selection or input. Keep geometry bounded to the HUD rectangle.
        int phase = (int)((System.currentTimeMillis() / 90L) % 120);
        for (int i = 0; i < 6; i++)
        {
            int x = 21 + ((phase + i * 47) % 291);
            int y = 62 + ((i * 39 + phase / 4) % 137);
            g.setColor(new Color(glow.getRed(), glow.getGreen(), glow.getBlue(), 85));
            g.fillOval(x, y, 3 + i % 3, 3 + i % 3);
        }
        panel(18, 58, 298, 142, new Color(12, 31, 48, 229), new Color(62, 120, 158));
        icon(107, 69, 92, glow);
        text(niceTask(), 29, 168, 19, true, glow, 270);
        text(detail, 29, 185, 11, false, Color.WHITE, 270);
        text(skillLabel(), 21, 220, 12, true, Color.WHITE, 288);
        progressBar(22, 230, 289, 11, glow);
        separator(19, 256, 314, new Color(66, 127, 161));
        text("RUN " + duration(script.getRuntimeSeconds()), 21, 273, 11, true, Color.WHITE, 131);
        text("BREAK " + breakTime(), 180, 273, 11, true, new Color(247, 207, 135), 128);
        text("SWITCH " + switchTime() + "    /    " + mode(), 21, 294, 10, false, new Color(173, 211, 237), 283);
    }

    private void classicPlus()
    {
        base(318, 286, new Color(28, 29, 31, 243), new Color(191, 158, 86), 9);
        frame(6, 6, 306, 274, new Color(100, 104, 109));
        strip(12, 12, 294, 38, new Color(54, 51, 44));
        text("KSP ACCOUNT BUILDER", 21, 37, 16, true, new Color(252, 210, 111), 275);
        icon(18, 63, 66, accent);
        text(niceTask(), 100, 87, 18, true, accent, 192);
        text(detail, 100, 108, 11, false, Color.WHITE, 190);
        text(skillLabel(), 20, 151, 12, true, Color.WHITE, 280);
        progressBar(20, 162, 276, 10, accent);
        separator(18, 186, 299, new Color(124, 111, 83));
        text("Runtime", 20, 205, 12, false, new Color(215, 215, 214), 160);
        text(duration(script.getRuntimeSeconds()), 197, 205, 12, true, Color.WHITE, 99);
        text("Account play", 20, 223, 12, false, new Color(215, 215, 214), 160);
        text(duration(script.getAccountPlayTimeSeconds()), 197, 223, 12, true, Color.WHITE, 99);
        text("Break / Switch", 20, 241, 12, false, new Color(215, 215, 214), 160);
        text(breakTime() + " / " + switchTime(), 192, 241, 11, true, Color.WHITE, 108);
        separator(18, 251, 299, new Color(124, 111, 83));
        text(mode() + "    •    " + state(), 20, 270, 11, true, new Color(236, 201, 137), 278);
    }

    private Skill resolveSkill(String name)
    {
        if (name.contains("RUNE_ESSENCE") || name.contains("MINING")) return Skill.MINING;
        if (name.contains("WOODCUTTING")) return Skill.WOODCUTTING;
        if (name.contains("FIREMAKING")) return Skill.FIREMAKING;
        if (name.contains("FISHING")) return Skill.FISHING;
        if (name.contains("COOKING")) return Skill.COOKING;
        if (name.contains("SMITHING") || name.contains("SMELTING")) return Skill.SMITHING;
        if (name.contains("CRAFTING")) return Skill.CRAFTING;
        if (name.contains("MAGIC")) return Skill.MAGIC;
        if (name.contains("RANGED")) return Skill.RANGED;
        if (name.contains("PRAYER")) return Skill.PRAYER;
        if (name.contains("MELEE")) return Skill.ATTACK;
        return null; // Quests, banking and other activities have no single XP bar.
    }

    private Color taskColor(String name)
    {
        if (name.contains("RUNE_ESSENCE")) return new Color(133, 157, 250);
        if (name.contains("MINING")) return new Color(106, 208, 246);
        if (name.contains("WOODCUTTING")) return new Color(110, 209, 123);
        if (name.contains("FIREMAKING")) return new Color(255, 132, 69);
        if (name.contains("FISHING")) return new Color(75, 178, 253);
        if (name.contains("COOKING")) return new Color(242, 161, 96);
        if (name.contains("SMITHING") || name.contains("SMELTING")) return new Color(248, 180, 86);
        if (name.contains("CRAFTING")) return new Color(201, 127, 235);
        if (name.contains("MELEE")) return new Color(232, 110, 110);
        if (name.contains("MAGIC")) return new Color(157, 121, 255);
        if (name.contains("QUEST")) return new Color(255, 218, 112);
        return new Color(103, 209, 231);
    }

    private String niceTask() { return task.replace('_', ' '); }
    private String skillLabel()
    {
        if (skill == null || level <= 0) return mode() + "  |  " + state();
        if (level >= 99) return skill.getName() + " Lv.99 MAX";
        return skill.getName() + " Lv." + level + "  •  " + xpToLevel + " XP to " + (level + 1);
    }
    private String mode()
    {
        return config.runSingleQuest() ? "SINGLE QUEST" : config.trainSingleSkill() ? "SINGLE SKILL" : "AUTOMATIC";
    }
    private String state() { return script.isAnyBreakActive() ? "BREAK" : "ACTIVE"; }
    private String breakTime()
    {
        if (!config.doBreaks()) return "OFF";
        return duration(script.isAnyBreakActive() ? script.getBreakTimeRemainingSeconds() : script.getTimeUntilBreakSeconds());
    }
    private String switchTime()
    {
        if (!config.enableActivitySwitchRandomization() || config.trainSingleSkill() || config.runSingleQuest()) return "--";
        return duration(script.getTimeUntilActivitySwitchSeconds());
    }
    private String duration(long seconds)
    {
        if (seconds < 0) return "--";
        long h = TimeUnit.SECONDS.toHours(seconds);
        long m = TimeUnit.SECONDS.toMinutes(seconds) % 60;
        long s = seconds % 60;
        return h > 0 ? String.format("%02d:%02d:%02d", h, m, s) : String.format("%02d:%02d", m, s);
    }

    private void base(int w, int h, Color bg, Color edge, int corner)
    {
        g.setPaint(new GradientPaint(0, 0, bg.brighter(), 0, h, bg));
        g.fill(new RoundRectangle2D.Float(0, 0, w - 1, h - 1, corner, corner));
        outline(1, 1, w - 3, h - 3, corner, edge, 2);
        // In-game metallic corner caps and ornamental task-colored trim,
        // not a flat concept-image texture.
        int d = Math.min(24, Math.min(w, h) / 6);
        g.setStroke(new BasicStroke(2.5f, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(edge.getRed(), edge.getGreen(), edge.getBlue(), 220));
        g.drawLine(7, d, 7, 9);
        g.drawLine(7, 9, d, 9);
        g.drawLine(w - d, 9, w - 8, 9);
        g.drawLine(w - 8, 9, w - 8, d);
        g.drawLine(7, h - d, 7, h - 9);
        g.drawLine(7, h - 9, d, h - 9);
        g.drawLine(w - d, h - 9, w - 8, h - 9);
        g.drawLine(w - 8, h - 9, w - 8, h - d);
        g.setColor(new Color(255, 230, 168, 115));
        for (int px : new int[]{14, w - 15})
            for (int py : new int[]{14, h - 15})
            {
                g.rotate(Math.PI / 4, px, py);
                g.fillRect(px - 2, py - 2, 5, 5);
                g.rotate(-Math.PI / 4, px, py);
            }
    }
    private void outline(int x, int y, int w, int h, int arc, Color c, int width)
    {
        g.setColor(c);
        g.setStroke(new BasicStroke(width));
        g.draw(new RoundRectangle2D.Float(x, y, w, h, arc, arc));
    }
    private void panel(int x, int y, int w, int h, Color fill, Color edge)
    {
        g.setColor(fill);
        g.fillRoundRect(x, y, w, h, 11, 11);
        outline(x, y, w, h, 11, edge, 1);
    }
    private void frame(int x, int y, int w, int h, Color color)
    {
        outline(x, y, w, h, 8, color, 2);
        for (int px : new int[]{x + 5, x + w - 5})
            for (int py : new int[]{y + 5, y + h - 5})
            {
                g.setColor(color.brighter());
                g.fillOval(px - 2, py - 2, 5, 5);
            }
    }
    private void strip(int x, int y, int w, int h, Color color)
    {
        g.setColor(color);
        g.fillRoundRect(x, y, w, h, 8, 8);
    }
    private void separator(int x, int y, int x2, Color color)
    {
        g.setColor(color);
        g.setStroke(new BasicStroke(1));
        g.drawLine(x, y, x2, y);
    }
    private void progressBar(int x, int y, int w, int h, Color color)
    {
        panel(x, y, w, h, new Color(19, 23, 37), new Color(80, 83, 93));
        if (progress >= 0)
        {
            int fill = Math.max(0, (int)((w - 4) * progress));
            if (fill > 0)
            {
                g.setPaint(new GradientPaint(x, y, color.darker(), x + Math.max(1, fill), y, color.brighter()));
                g.fillRoundRect(x + 2, y + 2, fill, Math.max(1, h - 4), 5, 5);
            }
        }
    }
    private void metricBlock(int x, int y, int w, String title, String value)
    {
        panel(x, y, w, 42, new Color(28, 34, 57, 238), new Color(58, 65, 91));
        text(title, x + 8, y + 15, 10, true, accent, w - 16);
        text(value, x + 8, y + 33, 14, true, Color.WHITE, w - 16);
    }
    private void pill(int x, int y, int w, int h, String content, Color bg, Color fg)
    {
        g.setColor(bg);
        g.fillRoundRect(x, y, w, h, 9, 9);
        text(content, x + 8, y + h - 5, 10, true, fg, w - 13);
    }
    private void text(String value, int x, int y, int size, boolean bold, Color color, int maxWidth)
    {
        if (value == null) value = "";
        g.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
        FontMetrics fm = g.getFontMetrics();
        String shown = value;
        while (shown.length() > 1 && fm.stringWidth(shown) > maxWidth)
            shown = shown.substring(0, shown.length() - 1);
        if (!shown.equals(value) && shown.length() > 3) shown = shown.substring(0, shown.length() - 2) + "..";
        g.setColor(color);
        g.drawString(shown, x, y);
    }

    /**
     * Use the game's actual item artwork rather than approximated shapes.
     * These item sprites are resolved by RuneLite's ItemManager; missing sprites
     * fall back to the vector emblem. Dynamic choice follows active task.
     */
    private int primaryItemId()
    {
        if (task.contains("RUNE_ESSENCE")) return 1436; // Rune essence
        if (task.contains("MINING")) return 1275; // Rune pickaxe
        if (task.contains("WOODCUTTING")) return 1359; // Rune axe
        if (task.contains("FIREMAKING")) return 590; // Tinderbox
        if (task.contains("FISHING")) return 309; // Fly fishing rod
        if (task.contains("COOKING")) return 379; // Lobster
        if (task.contains("SMITHING")) return 2347; // Hammer
        if (task.contains("SMELTING")) return 2353; // Steel bar
        if (task.contains("CRAFTING")) return 1755; // Chisel
        if (task.contains("MELEE")) return 1289; // Rune sword
        if (task.contains("MAGIC")) return 554; // Fire rune
        if (task.contains("QUEST")) return 8007; // Varrock teleport tablet
        return 1275;
    }

    private int secondaryItemId()
    {
        if (task.contains("RUNE_ESSENCE")) return 1275;
        if (task.contains("MINING")) return 440; // Iron ore
        if (task.contains("WOODCUTTING")) return 1519; // Willow logs
        if (task.contains("FIREMAKING")) return 1511; // Logs
        if (task.contains("FISHING")) return 335; // Raw trout
        if (task.contains("COOKING")) return 377; // Raw lobster
        if (task.contains("SMITHING")) return 2353;
        if (task.contains("SMELTING")) return 453; // Coal
        if (task.contains("CRAFTING")) return 1623; // Uncut sapphire
        if (task.contains("MELEE")) return 1191; // Iron kiteshield
        if (task.contains("MAGIC")) return 556; // Air rune
        return 1511;
    }

    private boolean drawRuneLiteSprite(int itemId, int cx, int cy, int w)
    {
        if (itemManager == null) return false;
        BufferedImage sprite;
        try { sprite = itemManager.getImage(itemId); }
        catch (RuntimeException ex) { return false; }
        if (sprite == null || sprite.getWidth() <= 0 || sprite.getHeight() <= 0) return false;
        // Draw using the original game sprite with bicubic scaling and an
        // understated double shadow. No mocked-up task illustrations.
        Graphics2D d = (Graphics2D) g.create();
        try
        {
            d.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            int h = Math.max(1, Math.round((float) sprite.getHeight() * w / Math.max(1, sprite.getWidth())));
            d.setComposite(AlphaComposite.getInstance(AlphaComposite.SRC_OVER, .36f));
            d.drawImage(sprite, cx - w / 2 + 3, cy - h / 2 + 4, w, h, null);
            d.setComposite(AlphaComposite.SrcOver);
            d.drawImage(sprite, cx - w / 2, cy - h / 2, w, h, null);
            return true;
        }
        finally { d.dispose(); }
    }

    private void drawIllustratedMedallion(int x, int y, int size, Color color)
    {
        int cx = x + size / 2, cy = y + size / 2;
        g.setPaint(new RadialGradientPaint(new Point2D.Float(cx, cy), size * .53f,
                new float[]{0f, .64f, 1f},
                new Color[]{new Color(54, 49, 73), new Color(20, 25, 38), new Color(6, 10, 19)}));
        g.fillOval(x, y, size, size);
        g.setStroke(new BasicStroke(3));
        g.setColor(color);
        g.drawOval(x + 2, y + 2, size - 4, size - 4);
        g.setColor(new Color(245, 216, 156, 175));
        g.setStroke(new BasicStroke(1));
        g.drawOval(x + 8, y + 8, size - 16, size - 16);
        for (int i = 0; i < 8; i++)
        {
            double a = i * Math.PI / 4;
            int px = cx + (int)((size / 2.0 - 5) * Math.cos(a));
            int py = cy + (int)((size / 2.0 - 5) * Math.sin(a));
            g.setColor(new Color(color.getRed(), color.getGreen(), color.getBlue(), 185));
            g.fillOval(px - 2, py - 2, 4, 4);
        }
        boolean drawn = drawRuneLiteSprite(primaryItemId(), cx - size / 10, cy - size / 13, size * 3 / 5);
        drawRuneLiteSprite(secondaryItemId(), cx + size / 4, cy + size / 4, size / 3);
        if (!drawn) drawFallbackGlyph(cx, cy, size, color);
    }

    private void drawFallbackGlyph(int cx, int cy, int size, Color color)
    {
        g.setColor(color);
        g.setFont(new Font("Serif", Font.BOLD, Math.max(16, size / 3)));
        String glyph = task.isEmpty() ? "?" : task.substring(0, 1);
        g.drawString(glyph, cx - size / 10, cy + size / 10);
    }
    private void icon(int x, int y, int size, Color color)
    {
        drawIllustratedMedallion(x, y, size, color);
    }

}
