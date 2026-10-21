package net.runelite.client.plugins.microbot.kspaccountbuilder;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.RenderingHints;
import java.awt.BasicStroke;
import java.awt.geom.RoundRectangle2D;
import java.util.concurrent.TimeUnit;

public class KSPAccountBuilderOverlay extends OverlayPanel
{
    private final KspAccountBuilderPlugin plugin;
    private final KspAccountBuilderScript script;
    private final KspAccountBuilderConfig config;

    @Inject
    public KSPAccountBuilderOverlay(KspAccountBuilderPlugin plugin, KspAccountBuilderScript script, KspAccountBuilderConfig config)
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
        KspAccountBuilderOverlayStyle style = config.overlayStyle();
        if (style != null && style != KspAccountBuilderOverlayStyle.DEFAULT)
        {
            return renderCustom(graphics, style);
        }
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(280, 0));
        String displayStatus = normalizeStatus(Microbot.status);

        String title = "KSP Account Builder V" + KspAccountBuilderPlugin.VERSION;
        if (script.isAnyBreakActive())
        {
            title = title + (script.isBreakActive()
                    ? " | Break " + formatDuration(script.getBreakTimeRemainingSeconds())
                    : " | Break");
        }

        panelComponent.getChildren().add(TitleComponent.builder()
                .text(title)
                .color(Color.CYAN)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Current Task:")
                .right(script.getCurrentTaskName())
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Status:")
                .right(displayStatus)
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Time Running:")
                .right(formatDuration(script.getRuntimeSeconds()))
                .build());

        panelComponent.getChildren().add(LineComponent.builder()
                .left("Account Play Time:")
                .right(formatDuration(script.getAccountPlayTimeSeconds()))
                .build());

        if (config.useAntiban())
        {
            String antibanState = plugin.isAntibanEnabledForCurrentTask() ? "ON" : "OFF";
            // The custom mouse trigger (and its configured chance/countdown)
            // is suspended, so do not present its percentage as an active rate.
            String antibanSummary = antibanState + " | Task profile";

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Antiban:")
                    .right(antibanSummary)
                    .build());

            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Antiban Activity:")
                    .right(plugin.getAntibanActivity())
                    .build());
        }

        if (config.doBreaks())
        {
            if (script.isBreakActive())
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Break Remaining:")
                        .right(formatDuration(script.getBreakTimeRemainingSeconds()))
                        .build());
            }
            else
            {
                panelComponent.getChildren().add(LineComponent.builder()
                        .left("Time till break:")
                        .right(formatDuration(script.getTimeUntilBreakSeconds()))
                        .build());
            }
        }

        if (config.enableActivitySwitchRandomization()
                && !config.trainSingleSkill()
                && !config.runSingleQuest())
        {
            panelComponent.getChildren().add(LineComponent.builder()
                    .left("Time till activity change:")
                    .right(formatDuration(script.getTimeUntilActivitySwitchSeconds()))
                    .build());
        }

        return super.render(graphics);
    }

    /**
     * Draw the four alternative themes directly on the RuneLite overlay canvas.
     * No fake task percentages or future objectives: labels are sourced from
     * the same live Account Builder state as the original overlay.
     */
    private Dimension renderCustom(Graphics2D g, KspAccountBuilderOverlayStyle style)
    {
        panelComponent.getChildren().clear();
        Graphics2D d = (Graphics2D) g.create();
        try
        {
            d.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            d.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);
            switch (style)
            {
                case ACCOUNT_DASHBOARD: drawDashboard(d); return new Dimension(362, 258);
                case TASK_FOCUS: drawTaskFocus(d); return new Dimension(290, 151);
                case ACCOUNT_ROADMAP: drawRoadmap(d); return new Dimension(360, 262);
                case WORKFLOW_HUD: drawWorkflow(d); return new Dimension(580, 116);
                default: return new Dimension(280, 0);
            }
        }
        finally
        {
            d.dispose();
        }
    }

    private String task() { return script.getCurrentTaskName().replace('_', ' '); }
    private String status() { return normalizeStatus(Microbot.status); }
    private String mode()
    {
        return config.runSingleQuest() ? "SINGLE QUEST" : config.trainSingleSkill() ? "SINGLE SKILL" : "AUTOMATIC";
    }
    private String breakText()
    {
        if (!config.doBreaks()) return "OFF";
        return formatDuration(script.isAnyBreakActive() ? script.getBreakTimeRemainingSeconds() : script.getTimeUntilBreakSeconds());
    }
    private String switchText()
    {
        if (!config.enableActivitySwitchRandomization() || config.trainSingleSkill() || config.runSingleQuest()) return "--";
        return formatDuration(script.getTimeUntilActivitySwitchSeconds());
    }
    private String runState() { return script.isAnyBreakActive() ? "BREAK" : "RUNNING"; }

    private void drawDashboard(Graphics2D g)
    {
        rounded(g, 0, 0, 362, 258, 14, new Color(12, 13, 28, 241), new Color(145, 91, 236), 2);
        rounded(g, 9, 9, 344, 39, 9, new Color(34, 24, 61), null, 0);
        label(g, "KSP ACCOUNT BUILDER", 19, 25, 15, Font.BOLD, new Color(204, 167, 255), 236);
        label(g, "v" + KspAccountBuilderPlugin.VERSION, 20, 40, 10, Font.PLAIN, new Color(160, 144, 187), 130);
        pill(g, 276, 16, 72, 23, runState(), new Color(13, 63, 47), new Color(115, 234, 170));
        rounded(g, 10, 56, 342, 97, 9, new Color(22, 25, 48), new Color(68, 67, 108), 1);
        label(g, "CURRENT ACCOUNT ACTIVITY", 22, 75, 10, Font.BOLD, new Color(171, 156, 218), 314);
        label(g, task(), 23, 100, 18, Font.BOLD, Color.WHITE, 310);
        label(g, status(), 23, 124, 12, Font.PLAIN, new Color(204, 213, 234), 309);
        label(g, mode() + "  |  " + (config.useAntiban() ? "ANTI-BAN ON" : "ANTI-BAN OFF"),
                23, 143, 10, Font.BOLD, new Color(122, 226, 198), 316);
        metric(g, 10, 163, 164, 37, "SESSION", formatDuration(script.getRuntimeSeconds()), new Color(96, 162, 255));
        metric(g, 182, 163, 170, 37, "ACCOUNT PLAY", formatDuration(script.getAccountPlayTimeSeconds()), new Color(164, 119, 242));
        metric(g, 10, 207, 164, 37, "BREAK", breakText(), new Color(245, 189, 91));
        metric(g, 182, 207, 170, 37, "ACTIVITY SWITCH", switchText(), new Color(113, 223, 227));
    }

    private void drawTaskFocus(Graphics2D g)
    {
        rounded(g, 0, 0, 290, 151, 12, new Color(9, 17, 23, 242), new Color(214, 162, 63), 2);
        rounded(g, 5, 5, 280, 30, 8, new Color(23, 34, 42), null, 0);
        label(g, "KSP  /  ACCOUNT BUILDER", 13, 25, 13, Font.BOLD, new Color(98, 222, 252), 244);
        label(g, task(), 14, 61, 17, Font.BOLD, new Color(255, 211, 115), 270);
        label(g, status(), 14, 81, 11, Font.PLAIN, Color.WHITE, 270);
        line(g, 12, 91, 278, 91, new Color(77, 85, 87));
        label(g, "RUN  " + formatDuration(script.getRuntimeSeconds()), 14, 108, 11, Font.BOLD, new Color(157, 231, 178), 153);
        label(g, "PLAY " + formatDuration(script.getAccountPlayTimeSeconds()), 166, 108, 11, Font.BOLD, new Color(167, 193, 235), 110);
        label(g, "BREAK  " + breakText(), 14, 131, 11, Font.PLAIN, new Color(248, 190, 88), 145);
        label(g, "SWITCH  " + switchText(), 166, 131, 11, Font.PLAIN, new Color(131, 222, 240), 112);
        label(g, mode(), 13, 144, 8, Font.PLAIN, new Color(151, 158, 168), 240);
    }

    private void drawRoadmap(Graphics2D g)
    {
        rounded(g, 0, 0, 360, 262, 9, new Color(44, 35, 24, 248), new Color(177, 138, 72), 3);
        rounded(g, 8, 8, 344, 42, 8, new Color(75, 54, 29), new Color(149, 112, 56), 1);
        label(g, "KSP ACCOUNT BUILDER", 22, 30, 18, Font.BOLD, new Color(255, 210, 119), 315);
        label(g, "ACCOUNT ROADMAP  /  " + mode(), 20, 66, 10, Font.BOLD, new Color(211, 177, 115), 325);
        rounded(g, 12, 78, 336, 84, 7, new Color(28, 25, 23), new Color(112, 86, 55), 1);
        label(g, "ACTIVE OBJECTIVE", 23, 97, 10, Font.BOLD, new Color(198, 164, 105), 300);
        label(g, task(), 23, 120, 18, Font.BOLD, new Color(255, 223, 150), 315);
        label(g, status(), 23, 143, 12, Font.PLAIN, Color.WHITE, 313);
        label(g, "ADVENTURE SESSION", 21, 186, 10, Font.BOLD, new Color(213, 175, 100), 155);
        label(g, formatDuration(script.getRuntimeSeconds()), 21, 205, 15, Font.BOLD, Color.WHITE, 140);
        label(g, "ACCOUNT PLAY", 192, 186, 10, Font.BOLD, new Color(213, 175, 100), 145);
        label(g, formatDuration(script.getAccountPlayTimeSeconds()), 192, 205, 15, Font.BOLD, Color.WHITE, 140);
        line(g, 16, 218, 344, 218, new Color(138, 105, 61));
        label(g, "BREAK  " + breakText(), 21, 239, 12, Font.BOLD, new Color(255, 204, 105), 152);
        label(g, "NEXT ACTIVITY  " + switchText(), 186, 239, 11, Font.BOLD, new Color(190, 217, 156), 153);
        label(g, config.useAntiban() ? "ANTI-BAN ON" : "ANTI-BAN OFF", 21, 254, 9, Font.PLAIN, new Color(181, 186, 156), 165);
    }

    private void drawWorkflow(Graphics2D g)
    {
        rounded(g, 0, 0, 580, 116, 12, new Color(7, 16, 26, 244), new Color(34, 187, 240), 2);
        rounded(g, 7, 7, 566, 36, 8, new Color(13, 35, 51), null, 0);
        label(g, "KSP ACCOUNT BUILDER", 19, 30, 16, Font.BOLD, new Color(76, 226, 255), 257);
        pill(g, 296, 14, 80, 23, runState(), new Color(13, 66, 51), new Color(121, 238, 175));
        label(g, task(), 388, 30, 12, Font.BOLD, new Color(252, 202, 119), 179);
        label(g, status(), 19, 61, 12, Font.PLAIN, Color.WHITE, 540);
        line(g, 14, 70, 566, 70, new Color(38, 91, 113));
        label(g, "RUN " + formatDuration(script.getRuntimeSeconds()), 17, 93, 12, Font.BOLD, new Color(170, 225, 253), 118);
        label(g, "PLAY " + formatDuration(script.getAccountPlayTimeSeconds()), 139, 93, 12, Font.BOLD, new Color(174, 200, 255), 129);
        label(g, "BREAK " + breakText(), 274, 93, 12, Font.BOLD, new Color(248, 199, 105), 127);
        label(g, "SWITCH " + switchText(), 416, 93, 12, Font.BOLD, new Color(127, 230, 221), 150);
        label(g, mode() + "  |  " + (config.useAntiban() ? "ANTI-BAN ON" : "ANTI-BAN OFF"),
                18, 107, 9, Font.PLAIN, new Color(131, 166, 187), 544);
    }

    private void metric(Graphics2D g, int x, int y, int w, int h, String name, String value, Color accent)
    {
        rounded(g, x, y, w, h, 7, new Color(21, 29, 50), new Color(51, 64, 91), 1);
        label(g, name, x + 10, y + 13, 9, Font.BOLD, accent, w - 18);
        label(g, value, x + 10, y + 30, 14, Font.BOLD, Color.WHITE, w - 18);
    }

    private void pill(Graphics2D g, int x, int y, int w, int h, String value, Color bg, Color fg)
    {
        rounded(g, x, y, w, h, 10, bg, fg, 1);
        label(g, value, x + 9, y + h - 7, 10, Font.BOLD, fg, w - 14);
    }

    private void rounded(Graphics2D g, int x, int y, int w, int h, int radius, Color fill, Color border, int borderWidth)
    {
        RoundRectangle2D.Float box = new RoundRectangle2D.Float(x, y, w, h, radius, radius);
        g.setColor(fill);
        g.fill(box);
        if (border != null && borderWidth > 0)
        {
            g.setColor(border);
            g.setStroke(new BasicStroke(borderWidth));
            g.draw(box);
        }
    }

    private void line(Graphics2D g, int x1, int y1, int x2, int y2, Color color)
    {
        g.setColor(color);
        g.drawLine(x1, y1, x2, y2);
    }

    private void label(Graphics2D g, String text, int x, int baseline, int size, int weight, Color color, int maxWidth)
    {
        if (text == null) text = "";
        g.setFont(new Font("SansSerif", weight, size));
        FontMetrics metrics = g.getFontMetrics();
        String displayed = text;
        while (displayed.length() > 1 && metrics.stringWidth(displayed) > maxWidth)
        {
            displayed = displayed.substring(0, displayed.length() - 1);
        }
        if (!displayed.equals(text) && displayed.length() > 2) displayed = displayed.substring(0, displayed.length() - 2) + "..";
        g.setColor(color);
        g.drawString(displayed, x, baseline);
    }

    private String formatDuration(long totalSeconds)
    {
        if (totalSeconds < 0)
        {
            return "--";
        }

        long hours = TimeUnit.SECONDS.toHours(totalSeconds);
        long minutes = TimeUnit.SECONDS.toMinutes(totalSeconds) % 60;
        long seconds = totalSeconds % 60;

        if (hours > 0)
        {
            return String.format("%02d:%02d:%02d", hours, minutes, seconds);
        }
        return String.format("%02d:%02d", minutes, seconds);
    }

    private String normalizeStatus(String status)
    {
        if (status == null || status.isEmpty())
        {
            return "Idle";
        }

        String normalizedStatus = status.trim();
        if (normalizedStatus.toLowerCase().startsWith("walking to closest bank"))
        {
            return "Walking to Bank";
        }

        String lowerCaseStatus = normalizedStatus.toLowerCase();
        if (lowerCaseStatus.startsWith("chopping down "))
        {
            String treeName = normalizedStatus.substring("Chopping Down ".length()).trim();
            if (!treeName.isEmpty())
            {
                return "Choppin " + treeName;
            }
        }

        return normalizedStatus;
    }
}
