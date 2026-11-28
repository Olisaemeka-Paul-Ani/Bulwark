package dev.bulwark.sentinel.detection;

import dev.bulwark.common.FeatureVector;

/**
 * A scored window.
 *
 * @param score   Isolation Forest anomaly score in (0, 1); around 0.5 or below is ordinary,
 *                values approaching 1 mean the window was isolated in very few splits
 * @param flagged whether the score crossed the detection threshold
 */
public record Detection(FeatureVector features, double score, boolean flagged) {
}
