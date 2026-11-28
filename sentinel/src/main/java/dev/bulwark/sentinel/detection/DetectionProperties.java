package dev.bulwark.sentinel.detection;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param threshold        anomaly score at or above which a window is flagged
 * @param quarantineExpiry how long a flagged client stays quarantined (extended while it stays anomalous)
 */
@ConfigurationProperties("sentinel.detection")
public record DetectionProperties(double threshold, Duration quarantineExpiry) {

    public DetectionProperties {
        if (threshold <= 0 || threshold >= 1) {
            throw new IllegalArgumentException("sentinel.detection.threshold must be in (0, 1), got " + threshold);
        }
        if (quarantineExpiry == null || quarantineExpiry.toMinutes() < 1) {
            throw new IllegalArgumentException("sentinel.detection.quarantine-expiry must be at least 1 minute");
        }
    }
}
