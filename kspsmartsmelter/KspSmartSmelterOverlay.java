package net.runelite.client.plugins.microbot.kspsmartsmelter;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.RouteQuote;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.SmeltRoute;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;
import java.util.List;

public class KspSmartSmelterOverlay extends OverlayPanel {
    private static final int WIDTH = 285;

    private final KspSmartSmelterConfig config;
    private final KspSmartSmelterScript script;

    @Inject
    public KspSmartSmelterOverlay(
            KspSmartSmelterPlugin plugin,
            KspSmartSmelterConfig config,
            KspSmartSmelterScript script
    ) {
        super(plugin);
        this.config = config;
        this.script = script;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        if (!config.showOverlay()) {
            return null;
        }

        try {
            panelComponent.setPreferredSize(new Dimension(WIDTH, 0));

            panelComponent.getChildren().add(
                    TitleComponent.builder()
                            .text("KSP Smart Smelter v" + KspSmartSmelterPlugin.VERSION)
                            .build()
            );

            addSection("Current");
            add("State", friendly(script.getState().name()));
            add("Status", shorten(Microbot.status, 31));
            add("Smithing", String.valueOf(script.getSmithingLevel()));
            add("Account", script.isMemberAccount() ? "Members" : "F2P");
            add("Location", config.furnaceLocation().getDisplayName());
            add("Ranking", config.rankingMode().toString());
            add("Anti-ban", shorten(script.getAntibanStatus(), 31));

            RouteQuote quote = script.getSelectedQuote();
            if (quote == null) {
                add("Selected route", "None");
                add("Price scan", scanAge());
            } else {
                addSection("Selected Route");
                add("Method", quote.getRoute().getOutputName());
                add("Input / cycle", formatGp(quote.getInputCostPerCycle()));
                add("Net output", formatGp(quote.getNetRevenuePerCycle()));
                add("Profit / cycle", signedGp(quote.getProfitPerCycle()));
                add("ROI", String.format("%.2f%%", quote.getRoiPercent()));
                add("Cycles / trip", String.valueOf(quote.getTripCycles()));
                add("Profit / trip", signedGp(quote.getTripProfit()));
                add("Bank cycles", String.valueOf(script.getSelectedBankCycles()));
                add("Inv cycles", String.valueOf(script.getSelectedInventoryCycles()));
                add("Liquidity", quote.getLiquidity() > 0 ? formatNumber(quote.getLiquidity()) : "Unknown");
                add("Price scan", scanAge());

                addSection("Inputs");
                addInputLines(quote.getRoute());
            }

            boolean sessionStarted = script.getStartedAt() > 0;
            addSection("Session");
            add("Runtime", sessionStarted
                    ? formatDuration(System.currentTimeMillis() - script.getStartedAt())
                    : "00:00:00");
            add("Trips", String.valueOf(script.getCompletedTrips()));
            add("Restocks", String.valueOf(script.getRestockCount()));
            add("Output made", formatNumber(script.getOutputProduced()));
            add("Output / h", formatNumber(script.getOutputPerHour()));
            add("Expected profit", signedGp(script.getExpectedSessionProfit()));
            add("Expected GP / h", signedGp(script.getExpectedProfitPerHour()));
            add("Smithing XP", sessionStarted ? formatNumber(script.getSmithingXpGained()) : "0");
            add("Smithing XP / h", sessionStarted ? formatNumber(script.getSmithingXpPerHour()) : "0");

            addSection("GE Restock");
            add("Enabled", config.autoRestock() ? "Yes" : "No");
            add("Sell outputs", config.autoSellOutput() ? "Yes" : "No");
            if (config.autoRestock()) {
                add("Restock cycles", String.valueOf(config.restockCycles()));
                add("Buy adjustment", signedPercent(config.buyPercent()));
                add("Sell adjustment", signedPercent(config.sellPercent()));
            }

            addAlternatives(script.getLastQuotes(), quote);
        } catch (Exception ex) {
            Microbot.logStackTrace(getClass().getSimpleName(), ex);
        }

        return super.render(graphics);
    }

