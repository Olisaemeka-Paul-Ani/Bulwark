package dev.bulwark.gateway.quarantine;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

import org.postgresql.PGConnection;
import org.postgresql.PGNotification;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.SmartLifecycle;
import org.springframework.stereotype.Component;

/**
 * Keeps this node's {@link QuarantineRegistry} in step with the quarantine table by listening on
 * the {@code quarantine_changed} channel.
 *
 * <p>LISTEN needs one long-lived session, which a pooled or reactive connection can't give us,
 * so this owns a raw PgJDBC connection on its own thread and polls it for notifications. Nothing
 * here touches the request path.
 */
@Component
public class QuarantineListener implements SmartLifecycle {

    static final String CHANNEL = "quarantine_changed";
    private static final Logger log = LoggerFactory.getLogger(QuarantineListener.class);
    private static final int POLL_TIMEOUT_MS = 1_000;

    private final QuarantineStore store;

    private volatile boolean running;
    private volatile Thread thread;
    private volatile Connection connection;

    public QuarantineListener(QuarantineStore store) {
        this.store = store;
    }

    @Override
    public void start() {
        running = true;
        thread = Thread.ofPlatform().name("quarantine-listener").daemon().start(this::run);
    }

    @Override
    public void stop() {
        running = false;
        closeConnection();
        Thread t = thread;
        if (t != null) {
            t.interrupt();
        }
    }

    @Override
    public boolean isRunning() {
        return running;
    }

    private void run() {
        try {
            connection = store.connect();
            try (Statement statement = connection.createStatement()) {
                statement.execute("LISTEN " + CHANNEL);
            }
            log.info("listening on {}", CHANNEL);
            PGConnection pg = connection.unwrap(PGConnection.class);
            while (running) {
                PGNotification[] notifications = pg.getNotifications(POLL_TIMEOUT_MS);
                if (notifications != null) {
                    for (PGNotification notification : notifications) {
                        log.info("quarantine notification: {}", notification.getParameter());
                    }
                }
            }
        } catch (SQLException e) {
            if (running) {
                log.warn("quarantine listener stopped: {}", e.toString());
            }
        } finally {
            closeConnection();
        }
    }

    private void closeConnection() {
        Connection c = connection;
        connection = null;
        if (c != null) {
            try {
                c.close();
            } catch (SQLException ignored) {
                // Already broken; nothing to do.
            }
        }
    }
}
