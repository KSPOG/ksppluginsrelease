package net.runelite.client.plugins.microbot.kspfleshcrawlers;

import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.ui.overlay.OverlayPanel;
import net.runelite.client.ui.overlay.OverlayPosition;
import net.runelite.client.ui.overlay.components.LineComponent;
import net.runelite.client.ui.overlay.components.TitleComponent;

import javax.inject.Inject;
import java.awt.Color;
import java.awt.Dimension;
import java.awt.Graphics2D;

public class KspFleshCrawlerOverlay extends OverlayPanel {
    private final KspFleshCrawlerPlugin plugin;
    private final KspFleshCrawlerScript script;
    private final KspFleshCrawlerConfig config;

    @Inject
    KspFleshCrawlerOverlay(
            KspFleshCrawlerPlugin plugin,
            KspFleshCrawlerScript script,
            KspFleshCrawlerConfig config
    ) {
        super(plugin);
        this.plugin = plugin;
        this.script = script;
        this.config = config;
        setPosition(OverlayPosition.TOP_LEFT);
    }

    @Override
    public Dimension render(Graphics2D graphics) {
        panelComponent.setPreferredSize(new Dimension(235, 0));

        panelComponent.getChildren().add(TitleComponent.builder()
                .text("KSP Flesh Crawlers")
                .color(Color.WHITE)
                .build());

        addLine("Runtime", plugin.getRuntimeText());
        addLine("State", prettyState(script.getState()));
        addLine("Action", script.getLastAction());
        addLine("Training", trainingText());
        addLine("Attack", levelText(Skill.ATTACK, config.attackTarget(), config.trainAttack()));
        addLine("Strength", levelText(Skill.STRENGTH, config.strengthTarget(), config.trainStrength()));
        addLine("Defence", levelText(Skill.DEFENCE, config.defenceTarget(), config.trainDefence()));
        addLine("HP", hpText());
        addLine("Heal at", config.useHealing() ? config.healAtHp() + " HP" : "Off");
        addLine("Food", String.valueOf(Rs2InventoryFacade.foodCount(config.foodName())));
        addLine("Kills", String.valueOf(script.getKills()));
        addLine("Kills/hr", String.valueOf(plugin.getKillsPerHour()));
        addLine("Looted", String.valueOf(script.getItemsLooted()));
        addLine("Bones buried", String.valueOf(script.getBonesBuried()));
        addLine("Food eaten", String.valueOf(script.getFoodEaten()));
        addLine("XP gained", String.format("%,d", plugin.getXpGained()));
        addLine("XP/hr", String.format("%,d", plugin.getXpPerHour()));
        addLine("Version", KspFleshCrawlerPlugin.VERSION);

        return super.render(graphics);
    }

    private void addLine(String left, String right) {
        panelComponent.getChildren().add(LineComponent.builder()
                .left(left + ":")
                .right(right == null ? "-" : right)
                .build());
    }

    private String trainingText() {
        Skill skill = script.getCurrentTrainingSkill();
        return skill == null ? "None" : skill.getName();
    }

    private String levelText(Skill skill, int target, boolean enabled) {
        if (!enabled || !Microbot.isLoggedIn()) {
            return "Off";
        }
        return Microbot.getClient().getRealSkillLevel(skill) + " / " + target;
    }

    private String hpText() {
        if (!Microbot.isLoggedIn()) {
            return "-";
        }
        int current = Microbot.getClient().getBoostedSkillLevel(Skill.HITPOINTS);
        int max = Microbot.getClient().getRealSkillLevel(Skill.HITPOINTS);
        return current + " / " + max;
    }

    private String prettyState(FleshCrawlerState state) {
        if (state == null) {
            return "-";
        }
        return state.name().replace('_', ' ');
    }

    /**
     * Keeps the overlay isolated from the rest of the script logic.
     */
    private static final class Rs2InventoryFacade {
        private static int foodCount(String foodName) {
            if (!Microbot.isLoggedIn() || foodName == null || foodName.trim().isEmpty()) {
                return 0;
            }
            return net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory.count(foodName, false);
        }
    }
}
