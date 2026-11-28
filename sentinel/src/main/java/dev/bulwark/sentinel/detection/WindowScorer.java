package dev.bulwark.sentinel.detection;

import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import dev.bulwark.common.FeatureVector;
import dev.bulwark.sentinel.features.FeatureExtractor;
import dev.bulwark.sentinel.model.AnomalyModel;
import dev.bulwark.sentinel.window.ClosedWindow;
import dev.bulwark.sentinel.window.WindowListener;

/**
 * Scores every closed window with the current model and passes the result on. Until a model has
 * been trained, windows are only buffered and logged.
 */
@Component
public class WindowScorer implements WindowListener {

    private static final Logger log = LoggerFactory.getLogger(WindowScorer.class);
    static final double THRESHOLD = 0.6;

    private final AnomalyModel model;
    private final List<DetectionListener> listeners;

    public WindowScorer(AnomalyModel model, List<DetectionListener> listeners) {
        this.model = model;
        this.listeners = listeners;
    }

    @Override
    public void onWindowClosed(ClosedWindow window) {
        model.current().ifPresent(trained -> {
            FeatureVector features = FeatureExtractor.extract(window);
            double score = trained.forest().score(features.toArray());
            Detection detection = new Detection(features, score, score >= THRESHOLD);
            if (detection.flagged()) {
                log.info("anomalous window client={} start={} score={}", features.clientId(),
                        features.windowStartMs(), String.format("%.3f", score));
            } else {
                log.debug("window client={} score={}", features.clientId(), String.format("%.3f", score));
            }
            for (DetectionListener listener : listeners) {
                listener.onDetection(detection);
            }
        });
    }
}
