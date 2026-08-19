# KSP GE Looter v0.1.2

## Features

- Configurable minimum **total Grand Exchange value** for ground-item stacks.
- Repeated fast `Take` interactions ("spam clicking") on the selected loot target.
- Hard perimeter guard using the exact coordinates supplied for the Grand Exchange area.
- Optional High Level Alchemy:
  - With **Staff of fire** equipped: item HA value must be greater than **1 Nature rune**.
  - Without **Staff of fire** equipped: item HA value must be greater than **1 Nature rune + 5 Fire runes**.
  - Requires Magic level 55 and the required runes in inventory.
- Full inventory:
  - High Alchs an eligible item first when High Alch is enabled.
  - If no actionable High Alch exists, opens the GE bank and deposits the loot.
  - With **Staff of fire** equipped, it keeps **Nature runes only**.
  - Without **Staff of fire**, it keeps **Nature runes + Fire runes only**.
  - Every other rune type is deposited.
- No generic `walkToBank()` call is used. Banking is attempted from inside the guarded GE area only.

## Overlay

The expanded session overlay shows:

- Current status and whether the player is inside the protected GE area.
- Runtime.
- Current loot target and its total GE value.
- Configured minimum GE value.
- Spam-click count and delay.
- Inventory slots used.
- Confirmed loot quantity.
- Confirmed total GE value looted and GE value per hour.
- High Alch enabled/disabled state.
- Exact Staff of fire equipped state.
- Nature/Fire rune inventory quantities and live GE prices.
- Current High Alch rune cost.
- Number of successful High Alchs.
- Total High Alch value.
- Total High Alch rune margin and margin per hour.

Loot statistics are recorded from confirmed inventory quantity increases after a loot interaction rather than simply counting occupied inventory slots.

## Source layout

Place the `KSPGELooter` folder alongside your other KSP source folders, for example:

`Sources/KSPGELooter/`

For a direct Microbot-Hub source integration, move the Java files into:

`src/main/java/net/runelite/client/plugins/microbot/kspgelooter/`

## Config defaults

- Minimum GE Value: `1000`
- High Alch: `false`
- Clicks Per Item: `5`
- Click Delay: `70 ms`

## Notes

The supplied perimeter is represented with RuneLite `WorldPoint` values. The polygon also explicitly includes each supplied perimeter tile so edge points are not rejected by `Polygon.contains()`.

The looter gives ground loot priority while inventory space exists. High Alchemy runs when the inventory is full or when there is currently no qualifying ground loot, reducing the chance of missing valuable drops.
