package dev.bulwark.sentinel;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Sentinel watches the request events the gateways publish, scores each client's behaviour with
 * an Isolation Forest, and quarantines clients that look anomalous. It is off the request path:
 * if sentinel is down, the gateways keep serving and enforcing existing quarantines.
 */
@SpringBootApplication
@EnableScheduling
public class SentinelApplication {

    public static void main(String[] args) {
        SpringApplication.run(SentinelApplication.class, args);
    }
}
