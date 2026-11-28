package dev.bulwark.gateway.events;

import java.time.Duration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.redis.connection.RedisStreamCommands.XAddOptions;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.ReactiveStringRedisTemplate;
import org.springframework.stereotype.Component;

import dev.bulwark.common.RequestEvent;

/**
 * Appends request events to the {@code bulwark:events} Redis stream for sentinel.
 *
 * <p>Fire-and-forget: the response has already been sent when this runs, and a failed or slow
 * write is logged and dropped rather than retried. Losing a few events only makes one window
 * slightly less accurate; holding up the gateway would be worse.
 */
@Component
public class RequestEventPublisher {

    private static final Logger log = LoggerFactory.getLogger(RequestEventPublisher.class);
    private static final Duration TIMEOUT = Duration.ofSeconds(1);

    private final ReactiveStringRedisTemplate redis;
    private final XAddOptions addOptions;

    public RequestEventPublisher(ReactiveStringRedisTemplate redis,
                                 @Value("${bulwark.events.stream-max-length}") long maxLength) {
        this.redis = redis;
        this.addOptions = XAddOptions.maxlen(maxLength).approximateTrimming(true);
    }

    public void publish(RequestEvent event) {
        if (event.clientId() == null) {
            // Unidentified requests (401) can't be attributed to a client, so sentinel can't use them.
            return;
        }
        redis.opsForStream()
                .add(StreamRecords.mapBacked(event.toFields()).withStreamKey(RequestEvent.STREAM), addOptions)
                .timeout(TIMEOUT)
                .subscribe(
                        id -> { },
                        e -> log.warn("dropped request event for {}: {}", event.clientId(), e.toString()));
    }
}
