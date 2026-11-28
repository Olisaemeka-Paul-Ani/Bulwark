package dev.bulwark.sentinel.eval;

import java.time.Instant;

/**
 * A detections row joined with ground truth.
 *
 * @param attackType attack name taken from the client id ({@code atk-<type>-NN}), or {@code null}
 *                   for normal clients
 */
record LabeledDetection(String clientId, String attackType, Instant windowStart, Instant detectedAt, boolean flagged) {
}
