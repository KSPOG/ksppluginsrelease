# KSP Chopper v0.1.3

Package: `net.runelite.client.plugins.microbot.kspwillowchopper`

> The version intentionally remains **0.1.3**. The implementation was rewritten from the ground up after the previous runtime proved unreliable.

## Runtime architecture

The plugin now has one deterministic state machine instead of the previous tree-despawn queues, retarget bursts, campfire morph queues and global Forestry blocker lifecycle.

States:

- `CHOPPING`
- `BANKING`
- `GETTING_TINDERBOX`
- `CREATING_FIRE`
- `BURNING`
- `FORESTRY`
- `PAUSED`
- `ERROR`

The core loop does **not** call `Script.run()` and therefore cannot be held forever by a stale `BlockingEvent`. It still respects `Microbot.pauseAllScripts`, login state and thread shutdown.

## Bank resources = ON

1. Select the configured tree.
2. Click the nearest loaded matching tree.
3. Continue chopping while the interaction is making progress.
4. When inventory is full, open the nearby bank.
5. Deposit only the configured tree resource.
6. Close the bank.
7. Resume chopping.

The axe and unrelated inventory items are preserved.

## Bank resources = OFF / Firemaking

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
- bank/burn mode
- explicit runtime state
- current status
- selected resource and inventory quantity
- campfire state
- current Forestry event and completed count
- runtime
- resources chopped/hour
- resources banked or logs burned
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
