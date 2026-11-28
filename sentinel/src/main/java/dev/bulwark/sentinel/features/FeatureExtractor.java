package dev.bulwark.sentinel.features;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
        Map<String, Integer> pathCounts = new HashMap<>();
        for (RequestEvent e : events) {
            pathCounts.merge(e.path(), 1, Integer::sum);
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
                pathCounts.size(),
                entropy(pathCounts.values(), count),
                0,
                0,
                0,
                0);
    }

    /**
     * Shannon entropy in bits of a distribution given as counts. 0 when every request hit the same
     * path; log2(n) when n paths were hit equally often. Scrapers and enumerators walk many paths
     * once each, which pushes this up; normal users revisit a few.
     */
    static double entropy(Iterable<Integer> counts, int total) {
        double h = 0;
        for (int c : counts) {
            double p = (double) c / total;
            h -= p * (Math.log(p) / Math.log(2));
        }
        return h == 0 ? 0 : h; // avoid -0.0
    }
}
