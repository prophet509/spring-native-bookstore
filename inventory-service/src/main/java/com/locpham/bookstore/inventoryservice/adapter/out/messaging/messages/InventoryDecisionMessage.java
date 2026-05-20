package com.locpham.bookstore.inventoryservice.adapter.out.messaging.messages;

public record InventoryDecisionMessage(Long orderId, String status, String reason) {}
