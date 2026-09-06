from pathlib import Path

service = Path('kspbossgear/BossGearService.java')
text = service.read_text(encoding='utf-8')

old_import = 'import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;'
new_import = 'import net.runelite.client.plugins.microbot.Microbot;\nimport net.runelite.client.plugins.microbot.util.bank.Rs2Bank;'
if 'import net.runelite.client.plugins.microbot.Microbot;' not in text:
    if old_import not in text:
        raise SystemExit('BossGearService import anchor missing')
    text = text.replace(old_import, new_import, 1)

old = '''        for (String name : names)
        {
            itemIdCache.computeIfAbsent(normalize(name), key -> resolveExactItemId(name));
        }
    }

    private int resolveExactItemId(String itemName)
    {
        try
        {
            return Rs2ItemManager.getItemIdByName(itemName, false);
        }
        catch (Throwable ignored)
        {
            return -1;
        }
    }
'''

new = '''        for (String name : names)
        {
            String key = normalize(name);
            Integer cached = itemIdCache.get(key);
            if (cached != null && cached > 0) continue;

            int id = resolveExactItemId(name);
            // Do not cache failed lookups: the client-backed item manager may still be warming up.
            if (id > 0) itemIdCache.put(key, id);
        }
    }

    private int resolveExactItemId(String itemName)
    {
        if (itemName == null || itemName.trim().isEmpty()) return -1;
        String candidate = itemName.trim();

        try
        {
            int id = Rs2ItemManager.getItemIdByName(candidate, false);
            if (id > 0) return id;
        }
        catch (Throwable ignored)
        {
            // Fall through to the client-backed item manager used by other KSP plugins.
        }

        try
        {
            int id = Microbot.getRs2ItemManager().getItemId(candidate);
            if (id > 0) return id;
        }
        catch (Throwable ignored)
        {
            // Return unresolved below.
        }
        return -1;
    }
'''

if old not in text:
    raise SystemExit('BossGearService resolver anchor missing')
text = text.replace(old, new, 1)
service.write_text(text, encoding='utf-8')

plugin = Path('kspbossgear/KspBossGearPlugin.java')
ptext = plugin.read_text(encoding='utf-8')
old_version = 'public static final String VERSION = "1.1.0";'
if old_version not in ptext:
    raise SystemExit('Boss Gear version anchor missing')
ptext = ptext.replace(old_version, 'public static final String VERSION = "1.1.1";', 1)
plugin.write_text(ptext, encoding='utf-8')
