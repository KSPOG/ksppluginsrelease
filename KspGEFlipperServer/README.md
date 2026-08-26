# KSP GE Flipper Server v1.0.0

Clean-room Java 21 backend for the KSP GE Flipper plugin.

## Capabilities

- Caches OSRS Wiki mapping, latest, 5-minute, 1-hour and optional 24-hour market data.
- Stores raw market responses and normalized market observations.
- Builds deterministic market features for spread, momentum, volatility, liquidity, imbalance, median deviation and volume acceleration.
- Produces separate low/high probabilistic forecasts with mean, q25 and q75 points over multiple horizons.
- Validates forecast MAE and IQR coverage as observations arrive.
- Estimates buy/sell fill probability and expected duration, with telemetry-driven duration calibration.
- Generates NORMAL_FLIP, DUMP, BUY_AND_HOLD and POSITION_EXIT candidates.
- Applies current account membership, item restrictions, cash, buy limits, liquidity, risk exposure and GE-slot constraints before ranking.
- Optimizes expected risk-adjusted GP/hour while treating capital and GE slots as scarce resources.
- Returns BUY, SELL, MODIFY_BUY, MODIFY_SELL, ABORT and WAIT actions with hysteresis.
- Protects manual/unattributed GE offers from automatic cancellation.
- Persists accounts, preferences, offer snapshots, recommendations, feature snapshots, executions, positions, position transactions, dump events, outcomes and model metrics.
- Tracks realized and unrealized portfolio P&L.
- Exposes global dump signals over JSON and SSE.
- Supports PostgreSQL for production and JSONL file persistence for zero-infrastructure development.
- Includes an offline fill-aware replay harness and a protobuf contract for a future binary transport adapter.

## Run without PostgreSQL

Requires Java 21.

```bash
mvn package
java -jar target/ksp-ge-flipper-server-1.0.0.jar
```

The default HTTP port is `8181` and file data is stored below `./data`.

## Run with Docker + PostgreSQL

Copy `.env.example` to `.env`, replace the example secrets, then run:

```bash
docker compose up --build -d
```

The compose deployment starts PostgreSQL 17 and the backend on port `8181`.

## Environment

- `KSP_BIND_HOST` default `0.0.0.0`
- `KSP_PORT` default `8181`
- `KSP_API_KEY` optional API key expected in `X-KSP-API-Key`
- `KSP_DB_URL` blank selects file persistence; set a JDBC PostgreSQL URL to enable PostgreSQL
- `KSP_DB_USER` default `ksp`
- `KSP_DB_PASSWORD` default `ksp`
- `KSP_DATA_DIR` default `data`
- `KSP_WIKI_POLL_SECONDS` default `30`
- `KSP_DUMP_POLL_SECONDS` default `15`
- `KSP_MARKET_HISTORY_LIMIT` default `4096`
- `KSP_LATEST_WARN_SECONDS` default `120`
- `KSP_LATEST_REJECT_SECONDS` default `300`

## HTTP API

- `GET /health`
- `POST /v1/account`
- `POST /v1/recommendation`
- `GET /v1/recommendations/{id}`
- `GET|POST /v1/transactions`
- `GET|POST /v1/outcomes`
- `POST /v1/offers`
- `GET /v1/prices/{itemId}?timeframe=30`
- `GET /v1/dumps`
- `GET /v1/events/dumps` (SSE)
- `GET /v1/portfolio?account=...`
- `GET /v1/metrics`

## Replay harness

Input CSV columns:

```text
timestamp,itemId,high,low,highVolume,lowVolume
```

Run:

```bash
java -cp target/classes com.ksp.geflipper.replay.ReplayHarness market.csv 10000000
```

The replay reports trades, realized GP, GP/hour, win rate, maximum drawdown, capital utilization and slot utilization. It uses a fill-probability simulator rather than assuming a trade fills simply because an aggregate Wiki price crossed an offer.

## Model boundary

The forecast, dump, fill, ranking, risk, repricing and portfolio algorithms in this project are independent clean-room implementations. They do not claim to reproduce Flipping Copilot's inaccessible private model, formula, training data or backend source code. Forecast and duration quality must be judged from collected execution outcomes and calibration metrics.
