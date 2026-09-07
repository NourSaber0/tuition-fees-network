package com.tuitionnetwork.audit.dto;

import java.util.Map;

public class AuditLogStatsDto {
    private long total;
    private Map<String, Long> bySeverity;

    public AuditLogStatsDto() {}

    public AuditLogStatsDto(long total, Map<String, Long> bySeverity) {
        this.total = total;
        this.bySeverity = bySeverity;
    }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public Map<String, Long> getBySeverity() { return bySeverity; }
    public void setBySeverity(Map<String, Long> bySeverity) { this.bySeverity = bySeverity; }
}
