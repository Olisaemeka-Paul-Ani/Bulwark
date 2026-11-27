package dev.bulwark.gateway.client;

import java.util.Optional;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import com.github.benmanes.caffeine.cache.AsyncCache;
import com.github.benmanes.caffeine.cache.Caffeine;

import reactor.core.publisher.Mono;

/**
 * API key → client lookup used on every request. Postgres is only hit on a miss, and concurrent
 * misses for the same key share one query. Unknown keys are cached too (as empty), so a flood of
 * requests with a bad key doesn't turn into a flood of queries; the max size bounds memory when
 * the bad keys are random.
 */
@Component
@EnableConfigurationProperties(ClientCacheProperties.class)
public class ClientCache {

    private final ClientRepository repository;
    private final AsyncCache<String, Optional<Client>> cache;

    public ClientCache(ClientRepository repository, ClientCacheProperties properties) {
        this.repository = repository;
        this.cache = Caffeine.newBuilder()
                .expireAfterWrite(properties.ttl())
                .maximumSize(properties.maxSize())
                .buildAsync();
    }

    public Mono<Client> findByApiKey(String apiKey) {
        return Mono.fromFuture(() -> cache.get(apiKey, (key, executor) -> repository.findByApiKey(key)
                        .map(Optional::of)
                        .defaultIfEmpty(Optional.empty())
                        .toFuture()))
                .flatMap(Mono::justOrEmpty);
    }
}
