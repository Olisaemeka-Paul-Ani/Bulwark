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

-- Tell every listening gateway about each quarantine change. NOTIFY is only delivered to
-- sessions listening at commit time, so gateways also resync from the table itself (see
-- QuarantineListener); the notification is the fast path, not the source of truth.
CREATE FUNCTION notify_quarantine_changed() RETURNS trigger AS $$
DECLARE
  r quarantine;
BEGIN
  IF TG_OP = 'DELETE' THEN
    r := OLD;
  ELSE
    r := NEW;
  END IF;
  PERFORM pg_notify('quarantine_changed', json_build_object(
    'op', TG_OP,
    'clientId', r.client_id,
    'reason', r.reason,
    'score', r.score,
    'expiresAtMs', (extract(epoch FROM r.expires_at) * 1000)::bigint
  )::text);
  RETURN NULL;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER quarantine_changed
AFTER INSERT OR UPDATE OR DELETE ON quarantine
FOR EACH ROW EXECUTE FUNCTION notify_quarantine_changed();
