package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.awt.Color;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

@Getter
public enum ItemCategory
{
    TELEPORTS("Teleports", new Color(0, 150, 255), Arrays.asList(
        "teleport", "teletab", "glory(", "dueling(", "games necklace(",
        "ring of wealth(", "skills necklace(", "combat bracelet(",
        "passage(", "burning amulet(", "digsite pendant(", "ectophial",
        "sceptre", "talisman", "medallion", "lyre", "slayer ring(",
        "seed pod", "chronicle", "ring of shadows", "ring of returning",
        "camulet", "quetzal whistle", "skull sceptre", "basalt",
        "lunar seal", "kharedst", "memoirs"
    )),
    GEAR("Combat", new Color(220, 50, 50), Arrays.asList(
        "helm", "full helm", "med helm", "platebody", "platelegs", "plateskirt",
        "chainbody", "shield", "kiteshield", "sq shield", "sword", "longsword",
        "scimitar", "dagger", "mace", "warhammer", "battleaxe", "halberd",
        "spear", "hasta", "crossbow", "shortbow", "longbow", "dart", "knife",
        "javelin", "thrownaxe", "arrow", "bolt", "whip", "godsword", "defender",
        "boots", "gloves", "vambraces", "bracelet", "amulet", "necklace", "ring",
        "coif", "hood", "hat", "body", "chaps", "d'hide"
    )),
    POTIONS("Potions", new Color(0, 200, 0), Arrays.asList(
        "potion", "brew", "restore", "mix", "overload", "prayer renewal",
        "antidote", "antifire", "antipoison", "antivenom", "battlemage",
        "bastion", "saradomin brew", "super combat", "ranging potion",
        "stamina", "energy"
    )),
    FOOD("Food", new Color(160, 32, 240), Arrays.asList(
        "shark", "lobster", "swordfish", "tuna", "salmon", "trout", "monkfish",
        "manta ray", "dark crab", "anglerfish", "karambwan", "bass", "pike",
        "shrimps", "anchovies", "sardine", "herring", "mackerel", "cod", "cake",
        "bread", "meat", "chicken", "wine", "stew", "potato", "mushroom",
        "sweetcorn", "cooked"
    )),
    SKILLING("Skilling", new Color(255, 255, 0), Arrays.asList(
        "pickaxe", "hammer", "chisel", "saw", "tinderbox", "needle", "spade",
        "rake", "seed dibber", "secateurs", "watering can", "trowel",
        "pestle and mortar", "glassblowing pipe", "shears", "bucket", "fish barrel",
        "herb sack", "gem bag", "coal bag", "plank sack", "seed box", "log basket",
        "forestry kit", "tackle box", "graceful", "lumberjack", "angler", "farmer",
        "prospector", "pyromancer", "rogue", "axe", "harpoon", "net", "rod", "compost"
    )),
    RAW_MATERIALS("Materials", new Color(255, 0, 200), Arrays.asList(
        " ore", "bronze bar", "iron bar", "steel bar", "mithril bar", "adamantite bar",
        "runite bar", "gold bar", "silver bar", "logs", "hide", "leather", "essence",
        "seed", "grimy", "herb", "feather", "bone", "wool", "flax", "clay", "sand"
    )),
    HIGH_ALCH("High Alch", new Color(255, 255, 255), Collections.emptyList()),
    CURRENCY("Currency", new Color(255, 215, 80), Arrays.asList(
        "coins", "platinum token", "tokkul", "trading sticks", "numulite",
        "pieces of eight", "warrior guild token", "mermaid's tear", "hallowed mark",
        "molch pearl", "stardust"
    )),
    QUEST_MISC("Quest/Misc", new Color(180, 100, 255), Collections.emptyList());

    private final String displayName;
    private final Color color;
    private final List<String> keywords;

    ItemCategory(String displayName, Color color, List<String> keywords)
    {
        this.displayName = displayName;
        this.color = color;
        this.keywords = keywords;
    }

    @Override
    public String toString()
    {
        return displayName;
    }
}
