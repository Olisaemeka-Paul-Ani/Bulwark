package dev.bulwark.orders;

import java.math.BigDecimal;
import java.time.Instant;

public record Order(long id, String customerId, String item, int quantity, BigDecimal total, Instant createdAt) {
}
