package dev.bulwark.gateway.ratelimit;

import java.time.Duration;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.data.redis.core.script.RedisScript;
import org.springframework.stereotype.Component;

import reactor.core.publisher.Mono;

/**
 * Runs {@code token_bucket.lua} against the Redis shared by all gateway nodes.
 *
 * <p>If Redis is slow or down the limiter fails open (allows the request): losing rate limiting
 * for a moment is better than turning a Redis outage into a full API outage. Identity and
 * quarantine checks don't depend on Redis, so bad clients are still blocked.
 *
 * <p>Named explicitly because Spring Cloud Gateway registers its own {@code redisRateLimiter} bean.
 */
@Component("bulwarkRateLimiter")
public class RedisRateLimiter {

    private static final Logger log = LoggerFactory.getLogger(RedisRateLimiter.class);
    private static final Duration REDIS_TIMEOUT = Duration.ofMillis(250);

    @SuppressWarnings("rawtypes")
    private static final RedisScript<List> SCRIPT =
            RedisScript.of(new ClassPathResource("scripts/token_bucket.lua"), List.class);

    private final ReactiveStringRedisTemplate redis;

    public RedisRateLimiter(ReactiveStringRedisTemplate redis) {
        this.redis = redis;
    }

    static String bucketKey(String clientId) {
        return "bucket:" + clientId;
    }

    public Mono<RateLimitDecision> tryConsume(String clientId, int capacity, double ratePerSecond) {
        List<String> keys = List.of(bucketKey(clientId));
        List<String> args = List.of(Integer.toString(capacity), Double.toString(ratePerSecond));
        return redis.execute(SCRIPT, keys, args)
                .next()
                .map(RedisRateLimiter::toDecision)
                .timeout(REDIS_TIMEOUT)
                .onErrorResume(e -> {
                    log.warn("rate limit check failed for client {}, allowing request: {}", clientId, e.toString());
                    return Mono.just(new RateLimitDecision(true, capacity, 0));
                });
    }

    private static RateLimitDecision toDecision(List<?> reply) {
        return new RateLimitDecision(
                ((Number) reply.get(0)).longValue() == 1,
                ((Number) reply.get(1)).longValue(),
                ((Number) reply.get(2)).longValue());
    }
}
