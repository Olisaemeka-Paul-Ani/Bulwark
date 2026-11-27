-- Token bucket: lazy refill + take one token, atomically.
--
-- Redis runs a script to completion without interleaving other commands, so every gateway node
-- sharing this Redis sees a consistent read-modify-write: two nodes can never both spend the
-- last token. The arithmetic mirrors dev.bulwark.gateway.ratelimit.TokenBucket; keep them in sync.
--
-- KEYS[1]  bucket hash, e.g. bucket:{clientId}  (fields: tokens, lastRefillMs)
-- ARGV[1]  capacity (max burst)
-- ARGV[2]  refill rate, tokens per second
--
-- Returns {allowed (1|0), remaining whole tokens, retryAfterMs (0 when allowed)}

local capacity = tonumber(ARGV[1])
local rate = tonumber(ARGV[2])

-- Use Redis' clock rather than the caller's, so nodes with skewed clocks share one timeline.
local time = redis.call('TIME')
local now = tonumber(time[1]) * 1000 + math.floor(tonumber(time[2]) / 1000)

local bucket = redis.call('HMGET', KEYS[1], 'tokens', 'lastRefillMs')
local tokens = tonumber(bucket[1])
local last = tonumber(bucket[2])

if tokens == nil or last == nil then
  -- First request (or the key expired): start with a full bucket.
  tokens = capacity
  last = now
else
  local elapsed = math.max(0, now - last)
  tokens = math.min(capacity, tokens + elapsed * rate / 1000)
  last = math.max(last, now)
end

local allowed = 0
local retryAfterMs = 0
if tokens >= 1 then
  tokens = tokens - 1
  allowed = 1
else
  retryAfterMs = math.ceil((1 - tokens) * 1000 / rate)
end

redis.call('HSET', KEYS[1], 'tokens', tostring(tokens), 'lastRefillMs', tostring(last))

-- An idle bucket is full again after capacity/rate seconds, so a missing key and an idle key mean
-- the same thing. Expire after twice that to keep Redis from accumulating one key per client ever seen.
local ttlMs = math.ceil(capacity / rate * 1000) * 2
redis.call('PEXPIRE', KEYS[1], math.max(ttlMs, 1000))

return {allowed, math.floor(tokens), retryAfterMs}
