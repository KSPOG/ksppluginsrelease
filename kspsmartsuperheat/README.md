# KSP Smart Superheat

A Microbot plugin that checks the bank before selecting a Superheat Item route, consumes complete input batches you already own, liquidates supported bar stock before committing fresh cash, and auto-restocks profitable recipes through the Grand Exchange.

## Supported recipes

- Bronze bar
- Iron bar
- Silver bar
- Lead bar (members)
- Steel bar
- Gold bar
- Mithril bar
- Adamantite bar
- Cupronickel bar (members)
- Runite bar

The plugin filters recipes by your real Smithing level and requires 43 Magic.
Members-only recipes are considered only when the account has membership and is currently on a members world.

## Bank-aware route selection

Smart Superheat opens the bank before its initial market decision so the route selector has a current view of existing ores, runes and bars.

Complete Superheat batches that are already owned are used before the plugin commits cash to a new restock. This avoids the old startup behavior where the plugin could immediately report `No profitable recipe` without first considering usable bank stock.

The configured profitability gates apply to **new input purchases**. If the bank already contains a complete batch, the plugin may consume that stock even when the current replacement-cost quote has moved below the purchase gate. Once the existing inputs are exhausted, the normal profitability checks are applied again before another GE buy is allowed.

## Profit model

The market scanner uses the OSRS Wiki real-time price endpoint. It includes the
tradeable Sailing-era Lead and Cupronickel Superheat recipes as well as the classic bars.

For every bar it estimates:

`output instant-sell offer - GE tax - ore costs - Nature rune - Fire runes`

If a recognised fire-providing staff is equipped, Fire rune cost is treated as zero.

The configured GE buy markup and sell discount are included before a recipe is approved for a **new restock**. New purchases must satisfy:

- Minimum GP profit per bar
- Minimum ROI
- Optional minimum projected GP/hour

Projected GP/hour is used to rank routes. It accounts for the different ore/coal inventory ratios and the configured banking-overhead estimate.

## Smart restocking

- Banked complete input batches are consumed before new inputs are purchased.
- Existing banked ores/runes are included when calculating how many complete bars can already be processed.
- `Cash reserve` is never intentionally allocated to a new restock plan.
- `Max spend %` limits how much spendable cash one restock may commit.
- Before any new input purchase, the selected recipe must still satisfy the configured profit, ROI and projected-GP/hour gates.
- If supported output bars already exist, `Auto-sell output` liquidates them before a new input restock is started.
- Saleable output includes both bars made during the current session and supported bars that were already in the bank when the plugin started.
- Existing output can still be liquidated when no recipe currently qualifies for a new profitable restock.
- All available Nature runes are treated as part of the usable supply pool.
- Restocking uses only free GE slots.
- The plugin does **not** intentionally cancel unrelated GE offers.
- Buy/sell offers are repriced by the existing GE retry logic instead of blindly placing unrelated replacement offers.

## Output liquidation

When `Auto-sell output` is enabled, supported bar stock is treated as working capital rather than protected historical stock.

Before buying another input batch the plugin scans the bank for supported outputs. It sells the selected recipe's bars first when present, then continues through other supported bar types until there is no saleable output stock left. The bank quantity observed at sale time is authoritative, so pre-existing bars are intentionally included.

Bars are withdrawn with the bank in **note mode** and verified in the inventory before the Grand Exchange sale is placed.

Output liquidation uses the bar's live sell price directly. It does not require a complete ore/rune recipe quote merely to sell bars that are already owned.

Disable `Auto-sell output` if you do not want existing supported bars liquidated automatically.

## Grand Exchange entry and pricing

Input buy prices use the configured markup over the current market high. Output sell prices use the configured discount from the current market low. The existing timeout/reprice flow can adjust an active offer when it does not fill at the initial price.

Only free GE slots are used for new input buys. Existing unrelated offers are left alone.

## Overlay

The overlay shows:

- State and current action
- Selected recipe
- Fire-rune source/cost mode
- Profit per bar
- ROI
- Projected GP/hour
- Input cost, output price and GE tax
- Batch capacity
- Bars made / bars per hour
- Estimated session profit / profit per hour
- Saleable output stock
- Craftable bank stock
- Spendable cash
- Magic XP and Smithing XP
- Runtime

## Important behavior

`Bank whole inventory` is enabled by default so the plugin has a predictable processing inventory. The persistent Nature-rune stack is exempt from that banking cleanup.

Disable `Bank whole inventory` if you want unrelated inventory items left untouched. Doing so may reduce or prevent valid Superheat batches when too few inventory slots remain.

A falling market margin does not force the plugin to abandon a complete input batch that is already owned. It does prevent the plugin from purchasing another batch once the existing stock is exhausted.

If live market data needed for route selection is unavailable, the status reports the price-data problem instead of presenting it as a confirmed lack of profitable recipes.

## Install in Microbot-Hub

Copy the Java files into:

`src/main/java/net/runelite/client/plugins/microbot/kspsmartsuperheat/`

Then build your plugin/client using your normal Microbot-Hub Gradle workflow.

For a single-plugin build in setups that support `-PpluginList`, use the plugin class name configured by your hub/build scripts.

## Notes

Market profit is an estimate, not a guarantee. Prices can move between the Wiki quote, the GE offer, and the eventual fill. The markup/discount controls are there to give the new-purchase profit filter a safety buffer.
