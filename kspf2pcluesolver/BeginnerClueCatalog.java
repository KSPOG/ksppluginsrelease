package net.runelite.client.plugins.microbot.kspf2pcluesolver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.InterfaceID;

/**
 * Coordinates and clue texts cross-checked against RuneLite's beginner
 * AnagramClue, CrypticClue, EmoteClue, BeginnerMapClue and HotColdLocation.
 */
final class BeginnerClueCatalog
{
    enum Kind { NPC, EMOTE, DIG, CHARLIE, HOT_COLD }
    static final class Step
    {
        final String key, label, npc, emote;
        final Kind kind;
        final WorldPoint destination;
        final String[] requiredItems;
        final int radius;
        Step(String key, String label, Kind kind, WorldPoint destination, int radius,
             String npc, String emote, String... requiredItems)
        {
            this.key = key; this.label = label; this.kind = kind;
            this.destination = destination; this.radius = radius;
            this.npc = npc; this.emote = emote; this.requiredItems = requiredItems;
        }
    }

    private static final List<Step> TEXT = new ArrayList<>();
    static final WorldPoint[] HOT_COLD_CENTERS = {
        at(3096,3379,0), at(3120,3282,0), at(3007,3475,0),
        at(3174,3336,0), at(3332,3313,0)
    };
    private static final WorldPoint CHARLIE = at(3209,3390,0);
    private static final Step HOT_COLD =
        new Step("hotcold", "Hot/cold device", Kind.HOT_COLD,
                 at(3211,3494,0), 3, "Reldo", null, "Spade");

    static
    {
        npc("AN EARL", "Ranael", 3315,3163,0);
        npc("CARPET AHOY", "Apothecary", 3195,3404,0);
        npc("CHAR GAME DISORDER", "Archmage Sedridor", 3102,9570,0);
        npc("I CORD", "Doric", 2951,3450,0);
        npc("IN BAR", "Brian", 3026,3246,0);
        npc("RAIN COVE", "Veronica", 3110,3330,0);
        npc("RUG DETER", "Gertrude", 3151,3412,0);
        npc("SIR SHARE RED", "Hairdresser", 2944,3381,0);
        npc("TAUNT ROOF", "Fortunato", 3080,3250,0);

        npc("Always walking around the castle grounds", "Hans", 3221,3218,0);
        npc("In the place Duke Horacio calls home", "Cook", 3208,3213,0);
        npc("In a village of barbarians", "Hunding", 3097,3432,2);
        npc("Talk to Charlie the Tramp in Varrock", "Charlie the Tramp", 3209,3390,0);
        npc("Near the open desert I reside", "Shantay", 3303,3123,0);

        emote("Blow a raspberry at Aris", "Raspberry at Aris", 3203,3424,0,3,"RASPBERRY","Gold ring","Gold necklace");
        emote("Bow to Brugsen Bursen", "Bow at GE", 3164,3477,0,4,"BOW");
        emote("Cheer at Iffie Nitter", "Cheer at Iffie", 3205,3416,0,3,"CHEER","Chef's hat","Red cape");
        emote("Clap at Bob's Brilliant Axes", "Clap at Bob's", 3231,3203,0,3,"CLAP","Bronze axe","Leather boots");
        emote("Panic at Al Kharid mine", "Panic at Al Kharid mine", 3303,3271,0,5,"PANIC");
        emote("Spin at Flynn's Mace Shop", "Spin at Flynn's", 2950,3387,0,3,"SPIN");

        charlie("cooked pike","Pike");
        charlie("cooked trout","Trout");
        charlie("leather body","Leather body");
        charlie("leather chaps","Leather chaps");
        charlie("raw herring","Raw herring");
        charlie("raw trout","Raw trout");
        charlie("piece of iron ore","Iron ore");
        charlie("iron dagger","Iron dagger");
    }

    private static WorldPoint at(int x,int y,int z) { return new WorldPoint(x,y,z); }
    private static void npc(String text,String who,int x,int y,int z)
    { TEXT.add(new Step(text.toLowerCase(Locale.ROOT),"Talk to "+who,Kind.NPC,at(x,y,z),4,who,null)); }
    private static void emote(String text,String label,int x,int y,int z,int radius,String action,String... items)
    { TEXT.add(new Step(text.toLowerCase(Locale.ROOT),label,Kind.EMOTE,at(x,y,z),radius,null,action,items)); }
    private static void charlie(String text,String item)
    { TEXT.add(new Step("charlie:"+text,"Give Charlie "+item,Kind.CHARLIE,CHARLIE,3,"Charlie the Tramp",null,item)); }

    static Step fromText(String clueText)
    {
        if (clueText == null || clueText.isBlank()) return null;
        String clue = clueText.replaceAll("<[^>]*>"," ").replaceAll("\\s+"," ").toLowerCase(Locale.ROOT).trim();
        if (clue.contains("buried beneath the ground") && clue.contains("reLdo".toLowerCase(Locale.ROOT))) return HOT_COLD;
        for (Step s : TEXT)
        {
            if (s.kind == Kind.CHARLIE)
            {
                if ((clue.contains("i need to give charlie") || clue.contains("i really need"))
                       && clue.contains(s.key.substring("charlie:".length()))) return s;
            }
            else if (clue.contains(s.key)) return s;
        }
        return null;
    }

    static Step fromMap(int groupId)
    {
        if (groupId == InterfaceID.TRAIL_MAP01) return dig("Champions' Guild tree",3167,3360);
        if (groupId == InterfaceID.TRAIL_MAP02) return dig("Varrock east mine",3290,3373);
        if (groupId == InterfaceID.TRAIL_MAP03) return dig("South of Draynor bank",3092,3226);
        if (groupId == InterfaceID.TRAIL_MAP06) return dig("Falador standing stones",3043,3399);
        if (groupId == InterfaceID.TRAIL_MAP11) return dig("Behind Wizards' Tower",3109,3153);
        return null;
    }

    private static Step dig(String label,int x,int y)
    { return new Step("map:"+x+":"+y,label,Kind.DIG,at(x,y,0),0,null,null,"Spade"); }

    static List<String> supportedPurchases()
    {
        return Arrays.asList("Spade","Bronze axe","Chef's hat","Gold necklace","Gold ring",
            "Leather boots","Red cape","Pike","Trout","Leather body","Leather chaps",
            "Raw herring","Raw trout","Iron ore","Iron dagger");
    }

    private BeginnerClueCatalog() { }
}