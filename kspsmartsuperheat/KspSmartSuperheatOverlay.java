package net.runelite.client.plugins.microbot.kspsmartsuperheat;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.Locale;

public class KspSmartSuperheatOverlay extends OverlayPanel
{
    private final KspSmartSuperheatPlugin plugin;
    private final KspSmartSuperheatConfig config;

    @Inject
    KspSmartSuperheatOverlay(KspSmartSuperheatPlugin plugin, KspSmartSuperheatConfig config)
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
        KspSmartSuperheatScript script = plugin.getScript();
        SuperheatQuote quote = script.getActiveQuote();

        panelComponent.setPreferredSize(new Dimension(292, 350));
        panelComponent.setBackgroundColor(new Color(0, 0, 0, 180));

        panelComponent.getChildren().add(
            TitleComponent.builder()
                .text("KSP Smart Superheat v" + KspSmartSuperheatPlugin.VERSION)
                .color(Color.ORANGE)
                .build()
        );

        addLine("State", pretty(script.getState()));
        addLine("Action", script.getStatus());
        addLine("Recipe", script.getActiveRecipe() == null ? "-" : script.getActiveRecipe().getOutputName());
        addLine("Fire cost", script.hasFreeFireRunes() ? "Staff / 0 runes" : "4 fire / cast");

        separator();

        if (quote != null && quote.isValid())
        {
            addLine("Profit / bar", signedGp(quote.getProfitPerBar()));
            addLine("ROI", String.format(Locale.ROOT, "%.2f%%", quote.getRoiPercent()));
            addLine("Projected GP/h", format(quote.getProjectedGpHour()));
            addLine("Input / bar", format(quote.getInputCostPerBar()) + " gp");
            addLine("Sell / bar", format(quote.getOutputSellPrice()) + " gp");
            addLine("GE tax / bar", format(quote.getTaxPerBar()) + " gp");
            addLine("Batch capacity", String.valueOf(quote.getBatchSize()));
        }
        else
        {
            addLine("Market quote", quote == null ? "Waiting" : quote.getReason());
        }

        addLine(
            "Profit gate",
            "+" + format(config.minProfitPerBar()) + " gp / " + config.minRoiPercent() + "% ROI"
        );

        separator();

        addLine("Bars made", format(script.getBarsMade()));
        addLine("Bars / h", format(script.getBarsPerHour()));
        addLine("Est. session profit", signedGp(script.getEstimatedProfit()));
        addLine("Est. profit / h", signedGp(script.getEstimatedProfitPerHour()));
        addLine("Saleable output stock", format(script.getUnsoldProduced()));
        addLine("Craftable in bank", format(script.getCraftableBarsInBank()));
        addLine("Current batch", format(script.getCurrentBatchTarget()));
        addLine("Spendable cash", format(script.getSpendableCoins()) + " gp");

        separator();

        addLine("Magic XP", format(script.getMagicXp()));
        addLine("Smithing XP", String.format(Locale.ROOT, "%,.1f", script.getSmithingXp()));
        addLine("Runtime", duration(script.getRuntimeMillis()));

        return super.render(graphics);
    }

    private void addLine(String left, String right)
    {
        panelComponent.getChildren().add(
            LineComponent.builder()
                .left(left + ":")
                .right(right == null ? "-" : right)
                .build()
        );
    }

    private void separator()
    {
        panelComponent.getChildren().add(LineComponent.builder().build());
    }

    private String pretty(SmartSuperheatState state)
    {
        if (state == null) return "-";
        String raw = state.name().replace('_', ' ').toLowerCase(Locale.ROOT);
        StringBuilder out = new StringBuilder(raw.length());
        boolean upper = true;
        for (int i = 0; i < raw.length(); i++)
        {
            char c = raw.charAt(i);
            out.append(upper && Character.isLetter(c) ? Character.toUpperCase(c) : c);
            upper = c == ' ';
        }
        return out.toString();
    }

    private String signedGp(long value)
    {
        return (value >= 0 ? "+" : "") + format(value) + " gp";
    }

    private String format(long value)
    {
        return String.format(Locale.ROOT, "%,d", value);
    }

    private String duration(long millis)
    {
        long totalSeconds = Math.max(0L, millis / 1000L);
        long hours = totalSeconds / 3600L;
        long minutes = (totalSeconds % 3600L) / 60L;
        long seconds = totalSeconds % 60L;
        return String.format(Locale.ROOT, "%02d:%02d:%02d", hours, minutes, seconds);
    }
}
