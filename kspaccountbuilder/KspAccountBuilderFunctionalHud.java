package net.runelite.client.plugins.microbot.kspaccountbuilder;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.geom.Arc2D;
import java.awt.image.BufferedImage;
import java.util.concurrent.TimeUnit;
import net.runelite.api.Experience;
import net.runelite.api.Skill;
import net.runelite.client.game.ItemManager;
import net.runelite.client.plugins.microbot.Microbot;

/**
 * Three distinct, usable RuneLite HUDs — not preview images. All numbers are
 * read from the current client and parent Account Builder script.
 */
final class KspAccountBuilderFunctionalHud
{
    private static final Skill[] ATLAS_SKILLS = {
        Skill.ATTACK, Skill.STRENGTH, Skill.DEFENCE, Skill.HITPOINTS,
        Skill.RANGED, Skill.PRAYER, Skill.MAGIC, Skill.MINING,
        Skill.WOODCUTTING, Skill.FIREMAKING, Skill.FISHING, Skill.COOKING,
        Skill.CRAFTING, Skill.SMITHING, Skill.RUNECRAFT
    };

    private final KspAccountBuilderScript script;
    private final KspAccountBuilderConfig config;
    private final ItemManager itemManager;
    private Graphics2D g;
    private String task;
    private String detail;
    private Skill skill;
    private int level;
    private int experience;
    private double fraction;
    private Color accent;

    KspAccountBuilderFunctionalHud(KspAccountBuilderScript script, KspAccountBuilderConfig config,
                                    ItemManager itemManager)
    {
        this.script = script;
        this.config = config;
        this.itemManager = itemManager;
    }

    Dimension render(Graphics2D original, KspAccountBuilderOverlayStyle style)
    {
        g = (Graphics2D) original.create();
        try
        {
            g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            task = script.getCurrentTaskName();
            if (task == null || task.isEmpty()) task = "IDLE";
            detail = script.getOverlayTaskDetail();
            if (detail == null || detail.isEmpty()) detail = "Waiting for task";
            skill = activeSkill(task);
            accent = activeColor(task);
            level = 0;
            experience = 0;
            fraction = -1;
            if (skill != null)
            {
                int[] current = readSkill(skill);
                if (current != null)
                {
                    level = current[0];
                    experience = current[1];
                    fraction = progress(level, experience);
                }
            }
            switch (style)
            {
                case SKILL_ATLAS: atlas(); return new Dimension(458, 345);
                case MISSION_CONSOLE: console(); return new Dimension(430, 261);
                case XP_DIAL: dial(); return new Dimension(390, 214);
                default: return null;
            }
        }
        finally
        {
            g.dispose();
        }
    }

    private void atlas()
    {
        // This layout is a genuine multi-skill account progression dashboard.
        canvas(458, 345, new Color(14, 19, 29), new Color(77, 114, 135));
        g.setColor(new Color(23, 37, 48));
        g.fillRect(2, 2, 454, 42);
        text("KSP  /  SKILL ATLAS", 18, 29, 18, true, Color.WHITE, 348);
        text("ACCOUNT SKILLS", 21, 64, 11, true, new Color(153, 198, 208), 200);
        line(19, 73, 439, 73, new Color(64, 105, 119));
        int[] allLevels = readAtlasLevels();
        for (int index = 0; index < ATLAS_SKILLS.length; index++)
        {
            int col = index % 3;
            int row = index / 3;
            int x = 18 + col * 143;
            int y = 83 + row * 44;
            Skill candidate = ATLAS_SKILLS[index];
            boolean active = candidate == skill;
            block(x, y, 135, 39, active ? new Color(31, 74, 86) : new Color(26, 34, 44),
                    active ? accent : new Color(62, 75, 84));
            if (active)
            {
                g.setColor(accent);
                g.fillRoundRect(x, y + 2, 4, 35, 3, 3);
            }
            text(candidate.getName(), x + 10, y + 15, 10, true,
                    active ? Color.WHITE : new Color(192, 204, 211), 97);
            text(allLevels[index] > 0 ? "Lv " + allLevels[index] : "--",
                    x + 10, y + 31, 12, true, active ? accent : new Color(154, 176, 184), 105);
        }
        line(18, 311, 439, 311, new Color(67, 104, 117));
        text("ACTIVE  " + humanTask(), 18, 327, 11, true, accent, 252);
        text("RUN " + duration(script.getRuntimeSeconds()), 311, 327, 11, true, Color.WHITE, 129);
        text("BREAK " + breakTime(), 18, 340, 10, false, new Color(219, 187, 120), 212);
        text("MODE " + mode(), 265, 340, 10, false, new Color(153, 198, 208), 173);
    }

