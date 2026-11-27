package dev.bulwark.gateway.ratelimit;

/**
 * Outcome of one rate limit check.
 *
 * @param allowed      whether the request may proceed
 * @param remaining    whole tokens left in the bucket
 * @param retryAfterMs when denied, how long until a token is available; 0 when allowed
 */
public record RateLimitDecision(boolean allowed, long remaining, long retryAfterMs) {
}
