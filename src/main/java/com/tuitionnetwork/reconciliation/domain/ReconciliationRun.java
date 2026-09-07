package com.tuitionnetwork.reconciliation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_run")
public class ReconciliationRun {
    @Id
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @jakarta.persistence.GeneratedValue(generator = "uuid2")
    private UUID id;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "total_transactions")
    private Integer totalTransactions = 0;

    @Column(name = "matched_count")
    private Integer matchedCount = 0;

    @Column(name = "exception_count")
    private Integer exceptionCount = 0;

    public ReconciliationRun() {}

    public ReconciliationRun(String status) {
        this.status = status;
    }

    public UUID getId() { return id; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public Integer getTotalTransactions() { return totalTransactions; }
    public void setTotalTransactions(Integer totalTransactions) { this.totalTransactions = totalTransactions; }
    public Integer getMatchedCount() { return matchedCount; }
    public void setMatchedCount(Integer matchedCount) { this.matchedCount = matchedCount; }
    public Integer getExceptionCount() { return exceptionCount; }
    public void setExceptionCount(Integer exceptionCount) { this.exceptionCount = exceptionCount; }
}
