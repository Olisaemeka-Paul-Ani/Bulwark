package dev.bulwark.gateway.quarantine;

import java.time.Instant;

import com.fasterxml.jackson.databind.ObjectMapper;

/**
 * Payload of a {@code quarantine_changed} notification, as built by the
 * {@code notify_quarantine_changed()} trigger in {@code init.sql}.
 *
 * @param op TG_OP: {@code INSERT}, {@code UPDATE} or {@code DELETE}
 */
record QuarantineNotification(String op, String clientId, String reason, Double score, long expiresAtMs) {

    private static final ObjectMapper JSON = new ObjectMapper();

    static QuarantineNotification parse(String payload) throws java.io.IOException {
        QuarantineNotification n = JSON.readValue(payload, QuarantineNotification.class);
        if (n.op() == null || n.clientId() == null) {
            throw new java.io.IOException("missing op or clientId in " + payload);
        }
        return n;
    }

    /** Applies this change to the registry. */
    void applyTo(QuarantineRegistry registry) {
        switch (op) {
            case "INSERT", "UPDATE" -> registry.put(
                    new QuarantineEntry(clientId, reason, score, Instant.ofEpochMilli(expiresAtMs)));
            case "DELETE" -> registry.remove(clientId);
            default -> throw new IllegalArgumentException("unknown op " + op);
        }
    }
}
