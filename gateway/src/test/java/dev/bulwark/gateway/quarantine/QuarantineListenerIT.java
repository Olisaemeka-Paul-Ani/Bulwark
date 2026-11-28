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
 * Two listeners (standing in for two gateway nodes) against one Postgres: a change to the
 * quarantine table must reach both registries through the trigger's NOTIFY.
 */
@Testcontainers
class QuarantineListenerIT {

    @Container
    static final PostgreSQLContainer<?> POSTGRES = QuarantinePostgres.container();

    private final QuarantineRegistry nodeA = new QuarantineRegistry();
    private final QuarantineRegistry nodeB = new QuarantineRegistry();
    private QuarantineListener listenerA;
    private QuarantineListener listenerB;

    @BeforeEach
    void startListeners() throws Exception {
        QuarantinePostgres.sql(POSTGRES, "DELETE FROM quarantine");
        QuarantineStore store = QuarantinePostgres.store(POSTGRES, Duration.ofMillis(100));
        listenerA = new QuarantineListener(store, nodeA);
        listenerB = new QuarantineListener(store, nodeB);
        listenerA.start();
        listenerB.start();
        await().atMost(Duration.ofSeconds(10))
                .until(() -> listenerA.isConnected() && listenerB.isConnected());
    }

    @AfterEach
    void stopListeners() {
        listenerA.stop();
        listenerB.stop();
    }

    @Test
    void insertReachesEveryNode() throws Exception {
        QuarantinePostgres.sql(POSTGRES, """
                INSERT INTO quarantine (client_id, reason, score, expires_at)
                VALUES ('atk-burst-01', 'anomaly', 0.71, now() + interval '10 minutes')""");

        await().atMost(Duration.ofSeconds(5)).untilAsserted(() -> {
            assertThat(nodeA.find("atk-burst-01")).isPresent();
            assertThat(nodeB.find("atk-burst-01")).isPresent();
        });
        QuarantineEntry entry = nodeA.find("atk-burst-01").orElseThrow();
        assertThat(entry.reason()).isEqualTo("anomaly");
        assertThat(entry.score()).isEqualTo(0.71);
    }

    @Test
    void updateReplacesEntry() throws Exception {
        QuarantinePostgres.sql(POSTGRES, """
                INSERT INTO quarantine (client_id, reason, expires_at)
                VALUES ('norm-001', 'manual', now() + interval '10 minutes')""");
        await().atMost(Duration.ofSeconds(5)).until(() -> nodeB.find("norm-001").isPresent());

        QuarantinePostgres.sql(POSTGRES, "UPDATE quarantine SET reason = 'anomaly', score = 0.9 WHERE client_id = 'norm-001'");

        await().atMost(Duration.ofSeconds(5))
                .untilAsserted(() -> assertThat(nodeB.find("norm-001").orElseThrow().reason()).isEqualTo("anomaly"));
    }

    @Test
    void updateToPastExpiryUnblocks() throws Exception {
        QuarantinePostgres.sql(POSTGRES, """
                INSERT INTO quarantine (client_id, reason, expires_at)
                VALUES ('norm-002', 'manual', now() + interval '10 minutes')""");
        await().atMost(Duration.ofSeconds(5)).until(() -> nodeA.find("norm-002").isPresent());

        QuarantinePostgres.sql(POSTGRES, "UPDATE quarantine SET expires_at = now() - interval '1 second'");

        await().atMost(Duration.ofSeconds(5)).until(() -> nodeA.find("norm-002").isEmpty());
    }

    @Test
    void deleteReachesEveryNode() throws Exception {
        QuarantinePostgres.sql(POSTGRES, """
                INSERT INTO quarantine (client_id, reason, expires_at)
                VALUES ('norm-003', 'manual', now() + interval '10 minutes')""");
        await().atMost(Duration.ofSeconds(5))
                .until(() -> nodeA.find("norm-003").isPresent() && nodeB.find("norm-003").isPresent());

        QuarantinePostgres.sql(POSTGRES, "DELETE FROM quarantine WHERE client_id = 'norm-003'");

        await().atMost(Duration.ofSeconds(5))
                .until(() -> nodeA.find("norm-003").isEmpty() && nodeB.find("norm-003").isEmpty());
        assertThat(nodeA.size()).isZero();
    }

    @Test
    void malformedNotificationIsIgnored() throws Exception {
        QuarantinePostgres.sql(POSTGRES, "SELECT pg_notify('quarantine_changed', 'not json')");
        QuarantinePostgres.sql(POSTGRES, """
                INSERT INTO quarantine (client_id, reason, expires_at)
                VALUES ('norm-004', 'manual', now() + interval '10 minutes')""");

        await().atMost(Duration.ofSeconds(5)).until(() -> nodeA.find("norm-004").isPresent());
        assertThat(listenerA.isConnected()).isTrue();
    }
}
