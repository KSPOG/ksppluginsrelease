package net.runelite.client.plugins.microbot.kspbossgear;

import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import java.io.IOException;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.inject.Inject;

/**
 * Reads live OSRS Wiki boss/raid pages and extracts every usable loadout it can
 * identify: recommended equipment tables, example inventory/setup matrices,
 * supplies/inventory sections, and prose recommendations as a final fallback.
 *
 * Equipment columns remain in Wiki order (most effective -> least effective),
 * allowing BossGearService to map them to Max/High/Mid/Budget without inventing
 * its own ranking. Inventory setups are exposed as normal selectable methods with
 * one item per row so the existing bank/inventory highlighting also works for them.
 */
final class WikiGearService
{
    private static final String API = "https://oldschool.runescape.wiki/api.php";
    private static final String WIKI = "https://oldschool.runescape.wiki/w/";
    private static final String USER_AGENT =
        "KSP-Boss-Gear/1.1 (RuneLite/Microbot plugin; https://github.com/KSPOG/ksppluginsrelease)";

    private static final Pattern RECOMMENDED = Pattern.compile("(?i)Recommended\\s+equipment\\s+for");
    private static final Pattern TABLE = Pattern.compile("(?is)<table\\b[^>]*>.*?</table>");
    private static final Pattern HEADING = Pattern.compile("(?is)<h([1-6])\\b[^>]*>(.*?)</h\\1>");
    private static final Pattern CAPTION = Pattern.compile("(?is)<caption\\b[^>]*>(.*?)</caption>");
    private static final Pattern PARAGRAPH = Pattern.compile("(?is)<p\\b[^>]*>(.*?)</p>");
    private static final Pattern ROW = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>");
    private static final Pattern CELL = Pattern.compile("(?is)<(td|th)\\b[^>]*>(.*?)</\\1>");
    private static final Pattern LINK_TITLE = Pattern.compile("(?is)<a\\b[^>]*?title\\s*=\\s*([\"'])(.*?)\\1[^>]*>");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern SPACE = Pattern.compile("\\s+");

    /** Search suggestions. Arbitrary typed Wiki boss/raid names still work. */
    static final List<String> KNOWN_BOSSES = Collections.unmodifiableList(Arrays.asList(
        "Abyssal Sire", "Akkha", "Alchemical Hydra", "Amoxliatl", "Araxxor", "Artio", "Ba-Ba",
        "Barrows", "Branda the Fire Queen", "Brutus (Demonic)", "Bryophyta", "Callisto", "Calvar'ion",
        "Cerberus", "Chaos Elemental", "Chaos Fanatic", "Chambers of Xeric",
        "Chambers of Xeric (Challenge Mode)", "Commander Zilyana", "Corporeal Beast",
        "Crazy Archaeologist", "Crystalline Hunllef", "Corrupted Hunllef", "Dagannoth Kings",
        "Dagannoth Prime", "Dagannoth Rex", "Dagannoth Supreme", "Dawn", "Deranged Archaeologist",
        "Doom of Mokhaiotl", "Duke Sucellus", "Dusk", "Eldric the Ice King", "Elidinis' Warden",
        "Fight Caves", "Fortis Colosseum", "General Graardor", "Gemstone Crab", "Giant Mole",
        "Great Olm", "Grotesque Guardians", "Hespori", "Inferno", "Kalphite Queen", "Kephri",
        "King Black Dragon", "Kraken", "Kree'arra", "K'ril Tsutsaroth", "Maggot King", "Mad Angel",
        "Moons of Peril", "Muttadile", "Nex", "Nylocas Vasilias", "Obor", "Pestilent Bloat",
        "Phantom Muspah", "Phosani's Nightmare", "Revenant maledictus", "Royal Titans", "Sarachnis",
        "Scorpia", "Scurrius", "Shellbane gryphon", "Skotizo", "Sol Heredit", "Sotetseg", "Spindel",
        "Tekton", "Tempoross", "The Corrupted Gauntlet", "The Gauntlet", "The Hueycoatl",
        "The Leviathan", "The Maiden of Sugadinti", "The Mimic", "The Nightmare", "The Whisperer",
        "Theatre of Blood", "Theatre of Blood (Entry Mode)", "Theatre of Blood (Hard Mode)",
        "Thermonuclear smoke devil", "Tombs of Amascut", "Tombs of Amascut (Entry Mode)",
        "Tombs of Amascut (Normal Mode)", "Tombs of Amascut (Expert Mode)", "Tumeken's Warden",
        "TzKal-Zuk", "TzTok-Jad", "Vanguard", "Vardorvis", "Vasa Nistirio", "Venenatis", "Verzik Vitur",
        "Vespula", "Vet'ion", "Vorkath", "Wintertodt", "Xarpus", "Yama", "Zalcano", "Zebak", "Zulrah"
    ));

