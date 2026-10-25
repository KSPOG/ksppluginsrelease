# KSP Jewelry Crafter v0.1.33

Microbot jewellery crafter with live profitability filtering and automatic GE recycling.

## Core rules

- Detects current Crafting level.
- Detects whether the account is a member.
- `membersOnly` is a hard recipe eligibility rule:
  - F2P account: members-only recipes are never evaluated/selected.
  - Members account: both F2P and members recipes can be selected.
- Every eligible recipe is priced from the OSRS Wiki real-time price API.
- Both the input buy side and output sell side must have traded within the last 15 minutes; stale/illiquid quotes are rejected.
- Uses conservative economics:
  - input = instant-buy/high price + configured buy markup;
  - output = instant-sell/low price - configured sell discount;
  - estimated 2% GE tax is deducted;
  - minimum GP/item and ROI are hard gates.
- Profit is checked again immediately before every furnace inventory.
- When inputs run out, crafted output is sold at the GE and the proceeds are reused to buy the next profitable recipe's inputs.
- Missing moulds are bought once as reusable tooling.
- GE offers time out, abort, collect to bank, and retry with progressively more aggressive prices.
- Uses a configurable furnace and its matching bank for crafting, and the Grand Exchange for liquidation/restocking.

## Furnace selection (v0.1.33)

In the Jewellery Crafter plugin configuration, use the **Furnace** dropdown.
The options are **Edgeville** (default), **Falador**, **Lumbridge** and **Al Kharid**.

| Furnace | Configured furnace search area (x, y, width, height, plane) | Banking destination |
|---|---|---|
| Edgeville | (3106, 3496, 8, 8, 0) | Edgeville bank |
| Falador | (2970, 3368, 7, 9, 0) | Falador west bank |
| Lumbridge | (3221, 3252, 9, 6, 0) | Lumbridge Castle top-floor bank (plane 2) |
| Al Kharid | (3271, 3184, 9, 5, 0) | Al Kharid bank |

The three newly added furnace regions preserve the supplied area
coordinates. The RuneLite `WorldArea` constructor orders its parameters
as `(x, y, width, height, plane)`, not `(x, y, plane, width, height)`.

Selecting a different furnace changes furnace lookup, the local bank used for
the next inventory, and the trip back from the Grand Exchange. It does not
change Grand Exchange pricing or purchasing behavior.

Location changes are saved in RuneLite config and applied **between batches**;
the script will not abruptly redirect a running jewellery batch.
Lumbridge banking uses the bank upstairs; the walker must navigate the
castle stairs. If pathfinding cannot access the upstairs bank, the
plugin must be tested/recovered in the live client.

The in-game overlay shows the currently active furnace, which may briefly
differ from the dropdown while the current batch finishes.

## Recipe coverage

- F2P: plain gold ring/necklace/amulet plus sapphire, emerald, ruby and diamond rings/necklaces/amulets at the appropriate Crafting levels.
- Members: all of the above plus bracelets, opal/jade/topaz silver jewellery, dragonstone, onyx and zenyte jewellery.

## Installation

Place these Java files in:

`runelite-client/src/main/java/net/runelite/client/plugins/microbot/kspjewelrycrafter/`

Then add/build the plugin using the same Microbot-Hub workflow as your other KSP plugins.

## Notes

The script intentionally refuses to craft when a current two-sided market quote cannot be obtained. This is safer than assuming stale or missing prices are profitable.


## v0.1.1 - Informative overlay
- Expanded the overlay with state/runtime/account/Crafting level.
- Shows actual Crafting XP gained and XP/hour.
- Shows active recipe requirements, access type, inputs and mould.
- Shows conservative live quote breakdown: input cost, sell value, GE tax, net profit/item, ROI and profit gate.
- Shows batch/session statistics and estimated net profit/hour.
- Shows active GE offer, retry count and restock queue progress.
- Profit values are explicitly estimated rather than falsely labelled as realized P&L.
