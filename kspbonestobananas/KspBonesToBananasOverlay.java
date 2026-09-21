package net.runelite.client.plugins.microbot.kspbonestobananas;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class KspBonesToBananasOverlay extends OverlayPanel
{
    private final KspBonesToBananasPlugin plugin;
    private final KspBonesToBananasConfig config;

    @Inject
    KspBonesToBananasOverlay(KspBonesToBananasPlugin plugin, KspBonesToBananasConfig config)
    {
        super(plugin);
        this.plugin = plugin;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        KspBonesToBananasScript script = plugin.getScript();
        BonesToBananasQuote quote = script.getActiveQuote();
        panelComponent.getChildren().clear();
        panelComponent.setPreferredSize(new Dimension(300, 0));
        panelComponent.setBackgroundColor(new Color(0, 0, 0, 180));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Bones to Bananas v" + KspBonesToBananasPlugin.VERSION)
                .color(Color.ORANGE)
                .build());

        line("State", pretty(script.getState()));
        line("Action", script.getStatus());
        line("Bone", script.getActiveBone() == null ? "-" : script.getActiveBone().getItemName());
        line("Staff", script.getStaffName());
        line("Free runes", runeSavings(script));
        line("Current batch", format(script.getCurrentBatch()));

        separator();

        if (quote != null && quote.isValid())
        {
            line("Bone buy", format(quote.getBoneBuyPrice()) + " gp");
            line("Banana sell", format(quote.getBananaSellPrice()) + " gp");
            line("Rune cost / cast", format(quote.getRuneCostPerCast()) + " gp");
            line("Profit / cast", signed(quote.getProfitPerCast()));
            line("Profit / bone", String.format(Locale.ROOT, "%+.1f gp", quote.getProfitPerBone()));
            line("ROI", String.format(Locale.ROOT, "%.2f%%", quote.getRoiPercent()));
            line("Projected GP/h", signed(quote.getProjectedGpHour()));
            line("Max batch", format(quote.getBatchSize()));
        }
        else
        {
            line("Market quote", quote == null ? "Waiting" : quote.getReason());
        }
        line("Profit gate", "+" + format(config.minProfitPerCast()) + " gp / " + config.minRoiPercent() + "% ROI");

        separator();

        line("Casts", format(script.getCasts()));
        line("Casts / h", format(script.getCastsPerHour()));
        line("Bones converted", format(script.getBonesConverted()));
        line("Bones / h", format(script.getBonesPerHour()));
        line("Bananas made", format(script.getBananasProduced()));
        line("Banked bananas", format(script.getBankedBananas()));
        line("Est. session profit", signed(script.getEstimatedProfit()));
        line("Est. profit / h", signed(script.getEstimatedProfitPerHour()));
        line("Magic XP", format(script.getMagicXp()));
        line("Spendable cash", format(script.getSpendableCoins()) + " gp");

        separator();

        line("Anti-ban", script.getAntibanActivity());
        line("Short pauses", format(script.getAntibanShortPauses()));
        line("Long breaks", format(script.getAntibanLongBreaks()));
        line("Runtime", duration(script.getRuntimeMillis()));

        return super.render(graphics);
    }

    private static String runeSavings(KspBonesToBananasScript script)
    {
        if (script.hasFreeWater() && script.hasFreeEarth()) return "Water + Earth";
        if (script.hasFreeWater()) return "Water";
        if (script.hasFreeEarth()) return "Earth";
        return "None";
    }

    private void line(String left, String right)
    {
        panelComponent.getChildren().add(LineComponent.builder().left(left + ":").right(right == null ? "-" : right).build());
    }

    private void separator()
    {
        panelComponent.getChildren().add(LineComponent.builder().build());
    }

    private static String pretty(KspBonesToBananasState state)
    {
        if (state == null) return "-";
        String raw = state.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder();
        boolean upper = true;
        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);
            out.append(upper && Character.isLetter(c) ? Character.toUpperCase(c) : c);
            upper = c == ' ';
        }
        return out.toString();
    }

    private static String signed(long value)
    {
        return (value >= 0 ? "+" : "") + format(value) + " gp";
    }

    private static String format(long value)
    {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private static String duration(long millis)
    {
        long s = Math.max(0L, millis / 1000L);
        return String.format(Locale.ROOT, "%02d:%02d:%02d", s / 3600L, (s % 3600L) / 60L, s % 60L);
    }
}
