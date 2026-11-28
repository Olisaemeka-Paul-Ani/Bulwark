package dev.bulwark.sentinel.model;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.List;

import org.springframework.stereotype.Component;

import dev.bulwark.common.FeatureVector;
import dev.bulwark.sentinel.features.FeatureExtractor;
import dev.bulwark.sentinel.window.ClosedWindow;
import dev.bulwark.sentinel.window.WindowListener;

/**
 * The most recent feature vectors, kept as training data for the Isolation Forest.
 *
 * <p>It records every window it sees and cannot tell normal from attack traffic, so the training
 * procedure is: clear it, run normal traffic only, then train (see {@code make train}). Bounded
 * so it can't grow without limit if nobody ever trains.
 */
@Component
public class TrainingBuffer implements WindowListener {

    private final int capacity;
    private final Deque<FeatureVector> vectors = new ArrayDeque<>();

    public TrainingBuffer(ModelProperties properties) {
        this.capacity = properties.trainingBufferSize();
    }

    @Override
    public void onWindowClosed(ClosedWindow window) {
        add(FeatureExtractor.extract(window));
    }

    public synchronized void add(FeatureVector vector) {
        if (vectors.size() == capacity) {
            vectors.removeFirst();
        }
        vectors.addLast(vector);
    }

    public synchronized List<FeatureVector> snapshot() {
        return List.copyOf(vectors);
    }

    public synchronized int size() {
        return vectors.size();
    }

    public synchronized void clear() {
        vectors.clear();
    }
}
