package dev.bulwark.sentinel.model;

import java.util.Map;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operator endpoints for training. Sentinel's port is internal to the stack. */
@RestController
@RequestMapping("/sentinel")
public class SentinelController {

    private final TrainingBuffer buffer;

    public SentinelController(TrainingBuffer buffer) {
        this.buffer = buffer;
    }

    @GetMapping("/training-buffer")
    public Map<String, Object> bufferStatus() {
        return Map.of("windows", buffer.size());
    }

    /** Clears the buffer so the next training run only sees traffic generated after this call. */
    @DeleteMapping("/training-buffer")
    public Map<String, Object> clearBuffer() {
        buffer.clear();
        return Map.of("windows", 0);
    }
}
