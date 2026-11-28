package dev.bulwark.sentinel.window;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import dev.bulwark.common.RequestEvent;
import dev.bulwark.sentinel.stream.RequestEventHandler;

/**
 * Groups request events into tumbling windows per client.
 *
 * <p>Events are placed by their own timestamp ({@code ts}, set by the gateway), not by when they
 * arrive, so a window means the same thing however far behind the consumer is. A window closes
 * once the wall clock passes its end plus a grace period, which absorbs events from the two
 * gateways arriving slightly out of order. Events for an already-closed window are dropped.
 *
 * <p>The consumer thread adds events and the scheduler thread closes windows, so both paths
 * synchronize on this object; listeners are called outside the lock.
 */
@Component
@EnableConfigurationProperties(WindowProperties.class)
public class WindowAggregator implements RequestEventHandler {

    private static final Logger log = LoggerFactory.getLogger(WindowAggregator.class);

    private final long sizeMs;
    private final long graceMs;
    private final List<WindowListener> listeners;
    private final Clock clock;

    /** clientId -> windowStart -> events. */
    private final Map<String, Map<Long, List<RequestEvent>>> open = new HashMap<>();
    /** Per client, the newest window start already closed; older events are late. */
    private final Map<String, Long> lastClosedStart = new HashMap<>();
    private long lateEvents;

    @Autowired
    public WindowAggregator(WindowProperties properties, List<WindowListener> listeners) {
        this(properties, listeners, Clock.systemUTC());
    }

    public WindowAggregator(WindowProperties properties, List<WindowListener> listeners, Clock clock) {
        this.sizeMs = properties.size().toMillis();
        this.graceMs = properties.grace().toMillis();
        this.listeners = listeners;
        this.clock = clock;
    }

    public long windowStart(long ts) {
        return ts - Math.floorMod(ts, sizeMs);
    }

    @Override
    public synchronized void onEvent(RequestEvent event) {
        if (event.clientId() == null) {
            return;
        }
        long start = windowStart(event.ts());
        Long closed = lastClosedStart.get(event.clientId());
        if (closed != null && start <= closed) {
            lateEvents++;
            return;
        }
        open.computeIfAbsent(event.clientId(), k -> new HashMap<>())
                .computeIfAbsent(start, k -> new ArrayList<>())
                .add(event);
    }

    /** Closes every window whose end plus grace has passed. */
    @Scheduled(fixedDelay = 1000)
    public void closeExpired() {
        for (ClosedWindow window : collectExpired(clock.millis())) {
            log.debug("closed window {}@{} with {} events", window.clientId(), window.windowStartMs(), window.events().size());
            for (WindowListener listener : listeners) {
                try {
                    listener.onWindowClosed(window);
                } catch (RuntimeException e) {
                    log.warn("window listener {} failed for {}: {}", listener.getClass().getSimpleName(),
                            window.clientId(), e.toString());
                }
            }
        }
    }

    synchronized List<ClosedWindow> collectExpired(long nowMs) {
        List<ClosedWindow> closed = new ArrayList<>();
        Iterator<Map.Entry<String, Map<Long, List<RequestEvent>>>> clients = open.entrySet().iterator();
        while (clients.hasNext()) {
            Map.Entry<String, Map<Long, List<RequestEvent>>> client = clients.next();
            Iterator<Map.Entry<Long, List<RequestEvent>>> windows = client.getValue().entrySet().iterator();
            while (windows.hasNext()) {
                Map.Entry<Long, List<RequestEvent>> window = windows.next();
                long start = window.getKey();
                if (start + sizeMs + graceMs <= nowMs) {
                    List<RequestEvent> events = window.getValue();
                    events.sort(Comparator.comparingLong(RequestEvent::ts));
                    closed.add(new ClosedWindow(client.getKey(), start, sizeMs, List.copyOf(events)));
                    lastClosedStart.merge(client.getKey(), start, Math::max);
                    windows.remove();
                }
            }
            if (client.getValue().isEmpty()) {
                clients.remove();
            }
        }
        // Forget clients that have been idle long enough that no late event can still arrive.
        lastClosedStart.values().removeIf(start -> start + 2 * (sizeMs + graceMs) <= nowMs);
        closed.sort(Comparator.comparingLong(ClosedWindow::windowStartMs));
        return closed;
    }

    public synchronized long lateEvents() {
        return lateEvents;
    }
}
