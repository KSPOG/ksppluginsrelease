from pathlib import Path
import re

ROOT = Path('.')
HELPER_DIR = Path('kspbank')
HELPER_FILE = HELPER_DIR / 'KspVerifiedBank.java'

HELPER = '''package net.runelite.client.plugins.microbot.kspbank;

import net.runelite.api.GameObject;
import net.runelite.client.plugins.microbot.api.npc.models.Rs2NpcModel;
import net.runelite.client.plugins.microbot.util.bank.Rs2Bank;
import net.runelite.client.plugins.microbot.util.gameobject.Rs2GameObject;
import net.runelite.client.plugins.microbot.util.npc.Rs2Npc;

/**
 * Central bank-target validation for KSP plugins.
 *
 * Generic KSP banking intentionally interacts only with an exact Banker NPC or
 * an exact Bank booth object. It never performs fuzzy name matching on "Bank".
 * Location-specific plugins may still use an explicitly verified object ID.
 */
public final class KspVerifiedBank
{
    private KspVerifiedBank() {}

    public static boolean openBank()
    {
        if (Rs2Bank.isOpen()) return true;

        Rs2NpcModel banker = Rs2Npc.getBankerNPC();
        if (banker != null
                && banker.getName() != null
                && "Banker".equalsIgnoreCase(banker.getName())
                && Rs2Npc.interact(banker, "Bank"))
        {
            return true;
        }

        GameObject booth = Rs2GameObject.get("Bank booth", true);
        return booth != null && Rs2GameObject.interact(booth, "Bank");
    }

    /**
     * Travel may use Microbot's bank location routing, but the final interaction
     * is always re-acquired through the strict target allow-list above.
     */
    public static boolean walkToBankAndOpenBank()
    {
        if (Rs2Bank.isOpen()) return true;
        if (openBank()) return true;
        if (!Rs2Bank.walkToBank()) return false;
        if (Rs2Bank.isOpen()) return true;
        return openBank();
    }
}
'''

IMPORT = 'import net.runelite.client.plugins.microbot.kspbank.KspVerifiedBank;'

# Jewellery Crafter has a deliberate no-camera direct invocation implementation.
# Chopper has a stricter Bank Booth 10583-only implementation.
EXCLUDE_GENERIC_REWRITE = {
    Path('kspjewelrycrafter/KspJewelryCrafterScript.java'),
    Path('kspwillowchopper/KspWillowChopperScript.java'),
}


def add_import(text: str) -> str:
    if IMPORT in text:
        return text
    package = re.search(r'^package\s+[^;]+;\s*', text, re.MULTILINE)
    if not package:
        raise RuntimeError('No package declaration found')
    return text[:package.end()] + '\n' + IMPORT + '\n' + text[package.end():].lstrip('\n')


def patch_generic_bank_calls(path: Path):
    if path in EXCLUDE_GENERIC_REWRITE:
        return False
    text = path.read_text(encoding='utf-8')
    original = text

    text = text.replace('Rs2Bank.walkToBankAndUseBank()', 'KspVerifiedBank.walkToBankAndOpenBank()')
    text = text.replace('Rs2Bank.openBank()', 'KspVerifiedBank.openBank()')

    if text != original:
        text = add_import(text)
        path.write_text(text, encoding='utf-8')
        return True
    return False


def patch_direct_fishing():
    path = Path('kspdirectfishing/KspDirectFishingScript.java')
    text = path.read_text(encoding='utf-8')
    old = 'withNames("Bank booth","Bank chest","Bank").where(o->o.getId()!=INVALID_DIRECT_BANK_OBJECT_ID)'
    new = 'withName("Bank booth").where(o->o.getName()!=null&&"Bank booth".equalsIgnoreCase(o.getName())&&o.getId()!=INVALID_DIRECT_BANK_OBJECT_ID)'
    if old not in text:
        raise RuntimeError('Direct Fishing fuzzy bank query not found')
    text = text.replace(old, new, 1)
    old_banker = 'withName("Banker").fromWorldView().nearestOnClientThread()'
    new_banker = 'withName("Banker").where(n->n.getName()!=null&&"Banker".equalsIgnoreCase(n.getName())).fromWorldView().nearestOnClientThread()'
    if old_banker not in text:
        raise RuntimeError('Direct Fishing banker query not found')
    text = text.replace(old_banker, new_banker, 1)
    path.write_text(text, encoding='utf-8')


