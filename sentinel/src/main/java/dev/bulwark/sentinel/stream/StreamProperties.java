package dev.bulwark.sentinel.stream;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sentinel.stream")
public record StreamProperties(String group, String consumer, int batchSize) {
}
