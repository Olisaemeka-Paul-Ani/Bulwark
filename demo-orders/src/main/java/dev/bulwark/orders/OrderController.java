package dev.bulwark.orders;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/orders")
public class OrderController {

    private static final int MAX_LIMIT = 100;

    private final OrderStore store;

    public OrderController(OrderStore store) {
        this.store = store;
    }

    @GetMapping
    public List<Order> list(@RequestParam(defaultValue = "0") int offset,
                            @RequestParam(defaultValue = "20") int limit) {
        return store.list(Math.max(0, offset), Math.clamp(limit, 1, MAX_LIMIT));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Order> get(@PathVariable long id) {
        return ResponseEntity.of(store.find(id));
    }
}
