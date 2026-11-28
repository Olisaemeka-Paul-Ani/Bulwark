package dev.bulwark.sentinel.detection;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Quarantines clients whose window was flagged, by writing the {@code quarantine} table. The
 * table's NOTIFY trigger then pushes the change to every gateway node; sentinel never talks to
 * the gateways directly.
 *
 * <p>A client that keeps looking anomalous has its quarantine extended each window. A manual
 * quarantine set by an admin is left alone, so sentinel can't shorten or relabel it.
 */
@Component
public class AutoQuarantine implements DetectionListener {

    private static final Logger log = LoggerFactory.getLogger(AutoQuarantine.class);
    static final int EXPIRY_MINUTES = 10;

    private final JdbcTemplate jdbc;

    public AutoQuarantine(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @Override
    public void onDetection(Detection detection) {
        if (!detection.flagged()) {
            return;
        }
        String clientId = detection.features().clientId();
        try {
            int rows = jdbc.update("""
                            INSERT INTO quarantine (client_id, reason, score, expires_at)
                            VALUES (?, 'anomaly', ?, now() + make_interval(mins => ?))
                            ON CONFLICT (client_id) DO UPDATE
                              SET score = EXCLUDED.score, expires_at = EXCLUDED.expires_at
                              WHERE quarantine.reason = 'anomaly'""",
                    clientId, detection.score(), EXPIRY_MINUTES);
            if (rows > 0) {
                log.info("quarantined {} for {} min (score {})", clientId, EXPIRY_MINUTES,
                        String.format("%.3f", detection.score()));
            }
        } catch (DataAccessException e) {
            log.warn("could not quarantine {}: {}", clientId, e.toString());
        }
    }
}
