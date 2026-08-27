package net.runelite.client.plugins.microbot.KSPGELooter;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.time.Duration;

public class KSPGELooterOverlay extends OverlayPanel
{
    private final KSPGELooterConfig config;

    @Inject
    KSPGELooterOverlay(KSPGELooterPlugin plugin, KSPGELooterConfig config)
    {
        super(plugin);
        this.config = config;
        setPosition(OverlayPosition.TOP_CENTER);
    }

    @Override
    public Dimension render(java.awt.Graphics2D graphics)
    {
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(300, 460));

        Duration runtime = KSPGELooterScript.getRuntime();
        long lootGpPerHour = perHour(KSPGELooterScript.totalLootGeValue, runtime);
        long alchMarginPerHour = perHour(KSPGELooterScript.totalAlchMargin, runtime);

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP GE Looter v" + KSPGELooterPlugin.VERSION)
                .color(Color.ORANGE)
                .build());

        addLine("Status:", KSPGELooterScript.status);
        addLine("Area:", KSPGELooterScript.insideArea ? "Inside GE area" : "OUTSIDE / PAUSED");
        addLine("Runtime:", formatDuration(runtime));
        addLine("Priority mode:", config.priorityMode() ? "ON" : "OFF");
        addLine("Priority takeover:", KSPGELooterScript.priorityTakeoverActive ? "ACTIVE" : "Idle");
        addLine("Script pause:", KSPGELooterScript.priorityPauseOwned
                ? "Owned by looter"
                : (KSPGELooterScript.priorityTakeoverActive ? "Shared / external" : "Not owned"));

        addLine("Ground items:", Integer.toString(KSPGELooterScript.groundItemsSeen));
        addLine("Eligible loot:", Integer.toString(KSPGELooterScript.eligibleGroundItems));
        addLine("Target:", KSPGELooterScript.targetName);
        addLine("Target GE:", formatGp(KSPGELooterScript.targetGeValue));
        addLine("Minimum GE:", formatGp(Math.max(0, config.minimumGeValue())));
        addLine("Spam clicks:", Math.max(1, Math.min(12, config.spamClicks())) + " @ " + Math.max(30, Math.min(250, config.spamDelayMs())) + "ms");

        addLine("Inventory:", KSPGELooterScript.inventorySlotsUsed + "/28 slots");
        addLine("Loot quantity:", Integer.toString(KSPGELooterScript.itemsLooted));
        addLine("Loot GE value:", formatGp(KSPGELooterScript.totalLootGeValue));
        addLine("Loot GE / hr:", formatGp(lootGpPerHour));

        addLine("High Alch:", config.highAlch() ? "ON" : "OFF");
        addLine("Staff of fire:", KSPGELooterScript.staffOfFireEquipped ? "Equipped" : "Not equipped");
        addLine("Nature runes:", KSPGELooterScript.natureRunes + "  (" + formatGp(KSPGELooterScript.natureRuneGePrice) + " ea)");
        addLine("Fire runes:", KSPGELooterScript.fireRunes + "  (" + formatGp(KSPGELooterScript.fireRuneGePrice) + " ea)");
        addLine("Alch rune cost:", formatGp(KSPGELooterScript.alchRuneCost));
        addLine("Items alched:", Integer.toString(KSPGELooterScript.itemsAlched));
        addLine("Alch value:", formatGp(KSPGELooterScript.totalAlchValue));
        addLine("Alch margin:", formatGp(KSPGELooterScript.totalAlchMargin));
        addLine("Alch margin / hr:", formatGp(alchMarginPerHour));

        return super.render(graphics);
    }

    private void addLine(String left, String right)
    {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(left)
                .right(right == null ? "-" : right)
                .build());
    }

    private static long perHour(long value, Duration runtime)
    {
        long seconds = runtime.getSeconds();
        if (value <= 0L || seconds <= 0L) return 0L;
        return Math.round(value * 3600.0D / seconds);
    }

    private static String formatDuration(Duration duration)
    {
        long totalSeconds = duration.getSeconds();
        long hours = totalSeconds / 3600;
        long minutes = (totalSeconds % 3600) / 60;
        long seconds = totalSeconds % 60;
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }

    private static String formatGp(long value)
    {
        if (value >= 1_000_000_000L) return String.format("%.2fB", value / 1_000_000_000.0D);
        if (value >= 1_000_000L) return String.format("%.2fM", value / 1_000_000.0D);
        if (value >= 1_000L) return String.format("%.1fK", value / 1_000.0D);
        return Long.toString(value);
    }
}
