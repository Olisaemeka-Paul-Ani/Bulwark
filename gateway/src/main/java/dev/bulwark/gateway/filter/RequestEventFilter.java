package dev.bulwark.gateway.filter;

import java.util.concurrent.atomic.AtomicLong;

import org.reactivestreams.Publisher;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cloud.gateway.filter.GatewayFilterChain;
import org.springframework.cloud.gateway.filter.GlobalFilter;
import org.springframework.core.Ordered;
import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.http.server.reactive.ServerHttpResponseDecorator;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ServerWebExchange;

import dev.bulwark.common.RequestEvent;
import dev.bulwark.gateway.client.Client;
import dev.bulwark.gateway.events.RequestEventPublisher;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;
import reactor.core.publisher.SignalType;

/**
 * Outermost filter: records what happened to every proxied request, including the ones Bulwark
 * rejected itself (401/403/429), and hands a {@link RequestEvent} to the publisher once the
 * response is done. These events are sentinel's only view of client behaviour.
 */
@Component
public class RequestEventFilter implements GlobalFilter, Ordered {

    private final RequestEventPublisher publisher;
    private final String nodeId;

    public RequestEventFilter(RequestEventPublisher publisher, @Value("${bulwark.node-id}") String nodeId) {
        this.publisher = publisher;
        this.nodeId = nodeId;
    }

    @Override
    public Mono<Void> filter(ServerWebExchange exchange, GatewayFilterChain chain) {
        long startedAtMs = System.currentTimeMillis();
        long startedNanos = System.nanoTime();
        ByteCountingResponse response = new ByteCountingResponse(exchange.getResponse());
        ServerWebExchange counted = exchange.mutate().response(response).build();

        return chain.filter(counted).doFinally(signal -> {
            ServerHttpRequest request = exchange.getRequest();
            Client client = ClientIdentityFilter.client(counted);
            publisher.publish(new RequestEvent(
                    client == null ? null : client.clientId(),
                    startedAtMs,
                    request.getMethod().name(),
                    request.getPath().value(),
                    status(response.getStatusCode(), signal),
                    (System.nanoTime() - startedNanos) / 1_000_000,
                    Math.max(0, request.getHeaders().getContentLength()),
                    response.bytesWritten(),
                    nodeId));
        });
    }

    private static int status(HttpStatusCode code, SignalType signal) {
        if (signal == SignalType.ON_ERROR) {
            // The error handler hasn't written its response yet; it will be a 5xx.
            return code != null && code.isError() ? code.value() : 500;
        }
        return code == null ? 200 : code.value();
    }

    @Override
    public int getOrder() {
        return FilterOrder.REQUEST_EVENT;
    }

    /** Counts body bytes as they are written, since proxied responses are often chunked. */
    private static final class ByteCountingResponse extends ServerHttpResponseDecorator {

        private final AtomicLong bytes = new AtomicLong();

        ByteCountingResponse(ServerHttpResponse delegate) {
            super(delegate);
        }

        long bytesWritten() {
            return bytes.get();
        }

        @Override
        public Mono<Void> writeWith(Publisher<? extends DataBuffer> body) {
            return super.writeWith(Flux.from(body).doOnNext(buffer -> bytes.addAndGet(buffer.readableByteCount())));
        }

        @Override
        public Mono<Void> writeAndFlushWith(Publisher<? extends Publisher<? extends DataBuffer>> body) {
            return super.writeAndFlushWith(Flux.from(body).map(inner ->
                    Flux.from(inner).doOnNext(buffer -> bytes.addAndGet(buffer.readableByteCount()))));
        }
    }
}
