package net.runelite.client.plugins.microbot.kspmadcow;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Locale;

public class KspMadCowOverlay extends OverlayPanel {
    private static final int PANEL_WIDTH = 320;
    private static final int MAX_VALUE_LENGTH = 42;
    private final KspMadCowScript script;

    @Inject
    KspMadCowOverlay(KspMadCowPlugin plugin, KspMadCowScript script) {
        super(plugin);
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        KspMadCowScript.OverlaySnapshot snapshot = script.getOverlaySnapshot();
        if (snapshot == null) return null;

        panelComponent.setPreferredSize(new Dimension(PANEL_WIDTH, 0));
        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Mad Cow v" + KspMadCowPlugin.VERSION)
                .color(snapshot.isRunning() ? Color.GREEN : Color.LIGHT_GRAY)
                .build());

        addLine("Runtime", snapshot.getRuntime());
        addLine("Kills", snapshot.getKills() + " (" + snapshot.getKillsPerHour() + "/hr)");
        addLine("State", formatState(snapshot.getState()));
        addLine("Action", shorten(snapshot.getAction()));
        divider();
        addLine("Instance", snapshot.isInstanceConfirmed() ? "Confirmed" : "Not confirmed");
        addLine("Client instance", snapshot.isClientInstance() ? "Detected" : "No");
        addLine("Travel phase", snapshot.getTravelPhase());
        addLine("Brutus", snapshot.getBossStatus());
        addLine("Combat mode", snapshot.getCombatMode());
        addLine("Training", snapshot.getTraining());
        addLine("Hitpoints", snapshot.getHitpointsPercent() + "%");
        addLine("Prayer", snapshot.getPrayerCurrent() + "/" + snapshot.getPrayerReal());
        addLine("Prayer plan", shorten(snapshot.getPrayerPlan()));
        addLine("Active prayers", shorten(snapshot.getActivePrayers()));
        divider();
        addLine("Special", snapshot.getSpecial());
        addLine("Brutus animation", snapshot.getBrutusAnimation() + " (frame " + snapshot.getBrutusAnimationFrame() + ")");
        addLine("Dodge target", snapshot.getSpecialTarget());
        addLine("Re-attack", snapshot.isReattackPending() ? "Pending" : "Ready");
        divider();
        addLine(snapshot.getFoodName(), snapshot.getFoodCount() + "/" + snapshot.getFoodTarget());
        addLine("Inventory", snapshot.getInventorySlots() + "/28 slots");
        addLine("Cowbell", snapshot.getCowbell());
        addLine("Air runes", Integer.toString(snapshot.getAirRunes()));
        addLine("Mooleta", snapshot.getMooleta());
        addLine("Boosting potion", shorten(snapshot.getPotion()));
        addLine("Altar restore", snapshot.isAltarRestore() ? "Required" : "Ready");
        return super.render(graphics);
    }

    private void divider() { panelComponent.getChildren().add(LineComponent.builder().build()); }

    private void addLine(String left, String right) {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(left)
                .right(right == null ? "-" : right)
                .build());
    }

    private String formatState(KspMadCowState state) {
        if (state == null) return "Unknown";
        String value = state.name().toLowerCase(Locale.ROOT).replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private String shorten(String value) {
        if (value == null || value.isBlank()) return "-";
        return value.length() <= MAX_VALUE_LENGTH ? value : value.substring(0, MAX_VALUE_LENGTH - 3) + "...";
    }
}
