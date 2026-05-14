package com.locpham.bookstore.orderservice.adapter.in.web.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

public record OrderRequest(
        @NotBlank(message = "The book ISBN must be defined")
                @Pattern(
                        regexp = "^([0-9]{10}|[0-9]{13})$",
                        message = "The ISBN format must follow the standards ISBN-10 or ISBN-13.")
                String isbn,
        @NotNull(message = "The book quantity must be defined.")
                @Min(value = 1, message = "You must order at least 1 item.")
                @Max(value = 5, message = "You cannot order more than 5 items.")
                int quantity) {}
