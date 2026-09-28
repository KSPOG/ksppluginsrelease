package net.runelite.client.plugins.microbot.kspf2paccountbuilder;

import net.runelite.api.Client;
import net.runelite.api.VarPlayer;
import net.runelite.client.plugins.microbot.Microbot;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

public final class F2pTradeRestrictionTracker
{
    public static final int REQUIRED_PLAYTIME_MINUTES = 20 * 60;
    public static final int REQUIRED_QUEST_POINTS = 10;
    public static final int REQUIRED_TOTAL_LEVEL = 100;
    private static final int PLAYTIME_VARC = 526;

    private static final Set<String> RESTRICTED_ITEMS = new HashSet<>(Arrays.asList(
            "oak logs",
            "willow logs",
            "yew logs",
            "raw shrimps",
            "raw shrimp",
            "shrimps",
            "shrimp",
            "raw anchovies",
            "anchovies",
            "raw lobster",
            "lobster",
            "clay",
            "soft clay",
            "copper ore",
            "tin ore",
            "iron ore",
            "silver ore",
            "gold ore",
            "coal",
            "mithril ore",
            "adamantite ore",
            "runite ore",
            "cowhide",
            "vial",
            "vial of water",
            "jug of water",
            "fishing bait",
            "feather",
            "eye of newt",
            "wine of zamorak",
            "air rune",
            "water rune",
            "earth rune",
            "fire rune",
            "mind rune",
            "chaos rune"
    ));

    private F2pTradeRestrictionTracker()
    {
    }

    public static Snapshot snapshot()
    {
        return Microbot.getClientThread().runOnClientThreadOptional(() -> {
            Client client = Microbot.getClient();
            int minutes = Math.max(0, client.getVarcIntValue(PLAYTIME_VARC));
            int questPoints = Math.max(0, client.getVarpValue(VarPlayer.QUEST_POINTS));
            int totalLevel = Math.max(0, client.getTotalLevel());
            return new Snapshot(minutes, questPoints, totalLevel);
        }).orElse(new Snapshot(0, 0, 0));
    }

    public static boolean isRestrictedItem(String itemName)
    {
        return itemName != null && RESTRICTED_ITEMS.contains(itemName.trim().toLowerCase(Locale.ROOT));
    }

    public static final class Snapshot
    {
        private final int playtimeMinutes;
        private final int questPoints;
        private final int totalLevel;

        Snapshot(int playtimeMinutes, int questPoints, int totalLevel)
        {
            this.playtimeMinutes = playtimeMinutes;
            this.questPoints = questPoints;
            this.totalLevel = totalLevel;
        }

        public int getPlaytimeMinutes()
        {
            return playtimeMinutes;
        }

        public int getQuestPoints()
        {
            return questPoints;
        }

        public int getTotalLevel()
        {
            return totalLevel;
        }

        public boolean hasPlaytime()
        {
            return playtimeMinutes >= REQUIRED_PLAYTIME_MINUTES;
        }

        public boolean hasQuestPoints()
        {
            return questPoints >= REQUIRED_QUEST_POINTS;
        }

        public boolean hasTotalLevel()
        {
            return totalLevel >= REQUIRED_TOTAL_LEVEL;
        }

        public boolean isTradeUnlocked()
        {
            return hasPlaytime() && hasQuestPoints() && hasTotalLevel();
        }

        public int playtimeRemaining()
        {
            return Math.max(0, REQUIRED_PLAYTIME_MINUTES - playtimeMinutes);
        }

        public int questPointsRemaining()
        {
            return Math.max(0, REQUIRED_QUEST_POINTS - questPoints);
        }

        public int totalLevelsRemaining()
        {
            return Math.max(0, REQUIRED_TOTAL_LEVEL - totalLevel);
        }

        public String formatPlaytime()
        {
            return formatMinutes(playtimeMinutes);
        }

        public String formatTradePlaytime()
        {
            return formatPlaytime() + " / 20:00";
        }

        private static String formatMinutes(int minutes)
        {
            int hours = minutes / 60;
            int mins = minutes % 60;
            return String.format("%d:%02d", hours, mins);
        }
    }
}