def patch_bank_organizer():
    path = Path('kspbankorganizer/BankActuator.java')
    text = path.read_text(encoding='utf-8')
    old = 'GameObject booth=Rs2GameObject.findBank(SEARCH_RADIUS);'
    new = 'GameObject booth=Rs2GameObject.get("Bank booth",true);'
    if old not in text:
        raise RuntimeError('Bank Organizer generic bank finder not found')
    text = text.replace(old, new, 1)
    old_banker = 'private Rs2NpcModel nearbyBanker(){Rs2NpcModel b=Rs2Npc.getBankerNPC();return b!=null&&near(b,INTERACT_DISTANCE)?b:null;}'
    new_banker = 'private Rs2NpcModel nearbyBanker(){Rs2NpcModel b=Rs2Npc.getBankerNPC();return b!=null&&b.getName()!=null&&"Banker".equalsIgnoreCase(b.getName())&&near(b,INTERACT_DISTANCE)?b:null;}'
    if old_banker not in text:
        raise RuntimeError('Bank Organizer banker helper not found')
    text = text.replace(old_banker, new_banker, 1)
    path.write_text(text, encoding='utf-8')


def patch_jewellery():
    path = Path('kspjewelrycrafter/KspJewelryCrafterScript.java')
    text = path.read_text(encoding='utf-8')
    old = 'GameObject bank = Rs2GameObject.findBank();'
    new = 'GameObject bank = Rs2GameObject.get("Bank booth", true);'
    if old not in text:
        raise RuntimeError('Jewellery Crafter generic bank finder not found')
    text = text.replace(old, new, 1)
    path.write_text(text, encoding='utf-8')


def validate():
    violations = []
    for path in ROOT.rglob('*.java'):
        if path == HELPER_FILE:
            continue
        text = path.read_text(encoding='utf-8')
        if 'Rs2Bank.walkToBankAndUseBank()' in text:
            violations.append(f'{path}: walkToBankAndUseBank')
        # No generic no-arg bank opener remains in runtime code. Jewellery comments
        # may mention it, but its implementation is direct/no-camera.
        for line_no, line in enumerate(text.splitlines(), 1):
            stripped = line.strip()
            if stripped.startswith('//') or stripped.startswith('*'):
                continue
            if 'Rs2Bank.openBank()' in line:
                violations.append(f'{path}:{line_no}: generic openBank')
            if '.withName("Bank")' in line or '.withNames("Bank"' in line:
                violations.append(f'{path}:{line_no}: fuzzy Bank query')
            if 'withNames("Bank booth","Bank chest","Bank")' in line:
                violations.append(f'{path}:{line_no}: fuzzy multi-name Bank query')

    direct = Path('kspdirectfishing/KspDirectFishingScript.java').read_text(encoding='utf-8')
    if '"Bank"' in direct and 'withNames("Bank booth","Bank chest","Bank")' in direct:
        violations.append('Direct Fishing fuzzy bank list remains')

    chopper = Path('kspwillowchopper/KspWillowChopperScript.java').read_text(encoding='utf-8')
    if 'BANK_BOOTH_OBJECT_ID = 10583' not in chopper and '10583' not in chopper:
        violations.append('Chopper Bank Booth 10583 restriction missing')

    smart = Path('kspsmartsmelter/KspSmartSmelterScript.java').read_text(encoding='utf-8')
    if 'EDGEVILLE_BANK_BOOTH_ID = 10583' not in smart:
        violations.append('Smart Smelter Edgeville Bank Booth 10583 restriction missing')
    edgeville_block = smart[smart.index('if (location == FurnaceLocation.EDGEVILLE)'):smart.index('Microbot.status = "Opening " + location.getDisplayName() + " bank";')]
    if 'Rs2Walker' in edgeville_block or 'walkToBank' in edgeville_block:
        violations.append('Smart Smelter Edgeville still walks to bank')

    if violations:
        raise RuntimeError('Bank target validation failed:\n' + '\n'.join(violations))


def main():
    HELPER_DIR.mkdir(exist_ok=True)
    HELPER_FILE.write_text(HELPER, encoding='utf-8')

    changed = []
    for path in ROOT.rglob('*.java'):
        if path == HELPER_FILE or '.github' in path.parts:
            continue
        if patch_generic_bank_calls(path):
            changed.append(str(path))

    patch_direct_fishing()
    patch_bank_organizer()
    patch_jewellery()
    validate()

    Path('.github/workflows/harden-verified-bank-targets.yml').unlink(missing_ok=True)
    Path('.github/scripts/harden_verified_bank_targets.py').unlink(missing_ok=True)

    print('Patched generic bank calls in:')
    for item in changed:
        print(' -', item)


if __name__ == '__main__':
    main()
