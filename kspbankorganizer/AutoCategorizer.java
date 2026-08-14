package net.runelite.client.plugins.microbot.kspbankorganizer;

import java.io.BufferedReader;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;
import lombok.extern.slf4j.Slf4j;

/**
 * Automatic item categorizer adapted from the category/override model in
 * 1504681/BankOrganizerPlugin.
 */
@Slf4j
final class AutoCategorizer
{
    private static final String DEFAULT_OVERRIDES_RESOURCE =
        "/net/runelite/client/plugins/microbot/kspbankorganizer/reference_default_overrides.txt";

    private final Map<Integer, ItemCategory> idOverrides = new HashMap<>();
    private final Map<Integer, ItemCategory> manualOverrides = new HashMap<>();
    private final Map<ItemCategory, Pattern> regexPatterns = new EnumMap<>(ItemCategory.class);

    AutoCategorizer()
    {
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
        ItemCategory manual = manualOverrides.get(stack.itemId());
        if (manual != null)
        {
            return manual;
        }

        ItemCategory byId = idOverrides.get(stack.itemId());
        if (byId != null)
        {
            return byId;
        }

        String lowerName = stack.name() == null ? "" : stack.name().toLowerCase();
        for (ItemCategory category : ItemCategory.values())
        {
            if (category == ItemCategory.QUEST_MISC)
            {
                continue;
            }
            for (String keyword : category.getKeywords())
            {
                if (lowerName.contains(keyword.toLowerCase()))
                {
                    return category;
                }
            }
        }

        for (Map.Entry<ItemCategory, Pattern> entry : regexPatterns.entrySet())
        {
            if (entry.getValue().matcher(lowerName).find())
            {
                return entry.getKey();
            }
        }

        // Equipment action is a final fallback for obscure gear names. Custom regex
        // intentionally wins first, matching the reference plugin's override model.
        if (stack.equipable())
        {
            return ItemCategory.GEAR;
        }

        return ItemCategory.QUEST_MISC;
    }

    private void putRegex(ItemCategory category, String value)
    {
        if (value == null || value.trim().isEmpty())
        {
            return;
        }

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
                    if (text.length() > 0)
                    {
                        text.append(',');
                    }
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
        if (text == null || text.trim().isEmpty())
        {
            return;
        }

        String normalized = text.replace("\\:", ":");
        for (String token : normalized.split(","))
        {
            String value = token.trim();
            int separator = value.indexOf(':');
            if (separator <= 0 || separator >= value.length() - 1)
            {
                continue;
            }

            try
            {
                int itemId = Integer.parseInt(value.substring(0, separator).trim());
                ItemCategory category = ItemCategory.valueOf(value.substring(separator + 1).trim().toUpperCase());
                destination.put(itemId, category);
            }
            catch (IllegalArgumentException ignored)
            {
                // Ignore malformed profile entries rather than blocking the organizer.
            }
        }
    }

    private void installCoreIds()
    {
        // Runes.
        int[] runes = {554, 555, 556, 557, 558, 559, 560, 561, 562, 563, 564, 565, 566, 9075, 21880};
        putAll(ItemCategory.TELEPORTS, runes);

        // Teleport tablets.
        putAll(ItemCategory.TELEPORTS, 8007, 8008, 8009, 8010, 8011, 8012, 8013);

        // Rune pouches and common teleport utility items.
        putAll(ItemCategory.TELEPORTS,
            12791, 27281, 13660, 11872, 11873, 22114, 19564, 26990, 22400,
            24709, 4251, 22947, 30638, 29893, 6099, 23956, 6707, 9781, 9790, 9811);

        // Common teleport jewelry charge families.
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

        // Currency.
        putAll(ItemCategory.CURRENCY, 995, 13204, 6529, 6306, 26792, 8901, 21129, 22820, 24712);

        // Common food.
        putAll(ItemCategory.FOOD, 385, 379, 373, 7946, 391, 13441, 11936, 3144);

        // Common potions.
        putAll(ItemCategory.POTIONS,
            12695, 12697, 12699, 12701, 2434, 139, 141, 143,
            6685, 6687, 6689, 6691);

        // Common skilling tools/containers/outfit pieces.
        putAll(ItemCategory.SKILLING,
            1755, 2347, 590, 946, 1735, 952, 25582, 13226, 12020, 12019,
            12013, 22994, 28786, 28788, 11850, 11852, 11854, 11856, 11858, 11860);

        // Common materials.
        putAll(ItemCategory.RAW_MATERIALS, 1436, 7936, 314);

        // Common gear anchors.
        putAll(ItemCategory.GEAR, 7462, 6570, 21295, 10499, 22109, 4089, 4091, 4093, 4107, 4109);
    }

    private void putAll(ItemCategory category, int... itemIds)
    {
        for (int itemId : itemIds)
        {
            idOverrides.putIfAbsent(itemId, category);
        }
    }
}
