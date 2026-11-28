package dev.bulwark.gateway.quarantine;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Blocking JDBC access to the quarantine table. Never call this from a request: it is used at
 * startup and from the listener's dedicated thread.
 */
@Component
@EnableConfigurationProperties(QuarantineProperties.class)
public class QuarantineStore {

    private static final String SELECT_ACTIVE = """
            SELECT client_id, reason, score, expires_at
            FROM quarantine
            WHERE expires_at > now()
            """;

    private final QuarantineProperties properties;

    public QuarantineProperties properties() {
        return properties;
    }

    public QuarantineStore(QuarantineProperties properties) {
        this.properties = properties;
    }

    /** Opens a new, unpooled connection. The caller owns and must close it. */
    public Connection connect() throws SQLException {
        Properties props = new Properties();
        props.setProperty("user", properties.username());
        props.setProperty("password", properties.password());
        props.setProperty("ApplicationName", "bulwark-gateway-quarantine");
        // Lets the OS notice a silently dropped connection instead of the listener waiting forever.
        props.setProperty("tcpKeepAlive", "true");
        return DriverManager.getConnection(properties.jdbcUrl(), props);
    }

    /** All unexpired quarantine rows. */
    public List<QuarantineEntry> loadActive(Connection connection) throws SQLException {
        List<QuarantineEntry> entries = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement(SELECT_ACTIVE);
             ResultSet rs = statement.executeQuery()) {
            while (rs.next()) {
                double score = rs.getDouble("score");
                entries.add(new QuarantineEntry(
                        rs.getString("client_id"),
                        rs.getString("reason"),
                        rs.wasNull() ? null : score,
                        rs.getTimestamp("expires_at").toInstant()));
            }
        }
        return entries;
    }

    public List<QuarantineEntry> loadActive() throws SQLException {
        try (Connection connection = connect()) {
            return loadActive(connection);
        }
    }
}
