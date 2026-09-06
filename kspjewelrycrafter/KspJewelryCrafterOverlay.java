package net.runelite.client.plugins.microbot.kspjewelrycrafter;

import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class KspJewelryCrafterOverlay extends OverlayPanel
{
    private static final int OVERLAY_WIDTH = 320;
    private static final Color TITLE_GOLD = new Color(255, 215, 0);
    private final KspJewelryCrafterScript script;
    private final KspJewelryCrafterConfig config;

    @Inject
    public KspJewelryCrafterOverlay(KspJewelryCrafterScript script, KspJewelryCrafterConfig config)
    {
        this.script = script;
        this.config = config;
        setResizable(false);
        setPreferredSize(new Dimension(OVERLAY_WIDTH, 0));
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        if (!config.showOverlay()) return null;
        setPreferredSize(new Dimension(OVERLAY_WIDTH, 0));
        panelComponent.setPreferredSize(new Dimension(OVERLAY_WIDTH, 0));

        panelComponent.getChildren().add(TitleComponent.builder()
            .text("KSP Jewelry Crafter v" + KspJewelryCrafterPlugin.VERSION)
            .color(TITLE_GOLD)
            .build());
        line(" ", "");
        line("Status", shorten(script.getStatus(), 40));
        line("Anti-ban", shorten(script.getAntibanStatus(), 40));
        line("State", prettyState(script.getState()));
        line("Runtime", script.getFormattedRuntime());

        line("Account", script.isMemberAccount() ? "Members" : "F2P");
        line("Crafting Lvl", script.getCraftingLevel() + " / +" + script.getCraftingLevelsGained() + " gained");
        line("XP", format(script.getCraftingXpGained()) + " / " + format(script.getCraftingXpPerHour()) + " p/h");

        JewelryRecipe recipe = script.getActiveRecipe();
        JewelryQuote quote = script.getActiveQuote();

        line("Recipe", recipe == null ? "None" : recipe.getOutputName());
        if (recipe != null)
        {
            line("Required lvl", String.valueOf(recipe.getCraftingLevel()));
            line("Access", recipe.isMembersOnly() ? "Members only" : "F2P");
            line("Inputs", recipe.usesGem()
                ? recipe.getBarName() + " + " + recipe.getGemName()
                : recipe.getBarName());
            line("Mould", recipe.getMouldName());
        }

        if (quote != null)
        {
            if (quote.isValid())
            {
                line("Input cost", gp(quote.getInputCost()));
                line("Sell value", gp(quote.getOutputSellPrice()));
                line("GE tax", gp(quote.getTax()));
                line("Net / item", gp(quote.getProfit()));
                line("ROI", String.format("%.2f%%", quote.getRoi()));
                line("Profit gate", quote.meets(config) ? "PASS" : "BLOCKED");
            }
            else
            {
                line("Market", "Quote unavailable");
                line("Reason", shorten(quote.getReason(), 40));
            }
        }

        if (script.getCurrentBatchTarget() > 0)
            line("Batch target", format(script.getCurrentBatchTarget()));
        if (script.getLastBatchMade() > 0)
            line("Last batch", format(script.getLastBatchMade()));

        line("Crafted", format(script.getCraftedCount()));
        line("Profit", gp(script.getEstimatedProfit()) + " / " + gp(script.getEstimatedProfitPerHour()) + " p/h");

        line("GE offer", shorten(script.getPendingOfferSummary(), 40));
        line("GE retry", script.getGeRetry() + "/" + config.maxOfferRetries());
        line("Restock", script.getRestockProgress());

        return super.render(graphics);
    }

    private void line(String left, String right)
    {
        panelComponent.getChildren().add(LineComponent.builder()
            .left(left)
            .right(right == null ? "" : right)
            .build());
    }

    private static String prettyState(JewelryCrafterState state)
    {
        if (state == null) return "Unknown";
        String value = state.name().toLowerCase().replace('_', ' ');
        return Character.toUpperCase(value.charAt(0)) + value.substring(1);
    }

    private static String shorten(String value, int max)
    {
        if (value == null || value.isBlank()) return "-";
        if (value.length() <= max) return value;
        return value.substring(0, Math.max(1, max - 3)) + "...";
    }

    private static String format(long value)
    {
        return String.format("%,d", value);
    }

    private static String gp(long value)
    {
        return String.format("%,d gp", value);
    }
}
