package dev.bulwark.gateway.quarantine;

import java.io.IOException;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicLong;

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
 * so this owns a raw PgJDBC connection on its own thread and polls it for notifications, applying
 * each one to the registry. Nothing
 * here touches the request path.
 */
@Component
public class QuarantineListener implements SmartLifecycle {

    static final String CHANNEL = "quarantine_changed";
    private static final Logger log = LoggerFactory.getLogger(QuarantineListener.class);
    private static final int POLL_TIMEOUT_MS = 1_000;

    private final QuarantineStore store;
    private final QuarantineRegistry registry;

    private final AtomicLong connects = new AtomicLong();

    private volatile boolean running;
    private volatile boolean connected;
    private volatile Thread thread;
    private volatile Connection connection;

    public QuarantineListener(QuarantineStore store, QuarantineRegistry registry) {
        this.store = store;
        this.registry = registry;
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

    /** Whether the listener currently holds a live LISTEN session. */
    public boolean isConnected() {
        return connected;
    }

    /** How many times the listener has (re)established its LISTEN session. */
    public long connectCount() {
        return connects.get();
    }

    private void run() {
        Duration initial = store.properties().reconnectInitialBackoff();
        Duration max = store.properties().reconnectMaxBackoff();
        Duration backoff = initial;
        while (running) {
            try {
                listen();
            } catch (SQLException e) {
                if (!running) {
                    break;
                }
                if (connected) {
                    // We had a working session, so this is a fresh outage: start backing off from scratch.
                    backoff = initial;
                }
                log.warn("quarantine listener disconnected, retrying in {} ms: {}", backoff.toMillis(), e.toString());
            } finally {
                connected = false;
                closeConnection();
            }
            if (!sleep(backoff)) {
                break;
            }
            backoff = backoff.multipliedBy(2).compareTo(max) > 0 ? max : backoff.multipliedBy(2);
        }
    }

    /** Opens a session and processes notifications until stopped or the connection fails. */
    private void listen() throws SQLException {
        connection = store.connect();
        try (Statement statement = connection.createStatement()) {
            statement.execute("LISTEN " + CHANNEL);
        }
        // NOTIFY isn't durable: anything sent while we were disconnected is gone. Reload the whole
        // table now. LISTEN runs first, so a change committed while the snapshot loads arrives as a
        // queued notification afterwards instead of falling into a gap between the two.
        resync();
        connected = true;
        connects.incrementAndGet();
        log.info("listening on {}", CHANNEL);
        PGConnection pg = connection.unwrap(PGConnection.class);
        while (running) {
            PGNotification[] notifications = pg.getNotifications(POLL_TIMEOUT_MS);
            if (notifications != null) {
                for (PGNotification notification : notifications) {
                    handle(notification.getParameter());
                }
            }
        }
    }

    private void resync() throws SQLException {
        List<QuarantineEntry> active = store.loadActive(connection);
        registry.replaceAll(active);
        log.info("quarantine resync: {} active entries", active.size());
    }

    private boolean sleep(Duration duration) {
        try {
            Thread.sleep(duration);
            return running;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return false;
        }
    }

    void handle(String payload) {
        try {
            QuarantineNotification notification = QuarantineNotification.parse(payload);
            notification.applyTo(registry);
            log.debug("applied quarantine {} for {}", notification.op(), notification.clientId());
        } catch (IOException | IllegalArgumentException e) {
            // A bad payload must not kill the listener; the next resync repairs anything missed.
            log.warn("ignoring malformed quarantine notification {}: {}", payload, e.toString());
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
