package com.tuitionnetwork.reconciliation.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.GenericGenerator;

import java.time.LocalDateTime;
import java.util.UUID;

@Entity
@Table(name = "reconciliation_exception")
public class ReconciliationException {
    @Id
    @GenericGenerator(name = "uuid2", strategy = "uuid2")
    @jakarta.persistence.GeneratedValue(generator = "uuid2")
    private UUID id;

    @Column(name = "run_id")
    private UUID reconciliationRunId;

    @Column(name = "payment_id")
    private UUID paymentId;

    @Column(name = "reason")
    private String reason;

    @Column(name = "status")
    private String status = "OPEN";

    @Column(name = "assigned_to")
    private String assignedTo;

    @Column(name = "priority")
    private String priority = "MEDIUM";

    @Column(name = "created_at")
    private LocalDateTime createdAt = LocalDateTime.now();

    public ReconciliationException() {}

    public UUID getId() { return id; }
    public UUID getReconciliationRunId() { return reconciliationRunId; }
    public void setReconciliationRunId(UUID reconciliationRunId) { this.reconciliationRunId = reconciliationRunId; }
    public UUID getPaymentId() { return paymentId; }
    public void setPaymentId(UUID paymentId) { this.paymentId = paymentId; }
    public String getReason() { return reason; }
    public void setReason(String reason) { this.reason = reason; }
    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }
    public String getAssignedTo() { return assignedTo; }
    public void setAssignedTo(String assignedTo) { this.assignedTo = assignedTo; }
    public String getPriority() { return priority; }
    public void setPriority(String priority) { this.priority = priority; }
    public LocalDateTime getCreatedAt() { return createdAt; }
}
