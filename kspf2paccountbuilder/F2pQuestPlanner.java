package net.runelite.client.plugins.microbot.kspf2paccountbuilder;

import net.runelite.api.Quest;
import net.runelite.api.QuestState;
import net.runelite.api.Skill;
import net.runelite.client.plugins.microbot.Microbot;
import net.runelite.client.plugins.microbot.util.player.Rs2Player;

import java.util.Arrays;
import java.util.List;

public final class F2pQuestPlanner
{
    private static final List<Quest> EARLY_QUESTS = Arrays.asList(
            Quest.COOKS_ASSISTANT,
            Quest.SHEEP_SHEARER,
            Quest.X_MARKS_THE_SPOT,
            Quest.ROMEO__JULIET,
            Quest.GOBLIN_DIPLOMACY,
            Quest.DORICS_QUEST,
            Quest.IMP_CATCHER,
            Quest.RUNE_MYSTERIES,
            Quest.THE_RESTLESS_GHOST,
            Quest.WITCHS_POTION,
            Quest.ERNEST_THE_CHICKEN,
            Quest.PIRATES_TREASURE,
            Quest.PRINCE_ALI_RESCUE,
            Quest.MISTHALIN_MYSTERY,
            Quest.DEMON_SLAYER,
            Quest.VAMPYRE_SLAYER,
            Quest.BELOW_ICE_MOUNTAIN,
            Quest.THE_CORSAIR_CURSE
    );

    private static final List<Quest> SOLO_F2P_QUESTS = Arrays.asList(
            Quest.COOKS_ASSISTANT,
            Quest.SHEEP_SHEARER,
            Quest.X_MARKS_THE_SPOT,
            Quest.ROMEO__JULIET,
            Quest.GOBLIN_DIPLOMACY,
            Quest.DORICS_QUEST,
            Quest.IMP_CATCHER,
            Quest.RUNE_MYSTERIES,
            Quest.THE_RESTLESS_GHOST,
            Quest.WITCHS_POTION,
            Quest.ERNEST_THE_CHICKEN,
            Quest.PIRATES_TREASURE,
            Quest.PRINCE_ALI_RESCUE,
            Quest.MISTHALIN_MYSTERY,
            Quest.DEMON_SLAYER,
            Quest.VAMPYRE_SLAYER,
            Quest.BELOW_ICE_MOUNTAIN,
            Quest.THE_CORSAIR_CURSE,
            Quest.THE_KNIGHTS_SWORD,
            Quest.BLACK_KNIGHTS_FORTRESS,
            Quest.DRAGON_SLAYER_I
    );

    private F2pQuestPlanner()
    {
    }

    public static Quest nextQuest(
            KspF2pAccountBuilderConfig.GoalProfile goal,
            F2pTradeRestrictionTracker.Snapshot snapshot,
            boolean skipPartnerQuests)
    {
        if (goal == KspF2pAccountBuilderConfig.GoalProfile.TRADE_UNLOCK
                && snapshot.getQuestPoints() >= F2pTradeRestrictionTracker.REQUIRED_QUEST_POINTS)
        {
            return null;
        }

        List<Quest> candidates = goal == KspF2pAccountBuilderConfig.GoalProfile.TRADE_UNLOCK
                ? EARLY_QUESTS
                : SOLO_F2P_QUESTS;

        for (Quest quest : candidates)
        {
            if (isFinished(quest) || !requirementsMet(quest, snapshot))
            {
                continue;
            }

            return quest;
        }

        if (!skipPartnerQuests && !isFinished(Quest.SHIELD_OF_ARRAV))
        {
            return Quest.SHIELD_OF_ARRAV;
        }

        return null;
    }

    public static boolean allConfiguredSoloQuestsFinished(boolean skipPartnerQuests)
    {
        for (Quest quest : SOLO_F2P_QUESTS)
        {
            if (!isFinished(quest))
            {
                return false;
            }
        }

        return skipPartnerQuests || isFinished(Quest.SHIELD_OF_ARRAV);
    }

    public static boolean isFinished(Quest quest)
    {
        if (quest == null || !Microbot.isLoggedIn())
        {
            return false;
        }

        return Microbot.getClientThread().runOnClientThreadOptional(
                () -> quest.getState(Microbot.getClient()) == QuestState.FINISHED
        ).orElse(false);
    }

    private static boolean requirementsMet(Quest quest, F2pTradeRestrictionTracker.Snapshot snapshot)
    {
        if (quest == Quest.THE_KNIGHTS_SWORD)
        {
            return Rs2Player.getRealSkillLevel(Skill.MINING) >= 10;
        }

        if (quest == Quest.BLACK_KNIGHTS_FORTRESS)
        {
            return snapshot.getQuestPoints() >= 12;
        }

        if (quest == Quest.DRAGON_SLAYER_I)
        {
            return snapshot.getQuestPoints() >= 32
                    && Rs2Player.getRealSkillLevel(Skill.ATTACK) >= 30
                    && Rs2Player.getRealSkillLevel(Skill.STRENGTH) >= 30
                    && Rs2Player.getRealSkillLevel(Skill.DEFENCE) >= 30;
        }

        return true;
    }
}
