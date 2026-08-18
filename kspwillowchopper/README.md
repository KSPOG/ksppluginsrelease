# KSP Willow Chopper v1.0.0

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
