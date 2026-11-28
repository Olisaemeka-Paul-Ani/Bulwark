package dev.bulwark.sentinel.stream;

import java.time.Duration;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.SmartLifecycle;
import org.springframework.data.redis.RedisSystemException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.connection.stream.StreamReadOptions;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Component;

import dev.bulwark.common.RequestEvent;

/**
 * Reads {@code bulwark:events} as a member of a Redis consumer group and passes each event to the
 * {@link RequestEventHandler}s, acknowledging a batch once it has been handled.
 *
 * <p>The group starts at the end of the stream ({@code $}): sentinel scores live behaviour, so
 * replaying a backlog of old events on first start would only produce stale windows. A single
 * thread does all reading and handling, so handlers don't need to be thread-safe with each other.
 */
@Component
@EnableConfigurationProperties(StreamProperties.class)
public class EventStreamConsumer implements SmartLifecycle {

    private static final Logger log = LoggerFactory.getLogger(EventStreamConsumer.class);
    private static final Duration BLOCK = Duration.ofSeconds(1);
    private static final Duration RETRY_DELAY = Duration.ofSeconds(2);

    private final StringRedisTemplate redis;
    private final StreamProperties properties;
    private final List<RequestEventHandler> handlers;

    private volatile boolean running;
    private volatile Thread thread;

    public EventStreamConsumer(StringRedisTemplate redis, StreamProperties properties,
                               List<RequestEventHandler> handlers) {
        this.redis = redis;
        this.properties = properties;
        this.handlers = handlers;
    }

    @Override
    public void start() {
        running = true;
        thread = Thread.ofPlatform().name("event-stream-consumer").daemon().start(this::run);
    }

    @Override
    public void stop() {
        running = false;
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
        boolean groupReady = false;
        while (running) {
            try {
                if (!groupReady) {
                    ensureGroup();
                    groupReady = true;
                    log.info("consuming {} as {}/{}", RequestEvent.STREAM, properties.group(), properties.consumer());
                }
                pollOnce();
            } catch (RuntimeException e) {
                if (!running) {
                    break;
                }
                // Redis restarted (the group may be gone) or is unreachable: recreate and retry.
                groupReady = false;
                log.warn("event stream read failed, retrying in {}s: {}", RETRY_DELAY.toSeconds(), e.toString());
                try {
                    Thread.sleep(RETRY_DELAY);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
    }

    private void ensureGroup() {
        try {
            redis.opsForStream().createGroup(RequestEvent.STREAM, ReadOffset.latest(), properties.group());
        } catch (RedisSystemException e) {
            String message = e.getMostSpecificCause().getMessage();
            if (message == null || !message.contains("BUSYGROUP")) {
                throw e;
            }
            // Group already exists: keep its position.
        }
    }

    private void pollOnce() {
        List<MapRecord<String, Object, Object>> records = redis.opsForStream().read(
                Consumer.from(properties.group(), properties.consumer()),
                StreamReadOptions.empty().count(properties.batchSize()).block(BLOCK),
                StreamOffset.create(RequestEvent.STREAM, ReadOffset.lastConsumed()));
        if (records == null || records.isEmpty()) {
            return;
        }
        for (MapRecord<String, Object, Object> record : records) {
            handle(record);
        }
        RecordId[] ids = records.stream().map(MapRecord::getId).toArray(RecordId[]::new);
        redis.opsForStream().acknowledge(RequestEvent.STREAM, properties.group(), ids);
    }

    private void handle(MapRecord<String, Object, Object> record) {
        RequestEvent event;
        try {
            @SuppressWarnings({"unchecked", "rawtypes"})
            Map<String, String> fields = (Map) record.getValue();
            event = RequestEvent.fromFields(fields);
        } catch (RuntimeException e) {
            log.warn("skipping malformed event {}: {}", record.getId(), e.toString());
            return;
        }
        for (RequestEventHandler handler : handlers) {
            handler.onEvent(event);
        }
    }
}
