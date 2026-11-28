package dev.bulwark.sentinel.model;

import java.util.LinkedHashMap;
import java.util.Map;

import org.springframework.http.ResponseEntity;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Operator endpoints for training. Sentinel's port is internal to the stack. */
@RestController
@RequestMapping("/sentinel")
public class SentinelController {

    private final TrainingBuffer buffer;
    private final AnomalyModel model;

    public SentinelController(TrainingBuffer buffer, AnomalyModel model) {
        this.buffer = buffer;
        this.model = model;
    }

    /** Trains a new forest on the buffered windows, which should all come from normal traffic. */
    @PostMapping("/train")
    public ResponseEntity<Map<String, Object>> train() {
        try {
            AnomalyModel.Trained trained = model.train(buffer.snapshot());
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("windows", trained.windows());
            body.put("trees", trained.forest().size());
            body.put("trainedAt", trained.trainedAt().toString());
            return ResponseEntity.ok(body);
        } catch (AnomalyModel.NotEnoughDataException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
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
