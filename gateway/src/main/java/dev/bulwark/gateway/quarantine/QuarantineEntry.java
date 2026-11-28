package dev.bulwark.gateway.quarantine;

import java.time.Instant;

/**
 * One row of the {@code quarantine} table as held in memory by each gateway node.
 *
 * @param score anomaly score that triggered the quarantine, or {@code null} for manual ones
 */
public record QuarantineEntry(String clientId, String reason, Double score, Instant expiresAt) {

    public boolean isExpired(Instant now) {
        return !expiresAt.isAfter(now);
    }
}
