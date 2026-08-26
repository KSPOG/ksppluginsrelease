# KSP GE Flipper v0.2.0

Automatic, execution-aware Grand Exchange flipper for Microbot.

## v0.2.0 decision engine

This release upgrades the old raw-profit market scanner into a clean-room deterministic recommendation engine. It does **not** claim to reproduce any private Flipping Copilot server formula or private model.

The engine now:

- Uses OSRS Wiki `latest`, `5m`, `1h`, and `mapping` market data.
- Filters by membership, current buy limits, quote freshness, matched volume, ROI, blocked items, and configured whitelist.
- Uses the current 2% GE seller tax, 5M gp per-item cap, tax-free sales below 50 gp, and known exempt utility items.
- Builds a lightweight forecast from latest/5m/1h price agreement, short-horizon trend, spread stability, quote freshness, and volume.
- Produces a deterministic confidence score and uncertainty estimate. These are local heuristics, not a copied ML model.
- Searches multiple prices inside the live spread rather than always applying one fixed undercut.
- Estimates fill probability and expected trade duration from quantity, matched hourly flow, price aggressiveness, uncertainty, and confidence.
- Ranks candidates by execution-adjusted expected GP/hour rather than raw total profit alone.
- Sizes quantities by available cash, GE buy limit, risk profile, configured capital cap, timeframe, and a conservative share of observed liquidity.
- Supports LOW / MEDIUM / HIGH risk profiles and a configurable strategy timeframe.
- Supports reserved GE slots and keeps unrelated/manual offers untouched.
- Supports a blocked-item list in addition to the existing optional whitelist.
- Adds an optional short-horizon dump/recovery detector. It is disabled by default and uses its own minimum-profit threshold.
- Reevaluates stale offers with hysteresis: WAIT when there is no material reason to churn, MODIFY when fresh utility materially improves, and ABORT stale unfilled buys when the opportunity has materially deteriorated.
- Fixes stale-buy relisting so a cancelled unfilled buy can actually be placed again at its improved price instead of simply disappearing.
- Keeps sell repricing tax-safe by never deliberately listing below calculated break-even.
- Emits structured `KSP_GE` telemetry for recommendations, fills, reprices, aborts, and completed flips so later calibration can compare predictions with real outcomes.

## Overlay

The overlay shows:

- status and account type
- runtime, cash, and capital committed
- active buy/sell flip counts
- candidate item and candidate type
- selected buy and sell prices
- quantity and net ROI
- estimated profit
- estimated duration
- execution-adjusted expected GP/hour
- deterministic confidence
- matched 1h volume
- realized profit and realized profit/hour
- completed flips and market item count

## Important model boundaries

The confidence, duration, fill-probability, dump-recovery, and utility formulas in v0.2.0 are independent heuristics. The OSRS Wiki aggregate price feeds are not a full order book, so expected duration and GP/hour are estimates and must be calibrated against real fills before being treated as predictive statistics.

The plugin logs recommendation/execution telemetry specifically to make that calibration possible in a later release.

## Install

Copy the `KspGEFlipper` folder into your Microbot-Hub source tree under the matching `net/runelite/client/plugins/microbot/` package path, or use it with your KSP source loader workflow.

## Notes

- Buy-limit tracking is still session-local. Restarting the plugin resets its local 4-hour usage tracker.
- Existing/manual GE offers are left alone.
- Do not disable the plugin while it has active flips unless you intend to manage those offers manually afterward.
- Dump mode is higher risk and is deliberately disabled by default.
- This release remains local-only; a future backend can persist market history, portfolios, recommendation snapshots, and execution outcomes without changing the core clean-room separation between market data, forecasting, execution modeling, and account-state-aware action policy.
