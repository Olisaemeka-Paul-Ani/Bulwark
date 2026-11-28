package dev.bulwark.gateway.filter;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import dev.bulwark.gateway.client.Client;
import dev.bulwark.gateway.quarantine.QuarantineEntry;
import dev.bulwark.gateway.quarantine.QuarantineRegistry;
import reactor.core.publisher.Mono;

/**
 * Blocks quarantined clients with {@code 403}. Runs before rate limiting so a quarantined client
 * doesn't spend Redis round trips (or tokens) at all.
 */
@Component
public class QuarantineFilter implements GlobalFilter, Ordered {

    private final QuarantineRegistry registry;

    public QuarantineFilter(QuarantineRegistry registry) {
        this.registry = registry;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        Client client = ClientIdentityFilter.client(exchange);
        if (client == null) {
            return Mono.error(new IllegalStateException("quarantine filter ran before client identification"));
        }
        return registry.find(client.clientId())
                .map(entry -> forbidden(exchange, entry))
                .orElseGet(() -> chain.filter(exchange));
    }

    private static Mono<Void> forbidden(ServerWebExchange exchange, QuarantineEntry entry) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", "quarantined");
        body.put("reason", entry.reason());
        body.put("until", entry.expiresAt().toString());
        return JsonResponses.error(exchange, HttpStatus.FORBIDDEN, body);
    }

    @Override
    public int getOrder() {
        return FilterOrder.QUARANTINE;
    }
}
