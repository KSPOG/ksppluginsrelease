CREATE TABLE IF NOT EXISTS users (
  user_key TEXT PRIMARY KEY,
  created_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);

CREATE TABLE IF NOT EXISTS accounts (
  account_key TEXT PRIMARY KEY,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  payload JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS account_preferences (
  account_key TEXT PRIMARY KEY,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  payload JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS items (
  item_id INTEGER PRIMARY KEY,
  name TEXT NOT NULL,
  members BOOLEAN NOT NULL,
  buy_limit INTEGER NOT NULL,
  tradeable BOOLEAN NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  payload JSONB NOT NULL
);

CREATE TABLE IF NOT EXISTS raw_market_responses (
  id BIGSERIAL PRIMARY KEY,
  endpoint TEXT NOT NULL,
  observed_at TIMESTAMPTZ NOT NULL,
  payload JSONB NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_raw_market_endpoint_time ON raw_market_responses(endpoint, observed_at DESC);

CREATE TABLE IF NOT EXISTS market_ticks (
  id BIGSERIAL PRIMARY KEY,
  item_id INTEGER NOT NULL,
  observed_at TIMESTAMPTZ NOT NULL,
  high_price BIGINT NOT NULL,
  low_price BIGINT NOT NULL,
  high_volume BIGINT NOT NULL,
  low_volume BIGINT NOT NULL,
  resolution TEXT NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_market_ticks_item_time ON market_ticks(item_id, observed_at DESC);

CREATE TABLE IF NOT EXISTS market_5m (
  item_id INTEGER NOT NULL,
  observed_at TIMESTAMPTZ NOT NULL,
  high_price BIGINT NOT NULL,
  low_price BIGINT NOT NULL,
  high_volume BIGINT NOT NULL,
  low_volume BIGINT NOT NULL,
  PRIMARY KEY(item_id, observed_at)
);
CREATE INDEX IF NOT EXISTS idx_market_5m_item_time ON market_5m(item_id, observed_at DESC);

CREATE TABLE IF NOT EXISTS market_1h (
  item_id INTEGER NOT NULL,
  observed_at TIMESTAMPTZ NOT NULL,
  high_price BIGINT NOT NULL,
  low_price BIGINT NOT NULL,
  high_volume BIGINT NOT NULL,
  low_volume BIGINT NOT NULL,
  PRIMARY KEY(item_id, observed_at)
);
CREATE INDEX IF NOT EXISTS idx_market_1h_item_time ON market_1h(item_id, observed_at DESC);

CREATE TABLE IF NOT EXISTS forecasts (
  id BIGSERIAL PRIMARY KEY,
  item_id INTEGER NOT NULL,
  generated_at TIMESTAMPTZ NOT NULL,
  quality TEXT NOT NULL,
  confidence DOUBLE PRECISION NOT NULL,
  payload JSONB NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_forecasts_item_time ON forecasts(item_id, generated_at DESC);

CREATE TABLE IF NOT EXISTS ge_offer_snapshots (
  id BIGSERIAL PRIMARY KEY,
  account_key TEXT NOT NULL,
  observed_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  payload JSONB NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_offer_snapshots_account_time ON ge_offer_snapshots(account_key, observed_at DESC);

CREATE TABLE IF NOT EXISTS recommendations (
  id UUID PRIMARY KEY,
  account_key TEXT NOT NULL,
  created_at TIMESTAMPTZ NOT NULL,
  action TEXT NOT NULL,
  item_id INTEGER NOT NULL,
  price BIGINT NOT NULL,
  quantity INTEGER NOT NULL,
  expected_profit BIGINT NOT NULL,
  expected_duration_seconds BIGINT NOT NULL,
  confidence DOUBLE PRECISION NOT NULL,
  risk_level TEXT,
  timeframe_minutes INTEGER,
  is_hold BOOLEAN NOT NULL,
  status TEXT NOT NULL DEFAULT 'ISSUED',
  payload JSONB NOT NULL
);
ALTER TABLE recommendations ADD COLUMN IF NOT EXISTS status TEXT NOT NULL DEFAULT 'ISSUED';
CREATE INDEX IF NOT EXISTS idx_recommendations_account_time ON recommendations(account_key, created_at DESC);

CREATE TABLE IF NOT EXISTS recommendation_features (
  recommendation_id UUID NOT NULL REFERENCES recommendations(id) ON DELETE CASCADE,
  feature_name TEXT NOT NULL,
  feature_value DOUBLE PRECISION NOT NULL,
  PRIMARY KEY (recommendation_id, feature_name)
);

CREATE TABLE IF NOT EXISTS transactions (
  id UUID PRIMARY KEY,
  account_key TEXT NOT NULL,
  item_id INTEGER NOT NULL,
  side TEXT NOT NULL,
  price BIGINT NOT NULL,
  quantity INTEGER NOT NULL,
  amount_spent BIGINT NOT NULL,
  executed_at TIMESTAMPTZ NOT NULL,
  suggestion_id UUID,
  recommendation_price_used BOOLEAN NOT NULL,
  recommendation_originated_trade BOOLEAN NOT NULL,
  payload JSONB NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_transactions_account_time ON transactions(account_key, executed_at DESC);

CREATE TABLE IF NOT EXISTS positions (
  id UUID PRIMARY KEY,
  account_key TEXT NOT NULL,
  item_id INTEGER NOT NULL,
  status TEXT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW(),
  payload JSONB NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_positions_account ON positions(account_key, status);

CREATE TABLE IF NOT EXISTS position_transactions (
  position_id UUID NOT NULL REFERENCES positions(id) ON DELETE CASCADE,
  transaction_id UUID NOT NULL REFERENCES transactions(id) ON DELETE CASCADE,
  PRIMARY KEY(position_id, transaction_id)
);

CREATE TABLE IF NOT EXISTS dump_events (
  id UUID PRIMARY KEY,
  item_id INTEGER NOT NULL,
  detected_at TIMESTAMPTZ NOT NULL,
  severity DOUBLE PRECISION NOT NULL,
  payload JSONB NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_dump_events_time ON dump_events(detected_at DESC);

CREATE TABLE IF NOT EXISTS recommendation_outcomes (
  recommendation_id UUID PRIMARY KEY,
  account_key TEXT NOT NULL,
  item_id INTEGER NOT NULL,
  recorded_at TIMESTAMPTZ NOT NULL,
  payload JSONB NOT NULL
);
CREATE INDEX IF NOT EXISTS idx_outcomes_time ON recommendation_outcomes(recorded_at DESC);

CREATE TABLE IF NOT EXISTS model_metrics (
  metric_key TEXT PRIMARY KEY,
  metric_value DOUBLE PRECISION NOT NULL,
  sample_count BIGINT NOT NULL,
  updated_at TIMESTAMPTZ NOT NULL DEFAULT NOW()
);
