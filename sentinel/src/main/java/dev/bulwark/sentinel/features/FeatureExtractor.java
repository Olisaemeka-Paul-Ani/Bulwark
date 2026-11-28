package dev.bulwark.sentinel.features;

import java.util.List;

import dev.bulwark.common.FeatureVector;
import dev.bulwark.common.RequestEvent;
import dev.bulwark.sentinel.window.ClosedWindow;

/**
 * Turns one client's window of requests into a {@link FeatureVector}. Pure and stateless so it
 * can be unit tested with hand-built windows.
 */
public final class FeatureExtractor {

    private FeatureExtractor() {
    }

    public static FeatureVector extract(ClosedWindow window) {
        List<RequestEvent> events = window.events();
        int count = events.size();
        if (count == 0) {
            throw new IllegalArgumentException("window has no events");
        }

        int clientErrors = 0;
        int authFailures = 0;
        for (RequestEvent e : events) {
            int status = e.status();
            if (status == 401 || status == 403) {
                authFailures++;
            } else if (status >= 400 && status < 500 && status != 429) {
                clientErrors++;
            }
        }

        return new FeatureVector(
                window.clientId(),
                window.windowStartMs(),
                count,
                (double) clientErrors / count,
                (double) authFailures / count,
                0,
                0,
                0,
                0,
                0,
                0);
    }
}
