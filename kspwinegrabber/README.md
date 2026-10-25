# KSP Wine of Zamorak Grabber

Microbot 2.6.19+ source-loader plugin; upstairs-only Asgarnian Chaos Temple wine.

## Flow
1. Requires account total level >= 500 and base Magic >= 33.
2. Walks to Falador West bank at (2946,3368,0), opens the bank.
3. Deposits only Wine of Zamorak. Checks inventory, equipped gear, and the **open bank** for the exact items:
   - Staff of air (1381)
   - Zamorak monk top (1035)
   - Zamorak monk bottom (1033)
   - Law rune (563; checked in inventory and bank)
4. Withdraws missing gear one item at a time, and all available law runes. Equips all three items.
5. Walks to Asgarnian Chaos Temple, climbs its ladder, walks to (2939,3517,1).
6. Telekinetic Grab on upstairs ground item Wine of Zamorak (245) via WIDGET_TARGET_ON_GROUND_ITEM (not Take); waits for inventory confirmation before retrying.
7. When inventory becomes full, climbs downstairs, **walks** to Falador West, banks only the wine, restocks any banked law runes, and repeats.
8. Immediately stops and raises an error dialog for missing bank/inventory/equipped requirements or unsupported level/spell conditions.

## Important
- The plugin never intentionally grabs the dangerous ground-floor wine.
- All scripted travel uses Rs2Walker with teleporting disabled for each call.
- A live RuneLite/Microbot integration test is still required; no local client runtime or Gradle build is bundled in this raw-source repository.

## Manual acceptance checklist
- Test missing robe top, missing robe bottom, missing staff, and missing law runes separately with items absent from BOTH inventory and open bank. Verify one error dialog and plugin disabled.
- Test all three gear items initially equipped, in inventory, and in bank separately.
- Test bank containing more law runes while inventory contains some: withdrawal merges the stacks.
- Test ladder interaction, upstairs-only ground-item selection, spell-on-ground-item action, and inventory count confirmation.
- Test 28 occupied inventory slots and > 0 wines; verify bank trip contains no teleport.
- Test pause/resume via KSP break handler, failed cast, missed spawn, logout/login, and manual plugin stop.
