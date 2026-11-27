package dev.bulwark.gateway.client;

import org.springframework.r2dbc.core.DatabaseClient;
import org.springframework.stereotype.Repository;

import reactor.core.publisher.Mono;

/**
 * Reads clients from Postgres over R2DBC, so a cache miss in the request path never blocks the
 * event loop. Request-path callers should go through the client cache rather than hitting this directly.
 */
@Repository
public class ClientRepository {

    private final DatabaseClient db;

    public ClientRepository(DatabaseClient db) {
        this.db = db;
    }

    public Mono<Client> findByApiKey(String apiKey) {
        return db.sql("SELECT client_id, tier, is_admin FROM clients WHERE api_key = :apiKey")
                .bind("apiKey", apiKey)
                .map(row -> new Client(
                        row.get("client_id", String.class),
                        row.get("tier", String.class),
                        Boolean.TRUE.equals(row.get("is_admin", Boolean.class))))
                .one();
    }
}
