# KSP Willow Chopper v0.1.0

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


## v0.1.0 overlay fix
- Fixed overlay using a different injected script instance.
- Script is now a singleton so the overlay reads the live running state.
- Runtime and XP panels now stay at 0 until a real session starts.


## v0.1.0 burn tracking fix
- Logs burned are now counted from actual Willow-log inventory decreases while Burn mode is active.
- Tracking no longer depends on the Burn widget remaining visible or `burningActive` staying true between scheduler ticks.
- A Willow log intentionally dropped to make room for a tinderbox is explicitly excluded from the burned counter.


## v0.1.0 overlay update
- Added `WC Lv` as current real Woodcutting level / levels gained this session.
- Added `FM Lv` as current real Firemaking level / levels gained this session.


## v0.1.0 tree selector
- Added a Tree dropdown using the full current Microbot WoodcuttingTree support list.
- Willow remains the default.
- Direct bank/tree behavior now uses the selected tree and selected resource dynamically.
- Overlay now shows selected Tree, Resource and inventory count.
- Chopped/banked/burned counters are generic to the selected resource.
- Woodcutting level requirement is checked from the selected tree.
- Campfire mode is available only for selections whose resource is a log.


## v0.1.0 runtime compiler compatibility
- Removed unsupported Lombok @Getter and @RequiredArgsConstructor from KspTree.
- Replaced them with a plain Java enum constructor and explicit getters for KSP Source Loader runtime compilation.


## v0.1.0 runtime compile fix
- Removed the remaining obsolete `WILLOW_LOG_ID` references introduced before the multi-tree conversion.
- Forestry inventory-space handling now drops the selected tree resource instead of hardcoded Willow logs.
- Beehive handling now uses the selected log-producing resource and skips non-log tree selections.


## v0.1.0 instant object retargeting
- Tracks the exact tree object ID and world tile that was clicked.
- A despawn/object-ID morph immediately invalidates the old target.
- Replacement tree selection bypasses movement, animation, and normal click throttles.
- Uses a short 20-60 ms cache-refresh retry burst, then falls back to the normal loop if no replacement is loaded.
- Removed the blocking post-click animation wait so target-change events can be acted on immediately.


## v0.1.0 campfire retargeting
- Forester's Campfire interactions now use the same event-driven object transition handling as tree targets.
- If the active campfire despawns or morphs to another object ID, the script immediately reacquires the replacement and repeats the burn interaction.
- If no replacement appears after the short scene-cache refresh burst, the script creates a new campfire and resumes the burn cycle.


## v0.1.0 interaction/state fix
- Removed every direct Rs2Walker import/call from this plugin.
- Forestry ritual/rainbow movement now uses direct minimap/canvas clicks only.
- Tree retargeting waits for the player to be idle instead of clicking through an active interaction.
- Campfire ID changes reacquire the replacement without repeating Use->campfire while Make-X is still running.
- Burn mode now remains in a burn cycle until the selected log/resource count reaches zero.
- Campfire creation no longer uses web-walker repositioning.


## v0.1.0 - burned-out campfire recovery
- The game message `The fire has burned out.` now immediately invalidates stale burn state.
- Despawn of the exact active campfire does the same.
- The script briefly checks for a replacement object, then creates a new fire if none exists.
- Remaining logs stay in the active burn cycle; chopping cannot resume until they reach zero.
- No `Rs2Walker` imports or calls were added.


## v0.1.0 universal tree retargeting
- Normalizes GameObject event coordinates through Rs2TileObjectModel so multi-tile trees match the stored clicked target.
- Adds a 300ms cache liveness fallback for tree types that morph/deplete without a matching spawn/despawn coordinate pair.
- Keeps immediate retargeting interaction guards and burn-cycle ownership.


## v0.1.0 Forestry interaction/counting fix
- Added a shared Forestry interaction guard to all event handlers.
- The guard blocks repeat clicks while the player is moving, interacting, or recently animating.
- The exact same Forestry target/action receives an additional click latch, while a genuinely different target can still be selected quickly.
- Applied the guard to roots, saplings, entlings, flowering bushes, poacher traps, beehives, pheasant nests/forester, ritual circles, and rainbow movement.
- Forestry completion counting is now deduplicated per event type for 120 seconds so temporary despawns/morphs cannot count the same event twice.
- No Rs2Walker calls/imports were introduced.


## v0.1.0 Friendly Ent fix
- Corrected current Entling overhead wording aliases.
- Added the missing `Prune-sides` step for the two combination hairstyles.
- Tracks haircut progress per Entling NPC index so combo cuts advance instead of repeating the first cut.
- Ignores satisfaction/countdown overhead text and waits for the pruned NPC morph before touching an Entling again.
- Keeps the shared Forestry interaction anti-spam guard and does not use Rs2Walker.


## v0.1.0 bank interaction latch
- Prevents re-clicking a bank while the player is already travelling to/opening it.
- Keeps the first direct bank interaction latched for up to 10 seconds before retrying.
- Waits for the selected resource stack to reach zero before closing the bank.

- Microbot 2.1+ compatibility: migrated legacy `Rs2GameObject` calls to `Rs2TileObjectQueryable` / `Rs2TileObjectModel`.

- Friendly Ent fix: repeatedly performs valid prune actions until the Entling actually morphs to the pruned state; the Entling loop no longer treats the player's stale NPC interaction pointer as an active prune, and uses the exact current `Short back and sides!` wording.

## v0.1.0 Entling hard target lock
- Once a Friendly Entling is selected, the handler locks to that exact NPC index.
- Other Entlings are ignored until the locked Entling morphs/despawns out of the regular Entling state.
- Temporary missing/changed overhead text does not release the target lock.
- Repeated prune actions continue only on the locked Entling, with the existing movement/animation/click anti-spam timing.

### Friendly Ent request mapping correction (v0.1.0)
- The locked Entling's overhead request is re-read immediately before every prune.
- Removed alternating prune sequences for combined requests; one valid action is repeated until that Entling morphs/despawns.
- Canonical mapping: Breezy at the back -> Prune-back; Short on top -> Prune-top; A leafy mullet -> Prune-top; Short back and sides -> Prune-back.
- Request text is tag-stripped and current/historical wording aliases are normalized before mapping.
