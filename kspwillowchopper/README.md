# KSP Willow Chopper v1.1.1

Package: `net.runelite.client.plugins.microbot.kspwillowchopper`

## Core behavior

### Bank logs = ON
1. Directly click the nearest loaded willow tree.
2. Chop until inventory is full.
3. Directly open/click the nearby bank with `Rs2Bank.openBank()`.
4. Deposit willow logs.
5. Close the bank.
6. Directly click the nearest loaded willow tree again.

The normal bank/tree loop intentionally does not use `Rs2Walker`.

### Bank logs = OFF
1. Chop willow until full.
2. Find a nearby Forester's Campfire/fire.
3. If none exists, get/use a tinderbox and create a fire.
4. Select willow logs and click the campfire.
5. Wait for the Burn make-X interface and press Space.
6. Burn the inventory and resume chopping.

## Forestry handlers

- Rising Roots
- Struggling Sapling
- Friendly Entlings
- Beehive
- Pheasant Control
- Poachers / Fox
- Enchantment Ritual
- Woodcutting Leprechaun rainbow boosts
- Flowering Tree

### Struggling Sapling
The optimal stage 1 / 2 / 3 ingredient combination is learned from the game's messages. Once a stage is known, that exact ingredient is reused for the rest of the event. The combination is reset only after the sapling ingredient piles despawn.

## Overlay

- mode
- status
- willow logs in inventory
- campfire status
- current Forestry event
- completed Forestry events
- runtime
- logs chopped and logs/hour
- logs banked or burned
- campfires created
- Woodcutting XP gained + XP/hour
- Firemaking XP gained + XP/hour
- anima-infused bark gained
- learned Struggling Sapling combination


## v1.1.1 overlay fix
- Fixed overlay using a different injected script instance.
- Script is now a singleton so the overlay reads the live running state.
- Runtime and XP panels now stay at 0 until a real session starts.


## v1.1.1 burn tracking fix
- Logs burned are now counted from actual Willow-log inventory decreases while Burn mode is active.
- Tracking no longer depends on the Burn widget remaining visible or `burningActive` staying true between scheduler ticks.
- A Willow log intentionally dropped to make room for a tinderbox is explicitly excluded from the burned counter.


## v1.1.1 overlay update
- Added `WC Lv` as current real Woodcutting level / levels gained this session.
- Added `FM Lv` as current real Firemaking level / levels gained this session.


## v1.1.1 tree selector
- Added a Tree dropdown using the full current Microbot WoodcuttingTree support list.
- Willow remains the default.
- Direct bank/tree behavior now uses the selected tree and selected resource dynamically.
- Overlay now shows selected Tree, Resource and inventory count.
- Chopped/banked/burned counters are generic to the selected resource.
- Woodcutting level requirement is checked from the selected tree.
- Campfire mode is available only for selections whose resource is a log.


## v1.1.1 runtime compiler compatibility
- Removed unsupported Lombok @Getter and @RequiredArgsConstructor from KspTree.
- Replaced them with a plain Java enum constructor and explicit getters for KSP Source Loader runtime compilation.
