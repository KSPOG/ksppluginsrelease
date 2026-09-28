package net.runelite.client.plugins.microbot.kspaccountbuilder.ksputil.experiencelamps;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.TimeUnit;
import javax.inject.Singleton;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.Script;
import net.runelite.client.plugins.microbot.util.inventory.Rs2Inventory;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;
import net.runelite.client.plugins.microbot.util.widget.Rs2Widget;

@Singleton
@Slf4j
public class KspExperienceLampScript extends Script {
    private static final String CONFIRM_TEXT = "Confirm";
    private static final String LAMP_ACTION_RUB = "Rub";
    private static final String LAMP_ACTION_USE = "Use";
    private static final List<Skill> F2P_SKILLS = Arrays.asList(
            Skill.ATTACK,
            Skill.STRENGTH,
            Skill.DEFENCE,
            Skill.RANGED,
            Skill.PRAYER,
            Skill.MAGIC,
            Skill.RUNECRAFT,
            Skill.HITPOINTS,
            Skill.CRAFTING,
            Skill.MINING,
            Skill.SMITHING,
            Skill.FISHING,
            Skill.COOKING,
            Skill.FIREMAKING,
            Skill.WOODCUTTING
    );

    public boolean run() {
        shutdown();
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() -> {
            try {
                if (!super.run() || !Microbot.isLoggedIn()) {
                    return;
                }

                handleLamp();
            } catch (Exception ex) {
                log.trace("Exception in KSP experience lamp loop", ex);
            }
        }, 0L, 1200L, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleLamp() {
        if (!hasLamp()) {
            return;
        }

        Skill targetSkill = getLowestExperienceF2pSkill();
        if (targetSkill == null) {
            return;
        }

        if (Rs2Widget.hasWidget(CONFIRM_TEXT)) {
            if (Rs2Widget.hasWidget(targetSkill.getName())) {
                Rs2Widget.clickWidget(targetSkill.getName());
                sleep(200, 400);
            }

            Rs2Widget.clickWidget(CONFIRM_TEXT);
            sleep(600, 900);
            return;
        }

        if (!Rs2Inventory.interact(item -> item != null
                && item.getName() != null
                && item.getName().toLowerCase(Locale.ENGLISH).contains("lamp"), LAMP_ACTION_RUB)) {
            Rs2Inventory.interact(item -> item != null
                    && item.getName() != null
                    && item.getName().toLowerCase(Locale.ENGLISH).contains("lamp"), LAMP_ACTION_USE);
        }

        sleepUntil(() -> Rs2Widget.hasWidget(CONFIRM_TEXT), 2000);
    }

    public boolean hasPendingLamp() {
        return hasLamp();
    }

    private boolean hasLamp() {
        return Rs2Inventory.get(item -> item != null
                && item.getName() != null
                && item.getName().toLowerCase(Locale.ENGLISH).contains("lamp")) != null;
    }

    private Skill getLowestExperienceF2pSkill() {
        return Microbot.getClientThread().invoke(() -> {
            Client client = Microbot.getClient();
            return F2P_SKILLS.stream()
                    .filter(this::isSkillUnlocked)
                    .min(Comparator.comparingInt(client::getSkillExperience))
                    .orElse(null);
        });
    }

    private boolean isSkillUnlocked(Skill skill) {
        if (skill == Skill.RUNECRAFT) {
            return Rs2Player.getQuestState(Quest.RUNE_MYSTERIES) == QuestState.FINISHED;
        }

        return true;
    }
}
