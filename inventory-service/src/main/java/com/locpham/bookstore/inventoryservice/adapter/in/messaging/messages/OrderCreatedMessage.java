package com.locpham.bookstore.inventoryservice.adapter.in.messaging.messages;

import java.util.List;

public record OrderCreatedMessage(Long orderId, List<OrderItem> items) {
    public record OrderItem(String isbn, int quantity) {}
}
