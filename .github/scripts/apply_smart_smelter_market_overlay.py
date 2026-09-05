from pathlib import Path


def replace_once(path: Path, old: str, new: str):
    text = path.read_text(encoding='utf-8')
    if old not in text:
        raise RuntimeError(f'Expected block not found in {path}: {old[:160]!r}')
    path.write_text(text.replace(old, new, 1), encoding='utf-8')


config = Path('kspsmartsmelter/KspSmartSmelterConfig.java')
replace_once(config,
'''    @ConfigItem(keyName = "restockCycles", name = "Restock cycles", description = "Target number of production cycles bought per GE restock", position = 11)
    default int restockCycles() { return 500; }
    @ConfigItem(keyName = "buyPercent", name = "GE buy %", description = "Percentage above the current GE offer baseline when restocking", position = 12)
    default int buyPercent() { return 5; }
    @ConfigItem(keyName = "sellPercent", name = "GE sell %", description = "Percentage adjustment used when selling output; normally negative", position = 13)
    default int sellPercent() { return -5; }
    @ConfigItem(keyName = "offerWaitSeconds", name = "GE wait (sec)", description = "How long to wait for restock offers before collecting and rechecking", position = 14)
    default int offerWaitSeconds() { return 15; }
    @ConfigItem(keyName = "showOverlay", name = "Overlay", description = "Show the Smart Smelter overlay", position = 15)
''',
'''    @ConfigItem(keyName = "offerWaitSeconds", name = "GE wait (sec)", description = "How long to wait for restock offers before collecting and rechecking", position = 11)
    default int offerWaitSeconds() { return 15; }
    @ConfigItem(keyName = "showOverlay", name = "Overlay", description = "Show the Smart Smelter overlay", position = 12)
''')

trader = Path('kspsmartsmelter/SmartSmelterGeTrader.java')
replace_once(trader,
'''    static boolean placeBuy(int itemId, String itemName, int quantity, int percent)
''',
'''    static boolean placeBuy(int itemId, String itemName, int quantity)
''')
replace_once(trader,
'''        int price = offerPrice(itemId, GrandExchangeAction.BUY, percent);
''',
'''        int price = offerPrice(itemId, GrandExchangeAction.BUY);
''')
replace_once(trader,
'''    static boolean placeSell(int itemId, String itemName, int quantity, int percent)
''',
'''    static boolean placeSell(int itemId, String itemName, int quantity)
''')
replace_once(trader,
'''        int price = offerPrice(itemId, GrandExchangeAction.SELL, percent);
''',
'''        int price = offerPrice(itemId, GrandExchangeAction.SELL);
''')
replace_once(trader,
'''    private static int offerPrice(int itemId, GrandExchangeAction action, int percent)
    {
        try
        {
            WikiPrice market = Rs2GrandExchange.getRealTimePrices(itemId);
            if (market == null)
            {
                return 0;
            }
            int baseline = action == GrandExchangeAction.BUY
                    ? market.buyPrice
                    : market.sellPrice;
            if (baseline <= 0)
            {
                return 0;
            }
            long adjusted = Math.round(baseline * ((100.0 + percent) / 100.0));
            return (int) Math.max(1L, Math.min(Integer.MAX_VALUE, adjusted));
        }
        catch (RuntimeException ex)
        {
            log.debug("Unable to price GE item {}: {}", itemId, ex.getMessage());
            return 0;
        }
    }
''',
'''    private static int offerPrice(int itemId, GrandExchangeAction action)
    {
        try
        {
            WikiPrice market = Rs2GrandExchange.getRealTimePrices(itemId);
            if (market == null)
            {
                return 0;
            }

            // RuneLite's real-time Wiki pricing is already side-aware: buyPrice is
            // the current instant-buy/high side and sellPrice is the instant-sell/low
            // side. Use that market value directly instead of applying a manual %.
            int marketPrice = action == GrandExchangeAction.BUY
                    ? market.buyPrice
                    : market.sellPrice;
            return marketPrice > 0 ? marketPrice : 0;
        }
        catch (RuntimeException ex)
        {
            log.debug("Unable to price GE item {}: {}", itemId, ex.getMessage());
            return 0;
        }
    }
''')

