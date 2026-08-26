# KSP GE Flipper v0.1.1

Compact automatic GE flipper for Microbot.

## What it does
- Scans the OSRS Wiki real-time market automatically.
- Filters by membership, quote freshness, 1-hour two-sided volume, net ROI and expected trade profit.
- Uses the current 2% GE seller tax with the 5m GP per-item cap and known tax-exempt utility items.
- Sizes each flip by free cash, configured per-flip capital cap, current 4-hour buy-limit usage and conservative liquidity.
- Uses free GE slots only and does not cancel unrelated offers.
- Collects completed buys, lists the outputs for sale, and recycles the capital.
- Cancels/reprices its own stale offers.
- Will not intentionally reprice a sale below a tax-adjusted break-even price.
- Supports an optional comma-separated whitelist; blank means scan the full market.

## Install
Copy the `KspGEFlipper` folder into your Microbot-Hub source tree under the matching `net/runelite/client/plugins/microbot/` package path, or use it with your KSP source loader workflow.

## Notes
- Profit shown in the overlay is conservative booked profit based on the plugin's listed sell prices and actual recorded buy spend. Better-than-listed fills can make the in-game result higher.
- Buy-limit tracking is session-local in v0.1.1. Restarting the plugin resets its local 4-hour usage tracker.
- Existing/manual GE offers are left alone. Do not disable the plugin while it has active flips unless you intend to manage those offers manually afterward.


## Overlay
The live overlay shows status, account type, runtime, cash, capital committed to flips, buy/sell flip counts, the current best market candidate, candidate buy/sell prices, quantity, net ROI, estimated profit, two-sided hourly volume, realized profit, profit/hour, completed flips, and market items loaded.
