package dev.bulwark.common;

import java.util.HashMap;
import java.util.Map;

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

    /** Redis stream the gateways publish to and sentinel consumes from. */
    public static final String STREAM = "bulwark:events";

    /** Flat string fields for a Redis stream entry. */
    public Map<String, String> toFields() {
        Map<String, String> fields = new HashMap<>();
        if (clientId != null) {
            fields.put("clientId", clientId);
        }
        fields.put("ts", Long.toString(ts));
        fields.put("method", method);
        fields.put("path", path);
        fields.put("status", Integer.toString(status));
        fields.put("latencyMs", Long.toString(latencyMs));
        fields.put("requestBytes", Long.toString(requestBytes));
        fields.put("responseBytes", Long.toString(responseBytes));
        fields.put("gatewayNode", gatewayNode);
        return fields;
    }

    /** Inverse of {@link #toFields()}. */
    public static RequestEvent fromFields(Map<String, String> fields) {
        return new RequestEvent(
                fields.get("clientId"),
                Long.parseLong(fields.get("ts")),
                fields.get("method"),
                fields.get("path"),
                Integer.parseInt(fields.get("status")),
                Long.parseLong(fields.get("latencyMs")),
                Long.parseLong(fields.get("requestBytes")),
                Long.parseLong(fields.get("responseBytes")),
                fields.get("gatewayNode"));
    }
}
