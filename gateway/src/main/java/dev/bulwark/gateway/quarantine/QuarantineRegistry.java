package dev.bulwark.gateway.quarantine;

import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.stereotype.Component;

/**
 * This node's copy of the quarantine table, read on every request.
 *
 * <p>Lookups are a single {@link ConcurrentHashMap} get, so the request path never waits on
 * Postgres. Writes come only from the quarantine listener (notifications and resyncs) and the
 * expiry sweep. Expired entries are ignored on lookup even before the sweep removes them, so an
 * entry never blocks a client past its {@code expiresAt}.
 */
@Component
public class QuarantineRegistry {

    private final Map<String, QuarantineEntry> entries = new ConcurrentHashMap<>();
    private final Clock clock;

    public QuarantineRegistry() {
        this(Clock.systemUTC());
    }

    QuarantineRegistry(Clock clock) {
        this.clock = clock;
    }

    /** The active quarantine for this client, if any. */
    public Optional<QuarantineEntry> find(String clientId) {
        QuarantineEntry entry = entries.get(clientId);
        if (entry == null || entry.isExpired(clock.instant())) {
            return Optional.empty();
        }
        return Optional.of(entry);
    }

    public void put(QuarantineEntry entry) {
        entries.put(entry.clientId(), entry);
    }

    public void remove(String clientId) {
        entries.remove(clientId);
    }

    /**
     * Makes the registry match a full snapshot of the table. Entries are updated in place rather
     * than swapping maps, so concurrent lookups always see either the old or the new entry.
     */
    public void replaceAll(Collection<QuarantineEntry> snapshot) {
        Map<String, QuarantineEntry> byClient = new ConcurrentHashMap<>();
        snapshot.forEach(e -> byClient.put(e.clientId(), e));
        entries.keySet().retainAll(byClient.keySet());
        entries.putAll(byClient);
    }

    /** Drops expired entries; returns how many were removed. */
    public int removeExpired() {
        Instant now = clock.instant();
        int before = entries.size();
        entries.values().removeIf(e -> e.isExpired(now));
        return before - entries.size();
    }

    public List<QuarantineEntry> snapshot() {
        return List.copyOf(entries.values());
    }

    public int size() {
        return entries.size();
    }
}
