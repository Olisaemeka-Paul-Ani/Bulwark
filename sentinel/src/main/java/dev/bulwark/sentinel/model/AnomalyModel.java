package dev.bulwark.sentinel.model;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.stereotype.Component;

import dev.bulwark.common.FeatureVector;
import smile.anomaly.IsolationForest;

/**
 * Holds the current Isolation Forest.
 *
 * <p>An Isolation Forest builds random trees that keep splitting the data on a random feature at a
 * random value. Points unlike the rest get isolated after only a few splits, so a short average
 * path length means "anomalous". It learns only from normal data, which is all we have labels-free
 * access to, and it needs no feature scaling because each split is drawn within a feature's own range.
 */
@Component
@EnableConfigurationProperties(ModelProperties.class)
public class AnomalyModel {

    /** A trained forest plus what it was trained on. */
    public record Trained(IsolationForest forest, int windows, Instant trainedAt) implements java.io.Serializable {
    }

    private final ModelProperties properties;
    private volatile Trained current;

    public AnomalyModel(ModelProperties properties) {
        this.properties = properties;
    }

    public Trained train(List<FeatureVector> vectors) {
        if (vectors.size() < properties.minTrainingWindows()) {
            throw new NotEnoughDataException(vectors.size(), properties.minTrainingWindows());
        }
        double[][] data = vectors.stream().map(FeatureVector::toArray).toArray(double[][]::new);
        IsolationForest forest = IsolationForest.fit(data,
                new IsolationForest.Options(properties.trees(), 0, properties.subsample(), 0));
        Trained trained = new Trained(forest, vectors.size(), Instant.now());
        current = trained;
        return trained;
    }

    public Optional<Trained> current() {
        return Optional.ofNullable(current);
    }

    /** Thrown when the training buffer is too small to train on. */
    public static class NotEnoughDataException extends RuntimeException {

        public NotEnoughDataException(int have, int need) {
            super("need at least " + need + " windows to train, have " + have);
        }
    }
}
