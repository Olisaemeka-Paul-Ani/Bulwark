package dev.bulwark.common;

/**
 * One proxied request, as seen by a gateway node after the response was sent.
 *
 * <p>Gateways publish these to the Redis stream {@code bulwark:events}; sentinel consumes them to
 * build per-client behaviour windows. It lives in {@code common} so both sides agree on the shape.
 *
 * @param clientId      resolved client id, or {@code null} if the API key was missing/unknown
 * @param ts            epoch millis when the request arrived at the gateway
 * @param method        HTTP method
 * @param path          request path without query string
 * @param status        HTTP status returned to the client
 * @param latencyMs     time spent in the gateway including the upstream call
 * @param requestBytes  request body size (Content-Length, 0 if absent)
 * @param responseBytes response body size (Content-Length, 0 if absent)
 * @param gatewayNode   id of the gateway replica that handled the request
 */
public record RequestEvent(
        String clientId,
        long ts,
        String method,
        String path,
        int status,
        long latencyMs,
        long requestBytes,
        long responseBytes,
        String gatewayNode) {
}