    private void console()
    {
        // Task-led operations board with four independent status areas.
        canvas(430, 261, new Color(10, 23, 27), new Color(61, 181, 146));
        block(10, 9, 410, 43, new Color(22, 57, 55), new Color(66, 164, 139));
        text("KSP / MISSION CONSOLE", 21, 35, 17, true, new Color(197, 255, 220), 296);
        text(script.isAnyBreakActive() ? "ON BREAK" : "ACTIVE", 328, 34, 12, true,
                script.isAnyBreakActive() ? new Color(244, 190, 91) : new Color(121, 249, 169), 87);
        block(11, 61, 408, 86, new Color(19, 42, 46), new Color(52, 103, 112));
        int item = taskItem(task);
        sprite(item, 40, 100, 43);
        text("CURRENT ASSIGNMENT", 87, 82, 11, true, new Color(132, 197, 196), 312);
        text(humanTask(), 87, 108, 20, true, accent, 313);
        text(detail, 87, 131, 12, false, Color.WHITE, 310);
        block(12, 155, 197, 64, new Color(21, 38, 45), new Color(49, 95, 97));
        block(221, 155, 197, 64, new Color(21, 38, 45), new Color(49, 95, 97));
        text("SESSION / ACCOUNT", 23, 173, 10, true, new Color(143, 195, 203), 177);
        text(duration(script.getRuntimeSeconds()), 23, 192, 16, true, Color.WHITE, 168);
        text("Total " + duration(script.getAccountPlayTimeSeconds()), 23, 208, 10, false,
                new Color(181, 206, 210), 168);
        text("SKILL MILESTONE", 233, 173, 10, true, new Color(143, 195, 203), 170);
        text(skill == null ? "Non-skill task" : skill.getName() + " / " + level,
                233, 191, 14, true, Color.WHITE, 170);
        gauge(233, 199, 168, 8, fraction, accent);
        text("NEXT BREAK  " + breakTime(), 15, 241, 12, true, new Color(244, 190, 104), 194);
        text("ROTATION  " + switchTime(), 227, 241, 12, true, new Color(151, 219, 225), 189);
        text(mode() + "  /  " + (config.useAntiban() ? "ANTIBAN ON" : "ANTIBAN OFF"),
                15, 254, 9, false, new Color(144, 178, 174), 394);
    }

