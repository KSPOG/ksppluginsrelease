from pathlib import Path

SCRIPT = Path('kspsmartsmelter/KspSmartSmelterScript.java')
PLUGIN = Path('kspsmartsmelter/KspSmartSmelterPlugin.java')

text = SCRIPT.read_text(encoding='utf-8')

old_import = 'import net.runelite.client.plugins.microbot.util.grandexchange.Rs2GrandExchange;\n'
new_import = old_import + 'import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;\n'
if 'import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;' not in text:
    if old_import not in text:
        raise RuntimeError('Could not locate import insertion point')
    text = text.replace(old_import, new_import, 1)

old_constants = '''    private static final int CANNONBALL_INTERFACE = 17694733;\n    private static final int CANNONBALL_BUTTON = 17694734;\n'''
new_constants = '''    private static final int CANNONBALL_INTERFACE = 17694733;\n    private static final int CANNONBALL_BUTTON = 17694734;\n    private static final int EDGEVILLE_BANK_BOOTH_ID = 10583;\n'''
if 'EDGEVILLE_BANK_BOOTH_ID' not in text:
    if old_constants not in text:
        raise RuntimeError('Could not locate constants')
    text = text.replace(old_constants, new_constants, 1)

old_bank = '''    private boolean openWorkBank() {\n        if (Rs2Bank.isOpen()) {\n            return true;\n        }\n\n        FurnaceLocation location = config.furnaceLocation();\n        state = SmartSmelterState.WALKING_TO_BANK;\n        Microbot.status = "Opening " + location.getDisplayName() + " bank";\n\n        // Interact first when a bank is already loaded/reachable in the selected area.\n        if (Rs2Bank.openBank() && sleepUntil(Rs2Bank::isOpen, 2500)) {\n            return true;\n        }\n\n        if (location != FurnaceLocation.CURRENT_AREA && location.getBankPoint() != null) {\n            Microbot.status = "Walking to " + location.getDisplayName() + " bank";\n            Rs2Walker.walkTo(location.getBankPoint(), 4);\n        }\n        return false;\n    }\n'''
new_bank = '''    private boolean openWorkBank() {\n        if (Rs2Bank.isOpen()) {\n            return true;\n        }\n\n        FurnaceLocation location = config.furnaceLocation();\n        state = SmartSmelterState.WALKING_TO_BANK;\n\n        // Edgeville is intentionally direct-interaction only. Do not use the generic\n        // bank finder/walker here: it can select unrelated objects containing \"Bank\"\n        // or start a route when the booth is already expected to be in the local scene.\n        if (location == FurnaceLocation.EDGEVILLE) {\n            Microbot.status = "Opening Edgeville Bank Booth";\n            if (Rs2GameObject.interact(EDGEVILLE_BANK_BOOTH_ID, "Bank")\n                    && sleepUntil(Rs2Bank::isOpen, 2500)) {\n                return true;\n            }\n            Microbot.status = "Waiting for Edgeville Bank Booth (10583)";\n            return false;\n        }\n\n        Microbot.status = "Opening " + location.getDisplayName() + " bank";\n\n        // Non-Edgeville locations retain their existing bank behavior.\n        if (Rs2Bank.openBank() && sleepUntil(Rs2Bank::isOpen, 2500)) {\n            return true;\n        }\n\n        if (location != FurnaceLocation.CURRENT_AREA && location.getBankPoint() != null) {\n            Microbot.status = "Walking to " + location.getDisplayName() + " bank";\n            Rs2Walker.walkTo(location.getBankPoint(), 4);\n        }\n        return false;\n    }\n'''
if old_bank not in text:
    raise RuntimeError('Could not locate openWorkBank block')
text = text.replace(old_bank, new_bank, 1)

old_furnace_missing = '''            if (furnace == null) {\n                if (location != FurnaceLocation.CURRENT_AREA && location.getFurnacePoint() != null) {\n                    Microbot.status = "Walking to " + location.getDisplayName() + " furnace";\n                    Rs2Walker.walkTo(location.getFurnacePoint(), 4);\n                } else {\n                    Microbot.status = "Cannot find Furnace";\n                }\n                return;\n            }\n'''
new_furnace_missing = '''            if (furnace == null) {\n                if (location == FurnaceLocation.EDGEVILLE) {\n                    // Edgeville is direct-interaction only: wait for the configured\n                    // furnace to be present instead of invoking Rs2Walker.\n                    Microbot.status = "Waiting for Edgeville Furnace";\n                } else if (location != FurnaceLocation.CURRENT_AREA && location.getFurnacePoint() != null) {\n                    Microbot.status = "Walking to " + location.getDisplayName() + " furnace";\n                    Rs2Walker.walkTo(location.getFurnacePoint(), 4);\n                } else {\n                    Microbot.status = "Cannot find Furnace";\n                }\n                return;\n            }\n'''
if old_furnace_missing not in text:
    raise RuntimeError('Could not locate furnace missing block')
text = text.replace(old_furnace_missing, new_furnace_missing, 1)

# Guard against any Edgeville bank walking regression in this method.
bank_start = text.index('    private boolean openWorkBank()')
bank_end = text.index('    private void smeltTrip(', bank_start)
bank_block = text[bank_start:bank_end]
if 'location == FurnaceLocation.EDGEVILLE' not in bank_block or 'EDGEVILLE_BANK_BOOTH_ID' not in bank_block:
    raise RuntimeError('Edgeville direct bank guard missing')

SCRIPT.write_text(text, encoding='utf-8')

plugin = PLUGIN.read_text(encoding='utf-8')
if 'public static final String VERSION = "0.0.8";' not in plugin:
    raise RuntimeError('Unexpected Smart Smelter version')
plugin = plugin.replace('public static final String VERSION = "0.0.8";', 'public static final String VERSION = "0.0.9";', 1)
PLUGIN.write_text(plugin, encoding='utf-8')

Path('.github/workflows/patch-smart-smelter-edgeville.yml').unlink(missing_ok=True)
Path('.github/scripts/patch_smart_smelter_edgeville.py').unlink(missing_ok=True)
