package dev.bulwark.gateway.admin;

import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.Map;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.r2dbc.spi.Readable;
import reactor.core.publisher.Flux;
import reactor.core.publisher.Mono;

/**
 * Manual quarantine management. Writes go straight to Postgres; the {@code quarantine_changed}
 * trigger then tells every gateway node (including this one) to update its registry, so all nodes
 * learn about admin changes the same way they learn about sentinel's.
 */
@RestController
@RequestMapping("/admin/quarantine")
public class AdminQuarantineController {

    static final int MAX_MINUTES = 7 * 24 * 60;

    private static final String COLUMNS = "client_id, reason, score, created_at, expires_at";

    private final DatabaseClient db;

    public AdminQuarantineController(DatabaseClient db) {
        this.db = db;
    }

    @GetMapping
    public Flux<QuarantineView> list() {
        return db.sql("SELECT " + COLUMNS + " FROM quarantine WHERE expires_at > now() ORDER BY created_at")
                .map(AdminQuarantineController::toView)
                .all();
    }

    @PostMapping
    public Mono<ResponseEntity<Object>> quarantine(@RequestBody QuarantineRequest request) {
        if (request.clientId() == null || request.clientId().isBlank()) {
            return Mono.just(badRequest("clientId is required"));
        }
        if (request.minutes() == null || request.minutes() < 1 || request.minutes() > MAX_MINUTES) {
            return Mono.just(badRequest("minutes must be between 1 and " + MAX_MINUTES));
        }
        String reason = request.reason() == null || request.reason().isBlank() ? "manual" : request.reason();

        Mono<ResponseEntity<Object>> upsert = db.sql("""
                        INSERT INTO quarantine (client_id, reason, score, expires_at)
                        VALUES (:clientId, :reason, NULL, now() + make_interval(mins => :minutes))
                        ON CONFLICT (client_id) DO UPDATE
                          SET reason = EXCLUDED.reason, score = NULL,
                              created_at = now(), expires_at = EXCLUDED.expires_at
                        RETURNING\s""" + COLUMNS)
                .bind("clientId", request.clientId())
                .bind("reason", reason)
                .bind("minutes", request.minutes())
                .map(AdminQuarantineController::toView)
                .one()
                .map(view -> ResponseEntity.status(HttpStatus.CREATED).body(view));

        return clientExists(request.clientId())
                .flatMap(exists -> exists ? upsert : Mono.just(notFound(request.clientId())));
    }

    @DeleteMapping("/{clientId}")
    public Mono<ResponseEntity<Object>> release(@PathVariable String clientId) {
        return db.sql("DELETE FROM quarantine WHERE client_id = :clientId")
                .bind("clientId", clientId)
                .fetch()
                .rowsUpdated()
                .map(rows -> rows > 0
                        ? ResponseEntity.noContent().build()
                        : ResponseEntity.status(HttpStatus.NOT_FOUND)
                                .body(Map.of("error", clientId + " is not quarantined")));
    }

    private Mono<Boolean> clientExists(String clientId) {
        return db.sql("SELECT 1 FROM clients WHERE client_id = :clientId")
                .bind("clientId", clientId)
                .fetch()
                .first()
                .hasElement();
    }

    private static QuarantineView toView(Readable row) {
        return new QuarantineView(
                row.get("client_id", String.class),
                row.get("reason", String.class),
                row.get("score", Double.class),
                toInstant(row.get("created_at", OffsetDateTime.class)),
                toInstant(row.get("expires_at", OffsetDateTime.class)));
    }

    private static Instant toInstant(OffsetDateTime value) {
        return value == null ? null : value.toInstant();
    }

    private static ResponseEntity<Object> badRequest(String message) {
        return ResponseEntity.badRequest().body(Map.of("error", message));
    }

    private static ResponseEntity<Object> notFound(String clientId) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(Map.of("error", "unknown client " + clientId));
    }
}
