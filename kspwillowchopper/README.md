# KSP Willow Chopper v1.1.6

Package: `net.runelite.client.plugins.microbot.kspwillowchopper`

## Core behavior

### Bank logs = ON
1. Directly click the nearest loaded willow tree.
2. Chop until inventory is full.
3. Directly open/click the nearby bank with `Rs2Bank.openBank()`.
4. Deposit willow logs.
5. Close the bank.
6. Directly click the nearest loaded willow tree again.

This plugin contains no direct `Rs2Walker` imports or calls; movement outside object interactions uses direct minimap/canvas clicks only.

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


## v1.1.6 overlay fix
- Fixed overlay using a different injected script instance.
- Script is now a singleton so the overlay reads the live running state.
- Runtime and XP panels now stay at 0 until a real session starts.


## v1.1.6 burn tracking fix
- Logs burned are now counted from actual Willow-log inventory decreases while Burn mode is active.
- Tracking no longer depends on the Burn widget remaining visible or `burningActive` staying true between scheduler ticks.
- A Willow log intentionally dropped to make room for a tinderbox is explicitly excluded from the burned counter.


## v1.1.6 overlay update
- Added `WC Lv` as current real Woodcutting level / levels gained this session.
- Added `FM Lv` as current real Firemaking level / levels gained this session.


## v1.1.6 tree selector
- Added a Tree dropdown using the full current Microbot WoodcuttingTree support list.
- Willow remains the default.
- Direct bank/tree behavior now uses the selected tree and selected resource dynamically.
- Overlay now shows selected Tree, Resource and inventory count.
- Chopped/banked/burned counters are generic to the selected resource.
- Woodcutting level requirement is checked from the selected tree.
- Campfire mode is available only for selections whose resource is a log.


## v1.1.6 runtime compiler compatibility
- Removed unsupported Lombok @Getter and @RequiredArgsConstructor from KspTree.
- Replaced them with a plain Java enum constructor and explicit getters for KSP Source Loader runtime compilation.


## v1.1.6 runtime compile fix
- Removed the remaining obsolete `WILLOW_LOG_ID` references introduced before the multi-tree conversion.
- Forestry inventory-space handling now drops the selected tree resource instead of hardcoded Willow logs.
- Beehive handling now uses the selected log-producing resource and skips non-log tree selections.


## v1.1.6 instant object retargeting
- Tracks the exact tree object ID and world tile that was clicked.
- A despawn/object-ID morph immediately invalidates the old target.
- Replacement tree selection bypasses movement, animation, and normal click throttles.
- Uses a short 20-60 ms cache-refresh retry burst, then falls back to the normal loop if no replacement is loaded.
- Removed the blocking post-click animation wait so target-change events can be acted on immediately.


## v1.1.6 campfire retargeting
- Forester's Campfire interactions now use the same event-driven object transition handling as tree targets.
- If the active campfire despawns or morphs to another object ID, the script immediately reacquires the replacement and repeats the burn interaction.
- If no replacement appears after the short scene-cache refresh burst, the script creates a new campfire and resumes the burn cycle.


## v1.1.6 interaction/state fix
- Removed every direct Rs2Walker import/call from this plugin.
- Forestry ritual/rainbow movement now uses direct minimap/canvas clicks only.
- Tree retargeting waits for the player to be idle instead of clicking through an active interaction.
- Campfire ID changes reacquire the replacement without repeating Use->campfire while Make-X is still running.
- Burn mode now remains in a burn cycle until the selected log/resource count reaches zero.
- Campfire creation no longer uses web-walker repositioning.


## v1.1.6 - burned-out campfire recovery
- The game message `The fire has burned out.` now immediately invalidates stale burn state.
- Despawn of the exact active campfire does the same.
- The script briefly checks for a replacement object, then creates a new fire if none exists.
- Remaining logs stay in the active burn cycle; chopping cannot resume until they reach zero.
- No `Rs2Walker` imports or calls were added.
