package net.runelite.client.plugins.microbot.kspjewelrycrafter;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
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

    private long trackedSessionStart;
    private int startingCraftingXp;
    private int startingCraftingLevel;
    private int currentCraftingXp;
    private int currentCraftingLevel;
    private boolean craftingBaselineReady;

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
        updateCraftingSession();

        panelComponent.getChildren().add(TitleComponent.builder()
            .text("KSP Jewelry Crafter v" + KspJewelryCrafterPlugin.VERSION)
            .color(TITLE_GOLD)
            .build());
        line(" ", "");
        line("Status", shorten(script.getStatus(), 40));
        line("State", prettyState(script.getState()));
        line("Runtime", script.getFormattedRuntime());

        line("Account", script.isMemberAccount() ? "Members" : "F2P");
        line("Crafting Lvl", currentCraftingLevel + " / +" + getCraftingLevelsGained() + " gained");
        line("XP gained", format(getCraftingXpGained()));
        line("XP / hr", format(getCraftingXpPerHour()));

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
        line("Total Profit", gp(script.getEstimatedProfit()));
        line("Est. profit / hr", gp(script.getEstimatedProfitPerHour()));

        line("GE offer", shorten(script.getPendingOfferSummary(), 40));
        line("GE retry", script.getGeRetry() + "/" + config.maxOfferRetries());
        line("Restock", script.getRestockProgress());

        return super.render(graphics);
    }

    private void updateCraftingSession()
    {
        long sessionStart = script.getSessionStartedAt();
        if (sessionStart <= 0L) return;

        int xp = Microbot.getClientThread().runOnClientThreadOptional(
            () -> Microbot.getClient().getSkillExperience(Skill.CRAFTING)).orElse(-1);
        int level = Microbot.getClientThread().runOnClientThreadOptional(
            () -> Microbot.getClient().getRealSkillLevel(Skill.CRAFTING)).orElse(-1);
        if (xp < 0 || level <= 0) return;

        if (!craftingBaselineReady || trackedSessionStart != sessionStart || xp < startingCraftingXp)
        {
            trackedSessionStart = sessionStart;
            startingCraftingXp = xp;
            startingCraftingLevel = level;
            craftingBaselineReady = true;
        }
        currentCraftingXp = xp;
        currentCraftingLevel = level;
    }

    private int getCraftingXpGained()
    {
        return craftingBaselineReady ? Math.max(0, currentCraftingXp - startingCraftingXp) : 0;
    }

    private int getCraftingLevelsGained()
    {
        return craftingBaselineReady ? Math.max(0, currentCraftingLevel - startingCraftingLevel) : 0;
    }

    private long getCraftingXpPerHour()
    {
        long elapsed = script.getRuntimeMillis();
        return elapsed <= 0L ? 0L : Math.round(getCraftingXpGained() * 3_600_000.0 / elapsed);
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
