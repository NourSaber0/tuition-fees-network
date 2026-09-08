package com.tuitionnetwork.reporting.dto;

/** A single column in a report preview / CSV. {@code align} is "left" | "right" | "center" (nullable). */
public record ReportColumn(String key, String label, String align) {

    public static ReportColumn of(String key, String label) {
        return new ReportColumn(key, label, "left");
    }

    public static ReportColumn right(String key, String label) {
        return new ReportColumn(key, label, "right");
    }

    public static ReportColumn center(String key, String label) {
        return new ReportColumn(key, label, "center");
    }
}
