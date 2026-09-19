package net.runelite.client.plugins.microbot.ksprobesofruin;

import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;
import java.util.List;
import javax.inject.Inject;
import net.runelite.client.plugins.cluescrolls.clues.emote.Emote;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

public class KspRobesOfRuinOverlay extends OverlayPanel
{
    private static final int WIDTH = 350;
    private static final int MAX_LISTED = 6;
    private final KspRobesOfRuinPlugin plugin;

    @Inject
    KspRobesOfRuinOverlay(KspRobesOfRuinPlugin plugin)
    {
        super(plugin);
        this.plugin = plugin;
        setPosition(OverlayPosition.TOP_LEFT);
        setNaughty();
    }

    @Override
    public Dimension render(Graphics2D graphics)
    {
        panelComponent.getChildren().clear();
        KspRobesOfRuinPlugin.GuideStage stage = plugin.resolveStage();
        panelComponent.setPreferredSize(new Dimension(WIDTH, 0));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Robes of Ruin Helper v" + KspRobesOfRuinPlugin.VERSION)
                .color(stage == KspRobesOfRuinPlugin.GuideStage.COMPLETE ? Color.GREEN : Color.CYAN)
                .build());

        line("Step", plugin.getStepNumber() + "/5");
        line("Stage", stageName(stage));
        line("Action", shorten(plugin.getInstruction(), 58));

        switch (stage)
        {
            case PREPARE_ITEMS:
                renderItems();
                break;
            case DIG_LUMBRIDGE:
            case CONFIRM_DIG:
                line("Dig tile", format(KspRobesOfRuinPlugin.LUMBRIDGE_DIG_TILE));
                line("Required on person", plugin.getPresentRequiredCount() + "/28");
                line("In inventory", plugin.getInventoryRequiredCount() + "/28");
                List<String> equipped = plugin.getEquippedRequiredItems();
                line("Equipped required", Integer.toString(equipped.size()));
                if (!equipped.isEmpty())
                {
                    line("Unequip", shorten(String.join(", ", equipped), 58));
                }
                break;
            case TRAVEL_VARROCK:
                line("Destination", "Varrock west bank basement");
                line("Gate tile", format(KspRobesOfRuinPlugin.VARROCK_VAULT_GATE_TILE));
                break;
            case EMOTE_SEQUENCE:
                renderEmotes();
                break;
            case SEARCH_REWARDS:
                line("Rewards", plugin.getRewardCount() + "/" + KspRobesOfRuinPlugin.REWARD_ITEMS.size());
                line("Chests", "Search highlighted Chest objects");
                break;
            case COMPLETE:
                line("Rewards", "7/7 detected");
                break;
            default:
                break;
        }

        String feedback = plugin.getFeedback();
        if (feedback != null && !feedback.isBlank())
        {
            line("Status", shorten(feedback, 58));
        }

        return super.render(graphics);
    }

    private void renderItems()
    {
        List<String> missing = plugin.getMissingItems();
        List<String> extras = plugin.getExtraInventoryItems();

        line("Required on person", plugin.getPresentRequiredCount() + "/28");
        line("In inventory", plugin.getInventoryRequiredCount() + "/28");
        line("Equipped required", Integer.toString(plugin.getEquippedRequiredItems().size()));
        line("Extra item types", Integer.toString(extras.size()));

        if (!missing.isEmpty())
        {
            int shown = Math.min(MAX_LISTED, missing.size());
            for (int i = 0; i < shown; i++)
            {
                line(i == 0 ? "Missing" : "", missing.get(i));
            }
            if (missing.size() > shown)
            {
                line("", "+" + (missing.size() - shown) + " more missing");
            }
        }

        if (!extras.isEmpty())
        {
            int shown = Math.min(3, extras.size());
            for (int i = 0; i < shown; i++)
            {
                line(i == 0 ? "Remove" : "", extras.get(i));
            }
            if (extras.size() > shown)
            {
                line("", "+" + (extras.size() - shown) + " more extra");
            }
        }
    }

    private void renderEmotes()
    {
        int index = plugin.getEmoteIndex();
        Emote expected = plugin.getExpectedEmote();
        line("Progress", Math.min(index, KspRobesOfRuinPlugin.EMOTE_SEQUENCE.size()) + "/17");
        line("Next emote", expected == null ? "Waiting for vault" : expected.getName());

        for (int i = index; i < Math.min(KspRobesOfRuinPlugin.EMOTE_SEQUENCE.size(), index + 4); i++)
        {
            Emote emote = KspRobesOfRuinPlugin.EMOTE_SEQUENCE.get(i);
            line(i == index ? "Now" : "Then", (i + 1) + ". " + emote.getName());
        }
    }

    private void line(String left, String right)
    {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(left)
                .right(right == null ? "-" : right)
                .build());
    }

    private String stageName(KspRobesOfRuinPlugin.GuideStage stage)
    {
        switch (stage)
        {
            case PREPARE_ITEMS: return "Prepare 28 items";
            case DIG_LUMBRIDGE: return "Lumbridge Swamp dig";
            case CONFIRM_DIG: return "Confirm clue";
            case TRAVEL_VARROCK: return "Travel to Varrock vault";
            case EMOTE_SEQUENCE: return "17-emote sequence";
            case SEARCH_REWARDS: return "Search reward chests";
            case COMPLETE: return "Complete";
            default: return stage.name();
        }
    }

    private String format(net.runelite.api.coords.WorldPoint point)
    {
        return point.getX() + ", " + point.getY() + ", " + point.getPlane();
    }

    private String shorten(String value, int max)
    {
        if (value == null || value.isBlank())
        {
            return "-";
        }
        return value.length() <= max ? value : value.substring(0, max - 3) + "...";
    }
}
