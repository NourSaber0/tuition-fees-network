package com.tuitionnetwork.reporting.dto;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Metadata for one report type ({@code GET /reports/catalogue}).
 *
 * @param available          false when the upstream data module for this report is not built yet
 * @param unavailableReason  human-readable explanation when {@code available} is false, else null
 * @param lastGeneratedAt    timestamp of the most recent generation run for this report, or null
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
        String unavailableReason,
        LocalDateTime lastGeneratedAt
) {
    public ReportCatalogueEntry(
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
        this(id, title, description, category, formats, singleDate, contextFilters, available, unavailableReason, null);
    }
}
