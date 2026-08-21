package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ItemComposition;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStats;
import net.runelite.client.plugins.microbot.Microbot;

@Slf4j
final class AutoCategorizer
{
    private static final String DEFAULT_OVERRIDES_RESOURCE =
        "/net/runelite/client/plugins/microbot/kspbankorganizer/reference_default_overrides.txt";

    private final Map<Integer, ItemCategory> idOverrides = new HashMap<>();
    private final Map<Integer, ItemCategory> manualOverrides = new HashMap<>();
    private final Map<ItemCategory, Pattern> regexPatterns = new EnumMap<>(ItemCategory.class);
    private final ItemManager itemManager;

    @Inject
    AutoCategorizer(ItemManager itemManager)
    {
        this.itemManager = itemManager;
        loadBundledOverrides();
        installCoreIds();
    }

    void configure(KspBankOrganizerConfig config)
    {
        manualOverrides.clear();
        parseOverrides(config.manualOverrides(), manualOverrides);
        regexPatterns.clear();
        putRegex(ItemCategory.TELEPORTS, config.regexTeleports());
        putRegex(ItemCategory.GEAR, config.regexGear());
        putRegex(ItemCategory.POTIONS, config.regexPotions());
        putRegex(ItemCategory.FOOD, config.regexFood());
        putRegex(ItemCategory.SKILLING, config.regexSkilling());
        putRegex(ItemCategory.RAW_MATERIALS, config.regexMaterials());
        putRegex(ItemCategory.HIGH_ALCH, config.regexHighAlch());
        putRegex(ItemCategory.CURRENCY, config.regexCurrency());
        putRegex(ItemCategory.QUEST_MISC, config.regexQuestMisc());
    }

    ItemCategory categorize(BankSnapshot.BankStack stack)
    {
        ItemCategory category = manualOverrides.get(stack.itemId());
        if (category != null) return category;
        category = idOverrides.get(stack.itemId());
        if (category != null) return category;

        String rawName = normalize(stack.name());
        for (Map.Entry<ItemCategory, Pattern> entry : regexPatterns.entrySet())
        {
            if (entry.getValue().matcher(rawName).find()) return entry.getKey();
        }

        ItemComposition item = Microbot.getClientThread()
            .runOnClientThreadOptional(() -> itemManager.getItemComposition(stack.itemId()))
            .orElse(null);
        if (item == null) return ItemCategory.QUEST_MISC;

        String name = item.getMembersName();
        if (name == null || name.isBlank()) name = stack.name();
        String lower = normalize(name);
        String[] actions = item.getInventoryActions();

        if (hasAction(actions, "drink") || looksLikePotion(lower)) return ItemCategory.POTIONS;
        if (hasAction(actions, "eat") || looksLikeFood(lower)) return ItemCategory.FOOD;
        if (looksLikeTeleport(lower)) return ItemCategory.TELEPORTS;

        try
        {
            ItemStats stats = Microbot.getClientThread()
                .runOnClientThreadOptional(() -> itemManager.getItemStats(stack.itemId()))
                .orElse(null);
            if (stats != null && stats.isEquipable()) return ItemCategory.GEAR;
        }
        catch (RuntimeException ignored)
        {
        }

        if (looksLikeSkilling(lower, actions)) return ItemCategory.SKILLING;
        if (looksLikeCurrency(lower)) return ItemCategory.CURRENCY;
        if (looksLikeMaterial(lower)) return ItemCategory.RAW_MATERIALS;
        return ItemCategory.QUEST_MISC;
    }

    private static String normalize(String value)
    {
        return value == null ? "" : value.toLowerCase(Locale.ROOT)
            .replace('\u00a0', ' ')
            .replaceAll("\\s+", " ")
            .trim();
    }

    private static boolean hasAction(String[] actions, String wanted)
    {
        if (actions == null) return false;
        for (String action : actions)
        {
            if (action != null && action.equalsIgnoreCase(wanted)) return true;
        }
        return false;
    }

    private static boolean containsAny(String name, String... values)
    {
        for (String value : values) if (name.contains(value)) return true;
        return false;
    }

