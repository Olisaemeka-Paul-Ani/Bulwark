package dev.bulwark.sentinel.model;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
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
 *
 * <p>Each trained model is saved to disk and loaded again at startup, so a restart doesn't leave
 * sentinel unable to score until someone retrains.
 */
@Component
@EnableConfigurationProperties(ModelProperties.class)
public class AnomalyModel implements InitializingBean {

    private static final Logger log = LoggerFactory.getLogger(AnomalyModel.class);

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
        save(trained);
        return trained;
    }

    @Override
    public void afterPropertiesSet() {
        Path path = properties.path();
        if (!Files.exists(path)) {
            log.info("no saved model at {}; train one with POST /sentinel/train", path);
            return;
        }
        try (InputStream in = Files.newInputStream(path); ObjectInputStream objects = new ObjectInputStream(in)) {
            current = (Trained) objects.readObject();
            log.info("loaded model trained on {} windows at {}", current.windows(), current.trainedAt());
        } catch (IOException | ClassNotFoundException | ClassCastException e) {
            // A model from an incompatible build: run unscored until retrained rather than fail to start.
            log.warn("could not load model from {}, ignoring it: {}", path, e.toString());
        }
    }

    /** Writes to a temp file then renames, so a crash mid-write never leaves a corrupt model. */
    private void save(Trained trained) {
        Path path = properties.path().toAbsolutePath();
        try {
            Files.createDirectories(path.getParent());
            Path tmp = Files.createTempFile(path.getParent(), "model", ".tmp");
            try (OutputStream out = Files.newOutputStream(tmp); ObjectOutputStream objects = new ObjectOutputStream(out)) {
                objects.writeObject(trained);
            }
            Files.move(tmp, path, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            log.info("saved model to {}", path);
        } catch (IOException e) {
            // The model is still live in memory; it just won't survive a restart.
            log.warn("could not save model to {}: {}", path, e.toString());
        }
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
