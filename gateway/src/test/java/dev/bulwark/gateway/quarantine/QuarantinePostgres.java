package dev.bulwark.gateway.quarantine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;

import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/** Test helpers: a Postgres container initialised with the real {@code infra/postgres/init.sql}. */
final class QuarantinePostgres {

    private QuarantinePostgres() {
    }

    static PostgreSQLContainer<?> container() {
        return new PostgreSQLContainer<>("postgres:16-alpine")
                .withCopyFileToContainer(
                        MountableFile.forHostPath("../infra/postgres/init.sql"),
                        "/docker-entrypoint-initdb.d/01-init.sql");
    }

    static QuarantineStore store(PostgreSQLContainer<?> pg, Duration initialBackoff) {
        return store(pg, initialBackoff, Duration.ofSeconds(60));
    }

    static QuarantineStore store(PostgreSQLContainer<?> pg, Duration initialBackoff, Duration resyncInterval) {
        return new QuarantineStore(new QuarantineProperties(
                pg.getJdbcUrl(), pg.getUsername(), pg.getPassword(),
                initialBackoff, Duration.ofSeconds(2), resyncInterval, Duration.ofSeconds(10)));
    }

    /** Number of open listener sessions (matched by the store's ApplicationName). */
    static int listenerSessions(PostgreSQLContainer<?> pg) throws SQLException {
        try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             Statement s = c.createStatement();
             var rs = s.executeQuery(
                     "SELECT count(*) FROM pg_stat_activity WHERE application_name = 'bulwark-gateway-quarantine'")) {
            rs.next();
            return rs.getInt(1);
        }
    }

    static void sql(PostgreSQLContainer<?> pg, String... statements) throws SQLException {
        try (Connection c = DriverManager.getConnection(pg.getJdbcUrl(), pg.getUsername(), pg.getPassword());
             Statement s = c.createStatement()) {
            for (String statement : statements) {
                s.execute(statement);
            }
        }
    }
}
