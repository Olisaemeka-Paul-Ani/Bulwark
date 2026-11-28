package dev.bulwark.sentinel.detection;

/** Called for every scored window, on the thread that closed it. */
public interface DetectionListener {

    void onDetection(Detection detection);
}
