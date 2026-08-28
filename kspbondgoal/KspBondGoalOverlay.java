package net.runelite.client.plugins.microbot.kspbondgoal;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import java.util.Locale;
import javax.inject.Inject;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class KspBondGoalOverlay extends OverlayPanel
{
    private static final Color GOLD = new Color(255, 193, 7);
    private static final Color GOOD = new Color(92, 184, 92);
    private static final Color WARN = new Color(255, 180, 70);
    private static final Color INFO = new Color(120, 190, 255);
    private static final Color MUTED = new Color(180, 180, 180);

    private final KspBondGoalPlugin plugin;

    @Inject
    public KspBondGoalOverlay(KspBondGoalPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        panelComponent.setPreferredSize(new Dimension(275, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!Microbot.isLoggedIn())
        {
            return null;
        }

        BondGoalSnapshot state = plugin.getSnapshot();

        panelComponent.getChildren().add(
            TitleComponent.builder()
                .text("KSP Bond Goal v" + KspBondGoalPlugin.VERSION)
                .color(GOLD)
                .build()
        );

        if (state.getBondPrice() <= 0)
        {
            addLine("Bond price", "Loading...", WARN);
            addLine("Current coins", formatGp(state.getCurrentCoins()), Color.WHITE);
            addLine("Goal", "--", MUTED);
            addLine("Remaining", "--", MUTED);
            addLine("", "Waiting for RuneLite price cache", MUTED);
            return super.render(graphics);
        }

        addLine("Bond price", formatGp(state.getBondPrice()), GOLD);
        addLine("Extra target", formatGp(state.getExtraCoins()), Color.WHITE);
        addLine("Total goal", formatGp(state.getTargetCoins()), Color.WHITE);

        String coinText = formatGp(state.getCurrentCoins());
        boolean bankWarning = plugin.getConfig().includeBankCoins() && !state.isBankKnown();
        if (bankWarning)
        {
            coinText += " *";
        }
        addLine("Current coins", coinText, bankWarning ? WARN : Color.WHITE);

        Color remainingColor = state.getRemainingCoins() == 0 ? GOOD : WARN;
        addLine("Remaining", formatGp(state.getRemainingCoins()), remainingColor);
        addLine("Progress", String.format(Locale.US, "%.1f%%", state.getProgressPercent()),
            state.getProgressPercent() >= 100.0 ? GOOD : INFO);

        if (bankWarning)
        {
            panelComponent.getChildren().add(
                LineComponent.builder()
                    .left("* Open bank once to include banked GP")
                    .leftColor(WARN)
                    .build()
            );
        }

        panelComponent.getChildren().add(
            TitleComponent.builder()
                .text("Activity Advisor")
                .color(INFO)
                .build()
        );

        boolean tradeLimitsReady = plugin.areTradeLimitsReady();
        addLine("Trade limits", plugin.getTradeLimitStatus(), tradeLimitsReady ? GOOD : WARN);
        if (!tradeLimitsReady)
        {
            panelComponent.getChildren().add(
                LineComponent.builder()
                    .left("GE-input methods are hidden until 4h limits load.")
                    .leftColor(MUTED)
                    .build()
            );
        }

        if (state.getRemainingCoins() == 0)
        {
            addLine("Status", "Goal reached", GOOD);
            return super.render(graphics);
        }

        List<BondActivityAdvisor.ActivityEstimate> activities = state.getActivities();
        if (activities.isEmpty())
        {
            addLine("Best activity", "No positive estimate", WARN);
            panelComponent.getChildren().add(
                LineComponent.builder()
                    .left("Prices may still be loading, or current conversion margins are negative.")
                    .leftColor(MUTED)
                    .build()
            );
            return super.render(graphics);
        }

        BondActivityAdvisor.ActivityEstimate best = activities.get(0);
        addLine("Best activity", best.getName(), GOOD);
        addLine("Est. GP/hour", "~" + formatGp(best.getGpPerHour()), GOOD);
        addLine("Est. time", estimateTime(state.getRemainingCoins(), best.getGpPerHour()), INFO);

        panelComponent.getChildren().add(
            LineComponent.builder()
                .left(best.getDetail())
                .leftColor(MUTED)
                .build()
        );

        if (plugin.getConfig().showAlternatives())
        {
            int max = Math.min(3, activities.size());
            for (int i = 1; i < max; i++)
            {
                BondActivityAdvisor.ActivityEstimate alternative = activities.get(i);
                addLine(
                    "#" + (i + 1) + " " + alternative.getName(),
                    "~" + compactGp(alternative.getGpPerHour()) + "/h",
                    Color.WHITE
                );
            }
        }

        panelComponent.getChildren().add(
            LineComponent.builder()
                .left("Advisor uses published 4h buy limits; prior limit usage is not visible.")
                .leftColor(MUTED)
                .build()
        );

        return super.render(graphics);
    }

    private void addLine(String left, String right, Color rightColor)
    {
        panelComponent.getChildren().add(
            LineComponent.builder()
                .left(left)
                .right(right)
                .rightColor(rightColor)
                .build()
        );
    }

    private static String formatGp(long value)
    {
        return String.format(Locale.US, "%,d gp", Math.max(0, value));
    }

    private static String compactGp(long value)
    {
        if (value >= 1_000_000)
        {
            return String.format(Locale.US, "%.2fm", value / 1_000_000.0);
        }
        if (value >= 1_000)
        {
            return String.format(Locale.US, "%.1fk", value / 1_000.0);
        }
        return Long.toString(value);
    }

    private static String estimateTime(long remaining, long gpPerHour)
    {
        if (gpPerHour <= 0)
        {
            return "--";
        }

        double hours = remaining / (double) gpPerHour;
        if (hours < 1.0)
        {
            long minutes = Math.max(1, Math.round(hours * 60.0));
            return "~" + minutes + " min";
        }

        if (hours < 24.0)
        {
            return String.format(Locale.US, "~%.1f h", hours);
        }

        return String.format(Locale.US, "~%.1f days", hours / 24.0);
    }
}
