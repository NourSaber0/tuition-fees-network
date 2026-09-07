package com.tuitionnetwork.reconciliation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_run")
public class ReconciliationRun {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "institution")
    private String institution;

    @Column(name = "institution_type")
    private String institutionType;

    @Column(name = "run_date")
    private LocalDate runDate = LocalDate.now();

    @Column(name = "tx_count")
    private Integer txCount = 0;

    @Column(name = "bank_amount_egp")
    private Long bankAmountEGP = 0L;

    @Column(name = "system_amount_egp")
    private Long systemAmountEGP = 0L;

    @Column(name = "school_amount_egp")
    private Long schoolAmountEGP = 0L;

    @Column(name = "status", nullable = false)
    private String status = "Matched";

    @Column(name = "total_transactions")
    private Integer totalTransactions = 0;

    @Column(name = "matched_count")
    private Integer matchedCount = 0;

    @Column(name = "exception_count")
    private Integer exceptionCount = 0;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    public ReconciliationRun() {}

    public ReconciliationRun(String status) {
        this.status = status;
    }

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (runDate == null) {
            runDate = LocalDate.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getInstitution() { return institution; }
    public void setInstitution(String institution) { this.institution = institution; }

    public String getInstitutionType() { return institutionType; }
    public void setInstitutionType(String institutionType) { this.institutionType = institutionType; }

    public LocalDate getRunDate() { return runDate; }
    public void setRunDate(LocalDate runDate) { this.runDate = runDate; }

    public Integer getTxCount() { return txCount; }
    public void setTxCount(Integer txCount) { this.txCount = txCount; }

    public Long getBankAmountEGP() { return bankAmountEGP; }
    public void setBankAmountEGP(Long bankAmountEGP) { this.bankAmountEGP = bankAmountEGP; }

    public Long getSystemAmountEGP() { return systemAmountEGP; }
    public void setSystemAmountEGP(Long systemAmountEGP) { this.systemAmountEGP = systemAmountEGP; }

    public Long getSchoolAmountEGP() { return schoolAmountEGP; }
    public void setSchoolAmountEGP(Long schoolAmountEGP) { this.schoolAmountEGP = schoolAmountEGP; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public Integer getTotalTransactions() { return totalTransactions; }
    public void setTotalTransactions(Integer totalTransactions) { this.totalTransactions = totalTransactions; }

    public Integer getMatchedCount() { return matchedCount; }
    public void setMatchedCount(Integer matchedCount) { this.matchedCount = matchedCount; }

    public Integer getExceptionCount() { return exceptionCount; }
    public void setExceptionCount(Integer exceptionCount) { this.exceptionCount = exceptionCount; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }
}