    private static boolean looksLikePotion(String name)
    {
        return containsAny(name,
            " potion", " potion(", " brew", " restore", " mix", " overload", " renewal",
            " antidote", " antifire", " antipoison", " antivenom", " battlemage", " bastion",
            " stamina", " energy potion", " divine ", " super combat", " ranging potion",
            " magic potion", " strength potion", " attack potion", " defence potion", "prayer potion",
            " saradomin brew", "zamorak brew", " combat potion");
    }

    private static boolean looksLikeFood(String name)
    {
        return containsAny(name,
            "shark", "lobster", "swordfish", "tuna", "salmon", "trout", "monkfish", "manta ray",
            "dark crab", "anglerfish", "karambwan", "bass", "pike", "shrimp", "anchovies", "sardine",
            "herring", "mackerel", "cod", "cake", "bread", "meat", "chicken", "stew", "potato",
            "mushroom", "sweetcorn", "cooked ", "pizza", "pie", "fruit", "berry", "papaya",
            "pineapple", "watermelon", "banana", "apple", "orange", "curry", "chocolate",
            "snape grass", "purple sweets", "summer pie", "wild pie");
    }

    private static boolean looksLikeTeleport(String name)
    {
        return containsAny(name,
            "teleport", "teleportation", "teletab", "tablet", "glory(", "dueling(", "games necklace",
            "ring of wealth", "skills necklace", "combat bracelet", "bracelet of combat", "ring of passage",
            "burning amulet", "digsite pendant", "slayer ring", "seed pod", "chronicle", "ring of shadows",
            "ring of returning", "camulet", "quetzal whistle", "skull sceptre", "lunar seal", "kharedst",
            "memoirs", "ectophial", "xeric's talisman", "mounted glory", "house teleport",
            "falador teleport", "varrock teleport", "lumbridge teleport", "camelot teleport",
            "ardougne teleport", "watchtower teleport", "trollheim teleport", "ape atoll teleport",
            "ancient teleport", "god wars teleport");
    }

    private static boolean looksLikeSkilling(String name, String[] actions)
    {
        return containsAny(name,
            "pickaxe", "hammer", "chisel", "saw", "tinderbox", "needle", "spade", "rake",
            "seed dibber", "secateurs", "watering can", "trowel", "pestle and mortar",
            "glassblowing pipe", "shears", "bucket", "fish barrel", "herb sack", "gem bag", "coal bag",
            "plank sack", "seed box", "log basket", "forestry kit", "tackle box", "graceful", "lumberjack",
            "angler outfit", "farmer outfit", "prospector", "pyromancer", "rogue outfit", "axe", "harpoon",
            "fishing rod", "fishing net", "compost", "mould", "crucible", "knife")
            || hasAction(actions, "Chop") || hasAction(actions, "Mine") || hasAction(actions, "Smelt")
            || hasAction(actions, "Craft") || hasAction(actions, "Fletch");
    }

    private static boolean looksLikeCurrency(String name)
    {
        return containsAny(name,
            "coins", "coin stack", "platinum token", "tokkul", "trading sticks", "numulite",
            "pieces of eight", "warrior guild token", "mermaid's tear", "hallowed mark", "molch pearl",
            "stardust", "deadman points", "league points", "tournament points", "commander's insignia");
    }

    private static boolean looksLikeMaterial(String name)
    {
        return containsAny(name,
            " ore", "ore ", " bar", "logs", " log", "hide", "leather", "essence", "seed", "grimy",
            " herb", "herb ", "feather", "bone", "wool", "flax", "clay", "sand", "granite", "limestone",
            "marble block", "steel nails", "iron nails", "bronze nails", "mithril nails", "adamantite nails",
            "runite nails", "oak plank", "teak plank", "mahogany plank", "gold leaf", "bolt tips",
            "arrowheads", "dart tip", "uncut ", "gem", "molten glass", "glass", "snape grass",
            "red spiders' eggs", "volcanic ash", "wine of zamorak", "bird nest", "bird's nest",
            "cactus spine", "giant seaweed", "potato cactus", "white berries", "red berries", "limpwurt root",
            "marrentill", "tarromin", "harralander", "ranarr", "toadflax", "irit", "avantoe", "kwuarm",
            "snapdragon", "cadantine", "lantadyme", "dwarf weed", "torstol", "grape", "hop", "sapling",
            "spirit seed", "tree seed", "fruit tree seed");
    }

