package dev.bulwark.gateway.filter;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import dev.bulwark.gateway.client.Client;
import dev.bulwark.gateway.ratelimit.RateLimitDecision;
import dev.bulwark.gateway.ratelimit.RedisRateLimiter;
import reactor.core.publisher.Mono;

/**
 * Spends one token from the client's bucket per request. Allowed responses carry
 * {@code X-RateLimit-Remaining}; denied requests get {@code 429} with {@code Retry-After}.
 */
@Component
public class RateLimitFilter implements GlobalFilter, Ordered {

    public static final String REMAINING_HEADER = "X-RateLimit-Remaining";

    private static final int CAPACITY = 20;
    private static final double REFILL_PER_SECOND = 10;

    private final RedisRateLimiter limiter;

    public RateLimitFilter(RedisRateLimiter limiter) {
        this.limiter = limiter;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Client client = ClientIdentityFilter.client(exchange);
        if (client == null) {
            // Identity filter runs first and rejects unknown clients, so this only happens if the
            // chain is misconfigured. Fail loudly rather than silently skipping the limit.
            return Mono.error(new IllegalStateException("rate limit filter ran before client identification"));
        }
        return limiter.tryConsume(client.clientId(), CAPACITY, REFILL_PER_SECOND)
                .flatMap(decision -> {
                    exchange.getResponse().getHeaders().set(REMAINING_HEADER, Long.toString(decision.remaining()));
                    if (decision.allowed()) {
                        return chain.filter(exchange);
                    }
                    return tooManyRequests(exchange, decision);
                });
    }

    private static Mono<Void> tooManyRequests(ServerWebExchange exchange, RateLimitDecision decision) {
        long retryAfterSeconds = Math.max(1, (decision.retryAfterMs() + 999) / 1000);
        exchange.getResponse().getHeaders().set(HttpHeaders.RETRY_AFTER, Long.toString(retryAfterSeconds));
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", "rate_limited");
        body.put("retryAfterSeconds", Long.toString(retryAfterSeconds));
        return JsonResponses.error(exchange, HttpStatus.TOO_MANY_REQUESTS, body);
    }

    @Override
    public int getOrder() {
        return FilterOrder.RATE_LIMIT;
    }
}
