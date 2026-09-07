package com.tuitionnetwork.common.dto;

import org.springframework.data.domain.Page;

import java.util.List;
import java.util.function.Function;

/**
 * Envelope for paginated list endpoints.
 * Mirrors the "Global Conventions" section of the API contract:
 * {@code { data, page, size, total, totalPages }}.
 */
public record PageResponse<T>(
        List<T> data,
        int page,
        int size,
        long total,
        int totalPages
) {
    public static <E, T> PageResponse<T> from(Page<E> page, Function<E, T> mapper) {
        return new PageResponse<>(
                page.getContent().stream().map(mapper).toList(),
                page.getNumber(),
                page.getSize(),
                page.getTotalElements(),
                page.getTotalPages()
        );
    }
}
