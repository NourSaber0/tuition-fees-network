package com.tuitionnetwork.reporting.dto;

import java.util.List;
import java.util.Map;

/** Inline preview returned with a generated report (first N rows). */
public record ReportPreview(
        List<ReportColumn> columns,
        List<Map<String, Object>> rows,
        String total,
        String note
) {
}
