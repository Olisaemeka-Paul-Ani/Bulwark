package dev.bulwark.gateway.ratelimit;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

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

import reactor.core.publisher.Flux;

/**
 * Proves the Lua script is the only thing needed for correctness across nodes: two limiters on
 * separate Redis connections (standing in for two gateway replicas) hammer one bucket concurrently
 * and together never get more than its capacity.
 */
@Testcontainers
class RateLimitConcurrencyIT {

    private static final int CAPACITY = 50;
    private static final double NO_REFILL = 0.001; // one token per ~17 minutes
    private static final int REQUESTS_PER_NODE = 200;

    @Container
    static final GenericContainer<?> REDIS =
            new GenericContainer<>(DockerImageName.parse("redis:7-alpine")).withExposedPorts(6379);

    private static LettuceConnectionFactory nodeAConnection;
    private static LettuceConnectionFactory nodeBConnection;
    private static RedisRateLimiter nodeA;
    private static RedisRateLimiter nodeB;

    @BeforeAll
    static void connect() {
        nodeAConnection = connectionFactory();
        nodeBConnection = connectionFactory();
        nodeA = new RedisRateLimiter(new ReactiveStringRedisTemplate(nodeAConnection));
        nodeB = new RedisRateLimiter(new ReactiveStringRedisTemplate(nodeBConnection));
    }

    @AfterAll
    static void disconnect() {
        nodeAConnection.destroy();
        nodeBConnection.destroy();
    }

    private static LettuceConnectionFactory connectionFactory() {
        LettuceConnectionFactory factory = new LettuceConnectionFactory(
                new RedisStandaloneConfiguration(REDIS.getHost(), REDIS.getMappedPort(6379)));
        factory.afterPropertiesSet();
        return factory;
    }

    @Test
    void concurrentRequestsFromTwoNodesNeverExceedCapacity() {
        String client = "concurrent-client";

        List<RateLimitDecision> decisions = Flux.range(0, REQUESTS_PER_NODE * 2)
                .flatMap(i -> (i % 2 == 0 ? nodeA : nodeB).tryConsume(client, CAPACITY, NO_REFILL),
                        REQUESTS_PER_NODE * 2)
                .collectList()
                .block();

        long allowed = decisions.stream().filter(RateLimitDecision::allowed).count();
        assertThat(decisions).hasSize(REQUESTS_PER_NODE * 2);
        assertThat(allowed).isEqualTo(CAPACITY);
        assertThat(decisions).filteredOn(d -> !d.allowed())
                .allSatisfy(d -> assertThat(d.remaining()).isZero());
    }

    @Test
    void everyRemainingCountIsHandedOutExactlyOnce() {
        String client = "remaining-client";

        List<Long> remainingSeenByAllowed = Flux.range(0, CAPACITY)
                .flatMap(i -> (i % 2 == 0 ? nodeA : nodeB).tryConsume(client, CAPACITY, NO_REFILL), CAPACITY)
                .filter(RateLimitDecision::allowed)
                .map(RateLimitDecision::remaining)
                .collectList()
                .block();

        // If two requests ever read the same bucket state, two of them would report the same
        // remaining count. Serialized execution gives exactly CAPACITY-1 .. 0.
        assertThat(remainingSeenByAllowed)
                .hasSize(CAPACITY)
                .doesNotHaveDuplicates()
                .allSatisfy(r -> assertThat(r).isBetween(0L, (long) CAPACITY - 1));
    }
}
