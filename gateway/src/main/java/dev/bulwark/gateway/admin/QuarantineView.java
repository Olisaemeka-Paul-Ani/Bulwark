package dev.bulwark.gateway.admin;

import java.time.Instant;

public record QuarantineView(String clientId, String reason, Double score, Instant createdAt, Instant expiresAt) {
}
