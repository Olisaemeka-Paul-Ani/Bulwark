package dev.bulwark.gateway.filter;

import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.core.io.buffer.DataBuffer;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.server.reactive.ServerHttpResponse;
import org.springframework.web.server.ServerWebExchange;

import reactor.core.publisher.Mono;

/**
 * Writes small flat JSON error bodies for requests the gateway rejects itself. Values are plain
 * strings, so a hand-rolled writer avoids pulling an ObjectMapper into every filter.
 */
public final class JsonResponses {

    private JsonResponses() {
    }

    public static Mono<Void> error(ServerWebExchange exchange, HttpStatus status, Map<String, String> fields) {
        ServerHttpResponse response = exchange.getResponse();
        response.setStatusCode(status);
        response.getHeaders().setContentType(MediaType.APPLICATION_JSON);
        String json = fields.entrySet().stream()
                .map(e -> quote(e.getKey()) + ":" + quote(e.getValue()))
                .collect(Collectors.joining(",", "{", "}"));
        DataBuffer body = response.bufferFactory().wrap(json.getBytes(StandardCharsets.UTF_8));
        return response.writeWith(Mono.just(body));
    }

    private static String quote(String value) {
        StringBuilder sb = new StringBuilder("\"");
        for (char c : value.toCharArray()) {
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