script = Path('kspsmartsmelter/KspSmartSmelterScript.java')
replace_once(script,
'''    private static final long TARGET_INTERACTION_TIMEOUT_MS = 8_000L;
''',
'''    private static final long TARGET_INTERACTION_TIMEOUT_MS = 8_000L;
    // Restock sizing is internal; users should not have to tune a cycle count.
    private static final int AUTO_RESTOCK_CYCLES = 500;
''')
replace_once(script,
'''    private volatile int startingSmithingXp;
''',
'''    private volatile int startingSmithingXp;
    private volatile int startingSmithingLevel;
''')
replace_once(script,
'''        startingSmithingXp = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getSkillExperience(Skill.SMITHING))
                .orElse(0);
''',
'''        startingSmithingXp = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getSkillExperience(Skill.SMITHING))
                .orElse(0);
        startingSmithingLevel = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> Microbot.getClient().getRealSkillLevel(Skill.SMITHING))
                .orElse(1);
''')
replace_once(script,
'''        int targetCycles = Math.max(1, config.restockCycles());
''',
'''        int targetCycles = AUTO_RESTOCK_CYCLES;
''')
replace_once(script,
'''            if (!SmartSmelterGeTrader.placeBuy(ids[i], inputName, wanted, config.buyPercent())) {
''',
'''            if (!SmartSmelterGeTrader.placeBuy(ids[i], inputName, wanted)) {
''')
replace_once(script,
'''        if (SmartSmelterGeTrader.placeSell(
                route.getOutputId(), outputName, quantity, config.sellPercent())) {
''',
'''        if (SmartSmelterGeTrader.placeSell(
                route.getOutputId(), outputName, quantity)) {
''')
replace_once(script,
'''    public boolean isMemberAccount() {
        return Rs2WorldUtil.isMemberAccount();
    }
''',
'''    public int getSmithingLevelsGained() {
        return Math.max(0, getSmithingLevel() - startingSmithingLevel);
    }

    public boolean isMemberAccount() {
        return Rs2WorldUtil.isMemberAccount();
    }
''')

overlay = Path('kspsmartsmelter/KspSmartSmelterOverlay.java')
overlay.write_text('''package net.runelite.client.plugins.microbot.kspsmartsmelter;

import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.kspsmartsmelter.model.RouteQuote;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.time.Duration;

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

            add("Status", shorten(Microbot.status, 31));
            add("Smithing", script.getSmithingLevel() + " / +" + script.getSmithingLevelsGained());
            add("Account", script.isMemberAccount() ? "Members" : "F2P");
            add("Anti-ban", shorten(script.getAntibanStatus(), 31));

            RouteQuote quote = script.getSelectedQuote();
            addSection("Route");
            if (quote == null) {
                add("Method", "None");
            } else {
                add("Method", quote.getRoute().getOutputName());
                add("Profit / cycle", signedGp(quote.getProfitPerCycle()));
                add("ROI", String.format("%.2f%%", quote.getRoiPercent()));
                add("Stock", script.getSelectedBankCycles() + " bank / "
                        + script.getSelectedInventoryCycles() + " inv");
            }

            boolean sessionStarted = script.getStartedAt() > 0;
            addSection("Session");
            add("Runtime", sessionStarted
                    ? formatDuration(System.currentTimeMillis() - script.getStartedAt())
                    : "00:00:00");
            add("Profit", signedGp(script.getExpectedSessionProfit()) + " / "
                    + signedGp(script.getExpectedProfitPerHour()) + "/h");
            add("Output", formatNumber(script.getOutputProduced()) + " / "
                    + formatNumber(script.getOutputPerHour()) + "/h");
            add("XP", (sessionStarted ? formatNumber(script.getSmithingXpGained()) : "0") + " / "
                    + (sessionStarted ? formatNumber(script.getSmithingXpPerHour()) : "0") + "/h");
            add("Trips", String.valueOf(script.getCompletedTrips()));
        } catch (Exception ex) {
            Microbot.logStackTrace(getClass().getSimpleName(), ex);
        }

        return super.render(graphics);
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

    private String shorten(String value, int max) {
        if (value == null) {
            return "";
        }
        if (value.length() <= max) {
            return value;
        }
        return value.substring(0, Math.max(0, max - 3)) + "...";
    }

    private String signedGp(double gp) {
        return (gp > 0 ? "+" : "") + formatGp(gp);
    }

    private String formatGp(double gp) {
        double abs = Math.abs(gp);
        if (abs >= 1_000_000_000) return String.format("%.2fb", gp / 1_000_000_000.0);
        if (abs >= 1_000_000) return String.format("%.2fm", gp / 1_000_000.0);
        if (abs >= 1_000) return String.format("%.1fk", gp / 1_000.0);
        return String.format("%.0f", gp);
    }

    private String formatNumber(double value) {
        double abs = Math.abs(value);
        if (abs >= 1_000_000_000) return String.format("%.2fb", value / 1_000_000_000.0);
        if (abs >= 1_000_000) return String.format("%.2fm", value / 1_000_000.0);
        if (abs >= 1_000) return String.format("%.1fk", value / 1_000.0);
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
''', encoding='utf-8')

plugin = Path('kspsmartsmelter/KspSmartSmelterPlugin.java')
replace_once(plugin,
'''    public static final String VERSION = "0.0.10";
''',
'''    public static final String VERSION = "0.0.11";
''')
