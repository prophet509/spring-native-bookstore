package com.locpham.bookstore.inventoryservice.application.port.in;

import com.locpham.bookstore.inventoryservice.domain.InventoryDecision;
import reactor.core.publisher.Mono;

public interface ReserveStockUseCase {
    Mono<InventoryDecision> reserveForOrder(OrderReserveRequest request);

    record OrderReserveRequest(Long orderId, java.util.List<OrderItem> items) {
        public OrderReserveRequest {
            if (orderId == null) {
                throw new IllegalArgumentException("Order ID must not be null");
            }
            if (items == null || items.isEmpty()) {
                throw new IllegalArgumentException("Items must not be empty");
            }
        }
    }

    record OrderItem(String isbn, int quantity) {
        public OrderItem {
            if (isbn == null || isbn.isBlank()) {
                throw new IllegalArgumentException("ISBN must not be blank");
            }
            if (quantity <= 0) {
                throw new IllegalArgumentException("Quantity must be positive");
            }
        }
    }
}