    private final HttpClient httpClient;
    private final Map<String, WikiGearPage> cache = new ConcurrentHashMap<>();

    @Inject
    WikiGearService()
    {
        httpClient = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(8))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    }

    WikiGearPage load(String bossQuery, boolean forceRefresh) throws IOException, InterruptedException
    {
        String query = normalizeQuery(bossQuery);
        if (query.isEmpty()) throw new IOException("Enter a boss or raid name first.");

        String cacheKey = query.toLowerCase(Locale.ROOT);
        if (!forceRefresh)
        {
            WikiGearPage cached = cache.get(cacheKey);
            if (cached != null) return cached;
        }

        IOException lastError = null;
        for (String page : candidatePages(query))
        {
            try
            {
                ParsedPage parsed = fetch(page);
                List<WikiGearPage.GearMethod> methods = parseAllLoadouts(parsed.html);
                if (methods.isEmpty())
                {
                    lastError = new IOException("No usable gear or inventory recommendations found on " + parsed.title + ".");
                    continue;
                }

                WikiGearPage result = new WikiGearPage(
                    displayBossName(parsed.title, query),
                    parsed.title,
                    wikiUrl(parsed.title),
                    methods);
                cache.put(cacheKey, result);
                return result;
            }
            catch (IOException ex)
            {
                lastError = ex;
            }
        }

        throw lastError != null
            ? lastError
            : new IOException("The OSRS Wiki did not return a usable loadout page for " + query + ".");
    }

    private ParsedPage fetch(String page) throws IOException, InterruptedException
    {
        String encoded = URLEncoder.encode(page, StandardCharsets.UTF_8.name());
        URI uri = URI.create(API
            + "?action=parse&format=json&formatversion=2&redirects=1&prop=text&page=" + encoded);

        HttpRequest request = HttpRequest.newBuilder(uri)
            .timeout(Duration.ofSeconds(12))
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .GET()
            .build();

        HttpResponse<String> response = httpClient.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() < 200 || response.statusCode() >= 300)
        {
            throw new IOException("OSRS Wiki HTTP " + response.statusCode() + " for " + page + ".");
        }

        JsonObject root;
        try
        {
            // Keep compatibility with the older Gson bundled by current Microbot builds.
            root = new JsonParser().parse(response.body()).getAsJsonObject();
        }
        catch (RuntimeException ex)
        {
            throw new IOException("OSRS Wiki returned invalid JSON.", ex);
        }

        JsonElement error = root.get("error");
        if (error != null)
        {
            String message = error.isJsonObject() && error.getAsJsonObject().has("info")
                ? error.getAsJsonObject().get("info").getAsString()
                : "Wiki page could not be parsed.";
            throw new IOException(message);
        }

        JsonObject parse = root.getAsJsonObject("parse");
        if (parse == null || !parse.has("text"))
        {
            throw new IOException("OSRS Wiki returned no page content for " + page + ".");
        }

        String title = parse.has("title") ? parse.get("title").getAsString() : page;
        return new ParsedPage(title, parse.get("text").getAsString());
    }

    private static List<WikiGearPage.GearMethod> parseAllLoadouts(String html)
    {
        if (html == null || html.isEmpty()) return Collections.emptyList();

        List<WikiGearPage.GearMethod> result = new ArrayList<>();
        Set<String> signatures = new LinkedHashSet<>();

        List<WikiGearPage.GearMethod> equipment = parseEquipmentTables(html);
        for (WikiGearPage.GearMethod method : equipment) addUnique(result, signatures, method);

        for (WikiGearPage.GearMethod method : parseInventoryTables(html)) addUnique(result, signatures, method);
        for (WikiGearPage.GearMethod method : parseInventorySections(html)) addUnique(result, signatures, method);

        WikiGearPage.GearMethod supplyNotes = parseSupplyRecommendations(html);
        if (supplyNotes != null) addUnique(result, signatures, supplyNotes);

        // Some bosses (for example simple/F2P bosses) publish recommendations in prose
        // rather than the standard recommended-equipment template.
        if (equipment.isEmpty())
        {
            WikiGearPage.GearMethod prose = parseProseRecommendations(html);
            if (prose != null) addUnique(result, signatures, prose);
        }

        return result;
    }

    private static List<WikiGearPage.GearMethod> parseEquipmentTables(String html)
    {
        List<WikiGearPage.GearMethod> methods = new ArrayList<>();
        Matcher tables = TABLE.matcher(html);
        int sequence = 1;

        while (tables.find())
        {
            String tableHtml = tables.group();
            String tableText = visibleText(tableHtml).toLowerCase(Locale.ROOT);
            String context = precedingVisibleText(html, tables.start(), 2200);
            String lowerContext = context.toLowerCase(Locale.ROOT);

            int recommendedAt = lowerContext.lastIndexOf("recommended equipment for");
            boolean nearRecommended = recommendedAt >= 0
                && lowerContext.length() - recommendedAt <= 700;
            boolean slotTable = tableHtml.toLowerCase(Locale.ROOT).contains("equipment-slot")
                || (tableText.contains("slot") && tableText.contains("item"));

            if (!nearRecommended && !slotTable) continue;

            List<WikiGearPage.GearRow> rows = parseRows(tableHtml);
            if (rows.isEmpty()) continue;

            String methodName;
            if (nearRecommended)
            {
                methodName = parseMethodName(context.substring(recommendedAt));
            }
            else
            {
                String caption = tableCaption(tableHtml);
                String heading = nearestHeading(html, tables.start());
                methodName = !caption.isEmpty() ? caption : heading;
                if (methodName.isEmpty() || methodName.equalsIgnoreCase("Equipment"))
                    methodName = sequence == 1 ? "Equipment" : "Equipment " + sequence;
            }

            methods.add(new WikiGearPage.GearMethod(cleanMethodName(methodName), rows));
            sequence++;
        }
        return methods;
    }

    /** Parses Wiki "Example setups" and other inventory/loadout matrices by column. */
    private static List<WikiGearPage.GearMethod> parseInventoryTables(String html)
    {
        List<WikiGearPage.GearMethod> methods = new ArrayList<>();
        Matcher tables = TABLE.matcher(html);

        while (tables.find())
        {
            String tableHtml = tables.group();
            String heading = nearestHeading(html, tables.start());
            String caption = tableCaption(tableHtml);
            String context = precedingVisibleText(html, tables.start(), 1200);
            String lowerContext = context.toLowerCase(Locale.ROOT);
            String label = (heading + " " + caption).toLowerCase(Locale.ROOT);

            int exampleAt = lowerContext.lastIndexOf("example setup");
            boolean nearExampleSetup = exampleAt >= 0 && lowerContext.length() - exampleAt <= 650;
            boolean explicitInventory = containsAny(label,
                "inventory", "inventories", "supplies", "loadout", "setup", "set-up");

            if (!nearExampleSetup && !explicitInventory) continue;
            if (looksLikeRecommendedEquipmentTable(tableHtml) && !nearExampleSetup) continue;

            String baseName = nearExampleSetup ? "Example setup" : (!caption.isEmpty() ? caption : heading);
            methods.addAll(parseSetupMatrix(tableHtml, baseName));
        }
        return methods;
    }

    /** Parses linked items from explicit Inventory/Supplies/Loadout sections. */
    private static List<WikiGearPage.GearMethod> parseInventorySections(String html)
    {
        List<WikiGearPage.GearMethod> methods = new ArrayList<>();
        Matcher headings = HEADING.matcher(html);
        List<HeadingBlock> blocks = new ArrayList<>();
        while (headings.find())
        {
            blocks.add(new HeadingBlock(headings.start(), headings.end(), visibleText(headings.group(2))));
        }

        for (int i = 0; i < blocks.size(); i++)
        {
            HeadingBlock block = blocks.get(i);
            String lower = block.name.toLowerCase(Locale.ROOT);
            if (!containsAny(lower, "inventory", "inventories", "supplies", "loadout", "setup", "set-up"))
                continue;

            int end = i + 1 < blocks.size() ? blocks.get(i + 1).start : html.length();
            if (end <= block.end) continue;
            String sectionHtml = html.substring(block.end, Math.min(end, block.end + 20000));
            List<String> items = extractLinkTitles(sectionHtml);
            if (items.size() < 2) continue;

            methods.add(itemsAsMethod("Inventory • " + cleanMethodName(block.name), items));
        }
        return methods;
    }

    /** Captures Wiki inventory advice embedded in prose/notes when no setup matrix is used. */
    private static WikiGearPage.GearMethod parseSupplyRecommendations(String html)
    {
        Set<String> items = new LinkedHashSet<>();
        Pattern blocks = Pattern.compile("(?is)<(?:p|li)\\b[^>]*>(.*?)</(?:p|li)>");
        Matcher matcher = blocks.matcher(html);
        while (matcher.find())
        {
            String blockHtml = matcher.group(1);
            String text = visibleText(blockHtml).toLowerCase(Locale.ROOT);
            if (!containsAny(text,
                "inventory", "supplies", "should bring", "bring ", "bring in", "pre-pot", "prepot",
                "rune pouch", "food", "potion", "potions", "ammo", "ammunition", "runes"))
            {
                continue;
            }
            items.addAll(extractLinkTitles(blockHtml));
        }
        return items.size() < 2
            ? null
            : itemsAsMethod("Inventory • Wiki recommendations", new ArrayList<>(items));
    }

    /** Last-resort extractor for prose-based recommendation pages. */
    private static WikiGearPage.GearMethod parseProseRecommendations(String html)
    {
        Set<String> items = new LinkedHashSet<>();
        Matcher paragraphs = PARAGRAPH.matcher(html);
        while (paragraphs.find())
        {
            String paragraphHtml = paragraphs.group(1);
            String text = visibleText(paragraphHtml).toLowerCase(Locale.ROOT);
            if (!containsAny(text,
                "recommended", "ideal setup", "ideal equipment", "best-in-slot", "best in slot",
                "should bring", "should have", "is recommended", "are recommended"))
            {
                continue;
            }
            items.addAll(extractLinkTitles(paragraphHtml));
        }

        return items.isEmpty() ? null : itemsAsMethod("Wiki recommendations", new ArrayList<>(items));
    }

    private static List<WikiGearPage.GearMethod> parseSetupMatrix(String tableHtml, String baseName)
    {
        List<List<String>> rows = new ArrayList<>();
        Matcher rowMatcher = ROW.matcher(tableHtml);
        while (rowMatcher.find())
        {
            List<String> cells = new ArrayList<>();
            Matcher cellMatcher = CELL.matcher(rowMatcher.group(1));
            while (cellMatcher.find()) cells.add(cellMatcher.group(2));
            if (!cells.isEmpty()) rows.add(cells);
        }
        if (rows.isEmpty()) return Collections.emptyList();

        int maxColumns = 0;
        for (List<String> row : rows) maxColumns = Math.max(maxColumns, row.size());
        if (maxColumns == 0) return Collections.emptyList();

        boolean firstRowIsHeader = true;
        for (String cell : rows.get(0))
        {
            if (!extractLinkTitles(cell).isEmpty())
            {
                firstRowIsHeader = false;
                break;
            }
        }

        List<WikiGearPage.GearMethod> methods = new ArrayList<>();
        int dataStart = firstRowIsHeader ? 1 : 0;
        for (int column = 0; column < maxColumns; column++)
        {
            Set<String> items = new LinkedHashSet<>();
            for (int row = dataStart; row < rows.size(); row++)
            {
                if (column < rows.get(row).size()) items.addAll(extractLinkTitles(rows.get(row).get(column)));
            }
            if (items.size() < 2) continue;

            String columnName = "";
            if (firstRowIsHeader && column < rows.get(0).size()) columnName = visibleText(rows.get(0).get(column));
            String name = columnName.isEmpty() ? baseName : columnName;
            methods.add(itemsAsMethod("Inventory • " + cleanMethodName(name), new ArrayList<>(items)));
        }

        if (!methods.isEmpty()) return methods;

        List<String> all = extractLinkTitles(tableHtml);
        if (all.size() >= 2)
            methods.add(itemsAsMethod("Inventory • " + cleanMethodName(baseName), all));
        return methods;
    }

    private static WikiGearPage.GearMethod itemsAsMethod(String name, List<String> items)
    {
        List<WikiGearPage.GearRow> rows = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        for (String item : items)
        {
            String key = item.toLowerCase(Locale.ROOT);
            if (!seen.add(key)) continue;
            List<List<String>> columns = new ArrayList<>();
            columns.add(Collections.singletonList(item));
            rows.add(new WikiGearPage.GearRow(GearSlot.UNKNOWN, columns));
        }
        return new WikiGearPage.GearMethod(cleanMethodName(name), rows);
    }

    private static List<WikiGearPage.GearRow> parseRows(String tableHtml)
    {
        List<WikiGearPage.GearRow> result = new ArrayList<>();
        Matcher rows = ROW.matcher(tableHtml);
        int fallbackSlot = 0;

        while (rows.find())
        {
            String rowHtml = rows.group(1);
            List<String> cells = new ArrayList<>();
            Matcher cellMatcher = CELL.matcher(rowHtml);
            while (cellMatcher.find()) cells.add(cellMatcher.group(2));
            if (cells.size() < 2) continue;

            String firstCellText = visibleText(cells.get(0)).toLowerCase(Locale.ROOT);
            String rowText = visibleText(rowHtml).toLowerCase(Locale.ROOT);
            if (firstCellText.equals("slot") || firstCellText.equals("equipment slot")
                || (firstCellText.contains("slot") && rowText.contains("most effective")))
            {
                continue;
            }

            GearSlot slot = GearSlot.detect(cells.get(0), fallbackSlot++);
            List<List<String>> columns = new ArrayList<>();
            boolean hasAnyCandidate = false;

            for (int i = 1; i < cells.size(); i++)
            {
                List<String> candidates = extractLinkTitles(cells.get(i));
                columns.add(candidates);
                hasAnyCandidate |= !candidates.isEmpty();
            }

            if (hasAnyCandidate) result.add(new WikiGearPage.GearRow(slot, columns));
        }
        return result;
    }

    private static boolean looksLikeRecommendedEquipmentTable(String tableHtml)
    {
        String text = visibleText(tableHtml).toLowerCase(Locale.ROOT);
        return tableHtml.toLowerCase(Locale.ROOT).contains("equipment-slot")
            || (text.contains("slot") && text.contains("most effective"));
    }

    private static List<String> extractLinkTitles(String html)
    {
        Set<String> result = new LinkedHashSet<>();
        Matcher links = LINK_TITLE.matcher(html == null ? "" : html);
        while (links.find())
        {
            String title = decodeHtml(links.group(2)).trim();
            if (isPlausibleItemTitle(title)) result.add(title);
        }
        return new ArrayList<>(result);
    }

    private static boolean isPlausibleItemTitle(String title)
    {
        if (title == null || title.isEmpty() || title.length() > 90) return false;
        String lower = title.toLowerCase(Locale.ROOT);
        if (title.contains(":")) return false;
        if (lower.startsWith("edit") || lower.startsWith("equipment slot")) return false;
        if (lower.equals("n/a") || lower.equals("none") || lower.equals("not applicable")) return false;
        if (lower.startsWith("file:") || lower.startsWith("category:") || lower.startsWith("template:")) return false;
        return true;
    }

    private static String parseMethodName(String headingHtml)
    {
        String plain = visibleText(headingHtml);
        Matcher m = Pattern.compile("(?i)Recommended\\s+equipment\\s+for\\s*(.*)").matcher(plain);
        String method = m.find() ? m.group(1).trim() : "Recommended";
        int stop = firstIndex(method, ".", "[", "Edit", "Slot", "Item (most effective");
        if (stop > 0) method = method.substring(0, stop).trim();
        if (method.length() > 70) method = method.substring(0, 70).trim();
        return method.isEmpty() ? "Recommended" : method;
    }

    private static String nearestHeading(String html, int before)
    {
        Matcher matcher = HEADING.matcher(html);
        String result = "";
        while (matcher.find())
        {
            if (matcher.start() >= before) break;
            result = visibleText(matcher.group(2));
        }
        return cleanMethodName(result);
    }

    private static String tableCaption(String tableHtml)
    {
        Matcher matcher = CAPTION.matcher(tableHtml);
        return matcher.find() ? cleanMethodName(visibleText(matcher.group(1))) : "";
    }

    private static String precedingVisibleText(String html, int before, int maxChars)
    {
        int start = Math.max(0, before - maxChars);
        return visibleText(html.substring(start, before));
    }

    private static void addUnique(
        List<WikiGearPage.GearMethod> target,
        Set<String> signatures,
        WikiGearPage.GearMethod method)
    {
        if (method == null || method.getRows().isEmpty()) return;
        StringBuilder signature = new StringBuilder(method.getName().toLowerCase(Locale.ROOT)).append('|');
        for (WikiGearPage.GearRow row : method.getRows())
        {
            for (List<String> column : row.getColumns())
                for (String item : column) signature.append(item.toLowerCase(Locale.ROOT)).append(';');
        }
        if (signatures.add(signature.toString())) target.add(method);
    }

    private static String cleanMethodName(String value)
    {
        String name = value == null ? "" : SPACE.matcher(value.trim()).replaceAll(" ");
        name = name.replace("[edit | edit source]", "").trim();
        if (name.length() > 72) name = name.substring(0, 72).trim();
        return name.isEmpty() ? "Recommended" : name;
    }

    private static int firstIndex(String text, String... needles)
    {
        int best = -1;
        for (String needle : needles)
        {
            int i = text.indexOf(needle);
            if (i >= 0 && (best < 0 || i < best)) best = i;
        }
        return best;
    }

    private static boolean containsAny(String haystack, String... needles)
    {
        if (haystack == null) return false;
        for (String needle : needles) if (haystack.contains(needle)) return true;
        return false;
    }

    private static String visibleText(String html)
    {
        if (html == null) return "";
        String withSpaces = html
            .replaceAll("(?is)<br\\s*/?>", " ")
            .replaceAll("(?is)</(?:p|div|h[1-6]|li|tr|td|th)>", " ");
        return SPACE.matcher(decodeHtml(TAG.matcher(withSpaces).replaceAll(" "))).replaceAll(" ").trim();
    }

    private static String decodeHtml(String text)
    {
        if (text == null) return "";
        return text
            .replace("&amp;", "&")
            .replace("&quot;", "\"")
            .replace("&#39;", "'")
            .replace("&#039;", "'")
            .replace("&apos;", "'")
            .replace("&nbsp;", " ")
            .replace("&lt;", "<")
            .replace("&gt;", ">");
    }

    private static List<String> candidatePages(String query)
    {
        String lower = query.toLowerCase(Locale.ROOT);
        List<String> pages = new ArrayList<>();

        if (lower.contains("chambers of xeric") && (lower.contains("challenge") || lower.contains(" cm")))
        {
            pages.add("Chambers of Xeric/Challenge Mode/Strategies");
            pages.add("Chambers of Xeric/Challenge Mode");
            return pages;
        }
        if (lower.contains("theatre of blood") && (lower.contains("hard") || lower.contains("hmt")))
        {
            pages.add("Theatre of Blood/Hard Mode");
            pages.add("Theatre of Blood/Strategies");
            return pages;
        }
        if (lower.contains("theatre of blood"))
        {
            pages.add("Theatre of Blood/Strategies");
            pages.add("Theatre of Blood");
            return pages;
        }
        if (lower.contains("tombs of amascut"))
        {
            pages.add("Tombs of Amascut/Strategies");
            pages.add("Tombs of Amascut");
            return pages;
        }

        if (lower.endsWith("/strategies"))
        {
            pages.add(query);
        }
        else
        {
            pages.add(query + "/Strategies");
            pages.add(query);
        }
        return pages;
    }

    private static String displayBossName(String pageTitle, String originalQuery)
    {
        if (pageTitle == null || pageTitle.isEmpty()) return originalQuery;
        String name = pageTitle;
        if (name.endsWith("/Strategies")) name = name.substring(0, name.length() - "/Strategies".length());
        return name;
    }

    private static String normalizeQuery(String query)
    {
        return query == null ? "" : SPACE.matcher(query.trim()).replaceAll(" ");
    }

    private static String wikiUrl(String pageName)
    {
        String path = pageName.replace(' ', '_').replace("'", "%27");
        return WIKI + path;
    }

    private static final class HeadingBlock
    {
        private final int start;
        private final int end;
        private final String name;

        private HeadingBlock(int start, int end, String name)
        {
            this.start = start;
            this.end = end;
            this.name = name;
        }
    }

    private static final class ParsedPage
    {
        private final String title;
        private final String html;

        private ParsedPage(String title, String html)
        {
            this.title = title;
            this.html = html;
        }
    }
}
