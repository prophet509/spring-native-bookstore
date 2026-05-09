package com.locpham.bookstore.searchservice.domain;

import java.util.List;

public record SearchPage<T>(
        List<T> content,
        int pageNumber,
        int pageSize,
        long totalElements,
        int totalPages,
        boolean hasNext,
        boolean hasPrevious) {
    public static <T> SearchPage<T> of(
            List<T> content, int pageNumber, int pageSize, long totalElements) {
        int totalPages = (int) Math.ceil((double) totalElements / pageSize);
        boolean hasNext = pageNumber < totalPages - 1;
        boolean hasPrevious = pageNumber > 0;
        return new SearchPage<>(
                content, pageNumber, pageSize, totalElements, totalPages, hasNext, hasPrevious);
    }
}
