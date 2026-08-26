# KSP GE Flipper v0.3.0

Automatic, execution-aware Grand Exchange flipper for Microbot with persistent self-calibration.

## v0.3.0 self-calibrating execution model

The deterministic v0.2.0 market model is still the baseline. v0.3.0 adds a conservative feedback layer that compares predictions with real GE outcomes and gradually corrects systematic execution errors.

For every accepted recommendation the plugin now records:

- predicted buy and sell price
- predicted quantity
- predicted profit
- predicted total duration
- predicted execution probability
- predicted confidence
- actual average buy fill price
- actual average sell fill price when exposed by the GE offer data
- actual realized profit
- actual end-to-end duration
- modification/reprice count
- completed vs aborted outcome

Finished outcomes are aggregated into EWMA calibration buckets for both the overall strategy type (`NORMAL` / `DUMP`) and individual items. Item-specific learning is blended with the larger global sample so a single item cannot overfit the engine quickly.

### What learning changes

After the configured warm-up sample count, the learned model can adjust:

- expected duration
- execution/fill probability
- confidence
- expected realized profit
- liquidity sizing (conservatively, through the execution correction)

These corrections feed directly back into candidate GP/hour and utility ranking.

Learning is bounded. With the default `Max learned adjustment % = 35`, no learned duration/execution/profit factor can move more than 35% away from the deterministic baseline. This prevents a small or noisy sample from destabilizing trading decisions.

### Default learning settings

- Self-calibration: enabled
- Warm-up flips: 8 finished outcomes
- Learning rate: 0.12 EWMA
- Maximum learned adjustment: 35%

The overlay shows whether the model is `Learning`, `Active`, or `Disabled`, along with sample count and the current duration, execution, profit, and modification-rate calibration metrics.

## Persistence

Calibration data is stored locally at:

```text
~/.runelite/ksp-ge-flipper-calibration.json
```

On Windows this normally resolves beneath the current user's profile directory. The file is written atomically where the filesystem supports it.

Only finished calibration statistics are persisted. Active GE offers are still managed by the current plugin session, so disabling the plugin while it owns active offers is still not recommended.

## Partial-fill accounting fix

v0.3.0 also fixes sell accounting across cancel/reprice cycles. GE offer filled quantity is per offer, while the flip tracks lifetime sold quantity. The plugin now keeps separate per-offer accounted quantity/value fields, preventing fills on a newly relisted sell from being ignored because an earlier offer already had a larger lifetime sold count.

Where `GrandExchangeOfferDetails.getSpent()` exposes cumulative exchanged value, the plugin uses the delta to estimate the actual average fill price. If it is unavailable or zero, it safely falls back to the listed sell price.

## Existing decision engine

The engine continues to:

- use OSRS Wiki `latest`, `5m`, `1h`, and `mapping` data
- filter by membership, buy limits, quote freshness, liquidity, ROI, blocked items, and whitelist
- apply current GE tax handling used by the plugin
- forecast from latest/5m/1h agreement, trend, spread stability, freshness, and volume
- search multiple prices inside the spread
- estimate fill probability and duration
- rank by execution-adjusted expected GP/hour and risk-adjusted utility
- size by cash, buy limit, risk profile, liquidity, timeframe, and capital cap
- support LOW / MEDIUM / HIGH risk
- support reserved GE slots
- support optional dump/recovery opportunities
- reevaluate stale offers with WAIT / MODIFY / ABORT-style hysteresis
- keep sell repricing above tax-adjusted break-even
- emit structured `KSP_GE` telemetry

## Model boundary

This remains an independent clean-room implementation. The learned factors are derived only from this plugin's own observed outcomes. They do not reproduce or claim to recover Flipping Copilot's private server model.

The feedback loop is deliberately calibration-first rather than unrestricted online machine learning: it learns systematic prediction error while keeping the underlying deterministic market logic inspectable and bounded.

## Install

Copy the `KspGEFlipper` folder into your Microbot-Hub source tree under the matching `net/runelite/client/plugins/microbot/` package path, or use it with the KSP source-loader workflow.

## Notes

- Buy-limit tracking remains session-local.
- Existing/manual GE offers are left alone.
- Do not disable the plugin while it has active flips unless you intend to manage those offers manually afterward.
- Dump mode remains higher risk and disabled by default.
- A future backend can centralize calibration across accounts/devices, but v0.3.0 intentionally keeps learning local and auditable.
