package com.tuitionnetwork.billing.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "fee_line")
public class FeeLine {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "institution_id", nullable = false)
    private UUID institutionId;

    @Column(name = "student_id", nullable = false)
    private UUID studentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "fee_type", nullable = false)
    private FeeType feeType;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(name = "paid_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal paidAmount = BigDecimal.ZERO;

    @Column(name = "remaining_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal remainingAmount;

    @Column(name = "currency", nullable = false, length = 3)
    private String currency = "EGP";

    @Column(name = "collection_period", nullable = false)
    private String collectionPeriod;

    @Column(name = "due_date", nullable = false)
    private LocalDate dueDate;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private FeeStatus status = FeeStatus.OUTSTANDING;

    @Version
    @Column(name = "version")
    private Long version;

    @Column(name = "row_idempotency_key", unique = true)
    private String rowIdempotencyKey;

    public FeeLine() {
    }

    public FeeLine(UUID institutionId, UUID studentId, FeeType feeType, BigDecimal totalAmount,
                   BigDecimal remainingAmount, String collectionPeriod, LocalDate dueDate) {
        this.institutionId = institutionId;
        this.studentId = studentId;
        this.feeType = feeType;
        this.totalAmount = totalAmount;
        this.paidAmount = BigDecimal.ZERO;
        this.remainingAmount = remainingAmount;
        this.collectionPeriod = collectionPeriod;
        this.dueDate = dueDate;
        this.status = FeeStatus.OUTSTANDING;
        this.currency = "EGP";
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public UUID getInstitutionId() {
        return institutionId;
    }

    public void setInstitutionId(UUID institutionId) {
        this.institutionId = institutionId;
    }

    public UUID getStudentId() {
        return studentId;
    }

    public void setStudentId(UUID studentId) {
        this.studentId = studentId;
    }

    public FeeType getFeeType() {
        return feeType;
    }

    public void setFeeType(FeeType feeType) {
        this.feeType = feeType;
    }

    public BigDecimal getTotalAmount() {
        return totalAmount;
    }

    public void setTotalAmount(BigDecimal totalAmount) {
        this.totalAmount = totalAmount;
    }

    public BigDecimal getPaidAmount() {
        return paidAmount;
    }

    public void setPaidAmount(BigDecimal paidAmount) {
        this.paidAmount = paidAmount;
    }

    public BigDecimal getRemainingAmount() {
        return remainingAmount;
    }

    public void setRemainingAmount(BigDecimal remainingAmount) {
        this.remainingAmount = remainingAmount;
    }

    public String getCurrency() {
        return currency;
    }

    public void setCurrency(String currency) {
        this.currency = currency;
    }

    public String getCollectionPeriod() {
        return collectionPeriod;
    }

    public void setCollectionPeriod(String collectionPeriod) {
        this.collectionPeriod = collectionPeriod;
    }

    public LocalDate getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDate dueDate) {
        this.dueDate = dueDate;
    }

    public FeeStatus getStatus() {
        return status;
    }

    public void setStatus(FeeStatus status) {
        this.status = status;
    }

    public Long getVersion() {
        return version;
    }

    public void setVersion(Long version) {
        this.version = version;
    }

    public String getRowIdempotencyKey() {
        return rowIdempotencyKey;
    }

    public void setRowIdempotencyKey(String rowIdempotencyKey) {
        this.rowIdempotencyKey = rowIdempotencyKey;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FeeLine feeLine)) return false;
        return Objects.equals(id, feeLine.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
