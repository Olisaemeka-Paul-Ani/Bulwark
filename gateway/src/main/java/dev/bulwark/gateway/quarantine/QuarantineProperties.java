package dev.bulwark.gateway.quarantine;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Settings for the quarantine table connection, used off the request path only.
 *
 * @param reconnectInitialBackoff first wait after the listener loses its connection
 * @param reconnectMaxBackoff     cap for the doubling backoff
 */
@ConfigurationProperties("bulwark.quarantine")
public record QuarantineProperties(
        String jdbcUrl,
        String username,
        String password,
        Duration reconnectInitialBackoff,
        Duration reconnectMaxBackoff) {

    public QuarantineProperties {
        if (reconnectInitialBackoff == null) {
            reconnectInitialBackoff = Duration.ofMillis(500);
        }
        if (reconnectMaxBackoff == null) {
            reconnectMaxBackoff = Duration.ofSeconds(30);
        }
    }
}
