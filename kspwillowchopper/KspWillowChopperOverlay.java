package net.runelite.client.plugins.microbot.kspwillowchopper;

import net.runelite.api.Client;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.text.NumberFormat;
import java.time.Duration;

public class KspWillowChopperOverlay extends OverlayPanel {
    private static final Color TITLE = new Color(125, 225, 155);
    private static final Color HEADER = new Color(155, 205, 255);
    private static final Color VALUE = Color.WHITE;
    private static final Color ACTIVE = new Color(255, 224, 130);
    private static final Color GOOD = new Color(145, 230, 160);

    private final KspWillowChopperPlugin plugin;
    private final KspWillowChopperConfig config;
    private final KspWillowChopperScript script;
    private final Client client;

    @Inject
    public KspWillowChopperOverlay(
            KspWillowChopperPlugin plugin,
            KspWillowChopperConfig config,
            KspWillowChopperScript script,
            Client client) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        this.script = script;
        this.client = client;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay() || !Microbot.isLoggedIn()) {
            return null;
        }

        try {
            panelComponent.setPreferredSize(new Dimension(265, 360));
            panelComponent.getChildren().clear();

            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("KSP Willow Chopper v" + KspWillowChopperPlugin.VERSION)
                    .color(TITLE)
                    .build());

            addLine("Mode:", config.bankLogs() ? "Bank logs" : "Burn logs", ACTIVE);
            addLine("Status:", script.getStatus(), ACTIVE);
            addLine("Willow logs:", String.valueOf(Rs2Inventory.count(KspWillowChopperScript.WILLOW_LOG_ID)), VALUE);

            if (!config.bankLogs()) {
                String fireState = script.isBurningActive()
                        ? "Burning"
                        : (script.isCampfireNearby() ? "Nearby" : "None");
                addLine("Campfire:", fireState, script.isCampfireNearby() ? GOOD : ACTIVE);
            }

            if (config.enableForestry()) {
                KspForestryEvent event = plugin.getCurrentForestryEvent();
                addLine("Forestry:", event == KspForestryEvent.NONE ? "Waiting" : event.toString(),
                        event == KspForestryEvent.NONE ? VALUE : ACTIVE);
                addLine("Events completed:", String.valueOf(plugin.getCompletedForestryEvents()), VALUE);
            }

            separator();
            header("Session");

            long elapsedMs = Math.max(1L, System.currentTimeMillis() - script.getStartTimeMillis());
            double hours = elapsedMs / 3_600_000.0;

            int wcXp = Math.max(0, client.getSkillExperience(Skill.WOODCUTTING) - script.getStartWoodcuttingXp());
            int fmXp = Math.max(0, client.getSkillExperience(Skill.FIREMAKING) - script.getStartFiremakingXp());
            int chopped = plugin.getLogsChopped();

            addLine("Runtime:", formatDuration(elapsedMs), VALUE);
            addLine("Logs chopped:", format(chopped) + " (" + formatRate(chopped, hours) + "/h)", VALUE);

            if (config.bankLogs()) {
                addLine("Logs banked:", format(script.getLogsBanked()), GOOD);
            } else {
                addLine("Logs burned:", format(script.getLogsBurned()), GOOD);
                addLine("Campfires lit:", format(script.getCampfiresLit()), VALUE);
            }

            addLine("WC XP:", format(wcXp) + " (" + formatRate(wcXp, hours) + "/h)", VALUE);
            if (!config.bankLogs() || fmXp > 0) {
                addLine("FM XP:", format(fmXp) + " (" + formatRate(fmXp, hours) + "/h)", VALUE);
            }

            if (plugin.getAnimaBarkGained() > 0) {
                addLine("Anima bark:", format(plugin.getAnimaBarkGained()), GOOD);
            }

            if (config.enableForestry() && plugin.hasLearnedSaplingCombination()) {
                separator();
                header("Sapling combination");
                String[] combo = plugin.getSaplingCombination();
                addLine("1st:", combo[0], GOOD);
                addLine("2nd:", combo[1], GOOD);
                addLine("3rd:", combo[2], GOOD);
            }
        } catch (Exception ex) {
            Microbot.logStackTrace("KspWillowChopperOverlay", ex);
        }

        return super.render(graphics);
    }

    private void header(String text) {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(text)
                .leftColor(HEADER)
                .build());
    }

    private void separator() {
        panelComponent.getChildren().add(LineComponent.builder().left("").build());
    }

    private void addLine(String left, String right, Color rightColor) {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(left)
                .right(right == null ? "-" : right)
                .rightColor(rightColor)
                .build());
    }

    private String format(long value) {
        return NumberFormat.getIntegerInstance().format(value);
    }

    private String formatRate(long value, double hours) {
        if (hours <= 0.0) {
            return "0";
        }
        return format(Math.round(value / hours));
    }

    private String formatDuration(long millis) {
        Duration d = Duration.ofMillis(millis);
        return String.format("%02d:%02d:%02d",
                d.toHours(),
                d.toMinutesPart(),
                d.toSecondsPart());
    }
}
