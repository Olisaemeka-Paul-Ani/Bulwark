package dev.bulwark.sentinel.model;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * @param trainingBufferSize   most recent windows kept as training data
 * @param minTrainingWindows   refuse to train on fewer windows than this
 * @param trees                number of isolation trees
 * @param subsample            fraction of the training set each tree is built from
 * @param path                 where the trained model is saved and loaded from
 */
@ConfigurationProperties("sentinel.model")
public record ModelProperties(int trainingBufferSize, int minTrainingWindows, int trees, double subsample,
                              java.nio.file.Path path) {
}
