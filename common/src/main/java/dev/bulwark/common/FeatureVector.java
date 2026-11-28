package dev.bulwark.common;

import java.util.List;

/**
 * Behaviour of one client during one tumbling window, as fed to the Isolation Forest.
 *
 * @param clientId           client the window belongs to (not a model input)
 * @param windowStartMs      epoch millis at the start of the window (not a model input)
 * @param requestCount       requests in the window
 * @param errorRatio4xx      share of responses that were 4xx, excluding 401/403/429
 * @param authFailRatio      share of responses that were 401 or 403
 * @param distinctPaths      number of different paths requested
 * @param pathEntropy        Shannon entropy (bits) of the path distribution
 * @param meanInterArrivalMs mean gap between consecutive requests
 * @param cvInterArrival     coefficient of variation of those gaps (stddev / mean); low = robotic
 * @param meanRequestBytes   mean request body size
 * @param rateLimitedRatio   share of responses that were 429
 */
public record FeatureVector(
        String clientId,
        long windowStartMs,
        int requestCount,
        double errorRatio4xx,
        double authFailRatio,
        int distinctPaths,
        double pathEntropy,
        double meanInterArrivalMs,
        double cvInterArrival,
        double meanRequestBytes,
        double rateLimitedRatio) {

    /** Model input names, in the same order as {@link #toArray()}. */
    public static final List<String> FEATURE_NAMES = List.of(
            "requestCount", "errorRatio4xx", "authFailRatio", "distinctPaths", "pathEntropy",
            "meanInterArrivalMs", "cvInterArrival", "meanRequestBytes", "rateLimitedRatio");

    /** The model inputs, in {@link #FEATURE_NAMES} order. */
    public double[] toArray() {
        return new double[] {
                requestCount, errorRatio4xx, authFailRatio, distinctPaths, pathEntropy,
                meanInterArrivalMs, cvInterArrival, meanRequestBytes, rateLimitedRatio};
    }
}
