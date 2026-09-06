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
 * Reads the live OSRS Wiki strategy page and extracts its
 * "Recommended equipment for ..." tables.
 *
 * The Wiki orders those table columns from most effective to least effective.
 * BossGearService maps those columns onto Max/High/Mid/Budget without inventing
 * its own equipment ranking.
 */
final class WikiGearService
{
    private static final String API = "https://oldschool.runescape.wiki/api.php";
    private static final String WIKI = "https://oldschool.runescape.wiki/w/";
    private static final String USER_AGENT =
        "KSP-Boss-Gear/1.0 (RuneLite/Microbot plugin; https://github.com/KSPOG/ksppluginsrelease)";

    private static final Pattern RECOMMENDED = Pattern.compile("(?i)Recommended\\s+equipment\\s+for");
    private static final Pattern ROW = Pattern.compile("(?is)<tr\\b[^>]*>(.*?)</tr>");
    private static final Pattern CELL = Pattern.compile("(?is)<(td|th)\\b[^>]*>(.*?)</\\1>");
    private static final Pattern LINK_TITLE = Pattern.compile("(?is)<a\\b[^>]*?title\\s*=\\s*([\"'])(.*?)\\1[^>]*>");
    private static final Pattern TAG = Pattern.compile("(?is)<[^>]+>");
    private static final Pattern SPACE = Pattern.compile("\\s+");

    /** Search suggestions. Arbitrary typed boss names still work even if absent here. */
    static final List<String> KNOWN_BOSSES = Collections.unmodifiableList(Arrays.asList(
        "Abyssal Sire", "Alchemical Hydra", "Amoxliatl", "Araxxor", "Artio", "Barrows",
        "Bryophyta", "Callisto", "Calvar'ion", "Cerberus", "Chaos Elemental", "Chaos Fanatic",
        "Chambers of Xeric", "Commander Zilyana", "Corporeal Beast", "Crazy Archaeologist",
        "Crystalline Hunllef", "Corrupted Hunllef", "Dagannoth Kings", "Deranged Archaeologist",
        "Doom of Mokhaiotl", "Duke Sucellus", "Fortis Colosseum", "General Graardor", "Gemstone Crab",
        "Giant Mole", "Grotesque Guardians", "Hespori", "Kalphite Queen", "King Black Dragon",
        "Kraken", "Kree'arra", "K'ril Tsutsaroth", "Maggot King", "Mad Angel", "Moons of Peril",
        "Nex", "Obor", "Phantom Muspah", "Phosani's Nightmare", "Royal Titans", "Sarachnis",
        "Scorpia", "Scurrius", "Shellbane gryphon", "Skotizo", "Sol Heredit", "The Hueycoatl",
        "The Leviathan", "The Mimic", "The Nightmare", "The Whisperer", "Theatre of Blood",
        "Thermonuclear smoke devil", "Tombs of Amascut", "TzKal-Zuk", "TzTok-Jad", "Vardorvis",
        "Venenatis", "Vet'ion", "Vorkath", "Yama", "Zalcano", "Zulrah"
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
        if (query.isEmpty()) throw new IOException("Enter a boss name first.");

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
                List<WikiGearPage.GearMethod> methods = parseEquipmentTables(parsed.html);
                if (methods.isEmpty())
                {
                    lastError = new IOException("No recommended equipment tables found on " + parsed.title + ".");
                    continue;
                }

                String bossName = parsed.title.endsWith("/Strategies")
                    ? parsed.title.substring(0, parsed.title.length() - "/Strategies".length())
                    : query;
                WikiGearPage result = new WikiGearPage(
                    bossName,
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
            : new IOException("The OSRS Wiki did not return a usable strategy page for " + query + ".");
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

    private static List<WikiGearPage.GearMethod> parseEquipmentTables(String html)
    {
        if (html == null || html.isEmpty()) return Collections.emptyList();

        Map<String, WikiGearPage.GearMethod> methods = new LinkedHashMap<>();
        Matcher recommended = RECOMMENDED.matcher(html);
        int searchFrom = 0;

        while (recommended.find(searchFrom))
        {
            int tableStart = indexOfIgnoreCase(html, "<table", recommended.end());
            if (tableStart < 0 || tableStart - recommended.end() > 3000) break;

            int tableEnd = indexOfIgnoreCase(html, "</table>", tableStart);
            if (tableEnd < 0) break;
            tableEnd += "</table>".length();

            String methodName = parseMethodName(html.substring(recommended.start(), tableStart));
            String tableHtml = html.substring(tableStart, tableEnd);
            List<WikiGearPage.GearRow> rows = parseRows(tableHtml);
            if (!rows.isEmpty())
            {
                String uniqueName = methodName;
                int suffix = 2;
                while (methods.containsKey(uniqueName.toLowerCase(Locale.ROOT)))
                {
                    uniqueName = methodName + " " + suffix++;
                }
                methods.put(uniqueName.toLowerCase(Locale.ROOT), new WikiGearPage.GearMethod(uniqueName, rows));
            }

            searchFrom = tableEnd;
        }

        return new ArrayList<>(methods.values());
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
            if (firstCellText.equals("slot") || firstCellText.equals("equipment slot")
                || (firstCellText.contains("slot") && visibleText(rowHtml).toLowerCase(Locale.ROOT).contains("most effective")))
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

    private static List<String> extractLinkTitles(String cellHtml)
    {
        Set<String> result = new LinkedHashSet<>();
        Matcher links = LINK_TITLE.matcher(cellHtml);
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
        if (lower.equals("n/a") || lower.equals("none")) return false;
        return true;
    }

    private static String parseMethodName(String headingHtml)
    {
        String plain = visibleText(headingHtml);
        Matcher m = Pattern.compile("(?i)Recommended\\s+equipment\\s+for\\s*(.*)").matcher(plain);
        String method = m.find() ? m.group(1).trim() : "Recommended";

        // The phrase itself is normally immediately above the table. Guard against
        // unrelated following prose if a Wiki layout changes.
        int stop = firstIndex(method, ".", "[", "Edit", "Slot");
        if (stop > 0) method = method.substring(0, stop).trim();
        if (method.length() > 60) method = method.substring(0, 60).trim();
        return method.isEmpty() ? "Recommended" : method;
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

    private static String visibleText(String html)
    {
        String withSpaces = html
            .replaceAll("(?is)<br\\s*/?>", " ")
            .replaceAll("(?is)</(?:p|div|h[1-6])>", " ");
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

    private static int indexOfIgnoreCase(String source, String target, int from)
    {
        if (source == null || target == null) return -1;
        int max = source.length() - target.length();
        for (int i = Math.max(0, from); i <= max; i++)
        {
            if (source.regionMatches(true, i, target, 0, target.length())) return i;
        }
        return -1;
    }

    private static List<String> candidatePages(String query)
    {
        List<String> pages = new ArrayList<>();
        if (query.toLowerCase(Locale.ROOT).endsWith("/strategies"))
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

    private static String normalizeQuery(String query)
    {
        return query == null ? "" : SPACE.matcher(query.trim()).replaceAll(" ");
    }

    private static String wikiUrl(String pageName)
    {
        String path = pageName.replace(' ', '_').replace("'", "%27");
        return WIKI + path;
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
