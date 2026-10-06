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
public class KspExperienceLampScript extends Script
{
    private static final int LOOP_DELAY_MS = 50;
    private static final long INTERACTION_RECOVERY_MS = 1_500L;
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

    private long pendingLampOpenAtMs;
    private String selectedSkillName;

    public boolean run()
    {
        shutdown();
        pendingLampOpenAtMs = 0L;
        selectedSkillName = null;
        mainScheduledFuture = scheduledExecutorService.scheduleWithFixedDelay(() ->
        {
            try
            {
                if (!super.run() || !Microbot.isLoggedIn()) return;
                handleLamp();
            }
            catch (Exception ex)
            {
                log.trace("Exception in KSP experience lamp loop", ex);
            }
        }, 0L, LOOP_DELAY_MS, TimeUnit.MILLISECONDS);
        return true;
    }

    private void handleLamp()
    {
        if (!hasLamp())
        {
            pendingLampOpenAtMs = 0L;
            selectedSkillName = null;
            return;
        }

        Skill targetSkill = getLowestExperienceF2pSkill();
        if (targetSkill == null) return;

        if (Rs2Widget.hasWidget(CONFIRM_TEXT))
        {
            pendingLampOpenAtMs = 0L;
            String targetName = targetSkill.getName();

            // Selection state is visible, so use the widget itself as the readiness signal.
            if (Rs2Widget.hasWidget(targetName) && !targetName.equals(selectedSkillName))
            {
                if (Rs2Widget.clickWidget(targetName)) selectedSkillName = targetName;
                return;
            }

            // Once the selection has been accepted, confirm immediately on the next scheduler tick.
            if (Rs2Widget.clickWidget(CONFIRM_TEXT))
            {
                selectedSkillName = null;
            }
            return;
        }

        selectedSkillName = null;
        long now = System.currentTimeMillis();
        if (pendingLampOpenAtMs != 0L && now - pendingLampOpenAtMs < INTERACTION_RECOVERY_MS) return;

        boolean interacted = Rs2Inventory.interact(item -> item != null
                && item.getName() != null
                && item.getName().toLowerCase(Locale.ENGLISH).contains("lamp"), LAMP_ACTION_RUB);
        if (!interacted)
        {
            interacted = Rs2Inventory.interact(item -> item != null
                    && item.getName() != null
                    && item.getName().toLowerCase(Locale.ENGLISH).contains("lamp"), LAMP_ACTION_USE);
        }
        if (interacted) pendingLampOpenAtMs = now;
    }

    public boolean hasPendingLamp()
    {
        return hasLamp();
    }

    private boolean hasLamp()
    {
        return Rs2Inventory.get(item -> item != null
                && item.getName() != null
                && item.getName().toLowerCase(Locale.ENGLISH).contains("lamp")) != null;
    }

    private Skill getLowestExperienceF2pSkill()
    {
        return Microbot.getClientThread().invoke(() ->
        {
            Client client = Microbot.getClient();
            return F2P_SKILLS.stream()
                    .filter(this::isSkillUnlocked)
                    .min(Comparator.comparingInt(client::getSkillExperience))
                    .orElse(null);
        });
    }

    private boolean isSkillUnlocked(Skill skill)
    {
        if (skill == Skill.RUNECRAFT)
        {
            return Rs2Player.getQuestState(Quest.RUNE_MYSTERIES) == QuestState.FINISHED;
        }
        return true;
    }

    @Override
    public void shutdown()
    {
        pendingLampOpenAtMs = 0L;
        selectedSkillName = null;
        super.shutdown();
    }
}
