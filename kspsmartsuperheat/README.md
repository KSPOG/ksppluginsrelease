# KSP Smart Superheat

A Microbot plugin that automatically chooses a **currently profitable Superheat Item recipe**, processes it, banks the bars, sells bars created by the current plugin session when cash is needed, and auto-restocks the required ores/runes through the Grand Exchange.

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

## Profit model

The market scanner uses the OSRS Wiki real-time price endpoint. It includes the
tradeable Sailing-era Lead and Cupronickel Superheat recipes as well as the classic bars.

For every bar it estimates:

`output instant-sell offer - GE tax - ore costs - Nature rune - Fire runes`

If a recognised fire-providing staff is equipped, Fire rune cost is treated as zero.

The configured GE buy markup and sell discount are included **before** a recipe is considered profitable. Recipes must also satisfy:

- Minimum GP profit per bar
- Minimum ROI
- Optional minimum projected GP/hour

Projected GP/hour is used to rank otherwise-profitable recipes. It accounts for the different ore/coal inventory ratios and the configured banking-overhead estimate.

## Smart restocking

- Restock quantity is calculated from the coins currently available to the plugin rather than a fixed bar target.
- Existing banked ores/runes are included when calculating how many complete bars can be funded, so the plugin only budgets for missing ingredients.
- `Cash reserve` is never intentionally allocated to a new restock plan.
- `Max spend %` limits how much spendable cash one restock may commit.
- Every individual GE buy is capped by both the remaining restock budget and the coins currently available before the offer is placed.
- Restocking uses only free GE slots.
- The plugin does **not** intentionally cancel unrelated GE offers.
- Buy/sell offers time out, abort, and collect partial fills rather than waiting forever.

## Output safety

The plugin tracks bars it created during the current session. When `Auto-sell output` is enabled, selling is bounded by both the tracked session output and a protected bank baseline captured before that recipe begins processing. This is designed to keep pre-existing banked bars out of automated sales, including after partial GE fills.

Because session tracking and protected baselines reset when the plugin/client restarts, previously produced bars are treated as pre-existing stock on the next run rather than automatically sold.

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
- Unsold session bars
- Craftable bank stock
- Spendable cash
- Magic XP and Smithing XP
- Runtime

## Important behavior

`Bank whole inventory` is enabled by default so the plugin has a predictable 28-slot processing inventory.

Disable it if you want unrelated inventory items left untouched. Doing so may reduce or prevent valid Superheat batches when too few inventory slots remain.

The plugin checks profitability again while running. If the active recipe falls below the configured profit gate, it stops initiating new casts for that recipe and rescans instead of deliberately continuing an unprofitable route.

## Install in Microbot-Hub

Copy the Java files into:

`src/main/java/net/runelite/client/plugins/microbot/kspsmartsuperheat/`

Then build your plugin/client using your normal Microbot-Hub Gradle workflow.

For a single-plugin build in setups that support `-PpluginList`, use the plugin class name configured by your hub/build scripts.

## Notes

Market profit is an estimate, not a guarantee. Prices can move between the Wiki quote, the GE offer, and the eventual fill. The conservative markup/discount controls are there to give the profit filter a safety buffer.
