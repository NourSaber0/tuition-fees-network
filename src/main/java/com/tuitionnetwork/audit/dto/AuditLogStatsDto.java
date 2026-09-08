package com.tuitionnetwork.audit.dto;

import java.util.Map;

public class AuditLogStatsDto {
    private long total;
    private long critical;
    private long warning;
    private long info;
    private Map<String, Long> bySeverity;

    public AuditLogStatsDto() {}

    public AuditLogStatsDto(long total, Map<String, Long> bySeverity) {
        this.total = total;
        this.bySeverity = bySeverity;
        if (bySeverity != null) {
            this.critical = bySeverity.entrySet().stream()
                    .filter(e -> "critical".equalsIgnoreCase(e.getKey()))
                    .mapToLong(Map.Entry::getValue).sum();
            this.warning = bySeverity.entrySet().stream()
                    .filter(e -> "warning".equalsIgnoreCase(e.getKey()))
                    .mapToLong(Map.Entry::getValue).sum();
            this.info = bySeverity.entrySet().stream()
                    .filter(e -> "info".equalsIgnoreCase(e.getKey()))
                    .mapToLong(Map.Entry::getValue).sum();
        }
    }

    public AuditLogStatsDto(long total, long critical, long warning, long info, Map<String, Long> bySeverity) {
        this.total = total;
        this.critical = critical;
        this.warning = warning;
        this.info = info;
        this.bySeverity = bySeverity;
    }

    public long getTotal() { return total; }
    public void setTotal(long total) { this.total = total; }
    public long getCritical() { return critical; }
    public void setCritical(long critical) { this.critical = critical; }
    public long getWarning() { return warning; }
    public void setWarning(long warning) { this.warning = warning; }
    public long getInfo() { return info; }
    public void setInfo(long info) { this.info = info; }
    public Map<String, Long> getBySeverity() { return bySeverity; }
    public void setBySeverity(Map<String, Long> bySeverity) { this.bySeverity = bySeverity; }
}
