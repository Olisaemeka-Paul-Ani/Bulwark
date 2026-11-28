package dev.bulwark.sentinel.window;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties("sentinel.window")
public record WindowProperties(Duration size, Duration grace) {
}
