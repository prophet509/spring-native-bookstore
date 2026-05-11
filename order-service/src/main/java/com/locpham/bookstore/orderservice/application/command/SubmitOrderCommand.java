package com.locpham.bookstore.orderservice.application.command;

public record SubmitOrderCommand(String isbn, int quantity, String createdBy) {}
