package dev.bulwark.orders;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Component;

/**
 * In-memory order data. The service only exists to give the gateway something realistic to proxy,
 * so data is generated from a fixed seed and lost on restart.
 */
@Component
public class OrderStore {

    private static final String[] ITEMS = {"keyboard", "monitor", "mouse", "headset", "webcam", "dock", "cable", "laptop stand"};
    private static final int SEED_ORDERS = 200;

    private final Map<Long, Order> orders = new ConcurrentHashMap<>();
    private final AtomicLong nextId = new AtomicLong(1);

    public OrderStore() {
        Random random = new Random(42);
        Instant now = Instant.now();
        for (int i = 0; i < SEED_ORDERS; i++) {
            long id = nextId.getAndIncrement();
            int quantity = 1 + random.nextInt(4);
            BigDecimal unitPrice = BigDecimal.valueOf(5 + random.nextInt(500)).add(BigDecimal.valueOf(random.nextInt(100), 2));
            orders.put(id, new Order(
                    id,
                    "cust-" + (1 + random.nextInt(50)),
                    ITEMS[random.nextInt(ITEMS.length)],
                    quantity,
                    unitPrice.multiply(BigDecimal.valueOf(quantity)).setScale(2, RoundingMode.HALF_UP),
                    now.minus(random.nextInt(60 * 24 * 30), ChronoUnit.MINUTES)));
        }
    }

    public List<Order> list(int offset, int limit) {
        return orders.values().stream()
                .sorted(Comparator.comparingLong(Order::id))
                .skip(offset)
                .limit(limit)
                .toList();
    }

    public Optional<Order> find(long id) {
        return Optional.ofNullable(orders.get(id));
    }
}
