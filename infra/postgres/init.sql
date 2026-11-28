-- Runs once, when the Postgres volume is first created (docker-entrypoint-initdb.d).

-- API clients known to the gateway. The gateway looks clients up by api_key and reads the tier to
-- pick the token bucket capacity and refill rate.
CREATE TABLE clients (
  client_id   TEXT PRIMARY KEY,
  api_key     TEXT NOT NULL UNIQUE,
  tier        TEXT NOT NULL DEFAULT 'free' CHECK (tier IN ('free', 'pro')),
  is_admin    BOOLEAN NOT NULL DEFAULT false,
  -- Ground truth for evaluating sentinel. Never read while scoring.
  is_attacker BOOLEAN NOT NULL DEFAULT false,
  created_at  TIMESTAMPTZ NOT NULL DEFAULT now()
);

-- Clients currently blocked at the gateway. Every gateway node keeps an in-memory copy of this
-- table, kept current by LISTEN/NOTIFY plus periodic resyncs.
CREATE TABLE quarantine (
  client_id  TEXT PRIMARY KEY,
  reason     TEXT NOT NULL,              -- 'anomaly' | 'manual'
  score      DOUBLE PRECISION,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
  expires_at TIMESTAMPTZ NOT NULL
);