    private void dial()
    {
        // A large circular XP instrument, physically different from a row-based HUD.
        canvas(390, 214, new Color(15, 19, 32), new Color(145, 121, 217));
        g.setColor(new Color(29, 31, 55));
        g.fillRoundRect(8, 8, 374, 33, 8, 8);
        text("KSP / XP DIAL", 19, 31, 17, true, new Color(219, 204, 255), 269);
        text(mode(), 284, 29, 10, true, new Color(176, 159, 214), 93);
        int cx = 99, cy = 117, radius = 65;
        g.setStroke(new BasicStroke(12, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
        g.setColor(new Color(54, 60, 77));
        g.draw(new Arc2D.Double(cx - radius + 8, cy - radius + 8,
                2 * (radius - 8), 2 * (radius - 8), 90, 360, Arc2D.OPEN));
        if (fraction >= 0)
        {
            g.setColor(accent);
            g.draw(new Arc2D.Double(cx - radius + 8, cy - radius + 8,
                    2 * (radius - 8), 2 * (radius - 8), 90,
                    -360 * fraction, Arc2D.OPEN));
        }
        sprite(taskItem(task), cx, cy - 8, 39);
        text(level > 0 ? "LV " + level : "TASK", cx - 26, cy + 38, 13, true, Color.WHITE, 66);
        text(humanTask(), 193, 69, 18, true, accent, 180);
        text(detail, 193, 88, 11, false, Color.WHITE, 177);
        text(skill == null ? "Current task" : xpRemaining(), 193, 109, 11, true,
                new Color(183, 185, 219), 179);
        line(190, 121, 367, 121, new Color(83, 79, 132));
        text("SESSION  " + duration(script.getRuntimeSeconds()), 193, 142, 12, true,
                Color.WHITE, 172);
        text("BREAK    " + breakTime(), 193, 162, 12, true,
                new Color(237, 197, 128), 172);
        text("SWITCH   " + switchTime(), 193, 181, 12, true,
                new Color(160, 218, 240), 172);
        text(script.isAnyBreakActive() ? "BREAK IN PROGRESS" : "ACCOUNT BUILDER RUNNING",
                17, 205, 10, true, new Color(169, 160, 209), 352);
    }

    private int[] readSkill(Skill chosen)
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            if (Microbot.getClient() == null) return null;
            return new int[]{Microbot.getClient().getRealSkillLevel(chosen),
                    Microbot.getClient().getSkillExperience(chosen)};
        }).orElse(null);
    }

    private int[] readAtlasLevels()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            int[] levels = new int[ATLAS_SKILLS.length];
            if (Microbot.getClient() == null) return levels;
            for (int i = 0; i < ATLAS_SKILLS.length; i++)
                levels[i] = Microbot.getClient().getRealSkillLevel(ATLAS_SKILLS[i]);
            return levels;
        }).orElse(new int[ATLAS_SKILLS.length]);
    }

    private static double progress(int lvl, int xp)
    {
        if (lvl < 1 || lvl >= 99) return -1;
        int start = Experience.getXpForLevel(lvl), next = Experience.getXpForLevel(lvl + 1);
        return Math.min(1, Math.max(0, (double)(xp - start) / Math.max(1, next - start)));
    }

    private String xpRemaining()
    {
        if (level >= 99) return "Level 99 reached";
        if (level < 1) return "Skill data unavailable";
        return Math.max(0, Experience.getXpForLevel(level + 1) - experience) + " XP to " + (level + 1);
    }

    private static Skill activeSkill(String task)
    {
        if (task.contains("RUNE_ESSENCE") || task.contains("MINING")) return Skill.MINING;
        if (task.contains("WOODCUTTING")) return Skill.WOODCUTTING;
        if (task.contains("FIREMAKING")) return Skill.FIREMAKING;
        if (task.contains("FISHING")) return Skill.FISHING;
        if (task.contains("COOKING")) return Skill.COOKING;
        if (task.contains("SMITHING") || task.contains("SMELTING")) return Skill.SMITHING;
        if (task.contains("CRAFTING")) return Skill.CRAFTING;
        if (task.contains("MAGIC")) return Skill.MAGIC;
        if (task.contains("MELEE")) return Skill.ATTACK;
        return null;
    }

    private static Color activeColor(String task)
    {
        if (task.contains("FIREMAKING")) return new Color(249, 142, 67);
        if (task.contains("WOODCUTTING")) return new Color(115, 208, 122);
        if (task.contains("RUNE_ESSENCE")) return new Color(150, 145, 241);
        if (task.contains("MINING")) return new Color(112, 205, 240);
        if (task.contains("COOKING")) return new Color(247, 180, 112);
        if (task.contains("CRAFTING")) return new Color(205, 153, 249);
        if (task.contains("MELEE")) return new Color(246, 120, 125);
        return new Color(125, 209, 206);
    }

    private static int taskItem(String task)
    {
        if (task.contains("RUNE_ESSENCE")) return 1436;
        if (task.contains("MINING")) return 1275;
        if (task.contains("WOODCUTTING")) return 1359;
        if (task.contains("FIREMAKING")) return 590;
        if (task.contains("FISHING")) return 309;
        if (task.contains("COOKING")) return 379;
        if (task.contains("SMITHING")) return 2347;
        if (task.contains("SMELTING")) return 2353;
        if (task.contains("CRAFTING")) return 1755;
        if (task.contains("MELEE")) return 1289;
        if (task.contains("MAGIC")) return 554;
        return 8007;
    }

    private void sprite(int id, int cx, int cy, int size)
    {
        BufferedImage image;
        try { image = itemManager == null ? null : itemManager.getImage(id); }
        catch (RuntimeException ex) { return; }
        if (image == null) return;
        g.drawImage(image, cx - size / 2, cy - size / 2, size, size, null);
    }

    private String mode()
    {
        return config.runSingleQuest() ? "SINGLE QUEST"
                : config.trainSingleSkill() ? "SINGLE SKILL" : "AUTOMATIC";
    }

    private String humanTask() { return task.replace('_', ' '); }
    private String breakTime()
    {
        if (!config.doBreaks()) return "OFF";
        long seconds = script.isAnyBreakActive() ? script.getBreakTimeRemainingSeconds()
                : script.getTimeUntilBreakSeconds();
        return duration(seconds);
    }
    private String switchTime()
    {
        if (!config.enableActivitySwitchRandomization()
                || config.trainSingleSkill() || config.runSingleQuest()) return "--";
        return duration(script.getTimeUntilActivitySwitchSeconds());
    }

    private static String duration(long seconds)
    {
        if (seconds < 0) return "--";
        long hours = TimeUnit.SECONDS.toHours(seconds);
        long mins = TimeUnit.SECONDS.toMinutes(seconds) % 60;
        long secs = seconds % 60;
        return hours > 0 ? String.format("%02d:%02d:%02d", hours, mins, secs)
                : String.format("%02d:%02d", mins, secs);
    }

    private void canvas(int w, int h, Color base, Color border)
    {
        g.setPaint(new GradientPaint(0, 0, base.brighter(), w, h, base));
        g.fillRoundRect(0, 0, w - 1, h - 1, 12, 12);
        g.setColor(border);
        g.setStroke(new BasicStroke(2));
        g.drawRoundRect(1, 1, w - 3, h - 3, 12, 12);
    }
    private void block(int x, int y, int w, int h, Color fill, Color stroke)
    {
        g.setColor(fill);
        g.fillRoundRect(x, y, w, h, 7, 7);
        g.setColor(stroke);
        g.setStroke(new BasicStroke(1));
        g.drawRoundRect(x, y, w, h, 7, 7);
    }
    private void gauge(int x, int y, int w, int h, double portion, Color color)
    {
        g.setColor(new Color(34, 47, 56));
        g.fillRoundRect(x, y, w, h, 6, 6);
        if (portion < 0) return;
        g.setColor(color);
        g.fillRoundRect(x, y, Math.max(1, (int)(w * portion)), h, 6, 6);
    }
    private void line(int x1, int y1, int x2, int y2, Color color)
    {
        g.setColor(color);
        g.setStroke(new BasicStroke(1));
        g.drawLine(x1, y1, x2, y2);
    }
    private void text(String value, int x, int y, int size, boolean bold, Color color, int width)
    {
        if (value == null) value = "";
        g.setFont(new Font("SansSerif", bold ? Font.BOLD : Font.PLAIN, size));
        FontMetrics fm = g.getFontMetrics();
        String shown = value;
        while (shown.length() > 1 && fm.stringWidth(shown) > width)
            shown = shown.substring(0, shown.length() - 1);
        if (!shown.equals(value) && shown.length() > 3)
            shown = shown.substring(0, shown.length() - 2) + "..";
        g.setColor(color);
        g.drawString(shown, x, y);
    }
}
