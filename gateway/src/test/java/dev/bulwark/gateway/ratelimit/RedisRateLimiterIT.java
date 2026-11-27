package dev.bulwark.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.UUID;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.data.redis.connection.RedisStandaloneConfiguration;
import org.springframework.data.redis.connection.lettuce.LettuceConnectionFactory;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.testcontainers.containers.GenericContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.utility.DockerImageName;

/**
 * Runs the real Lua script against a real Redis, checking that it agrees with {@link TokenBucket}.
 */
@Testcontainers
class RedisRateLimiterIT {

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static LettuceConnectionFactory connectionFactory;
    private static ReactiveStringRedisTemplate redis;
    private static RedisRateLimiter limiter;

    @BeforeAll
    static void connect() {
        connectionFactory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        connectionFactory.afterPropertiesSet();
        redis = new ReactiveStringRedisTemplate(connectionFactory);
        limiter = new RedisRateLimiter(redis);
    }

    @AfterAll
    static void disconnect() {
        connectionFactory.destroy();
    }

    private static String newClient() {
        return "test-" + UUID.randomUUID();
    }

    @Test
    void allowsBurstUpToCapacityThenRejects() {
        String client = newClient();
        // 1 token/s: no meaningful refill happens during the few ms this loop takes.
        for (int i = 4; i >= 0; i--) {
            RateLimitDecision d = limiter.tryConsume(client, 5, 1).block();
            assertThat(d.allowed()).isTrue();
            assertThat(d.remaining()).isEqualTo(i);
        }

        RateLimitDecision denied = limiter.tryConsume(client, 5, 1).block();

        assertThat(denied.allowed()).isFalse();
        assertThat(denied.remaining()).isZero();
        assertThat(denied.retryAfterMs()).isBetween(1L, 1000L);
    }

    @Test
    void refillsOverTime() throws InterruptedException {
        String client = newClient();
        // 20 tokens/s = one token every 50ms.
        for (int i = 0; i < 5; i++) {
            assertThat(limiter.tryConsume(client, 5, 20).block().allowed()).isTrue();
        }
        assertThat(limiter.tryConsume(client, 5, 20).block().allowed()).isFalse();

        Thread.sleep(120);

        assertThat(limiter.tryConsume(client, 5, 20).block().allowed()).isTrue();
        assertThat(limiter.tryConsume(client, 5, 20).block().allowed()).isTrue();
    }

    @Test
    void refillNeverExceedsCapacity() throws InterruptedException {
        String client = newClient();
        limiter.tryConsume(client, 3, 100).block();

        Thread.sleep(200); // would be 20 tokens without the cap

        int allowed = 0;
        for (int i = 0; i < 10; i++) {
            if (limiter.tryConsume(client, 3, 100).block().allowed()) {
                allowed++;
            }
        }
        // 3 from the capped bucket, plus at most one refilled while the loop runs.
        assertThat(allowed).isBetween(3, 4);
    }

    @Test
    void bucketsAreIndependentPerClient() {
        String a = newClient();
        String b = newClient();
        limiter.tryConsume(a, 1, 0.01).block();

        assertThat(limiter.tryConsume(a, 1, 0.01).block().allowed()).isFalse();
        assertThat(limiter.tryConsume(b, 1, 0.01).block().allowed()).isTrue();
    }

    @Test
    void idleBucketKeyExpires() {
        String client = newClient();
        limiter.tryConsume(client, 20, 10).block();

        Long ttlMs = redis.getExpire(RedisRateLimiter.bucketKey(client)).map(d -> d.toMillis()).block();

        // capacity/rate = 2s to refill fully, doubled.
        assertThat(ttlMs).isBetween(3_000L, 4_000L);
    }
}