    private void addInputLines(SmeltRoute route) {
        int[] ids = route.getInputIds();
        int[] quantities = route.getInputQuantities();

        for (int i = 0; i < ids.length; i++) {
            int id = ids[i];
            int perCycle = quantities[i];
            int inventory = Rs2Inventory.itemQuantity(id);
            int bank = bankQuantity(id);
            add(
                    shorten(itemName(id), 18),
                    perCycle + "/c | I:" + formatNumber(inventory) + " B:" + formatNumber(bank)
            );
        }
    }

    private void addAlternatives(List<RouteQuote> quotes, RouteQuote selected) {
        if (quotes == null || quotes.isEmpty()) {
            return;
        }

        int shown = 0;
        for (RouteQuote quote : quotes) {
            if (selected != null && quote.getRoute() == selected.getRoute()) {
                continue;
            }

            if (shown == 0) {
                addSection("Next Best Routes");
            }

            add(
                    (shown + 2) + ". " + shorten(quote.getRoute().getOutputName(), 16),
                    signedGp(quote.getTripProfit()) + " / trip"
            );

            if (++shown >= 3) {
                break;
            }
        }
    }

    private int bankQuantity(int id) {
        try {
            return Rs2Bank.bankItems().stream()
                    .filter(item -> item.getId() == id)
                    .mapToInt(item -> item.getQuantity())
                    .sum();
        } catch (Exception ignored) {
            return 0;
        }
    }

    private String itemName(int itemId) {
        return Microbot.getClientThread().runOnClientThreadOptional(() ->
                Microbot.getItemManager().getItemComposition(itemId).getName()
        ).orElse(String.valueOf(itemId));
    }

    private String scanAge() {
        long scan = script.getLastPriceScan();
        if (scan <= 0) {
            return "Pending";
        }

        long age = Math.max(0, (System.currentTimeMillis() - scan) / 1000L);
        return age + "s ago";
    }

    private void addSection(String title) {
        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left("— " + title + " —")
                        .build()
        );
    }

    private void add(String left, String right) {
        panelComponent.getChildren().add(
                LineComponent.builder()
                        .left(left == null ? "" : left)
                        .right(right == null ? "" : right)
                        .build()
        );
    }

    private String friendly(String value) {
        if (value == null || value.isEmpty()) {
            return "";
        }

        String lower = value.replace('_', ' ').toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private String shorten(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private String signedPercent(int value) {
        return (value > 0 ? "+" : "") + value + "%";
    }

    private String signedGp(double gp) {
        return (gp > 0 ? "+" : "") + formatGp(gp);
    }

    private String formatGp(double gp) {
        double abs = Math.abs(gp);
        if (abs >= 1_000_000_000) {
            return String.format("%.2fb", gp / 1_000_000_000.0);
        }
        if (abs >= 1_000_000) {
            return String.format("%.2fm", gp / 1_000_000.0);
        }
        if (abs >= 1_000) {
            return String.format("%.1fk", gp / 1_000.0);
        }
        return String.format("%.0f", gp);
    }

    private String formatNumber(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000) {
            return String.format("%.2fb", value / 1_000_000_000.0);
        }
        if (abs >= 1_000_000) {
            return String.format("%.2fm", value / 1_000_000.0);
        }
        if (abs >= 1_000) {
            return String.format("%.1fk", value / 1_000.0);
        }
        return String.format("%.0f", value);
    }

    private String formatDuration(long millis) {
        Duration d = Duration.ofMillis(Math.max(0, millis));
        long hours = d.toHours();
        long minutes = d.minusHours(hours).toMinutes();
        long seconds = d.minusHours(hours).minusMinutes(minutes).getSeconds();
        return String.format("%02d:%02d:%02d", hours, minutes, seconds);
    }
}
