# KSP Chopper v0.1.5

Package: `net.runelite.client.plugins.microbot.kspwillowchopper`

> Progressive modes extend the existing deterministic Bank, Burn and Drop state machine; legacy modes remain available.

## Runtime architecture

The plugin now has one deterministic state machine instead of the previous tree-despawn queues, retarget bursts, campfire morph queues and global Forestry blocker lifecycle.

States:

- `CHOPPING`
- `BANKING`
- `DROPPING`
- `GETTING_TINDERBOX`
- `CREATING_FIRE`
- `BURNING`
- `FORESTRY`
- `PAUSED`
- `ERROR`

The core loop does **not** call `Script.run()` and therefore cannot be held forever by a stale `BlockingEvent`. It still respects `Microbot.pauseAllScripts`, login state and thread shutdown.

## Side-panel Start / Pause / Stop

Enabling the RuneLite plugin **does not start chopping**. Open the **KSP Chopper**
navigation panel and press **Start**. The overlay is hidden until Start. The
panel retains the tree/resource mode selectors, Forestry event options and
overlay setting.

- **Start:** begins a new chopping session and resets session counters. Only
  after Start are the break handler, mule worker, Forestry handlers and overlay
  activated.
- **Pause:** suspends the Chopper's task loop, Forestry interaction dispatch,
  break timer and mule worker. The overlay stays visible and shows the paused
  state; session progress is preserved.
- **Resume:** continues the current session with its saved counters and selected
  progressive tier, reactivating the paused services.
- **Stop:** terminates the script and its scheduled work, stops Forestry,
  breaks and mule handling, removes the overlay, and leaves the configuration
  panel open for the next Start.

The **Resource handling** dropdown offers manual **Chop & Bank**, **Chop & Burn**,
**Chop & Drop**, and **Progressive Chop & Bank**, **Progressive Chop & Burn**,
**Progressive Chop & Drop**. Settings are saved through RuneLite ConfigManager.
Previous `bankLogs=false` settings migrate to **Chop & Burn** unless the mode
has already been saved.

## Progressive modes — assigned areas only

All three Progressive modes select the highest unlocked tier among **Tree,
Oak, Willow and Yew**. The tier is chosen by Woodcutting level for Bank/Drop;
Burn uses the lower of Woodcutting and Firemaking levels so its selected logs
can be burned. Membership does not change these four assigned training
locations. Other trees (Maple, Magic, Redwood and specialized trees) remain
available in manual mode.

| Tier | Required WC / FM for Burn | Assigned RuneLite WorldArea (x, y, width, height, plane) |
|---|---:|---|
| Tree | 1 | `(3149, 3449, 23, 16, 0)` |
| Oak | 15 | `(3159, 3409, 13, 12, 0)` |
| Willow | 30 | `(3056, 3250, 8, 7, 0)` |
| Yew | 60 | `(3085, 3468, 5, 15, 0)` |

**Strict boundaries:** The selected tree's live object must lie **inside its
assigned rectangle**, and it must expose the expected chopping action. A
matching tree nearby but outside the area must never be targeted. If the
player is outside the area, Microbot WebWalker navigates to its interior
anchor. Once inside, only in-area trees are eligible; banking/firemaking may
leave the area but the next chopping trip returns to it.

During tier upgrades, previous-tier resources are processed according to
Bank/Burn/Drop before changing resource type. Manual tree mode is unaffected.

## Chop & Bank

1. Select the configured tree.
2. Click the nearest loaded matching tree.
3. Continue chopping while the interaction is making progress.
4. When inventory is full, open the nearby bank.
5. Deposit only the configured tree resource.
6. Close the bank.
7. Resume chopping.

The axe and unrelated inventory items are preserved.

## Chop & Burn / Firemaking

Only log-producing tree resources can use this mode.

1. Chop until inventory is full.
2. If a nearby campfire/fire already exists, use it.
3. If no fire exists and no tinderbox is held:
   - make one inventory slot if necessary;
   - open a nearby bank;
   - withdraw a tinderbox;
   - close the bank.
4. Create a fire with the configured log type when required.
5. Select the configured logs and use them on the fire/campfire.
6. Confirm the Burn interface with Space when it appears.
7. Track real inventory decreases as Firemaking progress.
8. If the fire burns out or progress stalls, reacquire/create a fire and continue.
9. Resume chopping only after the batch is consumed.

## Chop & Drop

1. Chop the selected resource until inventory is full.
2. Drop **only** the selected tree's resource, preserving axes, tinderboxes and
   all other inventory items.
3. When inventory space is available, resume chopping.
4. Pause with a clear status if the inventory is full but contains no selected
   resource to discard.

## Tree support

The tree catalogue retains the existing supported selections including regular trees, oak, willow, teak, maple, mahogany, arctic pine, yew, blisterwood, camphor, magic, ironwood, redwood, rosewood and the existing special-resource tree entries.

## Forestry

Forestry remains optional and each event can still be enabled separately:

- Rising Roots
- Struggling Sapling
- Friendly Entlings
- Beehives
- Pheasant Control
- Poachers / Fox
- Enchantment Ritual
- Woodcutting Leprechaun
- Flowering Tree

### Important lifecycle change

Forestry handlers are **not registered with Microbot's global `BlockingEventManager` anymore**.

Instead, the Chopper owns them privately and runs a valid event on its own script thread. Every handler is bounded by a maximum event runtime so a broken or stale event cannot permanently own the Chopper.

On startup and shutdown, the plugin also removes legacy Chopper Forestry handlers left in the global manager by older builds. Other plugins and Microbot core blocking events are not removed.

## Overlay

The overlay shows:

- selected tree
- selected resource handling mode (bank/burn/drop)
- explicit runtime state
- current status
- selected resource and inventory quantity
- campfire state
- current Forestry event and completed count
- runtime
- resources chopped/hour
- resources banked, dropped or logs burned
- campfires lit
- Woodcutting and Firemaking level gains
- Woodcutting and Firemaking XP/hour
- anima-infused bark gained
- learned Struggling Sapling combination

## Local Mule

The existing `KspMuleConfig` integration and `KspMuleWorkerService` remain wired into plugin startup/shutdown.

## Rewrite goals

This rewrite deliberately prioritizes deterministic recovery over clever event-driven latches:

- no global Forestry blocking registration;
- no immediate retarget thread bursts;
- no tree object identity latch requirements;
- no campfire object morph queue;
- no permanent `Starting` state caused by the Chopper's own blockers;
- banking and Firemaking are driven by current inventory state every loop;
- all interaction failures fall back to retryable states with visible overlay status.
