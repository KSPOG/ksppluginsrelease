# KSP GE Looter v0.1.22

## Features

- Configurable minimum **total Grand Exchange value** for ground-item stacks.
- Repeated `Take` interactions through the game's context menu. The looter right-clicks the loot tile, then left-clicks the matching item’s `Take` entry.
- Hard perimeter guard using the exact coordinates supplied for the Grand Exchange area.
- When no eligible loot remains, returns to the waiting area spanning X 3161–3167 and Y 3486–3493 on plane 0. It picks a random reachable tile for each return, stops issuing return clicks anywhere inside the area, and keeps scanning for loot. It pauses the return after three walk requests without getting closer.
- Optional **Priority Mode**:
  - Watches for ground items meeting the configured GE-value threshold.
  - When qualifying loot appears, temporarily pauses other Microbot scripts without restarting them.
  - The looter remains active while it owns that pause, including while creating inventory space.
  - As soon as no qualifying ground loot remains, it releases only the pause it created and hands control back.
  - If scripts were already paused before the looter tried to take priority, it does not claim ownership and will not unpause that external pause.
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
- Priority Mode state, takeover state, and whether the looter owns the shared script pause.
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

## Troubleshooting logs

Search the client log for `GE Looter`. It records the effective settings at startup, why the script is waiting, why ground items did not qualify, the outcome of Take attempts, and return-walk requests, arrival, or lack of progress. Routine empty scans and successful Take results are sampled to keep the log readable.

## Source layout

Place the `KSPGELooter` folder alongside your other KSP source folders, for example:

`Sources/KSPGELooter/`

For a direct Microbot-Hub source integration, move the Java files into:

`src/main/java/net/runelite/client/plugins/microbot/kspgelooter/`

## Config defaults

- Minimum GE Value: `1000`
- Priority Mode: `false`
- High Alch: `false`
- Clicks Per Item: `5`
- Click Delay: `70 ms` (range `1–250 ms` between repeated Take attempts)
- Do Breaks: `true`. Set it to `false` to disable GE Looter’s randomized logouts. Starting GE Looter turns off the standalone Microbot BreakHandler and BreakHandler V2 so its own setting controls scheduling.

## Notes

The supplied perimeter is represented with RuneLite `WorldPoint` values. The polygon also explicitly includes each supplied perimeter tile so edge points are not rejected by `Polygon.contains()`.

The looter gives ground loot priority while inventory space exists. High Alchemy runs when the inventory is full or when there is currently no qualifying ground loot, reducing the chance of missing valuable drops.

## Priority Mode behavior

Microbot currently exposes `Microbot.pauseAllScripts` as the shared script-level pause gate. KSP GE Looter uses that gate transactionally: it only resumes it when the looter itself successfully changed the flag from running to paused. This preserves the running script's state instead of disabling/restarting its plugin.

When Priority Mode is enabled, idle High Alchemy is intentionally suppressed after the last qualifying ground item disappears so control is returned immediately to the interrupted script. High Alchemy can still be used during an active loot takeover when inventory space must be created.
