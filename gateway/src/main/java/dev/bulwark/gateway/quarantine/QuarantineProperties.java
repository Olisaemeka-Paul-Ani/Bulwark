package dev.bulwark.gateway.quarantine;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** Connection settings for the quarantine table, used off the request path only. */
@ConfigurationProperties("bulwark.quarantine")
public record QuarantineProperties(String jdbcUrl, String username, String password) {
}
