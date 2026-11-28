package dev.bulwark.sentinel.eval;

import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/**
 * {@code GET /sentinel/eval}: scores past detections against ground truth.
 *
 * <p>This is the only place that reads {@code clients.is_attacker}. Scoring never does: it sees
 * only feature vectors, so the labels can't leak into detection.
 */
@RestController
public class EvalController {

    private final JdbcTemplate jdbc;

    public EvalController(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /**
     * @param since only windows starting at or after this instant (default: the last hour)
     */
    @GetMapping("/sentinel/eval")
    public EvalReport eval(@RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant since) {
        Instant from = since != null ? since : Instant.now().minus(Duration.ofHours(1));
        List<LabeledDetection> rows = jdbc.query("""
                        SELECT d.client_id, c.is_attacker, d.window_start, d.detected_at, d.flagged
                        FROM detections d
                        JOIN clients c ON c.client_id = d.client_id
                        WHERE d.window_start >= ?""",
                (rs, i) -> new LabeledDetection(
                        rs.getString("client_id"),
                        Evaluator.attackType(rs.getString("client_id"), rs.getBoolean("is_attacker")),
                        rs.getTimestamp("window_start").toInstant(),
                        rs.getTimestamp("detected_at").toInstant(),
                        rs.getBoolean("flagged")),
                Timestamp.from(from));
        return Evaluator.evaluate(rows);
    }
}
