package com.tuitionnetwork.reporting.dto;

import java.util.List;

/**
 * Metadata for one report type ({@code GET /reports/catalogue}).
 *
 * @param available          false when the upstream data module for this report is not built yet
 * @param unavailableReason  human-readable explanation when {@code available} is false, else null
 */
public record ReportCatalogueEntry(
        String id,
        String title,
        String description,
        String category,
        List<String> formats,
        boolean singleDate,
        List<String> contextFilters,
        boolean available,
        String unavailableReason
) {
}
