# KSP Wine of Zamorak Grabber

Microbot 2.6.19+ source-loader plugin; upstairs-only Asgarnian Chaos Temple wine.

## Flow
1. Requires account total level >= 500 and base Magic >= 33.
2. **Fast-start with Use teleport? disabled:** first checks the current inventory and equipment. If Staff of air, both Zamorak monk pieces, at least one Law rune and an empty inventory slot are available, skips opening/checking the bank altogether; equips carried items and walks straight to the temple. Otherwise walks to Falador West bank at (2946,3368,0) for supply validation and restocking.
3. Deposits only Wine of Zamorak. Checks inventory, equipped gear, and the **open bank** for the exact items:
   - Staff of air (1381)
   - Zamorak monk top (1035)
   - Zamorak monk bottom (1033)
   - Law rune (563; checked in inventory and bank)
4. Withdraws missing gear one item at a time, and all available law runes. When **Use teleport?** is enabled, also withdraws all banked Water runes (555) for Falador Teleport. Equips all three items.
5. Walks to Asgarnian Chaos Temple, climbs its ladder, walks to (2939,3517,1).
6. Telekinetic Grab on upstairs ground item Wine of Zamorak (245) via WIDGET_TARGET_ON_GROUND_ITEM (not Take); waits for inventory confirmation before retrying.
7. When inventory becomes full, **Use teleport?** attempts Falador Teleport (37 Magic, 1 Law rune, 1 Water rune, 3 Air runes provided by the equipped Staff of air) from the temple and walks from the landing point to Falador West bank. If disabled, unavailable, or unconfirmed, the script climbs downstairs and walks as before. It banks only wine, restocks runes and repeats.
8. Immediately stops and raises an error dialog for missing bank/inventory/equipped requirements or unsupported level/spell conditions.

## Optional Falador Teleport (v0.1.2)
- New configuration toggle: **Use teleport?** (default **off**).
- On, the script visits Falador West bank at startup and withdraws **all** water runes in addition to its existing law-rune restock.
- On return trips it attempts **one** Falador Teleport per trip if the account currently has at least level 37 Magic and the required runes. A failed/unavailable spell falls back to the original walking route without repeatedly clicking the spell.
- Off, the original fast-start and walking behavior are unchanged. Missing water runes do not halt wine collection; the script walks to bank instead.
- The estimated profit overlay does not yet subtract Water runes or extra Law runes spent on Falador Teleport.

## Overlay (v0.1.2)
- `Collected: total (per hour ph)` counts confirmed wine pickups during this session.
- `Profit: current (ph)` estimates session profit using RuneLite GE guide prices: `confirmed wine count × (wine unit price - law rune unit price)`. With Staff of air equipped, no Air runes are consumed. GE prices are refreshed every 30 seconds and profit is shown as `N/A (N/A ph)` when current price data cannot be obtained.
- Profit is an estimate, not realized sale proceeds. It does not account for failed casts, selling-price slippage or taxes.
- While waiting for a wine to spawn, status reads exactly `Waiting`.

## Important
- The plugin never intentionally grabs the dangerous ground-floor wine.
- Walking still uses Rs2Walker with automatic teleporting disabled for each call; the optional Falador Teleport is an explicit spell cast.
- A live RuneLite/Microbot integration test is still required; no local client runtime or Gradle build is bundled in this raw-source repository.

## Manual acceptance checklist
- Test missing robe top, missing robe bottom, missing staff, and missing law runes separately with items absent from BOTH inventory and open bank. Verify one error dialog and plugin disabled.
- Test all three gear items initially equipped, in inventory, and in bank separately. When all gear and law runes are already equipped/carried and at least one slot is free, confirm that startup does not open or walk to the bank.
- With gear in the inventory rather than equipped, confirm that it equips without banking. With a full inventory, confirm that it still banks as required.
- Test bank containing more law runes while inventory contains some: withdrawal merges the stacks.
- Test ladder interaction, upstairs-only ground-item selection, spell-on-ground-item action, and inventory count confirmation.
- Test 28 occupied inventory slots and > 0 wines; verify walking when Use teleport? is disabled, or Falador Teleport when it is enabled and runes/level allow.
- With Use teleport? enabled, verify all Water runes are withdrawn; verify successful teleport from upstairs and fallback when Water runes, Law runes, 37 Magic, or the spell widget is unavailable.
- Test overlay Collected: total (ph), estimated Profit: total (ph), live guide-price refresh and price-unavailable display.
- Test pause/resume via KSP break handler, failed cast, missed spawn, logout/login, and manual plugin stop.
