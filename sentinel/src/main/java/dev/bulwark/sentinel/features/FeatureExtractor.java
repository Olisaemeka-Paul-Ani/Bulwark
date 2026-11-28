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
        int rateLimited = 0;
        long requestBytes = 0;
        Map<String, Integer> pathCounts = new HashMap<>();
        for (RequestEvent e : events) {
            pathCounts.merge(e.path(), 1, Integer::sum);
            requestBytes += e.requestBytes();
            int status = e.status();
            if (status == 429) {
                rateLimited++;
            } else if (status == 401 || status == 403) {
                authFailures++;
            } else if (status >= 400 && status < 500) {
                clientErrors++;
            }
        }

        double[] gaps = interArrivalGaps(events);
        double meanGap = gaps.length == 0 ? window.windowSizeMs() : mean(gaps);
        double cvGap = gaps.length < 2 || meanGap == 0 ? 0 : stddev(gaps, meanGap) / meanGap;

        return new FeatureVector(
                window.clientId(),
                window.windowStartMs(),
                count,
                (double) clientErrors / count,
                (double) authFailures / count,
                pathCounts.size(),
                entropy(pathCounts.values(), count),
                meanGap,
                cvGap,
                (double) requestBytes / count,
                (double) rateLimited / count);
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

    /**
     * Gaps between consecutive requests (events are sorted by ts). With a single request there is
     * no gap, so the mean falls back to the window length and the CV to 0.
     */
    static double[] interArrivalGaps(List<RequestEvent> events) {
        double[] gaps = new double[Math.max(0, events.size() - 1)];
        for (int i = 1; i < events.size(); i++) {
            gaps[i - 1] = events.get(i).ts() - events.get(i - 1).ts();
        }
        return gaps;
    }

    private static double mean(double[] values) {
        double sum = 0;
        for (double v : values) {
            sum += v;
        }
        return sum / values.length;
    }

    /** Population standard deviation: the window is the whole population we're describing. */
    private static double stddev(double[] values, double mean) {
        double squares = 0;
        for (double v : values) {
            squares += (v - mean) * (v - mean);
        }
        return Math.sqrt(squares / values.length);
    }
}
