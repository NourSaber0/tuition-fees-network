package com.tuitionnetwork.reporting.dto;

import jakarta.validation.constraints.NotBlank;
import org.springframework.format.annotation.DateTimeFormat;

import java.time.LocalDate;

/**
 * {@code POST /reports/generate}. Provide {@code date} for single-date reports,
 * otherwise {@code dateFrom} + {@code dateTo}. {@code format} defaults to CSV.
 */
public record GenerateReportRequest(
        @NotBlank String reportId,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
        @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
        String format,
        ReportFilters filters
) {
    public ReportFilters filtersOrEmpty() {
        return filters != null ? filters : ReportFilters.empty();
    }

    public String formatOrDefault() {
        return (format == null || format.isBlank()) ? "CSV" : format.trim().toUpperCase();
    }
}
