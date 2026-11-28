package dev.bulwark.sentinel.model;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import dev.bulwark.common.FeatureVector;
import smile.anomaly.IsolationForest;

class AnomalyModelTest {

    @TempDir
    Path dir;

    private final Random random = new Random(42);
    private AnomalyModel model;

    @BeforeEach
    void setUp() {
        model = new AnomalyModel(properties());
    }

    private ModelProperties properties() {
        return new ModelProperties(20_000, 50, 200, 0.7, dir.resolve("model.bin"));
    }

    /** A plausible human window: ~20 requests, a few paths, irregular gaps, rare 404s. */
    private FeatureVector normal() {
        int count = 10 + random.nextInt(25);
        int paths = Math.max(1, (int) (count * (0.3 + 0.4 * random.nextDouble())));
        return new FeatureVector("norm", 0, count,
                random.nextDouble() < 0.3 ? 0.05 * random.nextDouble() : 0,
                0,
                paths,
                Math.log(paths) / Math.log(2) * (0.8 + 0.2 * random.nextDouble()),
                30_000.0 / count * (0.8 + 0.4 * random.nextDouble()),
                0.5 + 0.4 * random.nextDouble(),
                random.nextDouble() < 0.1 ? 40 + 20 * random.nextDouble() : 0,
                0);
    }

    private List<FeatureVector> normals(int n) {
        List<FeatureVector> vectors = new ArrayList<>();
        for (int i = 0; i < n; i++) {
            vectors.add(normal());
        }
        return vectors;
    }

    private double score(FeatureVector v) {
        return model.current().orElseThrow().forest().score(v.toArray());
    }

    @Test
    void obviousOutliersScoreAboveNormalTraffic() {
        model.train(normals(1_000));
        double[] heldOut = normals(500).stream().mapToDouble(this::score).sorted().toArray();
        double p99 = heldOut[(int) (heldOut.length * 0.99)];

        FeatureVector burstFlood = new FeatureVector("atk", 0, 900, 0, 0, 1, 0, 33, 0.2, 0, 0.75);
        FeatureVector enumeration = new FeatureVector("atk", 0, 240, 0.9, 0, 240, 7.9, 125, 0.1, 0, 0);
        FeatureVector credentialStuffing = new FeatureVector("atk", 0, 150, 0, 0.95, 1, 0, 200, 0.15, 60, 0);

        for (FeatureVector outlier : List.of(burstFlood, enumeration, credentialStuffing)) {
            assertThat(score(outlier))
                    .as("outlier %s vs normal p99 %.3f", Arrays.toString(outlier.toArray()), p99)
                    .isGreaterThan(p99)
                    .isGreaterThan(0.6);
        }
    }

    @Test
    void typicalNormalWindowsScoreBelowHalf() {
        model.train(normals(1_000));

        double median = normals(500).stream().mapToDouble(this::score).sorted().toArray()[250];

        assertThat(median).isLessThan(0.5);
    }

    @Test
    void refusesToTrainOnTooFewWindows() {
        assertThatThrownBy(() -> model.train(normals(10)))
                .isInstanceOf(AnomalyModel.NotEnoughDataException.class);
        assertThat(model.current()).isEmpty();
    }

    @Test
    void savedModelIsLoadedOnStartupAndScoresTheSame() {
        model.train(normals(500));
        FeatureVector probe = normal();
        double before = score(probe);

        AnomalyModel restarted = new AnomalyModel(properties());
        restarted.afterPropertiesSet();

        IsolationForest loaded = restarted.current().orElseThrow().forest();
        assertThat(loaded.score(probe.toArray())).isEqualTo(before);
        assertThat(restarted.current().orElseThrow().windows()).isEqualTo(500);
    }

    @Test
    void startsWithoutModelWhenNoneSaved() {
        model.afterPropertiesSet();

        assertThat(model.current()).isEmpty();
    }
}
