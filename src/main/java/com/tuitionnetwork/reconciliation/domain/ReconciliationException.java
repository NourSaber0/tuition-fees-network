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
@Table(name = "reconciliation_exception")
public class ReconciliationException {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(name = "run_id")
    private UUID reconciliationRunId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "tx_ref")
    private String txRef;

    @Column(name = "institution")
    private String institution;

    @Column(name = "institution_type")
    private String institutionType;

    @Column(name = "bank_amount_egp")
    private Long bankAmountEGP = 0L;

    @Column(name = "system_amount_egp")
    private Long systemAmountEGP = 0L;

    @Column(name = "school_amount_egp")
    private Long schoolAmountEGP = 0L;

    @Column(name = "difference_egp")
    private Long differenceEGP = 0L;

    @Column(name = "type")
    private String type;

    @Column(name = "exception_date")
    private LocalDate date = LocalDate.now();

    @Column(name = "status")
    private String status = "Open";

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "priority")
    private String priority = "Medium";

    @Column(name = "tx_status")
    private String txStatus;

    @Column(name = "pay_method")
    private String payMethod;

    @Column(name = "bank_ref")
    private String bankRef;

    @Column(name = "bank_status")
    private String bankStatus;

    @Column(name = "settlement_date")
    private LocalDate settlementDate;

    @Column(name = "fee_ref")
    private String feeRef;

    @Column(name = "collection_date")
    private LocalDate collectionDate;

    @Column(name = "reason", length = 1000)
    private String reason;

    @Column(name = "resolution_action")
    private String resolutionAction;

    @Column(name = "supporting_reference")
    private String supportingReference;

    @Column(name = "notes", length = 2000)
    private String notes;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "resolved_at")
    private LocalDateTime resolvedAt;

    public ReconciliationException() {}

    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (date == null) {
            date = LocalDate.now();
        }
    }

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public UUID getReconciliationRunId() { return reconciliationRunId; }
    public void setReconciliationRunId(UUID reconciliationRunId) { this.reconciliationRunId = reconciliationRunId; }

    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }

    public String getTxRef() { return txRef; }
    public void setTxRef(String txRef) { this.txRef = txRef; }

    public String getInstitution() { return institution; }
    public void setInstitution(String institution) { this.institution = institution; }

    public String getInstitutionType() { return institutionType; }
    public void setInstitutionType(String institutionType) { this.institutionType = institutionType; }

    public Long getBankAmountEGP() { return bankAmountEGP; }
    public void setBankAmountEGP(Long bankAmountEGP) { this.bankAmountEGP = bankAmountEGP; }

    public Long getSystemAmountEGP() { return systemAmountEGP; }
    public void setSystemAmountEGP(Long systemAmountEGP) { this.systemAmountEGP = systemAmountEGP; }

    public Long getSchoolAmountEGP() { return schoolAmountEGP; }
    public void setSchoolAmountEGP(Long schoolAmountEGP) { this.schoolAmountEGP = schoolAmountEGP; }

    public Long getDifferenceEGP() { return differenceEGP; }
    public void setDifferenceEGP(Long differenceEGP) { this.differenceEGP = differenceEGP; }

    public String getType() { return type; }
    public void setType(String type) { this.type = type; }

    public LocalDate getDate() { return date; }
    public void setDate(LocalDate date) { this.date = date; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }

    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }

    public String getTxStatus() { return txStatus; }
    public void setTxStatus(String txStatus) { this.txStatus = txStatus; }

    public String getPayMethod() { return payMethod; }
    public void setPayMethod(String payMethod) { this.payMethod = payMethod; }

    public String getBankRef() { return bankRef; }
    public void setBankRef(String bankRef) { this.bankRef = bankRef; }

    public String getBankStatus() { return bankStatus; }
    public void setBankStatus(String bankStatus) { this.bankStatus = bankStatus; }

    public LocalDate getSettlementDate() { return settlementDate; }
    public void setSettlementDate(LocalDate settlementDate) { this.settlementDate = settlementDate; }

    public String getFeeRef() { return feeRef; }
    public void setFeeRef(String feeRef) { this.feeRef = feeRef; }

    public LocalDate getCollectionDate() { return collectionDate; }
    public void setCollectionDate(LocalDate collectionDate) { this.collectionDate = collectionDate; }

    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }

    public String getResolutionAction() { return resolutionAction; }
    public void setResolutionAction(String resolutionAction) { this.resolutionAction = resolutionAction; }

    public String getSupportingReference() { return supportingReference; }
    public void setSupportingReference(String supportingReference) { this.supportingReference = supportingReference; }

    public String getNotes() { return notes; }
    public void setNotes(String notes) { this.notes = notes; }

    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime createdAt) { this.createdAt = createdAt; }

    public LocalDateTime getResolvedAt() { return resolvedAt; }
    public void setResolvedAt(LocalDateTime resolvedAt) { this.resolvedAt = resolvedAt; }
}
