package net.runelite.client.plugins.microbot.kspwillowchopper;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import javax.inject.Singleton;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.text.NumberFormat;
import java.time.Duration;

@Singleton
public class KspWillowChopperOverlay extends OverlayPanel {
    private static final Color TITLE = new Color(125, 225, 155);
    private static final Color HEADER = new Color(155, 205, 255);
    private static final Color VALUE = Color.WHITE;
    private static final Color ACTIVE = new Color(255, 224, 130);
    private static final Color GOOD = new Color(145, 230, 160);

    private final KspWillowChopperPlugin plugin;
    private final KspWillowChopperConfig config;
    private final KspWillowChopperScript script;

    @Inject
    public KspWillowChopperOverlay(
            KspWillowChopperPlugin plugin,
            KspWillowChopperConfig config,
            KspWillowChopperScript script) {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay() || !Microbot.isLoggedIn()) {
            return null;
        }

        try {
            panelComponent.setPreferredSize(new Dimension(275, 0));
            panelComponent.getChildren().clear();

            panelComponent.getChildren().add(TitleComponent.builder()
                    .text("KSP Willow Chopper v" + KspWillowChopperPlugin.VERSION)
                    .color(TITLE)
                    .build());

            KspTree tree = script.getActiveTree();
            addLine("Tree:", tree.toString(), ACTIVE);
            addLine("Mode:", config.bankLogs() ? "Bank resources" : "Burn logs", ACTIVE);
            addLine("Status:", script.getStatus(), ACTIVE);
            addLine("Resource:", tree.getResourceName(), VALUE);
            addLine("Inventory:", String.valueOf(script.getCurrentResourceCount()), VALUE);

            if (!config.bankLogs() && tree.isCampfireBurnable()) {
                String fireState = script.isBurningActive() ? "Burning" : (script.isCampfireNearby() ? "Nearby" : "None");
                addLine("Campfire:", fireState, script.isCampfireNearby() ? GOOD : ACTIVE);
            }

            if (config.enableForestry()) {
                KspForestryEvent event = plugin.getCurrentForestryEvent();
                addLine("Forestry:", event == KspForestryEvent.NONE ? "Waiting" : event.toString(),
                        event == KspForestryEvent.NONE ? VALUE : ACTIVE);
                addLine("Events done:", String.valueOf(plugin.getCompletedForestryEvents()), VALUE);
            }

            separator();
            header("Session");

            long elapsedMs = script.getRuntimeMillis();
            double hours = elapsedMs / 3_600_000.0;
            int wcXp = script.getWoodcuttingXpGained();
            int fmXp = script.getFiremakingXpGained();
            int chopped = script.getResourcesChopped();

            addLine("Runtime:", formatDuration(elapsedMs), VALUE);
            addLine("Items chopped:", format(chopped) + " (" + formatRate(chopped, hours) + "/h)", VALUE);

            if (config.bankLogs()) {
                addLine("Items banked:", format(script.getResourcesBanked()), GOOD);
            } else if (tree.isCampfireBurnable()) {
                addLine("Logs burned:", format(script.getResourcesBurned()), GOOD);
                addLine("Campfires lit:", format(script.getCampfiresLit()), VALUE);
            }

            addLine("WC Lv:", formatLevel(script.getWoodcuttingLevel(), script.getWoodcuttingLevelsGained()), VALUE);
            addLine("FM Lv:", formatLevel(script.getFiremakingLevel(), script.getFiremakingLevelsGained()), VALUE);
            addLine("WC XP:", format(wcXp) + " (" + formatRate(wcXp, hours) + "/h)", VALUE);
            if (!config.bankLogs() || fmXp > 0) {
                addLine("FM XP:", format(fmXp) + " (" + formatRate(fmXp, hours) + "/h)", VALUE);
            }

            if (plugin.getAnimaBarkGained() > 0) {
                addLine("Anima bark:", format(plugin.getAnimaBarkGained()), GOOD);
            }

            if (config.enableForestry() && plugin.hasLearnedSaplingCombination()) {
                separator();
                header("Sapling combo");
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
        panelComponent.getChildren().add(LineComponent.builder().left(text).leftColor(HEADER).build());
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
        if (hours <= 0.0 || value <= 0) {
            return "0";
        }
        return format(Math.round(value / hours));
    }

    private String formatLevel(int current, int gained) {
        return current + " / +" + gained;
    }

    private String formatDuration(long millis) {
        if (millis <= 0) {
            return "00:00:00";
        }
        Duration d = Duration.ofMillis(millis);
        return String.format("%02d:%02d:%02d", d.toHours(), d.toMinutesPart(), d.toSecondsPart());
    }
}
