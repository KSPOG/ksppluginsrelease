# KSP GE Flipper v1.1.0

State-aware Grand Exchange flipping for Microbot with the full recommendation backend embedded directly inside the plugin process.

## Default architecture

`Engine mode` controls where recommendations come from:

- `EMBEDDED` (default): run market history, probabilistic forecasts, portfolio accounting, dump detection, action policy and calibration in-process. No separately hosted server is required.
- `AUTO`: prefer the embedded engine; if embedded startup fails, try the optional remote server, then the legacy local fallback when fallback is enabled.
- `REMOTE`: use `KspGEFlipperServer` as a shared multi-client backend. The legacy `SERVER` enum remains accepted for existing saved configuration and behaves like `REMOTE`.
- `LOCAL`: run the earlier deterministic/self-calibrating local engine only.

The embedded path keeps recommendation logic separate from Microbot execution even though both live in the same JVM:

```text
RuneLite / Microbot
  -> AccountStateCollector
  -> Embedded market/history core
  -> Feature + probabilistic forecast model
  -> Fill/duration model
  -> NORMAL / DUMP / BUY_AND_HOLD / POSITION_EXIT candidates
  -> Portfolio + slot/capital optimizer
  -> BUY / SELL / MODIFY / ABORT / WAIT action policy
  -> MANUAL or AUTO execution adapter
  -> observed GE fills
  -> persistent calibration + portfolio state
```

## Embedded persistence

Embedded state is stored under:

```text
~/.runelite/ksp-ge-flipper/embedded-state.json.gz
```

The snapshot is written atomically and contains bounded state required for continuity across RuneLite restarts:

- liquid-item market history
- forecast snapshots
- recommendations and feature snapshots
- recommendation status
- observed executions
- positions and cost basis
- outcomes
- calibration buckets
- forecast/recommendation metrics

Market history is sampled once per five minutes for items with enough liquidity to be actionable. The default 288 retained points therefore represents about 24 hours of persistent history per retained item without continuously rewriting an unbounded database.

The embedded implementation deliberately uses compressed local persistence rather than SQLite because this repository is loaded as raw Java source and does not bundle an external SQLite JDBC driver. The optional remote server still supports PostgreSQL.

## Account state

The embedded and remote engines consume the same account state:

- account/display key
- current membership/F2P constraints
- detected GE capacity and configured plugin slot cap
- inventory and cached bank contents
- uncollected GE items/proceeds when exposed by Microbot
- available GP
- active offer slot, item, side, price, quantity and fill progress
- first-seen/last-changed timestamps
- suggestion attribution
- blocked/allowed items
- risk, timeframe, sell-only, buy-and-hold, dump, reserved-slot and profit preferences

## Embedded intelligence

The in-process core provides:

- OSRS Wiki `mapping`, `latest`, `5m`, `1h` and optional `24h` ingestion
- persistent rolling market history
- spread, momentum, volatility, volume acceleration, imbalance, liquidity, rolling-median distance and abnormality features
- separate low/high probabilistic forecasts with q25 / mean / q75 output over multiple horizons
- GOOD / LIMITED / STALE / INVALID forecast quality
- forecast MAE and IQR-coverage validation
- fill probability and expected duration estimates
- current GE tax and tax-safe break-even pricing
- current item buy-limit constraints
- NORMAL_FLIP candidates
- independent dump/recovery candidates
- BUY_AND_HOLD candidates
- persistent POSITION_EXIT candidates
- risk-adjusted expected GP/hour and utility ranking
- capital exposure limits
- reserved slots and dump-slot allocation
- manual-offer protection
- BUY / SELL / MODIFY_BUY / MODIFY_SELL / ABORT / WAIT hysteresis
- persistent COPILOT / PERSONAL / UNMATCHED position classification
- stable remaining/realized cost basis across partial sells and later rebuys
- realized and unrealized P&L
- execution attribution
- predicted-vs-actual outcome recording
- bounded item/type calibration for duration, fill, price error and realized profit
- recommendation acceptance / modify / abort metrics
- local dump signal feed for the overlay and panel

## Execution mode

- `MANUAL`: recommendations are displayed but are not translated into GE actions.
- `AUTO`: recommendations are executed through the isolated Microbot adapter.

Automatic MODIFY/ABORT only cancels an offer with plugin suggestion attribution, so unrelated manual GE offers remain protected.

## UI

The overlay shows engine state, account, cash, capital in plugin flips, active buys/sells, current recommendation, expected profit/time/GP-hour/confidence, dump state and explanation.

The RuneLite toolbar panel contains:

- Suggestion
- Forecast
- Portfolio
- Analytics

In `EMBEDDED` mode those tabs read the in-process core directly. In `REMOTE` mode they read the HTTP API.

## Optional remote server

`KspGEFlipperServer/` remains available for shared intelligence across multiple RuneLite clients/computers. It is no longer required for normal operation.

Use remote mode when you explicitly want centralized PostgreSQL persistence or cross-client state. See `KspGEFlipperServer/README.md` for deployment.

## Safety and correctness boundaries

- This remains a clean-room implementation based on public OSRS Wiki data and independently implemented models.
- It does not claim to contain or recover Flipping Copilot's private formulas/model family.
- Wiki prices are aggregate executions rather than a complete order book, so fill/duration values remain predictions.
- Calibration only learns from labelled executions observed by this plugin and is bounded by the configured maximum adjustment.
- Existing unrelated/manual offers are never automatically cancelled.
- `UNMATCHED` sales do not fabricate profit when their acquisition cost is unknown.
