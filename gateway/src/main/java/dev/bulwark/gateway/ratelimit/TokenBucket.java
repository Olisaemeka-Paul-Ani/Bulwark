package dev.bulwark.gateway.ratelimit;

/**
 * Reference implementation of the token bucket algorithm used by {@code token_bucket.lua}.
 *
 * <p>Production traffic never goes through this class: the real bucket lives in Redis and is
 * updated atomically by the Lua script. This mirrors the script's arithmetic exactly so the
 * refill/take rules can be unit tested without Redis. Keep the two in sync.
 *
 * <p>The bucket refills lazily: nothing runs on a timer, instead each request first adds the
 * tokens earned since the last request ({@code elapsed * rate}, capped at capacity) and then
 * tries to take one.
 */
public final class TokenBucket {

    /** Bucket contents as stored in Redis. Tokens are fractional because refill is continuous. */
    public record State(double tokens, long lastRefillMs) {
    }

    /**
     * @param allowed      whether a token was taken
     * @param state        bucket state to store after this request
     * @param retryAfterMs when denied, how long until one whole token is available; 0 when allowed
     */
    public record Decision(boolean allowed, State state, long retryAfterMs) {

        /** Whole tokens left after this request, as reported in {@code X-RateLimit-Remaining}. */
        public long remaining() {
            return (long) Math.floor(state.tokens());
        }
    }

    private TokenBucket() {
    }

    /**
     * Tokens in the bucket at {@code nowMs}. A clock that moved backwards adds nothing rather than
     * removing tokens.
     */
    public static double refill(double tokens, long lastRefillMs, long nowMs, double capacity, double ratePerSec) {
        long elapsedMs = Math.max(0, nowMs - lastRefillMs);
        return Math.min(capacity, tokens + elapsedMs * ratePerSec / 1000.0);
    }

    /**
     * Refill, then try to take one token.
     *
     * @param current bucket state, or {@code null} for a client with no bucket yet (starts full)
     */
    public static Decision tryTake(State current, long nowMs, double capacity, double ratePerSec) {
        double tokens;
        long lastRefillMs;
        if (current == null) {
            tokens = capacity;
            lastRefillMs = nowMs;
        } else {
            tokens = refill(current.tokens(), current.lastRefillMs(), nowMs, capacity, ratePerSec);
            lastRefillMs = Math.max(current.lastRefillMs(), nowMs);
        }

        if (tokens >= 1.0) {
            return new Decision(true, new State(tokens - 1.0, lastRefillMs), 0);
        }
        long retryAfterMs = (long) Math.ceil((1.0 - tokens) * 1000.0 / ratePerSec);
        return new Decision(false, new State(tokens, lastRefillMs), retryAfterMs);
    }
}
