package dev.bulwark.orders;

import java.math.BigDecimal;

public record CreateOrderRequest(String customerId, String item, int quantity, BigDecimal unitPrice) {

    boolean isValid() {
        return customerId != null && !customerId.isBlank()
                && item != null && !item.isBlank()
                && quantity > 0
                && unitPrice != null && unitPrice.signum() > 0;
    }
}
