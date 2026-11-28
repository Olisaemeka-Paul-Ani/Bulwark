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
        return new QuarantineStore(new QuarantineProperties(
                pg.getJdbcUrl(), pg.getUsername(), pg.getPassword(),
                initialBackoff, Duration.ofSeconds(2), Duration.ofSeconds(60), Duration.ofSeconds(10)));
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
