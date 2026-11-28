package dev.bulwark.sentinel.features;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.Test;

import dev.bulwark.common.FeatureVector;
import dev.bulwark.common.RequestEvent;
import dev.bulwark.sentinel.window.ClosedWindow;

class FeatureExtractorTest {

    private static final long START = 1_800_000_000_000L;
    private static final long SIZE = 30_000;

    private static RequestEvent event(long offsetMs, String path, int status) {
        return event(offsetMs, path, status, 0);
    }

    private static RequestEvent event(long offsetMs, String path, int status, long requestBytes) {
        return new RequestEvent("c", START + offsetMs, "GET", path, status, 5, requestBytes, 100, "gateway-1");
    }

    private static FeatureVector extract(List<RequestEvent> events) {
        return FeatureExtractor.extract(new ClosedWindow("c", START, SIZE, events));
    }

    @Test
    void copiesIdentityAndCountsRequests() {
        FeatureVector v = extract(List.of(event(0, "/a", 200), event(10, "/a", 200), event(20, "/a", 200)));

        assertThat(v.clientId()).isEqualTo("c");
        assertThat(v.windowStartMs()).isEqualTo(START);
        assertThat(v.requestCount()).isEqualTo(3);
    }

    @Test
    void statusRatiosDoNotDoubleCount() {
        FeatureVector v = extract(List.of(
                event(0, "/a", 200),
                event(1, "/a", 404),
                event(2, "/a", 400),
                event(3, "/a", 401),
                event(4, "/a", 403),
                event(5, "/a", 429),
                event(6, "/a", 429),
                event(7, "/a", 500)));

        assertThat(v.errorRatio4xx()).isEqualTo(2 / 8.0);   // 404, 400
        assertThat(v.authFailRatio()).isEqualTo(2 / 8.0);   // 401, 403
        assertThat(v.rateLimitedRatio()).isEqualTo(2 / 8.0); // 429s
    }

    @Test
    void allSuccessfulRequestsHaveZeroRatios() {
        FeatureVector v = extract(List.of(event(0, "/a", 200), event(5, "/b", 201)));

        assertThat(v.errorRatio4xx()).isZero();
        assertThat(v.authFailRatio()).isZero();
        assertThat(v.rateLimitedRatio()).isZero();
    }

    @Test
    void singlePathHasZeroEntropy() {
        FeatureVector v = extract(List.of(event(0, "/orders", 200), event(1, "/orders", 200), event(2, "/orders", 200)));

        assertThat(v.distinctPaths()).isEqualTo(1);
        assertThat(v.pathEntropy()).isEqualTo(0.0);
    }

    @Test
    void uniformPathsHaveLog2Entropy() {
        List<RequestEvent> events = new ArrayList<>();
        for (int i = 0; i < 8; i++) {
            events.add(event(i, "/users/" + i, 200));
        }

        FeatureVector v = extract(events);

        assertThat(v.distinctPaths()).isEqualTo(8);
        assertThat(v.pathEntropy()).isCloseTo(3.0, within(1e-9));
    }

    @Test
    void skewedPathsHaveLowerEntropyThanUniform() {
        // p = 3/4, 1/4 -> H = 0.811 bits
        FeatureVector v = extract(List.of(event(0, "/a", 200), event(1, "/a", 200), event(2, "/a", 200), event(3, "/b", 200)));

        assertThat(v.pathEntropy()).isCloseTo(0.8113, within(1e-4));
    }

    @Test
    void regularTimingHasZeroVariation() {
        FeatureVector v = extract(List.of(event(0, "/a", 200), event(500, "/a", 200), event(1000, "/a", 200), event(1500, "/a", 200)));

        assertThat(v.meanInterArrivalMs()).isEqualTo(500.0);
        assertThat(v.cvInterArrival()).isEqualTo(0.0);
    }

    @Test
    void irregularTimingHasPositiveVariation() {
        // gaps 100, 300 -> mean 200, population stddev 100, cv 0.5
        FeatureVector v = extract(List.of(event(0, "/a", 200), event(100, "/a", 200), event(400, "/a", 200)));

        assertThat(v.meanInterArrivalMs()).isEqualTo(200.0);
        assertThat(v.cvInterArrival()).isCloseTo(0.5, within(1e-9));
    }

    @Test
    void singleRequestFallsBackToWindowLength() {
        FeatureVector v = extract(List.of(event(0, "/a", 200)));

        assertThat(v.meanInterArrivalMs()).isEqualTo((double) SIZE);
        assertThat(v.cvInterArrival()).isZero();
    }

    @Test
    void simultaneousRequestsDoNotDivideByZero() {
        FeatureVector v = extract(List.of(event(0, "/a", 200), event(0, "/a", 200), event(0, "/a", 200)));

        assertThat(v.meanInterArrivalMs()).isZero();
        assertThat(v.cvInterArrival()).isZero();
    }

    @Test
    void meanRequestBytesAveragesBodies() {
        FeatureVector v = extract(List.of(event(0, "/a", 201, 100), event(1, "/a", 200, 0), event(2, "/a", 201, 50)));

        assertThat(v.meanRequestBytes()).isEqualTo(50.0);
    }

    @Test
    void featureArrayMatchesNames() {
        FeatureVector v = extract(List.of(event(0, "/a", 200), event(10, "/b", 404)));

        double[] array = v.toArray();

        assertThat(array).hasSize(FeatureVector.FEATURE_NAMES.size());
        assertThat(array[FeatureVector.FEATURE_NAMES.indexOf("requestCount")]).isEqualTo(2);
        assertThat(array[FeatureVector.FEATURE_NAMES.indexOf("distinctPaths")]).isEqualTo(2);
        assertThat(array[FeatureVector.FEATURE_NAMES.indexOf("errorRatio4xx")]).isEqualTo(0.5);
    }

    @Test
    void emptyWindowIsRejected() {
        assertThatThrownBy(() -> extract(List.of())).isInstanceOf(IllegalArgumentException.class);
    }
}
