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
import dev.bulwark.gateway.client.ClientCache;
import reactor.core.publisher.Mono;

/**
 * First filter in the chain: resolves {@code X-Api-Key} to a {@link Client} and stores it on the
 * exchange for the filters after it. Requests without a known key never reach a backend.
 */
@Component
public class ClientIdentityFilter implements GlobalFilter, Ordered {

    public static final String API_KEY_HEADER = "X-Api-Key";
    public static final String CLIENT_ATTRIBUTE = ClientIdentityFilter.class.getName() + ".client";

    private final ClientCache clients;

    public ClientIdentityFilter(ClientCache clients) {
        this.clients = clients;
    }

    /** The client resolved for this exchange, or {@code null} before/without identification. */
    public static Client client(ServerWebExchange exchange) {
        return exchange.getAttribute(CLIENT_ATTRIBUTE);
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        String apiKey = exchange.getRequest().getHeaders().getFirst(API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            return unauthorized(exchange, "missing api key");
        }
        return clients.findByApiKey(apiKey)
                .flatMap(client -> {
                    exchange.getAttributes().put(CLIENT_ATTRIBUTE, client);
                    return chain.filter(exchange).thenReturn(true);
                })
                .switchIfEmpty(Mono.defer(() -> unauthorized(exchange, "unknown api key").thenReturn(true)))
                .then();
    }

    private static Mono<Void> unauthorized(ServerWebExchange exchange, String reason) {
        Map<String, String> body = new LinkedHashMap<>();
        body.put("error", "unauthorized");
        body.put("reason", reason);
        return JsonResponses.error(exchange, HttpStatus.UNAUTHORIZED, body);
    }

    @Override
    public int getOrder() {
        return FilterOrder.CLIENT_IDENTITY;
    }
}
