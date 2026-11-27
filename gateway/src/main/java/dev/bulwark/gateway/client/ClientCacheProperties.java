package dev.bulwark.gateway.client;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("bulwark.client-cache")
public record ClientCacheProperties(Duration ttl, long maxSize) {
}
