# KSP GE Flipper v1.0.0

State-aware Grand Exchange flipping for Microbot with a full optional backend and a proven local fallback.

## Architecture

`Engine mode` controls where recommendations come from:

- `AUTO`: use `KspGEFlipperServer` when healthy; otherwise start the existing local deterministic/self-calibrating engine.
- `SERVER`: require the backend.
- `LOCAL`: run the v0.3 local engine only.

The server path separates account-state collection, recommendation policy and Microbot execution. The recommendation model therefore does not depend on automation.

`Execution mode` controls what happens to server recommendations:

- `MANUAL`: display recommendations without executing them.
- `AUTO`: translate recommendations through the isolated Microbot execution adapter.

Automatic MODIFY/ABORT is guarded by suggestion attribution and will not cancel an unrelated manual GE offer.

## Plugin state sent to the backend

The plugin synchronizes:

- account/display key
- current world/account membership state
- F2P-only preference
- detected GE capacity and configured plugin slot limit
- inventory and cached bank contents
- uncollected GE items/proceeds when the Microbot GE model exposes them
- available GP
- active offer slot, item, side, price, quantity, fill progress, amount spent/received and local firstSeen/lastChanged timestamps
- suggestion attribution
- blocked and allowed item names
- risk, timeframe, sell-only, buy-and-hold, dump, reserved-slot and profit preferences

## Backend intelligence

The Java 21 server provides:

- OSRS Wiki latest / 5m / 1h market ingestion and history persistence
- feature extraction
- low/high q25/q50-like mean/q75 forecast output across multiple horizons
- forecast quality and confidence
- forecast MAE and IQR coverage validation
- execution/fill probability and duration estimation
- telemetry-driven duration calibration
- execution-aware entry and exit price search
- GE tax handling
- current buy-limit constraints
- NORMAL_FLIP, DUMP, BUY_AND_HOLD and POSITION_EXIT candidates
- risk-adjusted GP/hour ranking
- capital exposure and GE-slot optimization
- reserved manual slots and short-timeframe dump-slot allocation
- bank-aware/portfolio-aware exits
- BUY / SELL / MODIFY_BUY / MODIFY_SELL / ABORT / WAIT policy with hysteresis
- persistent recommendations and feature snapshots
- transaction attribution and recommendation status
- persistent portfolio positions, realized/unrealized profit and tax
- recommendation outcome calibration
- global dump detection and SSE stream
- PostgreSQL or local JSONL persistence
- offline fill-aware replay harness
- JSON v1 API plus a protobuf contract for future transport

## UI

The overlay shows engine/backend status, account, cash, capital in plugin flips, active buys/sells, current recommendation, expected profit/time/GP-hour/confidence, dump-stream status and model explanation.

The RuneLite toolbar panel provides separate Suggestion, Forecast, Portfolio and Analytics tabs. Forecasts render historical high/low series plus server q25-q75 uncertainty bands.

## Backend setup

See `KspGEFlipperServer/README.md` in this repository. The default URL is:

```text
http://127.0.0.1:8181
```

An optional server API key can be configured on both sides.

## Safety and correctness boundaries

- The backend is a clean-room implementation based on public behavior/data boundaries and our own models.
- It does not claim to contain Flipping Copilot's private algorithm.
- OSRS Wiki prices are aggregate executions, not a complete order book. Expected fills and duration remain estimates and are calibrated against actual plugin outcomes.
- MANUAL mode is available when recommendations should not be automatically executed.
- Existing unrelated/manual GE offers remain protected from automatic cancellation.
- Server persistence enables cross-session/cross-device state only when all clients are pointed at the same backend and use the same account key.
