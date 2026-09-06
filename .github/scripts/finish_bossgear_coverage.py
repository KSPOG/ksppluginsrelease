from pathlib import Path

path = Path('kspbossgear/WikiGearService.java')
text = path.read_text(encoding='utf-8')

start_marker = '    static final List<String> KNOWN_BOSSES = Collections.unmodifiableList(Arrays.asList('
start = text.index(start_marker)
end = text.index('    ));', start) + len('    ));')

new_list = '''    static final List<String> KNOWN_BOSSES = Collections.unmodifiableList(Arrays.asList(
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
    ));'''
text = text[:start] + new_list + text[end:]

loadout_marker = '        for (WikiGearPage.GearMethod method : parseInventorySections(html)) addUnique(result, signatures, method);\n\n'
if loadout_marker not in text:
    raise SystemExit('parseAllLoadouts marker missing')
text = text.replace(
    loadout_marker,
    loadout_marker +
    '        WikiGearPage.GearMethod supplyNotes = parseSupplyRecommendations(html);\n'
    '        if (supplyNotes != null) addUnique(result, signatures, supplyNotes);\n\n',
    1)

method_marker = '    /** Last-resort extractor for prose-based recommendation pages. */\n'
if method_marker not in text:
    raise SystemExit('prose method marker missing')

supply_method = r'''    /** Captures Wiki inventory advice embedded in prose/notes when no setup matrix is used. */
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

'''
text = text.replace(method_marker, supply_method + method_marker, 1)
path.write_text(text, encoding='utf-8')
