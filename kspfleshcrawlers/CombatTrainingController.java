package net.runelite.client.plugins.microbot.kspfleshcrawlers;

import lombok.Getter;
import net.runelite.api.EnumID;
import net.runelite.api.ParamID;
import net.runelite.api.Skill;
import net.runelite.api.VarPlayer;
import net.runelite.api.Varbits;
import net.runelite.api.widgets.WidgetInfo;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.globval.enums.InterfaceTab;
import net.runelite.client.plugins.microbot.util.combat.Rs2Combat;
import net.runelite.client.plugins.microbot.util.tabs.Rs2Tab;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

final class CombatTrainingController {
    @Getter private Skill currentTrainingSkill;
    private long lastStyleCheck;

    boolean allEnabledGoalsReached(KspFleshCrawlerConfig config) {
        boolean anyEnabled = false;
        boolean allReached = true;

        if (config.trainAttack()) {
            anyEnabled = true;
            allReached &= level(Skill.ATTACK) >= config.attackTarget();
        }
        if (config.trainStrength()) {
            anyEnabled = true;
            allReached &= level(Skill.STRENGTH) >= config.strengthTarget();
        }
        if (config.trainDefence()) {
            anyEnabled = true;
            allReached &= level(Skill.DEFENCE) >= config.defenceTarget();
        }

        return anyEnabled && allReached;
    }

    void update(KspFleshCrawlerConfig config) {
        long now = System.currentTimeMillis();
        if (now - lastStyleCheck < 2_000L) {
            return;
        }
        lastStyleCheck = now;

        Skill desired = chooseSkill(config);
        if (desired == null) {
            currentTrainingSkill = null;
            return;
        }

        currentTrainingSkill = desired;
        WidgetInfo targetWidget = findStyleWidget(desired, config.avoidControlled());
        if (targetWidget == null) {
            Microbot.log("KSP Flesh Crawlers: no compatible attack style found for " + desired.getName());
            return;
        }

        int desiredIndex = widgetIndex(targetWidget);
        int currentIndex = Microbot.getVarbitPlayerValue(VarPlayer.ATTACK_STYLE);
        if (desiredIndex == currentIndex) {
            return;
        }

        if (Rs2Tab.getCurrentTab() != InterfaceTab.COMBAT) {
            Rs2Tab.switchToCombatOptionsTab();
        }
        Rs2Combat.setAttackStyle(targetWidget);
    }

    private Skill chooseSkill(KspFleshCrawlerConfig config) {
        List<Skill> pending = new ArrayList<>();
        if (config.trainAttack() && level(Skill.ATTACK) < config.attackTarget()) {
            pending.add(Skill.ATTACK);
        }
        if (config.trainStrength() && level(Skill.STRENGTH) < config.strengthTarget()) {
            pending.add(Skill.STRENGTH);
        }
        if (config.trainDefence() && level(Skill.DEFENCE) < config.defenceTarget()) {
            pending.add(Skill.DEFENCE);
        }

        if (pending.isEmpty()) {
            return null;
        }
        if (!config.balanceCombatLevels()) {
            return pending.get(0);
        }

        return pending.stream()
                .min(Comparator.comparingInt(this::level))
                .orElse(null);
    }

    private WidgetInfo findStyleWidget(Skill desired, boolean avoidControlled) {
        int weaponType = Microbot.getVarbitValue(Varbits.EQUIPPED_WEAPON_TYPE);
        int styleEnum = Microbot.getEnum(EnumID.WEAPON_STYLES).getIntValue(weaponType);
        int[] styleStructs = Microbot.getEnum(styleEnum).getIntVals();

        WidgetInfo controlledFallback = null;
        for (int i = 0; i < styleStructs.length && i < 4; i++) {
            String styleName = Microbot.getStructComposition(styleStructs[i])
                    .getStringValue(ParamID.ATTACK_STYLE_NAME);
            if (styleName == null) {
                continue;
            }

            if (matchesDedicatedStyle(styleName, desired)) {
                return widgetForIndex(i);
            }

            if ("Controlled".equalsIgnoreCase(styleName)
                    && (desired == Skill.ATTACK || desired == Skill.STRENGTH || desired == Skill.DEFENCE)) {
                controlledFallback = widgetForIndex(i);
            }
        }

        return avoidControlled ? null : controlledFallback;
    }

    private boolean matchesDedicatedStyle(String styleName, Skill desired) {
        if (desired == Skill.ATTACK) return "Accurate".equalsIgnoreCase(styleName);
        if (desired == Skill.STRENGTH) return "Aggressive".equalsIgnoreCase(styleName);
        return desired == Skill.DEFENCE && "Defensive".equalsIgnoreCase(styleName);
    }

    private WidgetInfo widgetForIndex(int index) {
        switch (index) {
            case 0:
                return WidgetInfo.COMBAT_STYLE_ONE;
            case 1:
                return WidgetInfo.COMBAT_STYLE_TWO;
            case 2:
                return WidgetInfo.COMBAT_STYLE_THREE;
            case 3:
                return WidgetInfo.COMBAT_STYLE_FOUR;
            default:
                return null;
        }
    }

    private int widgetIndex(WidgetInfo widgetInfo) {
        if (widgetInfo == WidgetInfo.COMBAT_STYLE_ONE) return 0;
        if (widgetInfo == WidgetInfo.COMBAT_STYLE_TWO) return 1;
        if (widgetInfo == WidgetInfo.COMBAT_STYLE_THREE) return 2;
        if (widgetInfo == WidgetInfo.COMBAT_STYLE_FOUR) return 3;
        return -1;
    }

    private int level(Skill skill) {
        return Microbot.getClient().getRealSkillLevel(skill);
    }
}
