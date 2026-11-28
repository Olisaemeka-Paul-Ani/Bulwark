package dev.bulwark.sentinel.detection;

import java.sql.Timestamp;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;

import dev.bulwark.common.FeatureVector;

/** Writes every scored window to the {@code detections} table (Grafana panel and eval input). */
@Component
public class DetectionRecorder implements DetectionListener {

    private static final Logger log = LoggerFactory.getLogger(DetectionRecorder.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper json;

    public DetectionRecorder(JdbcTemplate jdbc, ObjectMapper json) {
        this.jdbc = jdbc;
        this.json = json;
    }

    @Override
    public void onDetection(Detection detection) {
        FeatureVector f = detection.features();
        try {
            jdbc.update("""
                            INSERT INTO detections (client_id, window_start, score, flagged, features)
                            VALUES (?, ?, ?, ?, ?::jsonb)""",
                    f.clientId(),
                    Timestamp.from(Instant.ofEpochMilli(f.windowStartMs())),
                    detection.score(),
                    detection.flagged(),
                    json.writeValueAsString(f));
        } catch (DataAccessException | JsonProcessingException e) {
            // Losing a detection row loses history, not protection: quarantine is written separately.
            log.warn("could not record detection for {}: {}", f.clientId(), e.toString());
        }
    }
}
