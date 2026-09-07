package com.tuitionnetwork.reporting.service;

import com.tuitionnetwork.reporting.dto.ReportColumn;

import java.util.List;
import java.util.Map;

/** Internal carrier between the per-report aggregation and the CSV / preview builders. */
public record ReportData(
        List<ReportColumn> columns,
        List<Map<String, Object>> rows,
        String total,
        String note
) {
}
