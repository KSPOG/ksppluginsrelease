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
- Before the first GE ingredient offer is placed, the plugin calculates the **complete missing-input cost** for the selected number of bars and funds that whole plan into the stackable inventory coin balance.
- A later ingredient such as Coal is no longer independently resized against a newly observed cash snapshot after earlier ingredients have already been purchased.
- Each ingredient purchase must fit the remaining pre-funded plan. If observed cash/bank state drifts, the plugin stays in `RESTOCKING` and recalculates the plan instead of entering `WAITING_FOR_PROFIT` with a misleading ingredient-level insufficient-coins message.
- Partial GE fills stop the current purchase chain and trigger a fresh plan using the materials actually collected plus the refunded cash. This prevents buying a full amount of Coal/runes against only a partially filled ore purchase.
- Coins already in the inventory are kept there while the plugin remains in `RESTOCKING`, including when `Bank whole inventory` is enabled.
- **All available Nature runes are withdrawn as items and kept in one persistent inventory stack.** Bank and inventory Nature runes are treated as one supply pool for affordability and craftable-bar calculations.
- Restocking uses only free GE slots.
- The plugin does **not** intentionally cancel unrelated GE offers.
- Buy/sell offers time out, abort, and collect partial fills rather than waiting forever.

## Grand Exchange entry pacing

Buy offers are entered as a staged transaction instead of firing the entire GE sequence immediately:

1. Open and settle the GE interface.
2. Open a free buy slot and wait for the item-search prompt.
3. Select the exact item and wait for the offer controls.
4. Enter the requested buy price and verify the GE price value actually changed.
5. Enter the requested quantity and verify the GE quantity value actually changed.
6. Only then press Confirm.

Price and quantity entry are retried when the GE does not register the requested value, which prevents default-price/default-quantity offers caused by UI timing.

## Output safety

The plugin tracks bars it created during the current session. When `Auto-sell output` is enabled, selling is bounded by both the tracked session output and a protected bank baseline captured before that recipe begins processing. This is designed to keep pre-existing banked bars out of automated sales, including after partial GE fills.

Produced bars are explicitly withdrawn with the bank in **note mode** before they are offered on the Grand Exchange. The plugin waits for the note-mode switch to settle and verifies that the bar stack appeared in inventory before beginning the sale.

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

`Bank whole inventory` is enabled by default so the plugin has a predictable processing inventory. The persistent Nature-rune stack is exempt from that banking cleanup.

During `RESTOCKING`, the active coin stack is also deliberately preserved so consecutive GE offers can reuse one stackable cash balance without unnecessary bank round-trips. Outside restocking, coins may be returned to the bank while Nature runes remain in inventory.

Disable `Bank whole inventory` if you want unrelated inventory items left untouched. Doing so may reduce or prevent valid Superheat batches when too few inventory slots remain.

The plugin checks profitability again while running. If the active recipe falls below the configured profit gate, it stops initiating new casts for that recipe and rescans instead of deliberately continuing an unprofitable route.

## Install in Microbot-Hub

Copy the Java files into:

`src/main/java/net/runelite/client/plugins/microbot/kspsmartsuperheat/`

Then build your plugin/client using your normal Microbot-Hub Gradle workflow.

For a single-plugin build in setups that support `-PpluginList`, use the plugin class name configured by your hub/build scripts.

## Notes

Market profit is an estimate, not a guarantee. Prices can move between the Wiki quote, the GE offer, and the eventual fill. The conservative markup/discount controls are there to give the profit filter a safety buffer.
