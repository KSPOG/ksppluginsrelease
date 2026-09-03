from pathlib import Path

p = Path('kspwillowchopper/KspWillowChopperScript.java')
text = p.read_text(encoding='utf-8')

# Remove imports used only by the generic/fallback bank routing path.
for line in [
    'import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;\n',
    'import net.runelite.client.plugins.microbot.util.bank.enums.BankLocation;\n',
    'import net.runelite.client.plugins.microbot.util.gameobject.Rs2BankID;\n',
    'import net.runelite.client.plugins.microbot.util.walker.Rs2Walker;\n',
]:
    text = text.replace(line, '')

old_constants = '''    // Cook's Guild bank targets are intentionally ignored. These are\n    // geometrically close to nearby chopping areas but can be inaccessible.\n    private static final int IGNORED_BANK_BOOTH_ID = 10583;\n    private static final int IGNORED_BANKER_ID_1 = 2897;\n    private static final int IGNORED_BANKER_ID_2 = 2898;\n'''
new_constants = '''    // Chopper banking is intentionally restricted to this exact booth.\n    // Never fall back to bankers, alternate booth IDs, deposit boxes or WebWalker.\n    private static final int BANK_BOOTH_ID = 10583;\n    private static final String BANK_BOOTH_NAME = "Bank Booth";\n'''
if old_constants not in text:
    raise RuntimeError('Bank constants block not found')
text = text.replace(old_constants, new_constants, 1)

start = text.index('    private boolean openAllowedBank() {')
end = text.index('    private void bankResource() {', start)
replacement = '''    private boolean openAllowedBank() {\n        if (Rs2Bank.isOpen()) return true;\n\n        Rs2TileObjectModel bankBooth = Microbot.getRs2TileObjectCache()\n                .query()\n                .where(object -> object != null\n                        && object.getId() == BANK_BOOTH_ID\n                        && BANK_BOOTH_NAME.equals(object.getName())\n                        && KspTileObjectSupport.hasAction(object, "Bank"))\n                .nearestOnClientThread();\n\n        if (bankBooth == null) {\n            status = "Bank Booth 10583 not loaded";\n            return false;\n        }\n\n        status = "Opening Bank Booth 10583";\n        if (!bankBooth.click("Bank")) {\n            status = "Bank Booth interaction failed - retrying";\n            return false;\n        }\n\n        sleepUntil(Rs2Bank::isOpen, 4_000);\n        return Rs2Bank.isOpen();\n    }\n\n'''
text = text[:start] + replacement + text[end:]

# Hard validation for exact banking behavior.
for forbidden in [
    'Rs2Bank.openBank(',
    'Rs2Bank.walkToBank(',
    'Rs2Bank.walkToBankAndUseBank(',
    'Rs2Walker.walkTo(',
    'Rs2NpcModel',
    'BankLocation',
    'Rs2BankID',
]:
    if forbidden in text:
        raise RuntimeError(f'Forbidden generic banking/WebWalker path remains: {forbidden}')

if 'object.getId() == BANK_BOOTH_ID' not in text or 'BANK_BOOTH_NAME.equals(object.getName())' not in text:
    raise RuntimeError('Exact Bank Booth ID/name validation missing')
if 'bankBooth.click("Bank")' not in text:
    raise RuntimeError('Exact Bank Booth interaction missing')

p.write_text(text, encoding='utf-8')

Path('.github/workflows/lock-chopper-bank-booth.yml').unlink(missing_ok=True)
Path('.github/scripts/lock_chopper_bank_booth.py').unlink(missing_ok=True)
