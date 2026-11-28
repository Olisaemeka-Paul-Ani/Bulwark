package dev.bulwark.sentinel.features;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.bulwark.common.FeatureVector;
import dev.bulwark.sentinel.window.ClosedWindow;
import dev.bulwark.sentinel.window.WindowListener;

/** Logs the feature vector of every closed window, one line each, for eyeballing behaviour. */
@Component
public class FeatureLogger implements WindowListener {

    private static final Logger log = LoggerFactory.getLogger(FeatureLogger.class);

    @Override
    public void onWindowClosed(ClosedWindow window) {
        FeatureVector v = FeatureExtractor.extract(window);
        log.info("window client={} start={} count={} err4xx={} authFail={} paths={} entropy={} "
                        + "gapMeanMs={} gapCv={} reqBytes={} limited={}",
                v.clientId(), v.windowStartMs(), v.requestCount(),
                fmt(v.errorRatio4xx()), fmt(v.authFailRatio()), v.distinctPaths(), fmt(v.pathEntropy()),
                fmt(v.meanInterArrivalMs()), fmt(v.cvInterArrival()), fmt(v.meanRequestBytes()),
                fmt(v.rateLimitedRatio()));
    }

    private static String fmt(double value) {
        return String.format("%.3f", value);
    }
}
