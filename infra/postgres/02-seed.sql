-- Local/demo clients. These keys are fixed, publicly known test values for the docker-compose stack
-- and the k6 scripts; they are not secrets and must never be used outside local development.

-- ~50 well-behaved clients driven by loadtest/normal.js. Every fifth one is on the pro tier.
INSERT INTO clients (client_id, api_key, tier)
SELECT 'norm-' || lpad(n::text, 3, '0'),
       'bk_norm_' || lpad(n::text, 3, '0'),
       CASE WHEN n % 5 = 0 THEN 'pro' ELSE 'free' END
FROM generate_series(1, 50) AS n;

-- Attacker clients driven by loadtest/attacks.js. The attack type is part of the id so eval can
-- report precision/recall per type; is_attacker is ground truth and never read while scoring.
INSERT INTO clients (client_id, api_key, tier, is_attacker)
SELECT 'atk-' || attack || '-' || lpad(n::text, 2, '0'),
       'bk_atk_' || attack || '_' || lpad(n::text, 2, '0'),
       'free',
       true
FROM unnest(ARRAY['credstuff', 'scraper', 'burst', 'enum', 'lowslow']) AS attack,
     generate_series(1, 5) AS n;

-- Admin client for /admin/** endpoints.
INSERT INTO clients (client_id, api_key, tier, is_admin)
VALUES ('admin', 'bk_admin_dev', 'pro', true);
