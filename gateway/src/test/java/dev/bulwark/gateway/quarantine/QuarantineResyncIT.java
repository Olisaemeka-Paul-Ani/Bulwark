package dev.bulwark.gateway.quarantine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

import java.time.Duration;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/**
 * NOTIFY is fire-and-forget: a change committed while a node isn't listening is never delivered
 * to it. These tests lose a notification on purpose and check the resync repairs the registry.
 */
@Testcontainers
class QuarantineResyncIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = QuarantinePostgres.container();

    private final QuarantineRegistry registry = new QuarantineRegistry();
    private QuarantineListener listener;

    @BeforeEach
    void clean() throws Exception {
        QuarantinePostgres.sql(POSTGRES, "DELETE FROM quarantine");
    }

    @AfterEach
    void stop() {
        if (listener != null) {
            listener.stop();
        }
    }

    @Test
    void notificationMissedWhileDisconnectedIsRecoveredOnReconnect() throws Exception {
        // A long first backoff leaves a clear window in which nobody is listening.
        listener = new QuarantineListener(QuarantinePostgres.store(POSTGRES, Duration.ofSeconds(2)), registry);
        listener.start();
        await().atMost(Duration.ofSeconds(10)).until(listener::isConnected);
        long connectsBefore = listener.connectCount();

        QuarantinePostgres.sql(POSTGRES, """
                SELECT pg_terminate_backend(pid) FROM pg_stat_activity
                WHERE application_name = 'bulwark-gateway-quarantine'""");
        await().atMost(Duration.ofSeconds(5))
                .until(() -> !listener.isConnected() && QuarantinePostgres.listenerSessions(POSTGRES) == 0);

        QuarantinePostgres.sql(POSTGRES, """
                INSERT INTO quarantine (client_id, reason, score, expires_at)
                VALUES ('atk-scraper-01', 'anomaly', 0.8, now() + interval '10 minutes')""");
        assertThat(registry.find("atk-scraper-01")).as("notification was lost").isEmpty();

        await().atMost(Duration.ofSeconds(10)).until(() -> registry.find("atk-scraper-01").isPresent());
        assertThat(listener.connectCount()).isGreaterThan(connectsBefore);
    }

    @Test
    void deleteMissedWhileDisconnectedIsRecoveredOnReconnect() throws Exception {
        QuarantinePostgres.sql(POSTGRES, """
                INSERT INTO quarantine (client_id, reason, expires_at)
                VALUES ('norm-010', 'manual', now() + interval '10 minutes')""");
        listener = new QuarantineListener(QuarantinePostgres.store(POSTGRES, Duration.ofSeconds(2)), registry);
        listener.start();
        await().atMost(Duration.ofSeconds(10)).until(() -> registry.find("norm-010").isPresent());

        QuarantinePostgres.sql(POSTGRES, """
                SELECT pg_terminate_backend(pid) FROM pg_stat_activity
                WHERE application_name = 'bulwark-gateway-quarantine'""");
        await().atMost(Duration.ofSeconds(5))
                .until(() -> !listener.isConnected() && QuarantinePostgres.listenerSessions(POSTGRES) == 0);
        QuarantinePostgres.sql(POSTGRES, "DELETE FROM quarantine WHERE client_id = 'norm-010'");

        await().atMost(Duration.ofSeconds(10)).until(() -> registry.find("norm-010").isEmpty());
    }

    @Test
    void changeWithoutNotificationIsRecoveredByPeriodicResync() throws Exception {
        listener = new QuarantineListener(
                QuarantinePostgres.store(POSTGRES, Duration.ofMillis(100), Duration.ofSeconds(1)), registry);
        listener.start();
        await().atMost(Duration.ofSeconds(10)).until(listener::isConnected);
        long connectsBefore = listener.connectCount();

        // Write with the trigger disabled, so no NOTIFY is sent at all.
        QuarantinePostgres.sql(POSTGRES,
                "ALTER TABLE quarantine DISABLE TRIGGER quarantine_changed",
                """
                INSERT INTO quarantine (client_id, reason, expires_at)
                VALUES ('norm-011', 'manual', now() + interval '10 minutes')""",
                "ALTER TABLE quarantine ENABLE TRIGGER quarantine_changed");

        await().atMost(Duration.ofSeconds(5)).until(() -> registry.find("norm-011").isPresent());
        assertThat(listener.connectCount()).as("recovered without reconnecting").isEqualTo(connectsBefore);
    }
}
