package dev.bulwark.gateway.filter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.concurrent.atomic.AtomicReference;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.http.HttpStatus;
import org.springframework.mock.http.server.reactive.MockServerHttpRequest;
import org.springframework.mock.web.server.MockServerWebExchange;
import org.springframework.web.server.ServerWebExchange;

import dev.bulwark.gateway.client.Client;
import dev.bulwark.gateway.client.ClientCache;
import reactor.core.publisher.Mono;

class ClientIdentityFilterTest {

    private ClientCache clients;
    private ClientIdentityFilter filter;
    private AtomicReference<ServerWebExchange> forwarded;
    private GatewayFilterChain chain;

    @BeforeEach
    void setUp() {
        clients = mock(ClientCache.class);
        filter = new ClientIdentityFilter(clients);
        forwarded = new AtomicReference<>();
        chain = exchange -> {
            forwarded.set(exchange);
            return Mono.empty();
        };
    }

    @Test
    void missingApiKeyIsRejectedWithoutLookup() {
        MockServerWebExchange exchange = MockServerWebExchange.from(MockServerHttpRequest.get("/orders"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block()).contains("missing api key");
        assertThat(forwarded.get()).isNull();
        verify(clients, never()).findByApiKey(anyString());
    }

    @Test
    void blankApiKeyIsTreatedAsMissing() {
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/orders").header(ClientIdentityFilter.API_KEY_HEADER, "  "));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void unknownApiKeyIsRejected() {
        when(clients.findByApiKey("bk_unknown")).thenReturn(Mono.empty());
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/orders").header(ClientIdentityFilter.API_KEY_HEADER, "bk_unknown"));

        filter.filter(exchange, chain).block();

        assertThat(exchange.getResponse().getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(exchange.getResponse().getBodyAsString().block())
                .isEqualTo("{\"error\":\"unauthorized\",\"reason\":\"unknown api key\"}");
        assertThat(forwarded.get()).isNull();
    }

    @Test
    void validApiKeyForwardsWithClientAttached() {
        Client client = new Client("norm-001", "free", false);
        when(clients.findByApiKey("bk_norm_001")).thenReturn(Mono.just(client));
        MockServerWebExchange exchange = MockServerWebExchange.from(
                MockServerHttpRequest.get("/orders").header(ClientIdentityFilter.API_KEY_HEADER, "bk_norm_001"));

        filter.filter(exchange, chain).block();

        assertThat(forwarded.get()).isSameAs(exchange);
        assertThat(ClientIdentityFilter.client(exchange)).isEqualTo(client);
        assertThat(exchange.getResponse().getStatusCode()).isNull();
    }
}
