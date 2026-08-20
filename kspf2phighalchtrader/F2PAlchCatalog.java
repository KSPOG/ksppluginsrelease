package net.runelite.client.plugins.microbot.kspf2phighalchtrader;

import net.runelite.client.plugins.microbot.Microbot;

import java.util.LinkedHashSet;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Curated High Alchemy candidate pool with account-aware members support.
 *
 * F2P IDs are deliberately numeric while members candidates are resolved by name at runtime,
 * so this external plugin does not depend on generated ItemID constant names changing between
 * RuneLite/Microbot revisions. Runtime item-definition
 * checks reject non-GE-tradeable or non-alchable entries while scanning. Members-only candidates
 * are only added when the caller has confirmed that members content is available for the account/world.
 * The selected candidate receives an additional OSRS Wiki mapping validation when available.
 */
final class F2PAlchCatalog {
    private F2PAlchCatalog() {
    }

    // Rune armour / weapons / tools.
    private static final int[] RUNE = {
            1079, 1093, 1113, 1127, 1147, 1163, 1185, 1201,
            1275, 1289, 1303, 1319, 1333, 1347, 1359, 1373, 1432
    };

    // Adamant armour / weapons / tools.
    private static final int[] ADAMANT = {
            1073, 1091, 1111, 1123, 1145, 1161, 1183, 1199,
            1271, 1287, 1301, 1317, 1331, 1345, 1357, 1371, 1430
    };

    // Mithril armour / weapons / tools.
    private static final int[] MITHRIL = {
            1071, 1085, 1109, 1121, 1143, 1159, 1181, 1197,
            1273, 1285, 1299, 1315, 1329, 1343, 1355, 1369, 1428
    };

    // Steel armour / weapons / tools.
    private static final int[] STEEL = {
            1069, 1083, 1105, 1119, 1141, 1157, 1177, 1193,
            1269, 1281, 1295, 1311, 1325, 1339, 1353, 1365, 1424
    };

    // Black armour / weapons / tools. Black pickaxe is a later-added F2P item.
    private static final int[] BLACK = {
            1077, 1089, 1107, 1125, 1151, 1165, 1179, 1195,
            12297, 1283, 1297, 1313, 1327, 1341, 1361, 1367, 1426
    };

    // Green dragonhide is F2P and frequently appears among profitable alch candidates.
    private static final int[] GREEN_DHIDE = {1065, 1099, 1135};

    /**
     * Members candidates based on the OSRS Wiki Alchemy market-watch pool plus common
     * high-volume members alchables. Names are resolved through Microbot at runtime so this
     * external plugin does not need generated ItemID constants for members-only items.
     * Profit, volume, GE-limit and live-price filters still decide whether an item is bought.
     */
    private static final String[] MEMBERS_ALCH = {
            // Current/high-ranking examples from the OSRS Wiki Alchemy market watch.
            "Dragon halberd",
            "Dragon med helm",
            "Dragon platelegs",
            "Dragon plateskirt",
            "Dragon dagger",
            "Dragon longsword",
            "Dragon scimitar",
            "Skull of vet'ion",
            "Gold locks",
            "Torn prayer scroll",
            "Amulet of the damned (full)",
            "Guthan's platebody 0",
            "Guthan's chainskirt 0",
            "Verac's brassard 0",
            "Torag's platebody 0",
            "Skeletal top",
            "Skeletal bottoms",
            "Ancient ceremonial mask",
            "Ancient ceremonial boots",
            "Mystic robe top",
            "Mystic gloves (dark)",
            "Mystic lava staff",
            "Splitbark legs",
            "Proselyte sallet",
            "Proselyte tasset",
            "Broken dragon hasta",
            "Leaf-bladed sword",
            "Crier hat",
            "Crier bell",
            "Flamtaer hammer",
            "Arceuus scarf",
            "Combat bracelet(4)",
            "Diamond bracelet",
            "Diamond necklace",
            "Atlatl dart",

            // High-volume/common members alchables that frequently appear in profitable-alchemy tables.
            "Air battlestaff",
            "Water battlestaff",
            "Earth battlestaff",
            "Fire battlestaff",
            "Lava battlestaff",
            "Mystic air staff",
            "Blue d'hide body",
            "Blue d'hide body (g)",
            "Blue d'hide vambraces",
            "Red d'hide body",
            "Red d'hide body (g)",
            "Red d'hide chaps",
            "Black d'hide body",
            "Magic longbow",
            "Magic shortbow",
            "Yew shortbow",
            "Yew comp bow",
            "Onyx bolts (e)",
            "Rune boots",
            "Adamant boots",
            "Rune halberd",
            "Rune spear",
            "Rune hasta",
            "Rune dagger(p+)",
            "Adamant hasta",
            "Mithril spear(p)",
            "Snakeskin chaps",
            "Frog-leather chaps",
            "Green d'hide shield",
            "Green dragon mask",
            "Blue dragon mask",
            "Red dragon mask",
            "Saradomin plateskirt",
            "Ancient plateskirt",
            "Armadyl platebody"
    };

    private static final Set<Integer> RESOLVED_MEMBERS_BUILT_INS = ConcurrentHashMap.newKeySet();

    static Set<Integer> buildCandidateSet(KspF2PHighAlchTraderConfig config, boolean allowMembers) {
        Set<Integer> result = new LinkedHashSet<>();

        // Built-in F2P families are always enabled. Profit/volume filters decide whether
        // an individual item is actually worth buying/alching at the current market.
        addAll(result, RUNE);
        addAll(result, ADAMANT);
        addAll(result, MITHRIL);
        addAll(result, STEEL);
        addAll(result, BLACK);
        addAll(result, GREEN_DHIDE);

        if (allowMembers) {
            addResolvedNames(result, MEMBERS_ALCH);
        }

        addParsedIds(result, config.customCandidateIds());

        Set<Integer> excluded = new LinkedHashSet<>();
        addParsedIds(excluded, config.excludedCandidateIds());
        result.removeAll(excluded);

        return result;
    }



    private static void addParsedIds(Set<Integer> target, String csv) {
        if (csv == null || csv.isBlank()) {
            return;
        }
        for (String token : csv.split(",")) {
            try {
                int id = Integer.parseInt(token.trim());
                if (id > 0) {
                    target.add(id);
                }
            } catch (NumberFormatException ignored) {
                // Ignore malformed custom tokens.
            }
        }
    }

    static boolean isBuiltIn(int itemId) {
        return contains(RUNE, itemId)
                || contains(ADAMANT, itemId)
                || contains(MITHRIL, itemId)
                || contains(STEEL, itemId)
                || contains(BLACK, itemId)
                || contains(GREEN_DHIDE, itemId)
                || RESOLVED_MEMBERS_BUILT_INS.contains(itemId);
    }

    private static void addResolvedNames(Set<Integer> target, String[] names) {
        if (Microbot.getRs2ItemManager() == null) {
            return;
        }
        for (String name : names) {
            int itemId = Microbot.getRs2ItemManager().getItemId(name);
            if (itemId > 0) {
                target.add(itemId);
                RESOLVED_MEMBERS_BUILT_INS.add(itemId);
            }
        }
    }

    private static boolean contains(int[] values, int itemId) {
        for (int value : values) {
            if (value == itemId) {
                return true;
            }
        }
        return false;
    }

    private static void addAll(Set<Integer> target, int[] ids) {
        for (int id : ids) {
            target.add(id);
        }
    }
}
