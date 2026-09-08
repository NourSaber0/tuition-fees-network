package com.tuitionnetwork.common.util;

import com.tuitionnetwork.reporting.dto.ReportColumn;

import java.util.List;
import java.util.Map;

/** Minimal RFC-4180 CSV serialiser for report rows. */
public final class CsvWriter {

    private CsvWriter() {
    }

    public static String toCsv(List<ReportColumn> columns, List<Map<String, Object>> rows) {
        StringBuilder sb = new StringBuilder();

        for (int i = 0; i < columns.size(); i++) {
            if (i > 0) {
                sb.append(',');
            }
            sb.append(escape(columns.get(i).label()));
        }
        sb.append("\r\n");

        for (Map<String, Object> row : rows) {
            for (int i = 0; i < columns.size(); i++) {
                if (i > 0) {
                    sb.append(',');
                }
                Object value = row.get(columns.get(i).key());
                sb.append(escape(value == null ? "" : String.valueOf(value)));
            }
            sb.append("\r\n");
        }
        return sb.toString();
    }

    private static String escape(String field) {
        boolean needsQuoting = field.contains(",") || field.contains("\"")
                || field.contains("\n") || field.contains("\r");
        if (!needsQuoting) {
            return field;
        }
        return '"' + field.replace("\"", "\"\"") + '"';
    }
}
