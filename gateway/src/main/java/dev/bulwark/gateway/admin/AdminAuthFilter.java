package dev.bulwark.gateway.admin;

import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;
import org.springframework.web.server.WebFilter;
import org.springframework.web.server.WebFilterChain;

import dev.bulwark.gateway.client.ClientCache;
import dev.bulwark.gateway.filter.ClientIdentityFilter;
import dev.bulwark.gateway.filter.JsonResponses;
import reactor.core.publisher.Mono;

/**
 * Guards {@code /admin/**}: the caller's {@code X-Api-Key} must belong to a client with
 * {@code is_admin}. The admin API is served by this gateway's own controllers rather than a
 * proxied route, so the gateway's global filters don't run for it and it needs its own check.
 */
@Component
public class AdminAuthFilter implements WebFilter {

    private static final String ADMIN_PREFIX = "/admin/";

    private final ClientCache clients;

    public AdminAuthFilter(ClientCache clients) {
        this.clients = clients;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, WebFilterChain chain) {
        String path = exchange.getRequest().getPath().pathWithinApplication().value();
        if (!path.startsWith(ADMIN_PREFIX) && !path.equals("/admin")) {
            return chain.filter(exchange);
        }
        String apiKey = exchange.getRequest().getHeaders().getFirst(ClientIdentityFilter.API_KEY_HEADER);
        if (apiKey == null || apiKey.isBlank()) {
            return JsonResponses.error(exchange, HttpStatus.UNAUTHORIZED,
                    Map.of("error", "unauthorized"));
        }
        return clients.findByApiKey(apiKey)
                .flatMap(client -> client.admin()
                        ? chain.filter(exchange).thenReturn(true)
                        : JsonResponses.error(exchange, HttpStatus.FORBIDDEN, Map.of("error", "admin key required"))
                                .thenReturn(true))
                .switchIfEmpty(Mono.defer(() -> JsonResponses.error(exchange, HttpStatus.UNAUTHORIZED,
                        Map.of("error", "unauthorized")).thenReturn(true)))
                .then();
    }
}