    private void putRegex(ItemCategory category, String value)
    {
        if (value == null || value.trim().isEmpty()) return;
        try
        {
            regexPatterns.put(category, Pattern.compile(value, Pattern.CASE_INSENSITIVE));
        }
        catch (PatternSyntaxException ex)
        {
            log.warn("Ignoring invalid {} regex '{}': {}", category, value, ex.getMessage());
        }
    }

    private void loadBundledOverrides()
    {
        try (InputStream stream = AutoCategorizer.class.getResourceAsStream(DEFAULT_OVERRIDES_RESOURCE))
        {
            if (stream == null)
            {
                log.warn("Bundled Bank Organizer overrides were not found.");
                return;
            }

            StringBuilder text = new StringBuilder();
            try (BufferedReader reader = new BufferedReader(new InputStreamReader(stream, StandardCharsets.UTF_8)))
            {
                String line;
                while ((line = reader.readLine()) != null)
                {
                    if (text.length() > 0) text.append(',');
                    text.append(line);
                }
            }
            parseOverrides(text.toString(), idOverrides);
        }
        catch (Exception ex)
        {
            log.warn("Could not load bundled Bank Organizer overrides: {}", ex.getMessage());
        }
    }

    private static void parseOverrides(String text, Map<Integer, ItemCategory> destination)
    {
        if (text == null || text.trim().isEmpty()) return;

        for (String token : text.replace("\\:", ":").split(","))
        {
            String value = token.trim();
            int separator = value.indexOf(':');
            if (separator <= 0 || separator >= value.length() - 1) continue;

            try
            {
                int itemId = Integer.parseInt(value.substring(0, separator).trim());
                ItemCategory category = ItemCategory.valueOf(
                    value.substring(separator + 1).trim().toUpperCase(Locale.ROOT));
                destination.put(itemId, category);
            }
            catch (IllegalArgumentException ignored)
            {
            }
        }
    }

    private void installCoreIds()
    {
        putAll(ItemCategory.TELEPORTS, 554, 555, 556, 557, 558, 559, 560, 561, 562, 563, 564, 565, 566, 9075, 21880);
        putAll(ItemCategory.TELEPORTS, 8007, 8008, 8009, 8010, 8011, 8012, 8013);
        putAll(ItemCategory.TELEPORTS,
            12791, 27281, 13660, 11872, 11873, 22114, 19564, 26990, 22400,
            24709, 4251, 22947, 30638, 29893, 6099, 23956, 6707, 9781, 9790, 9811);
        putAll(ItemCategory.TELEPORTS,
            1704, 1706, 1708, 1710, 1712, 11976, 11978,
            2552, 2554, 2556, 2558, 2560, 2562, 2564, 2566,
            3853, 3855, 3857, 3859, 3861, 3863, 3865, 3867,
            11980, 11982, 11984, 11986, 11988,
            11105, 11107, 11109, 11111, 11113, 11115,
            11118, 11120, 11122, 11124, 11126, 11128,
            21146, 21149, 21151, 21153, 21155,
            21166, 21169, 21171, 21173, 21175,
            11190, 11191, 11192, 11193, 11194,
            13281, 13282, 13283, 13284, 13285, 13286, 13287, 13288, 21268);
        putAll(ItemCategory.CURRENCY, 995, 13204, 6529, 6306, 26792, 8901, 21129, 22820, 24712);
        putAll(ItemCategory.FOOD, 385, 379, 373, 7946, 391, 13441, 11936, 3144);
        putAll(ItemCategory.POTIONS, 12695, 12697, 12699, 12701, 2434, 139, 141, 143, 6685, 6687, 6689, 6691);
        putAll(ItemCategory.SKILLING,
            1755, 2347, 590, 946, 1735, 952, 25582, 13226, 12020, 12019,
            12013, 22994, 28786, 28788, 11850, 11852, 11854, 11856, 11858, 11860);
        putAll(ItemCategory.RAW_MATERIALS, 1436, 7936, 314);
        putAll(ItemCategory.GEAR, 7462, 6570, 21295, 10499, 22109, 4089, 4091, 4093, 4107, 4109);
    }

    private void putAll(ItemCategory category, int... itemIds)
    {
        for (int itemId : itemIds) idOverrides.putIfAbsent(itemId, category);
    }
}
