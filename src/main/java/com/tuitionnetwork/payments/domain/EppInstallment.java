package com.tuitionnetwork.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

/**
 * EPP Installment Entity.
 * =========================================================================================
 * ARCHITECTURAL NOTE / READ-ONLY PROJECTION:
 * This entity is a READ-ONLY projection synchronized from the external Bank Gateway.
 * It is maintained strictly for audit and UI display purposes (e.g. showing upcoming monthly
 * payment schedules). Internal settlement workflows do NOT process or mutate individual
 * installment rows directly.
 * =========================================================================================
 */
@Entity
@Table(name = "epp_installment")
public class EppInstallment {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "epp_plan_id", nullable = false)
    private EPPSchedule eppPlan;

    @Column(name = "installment_number", nullable = false)
    private int installmentNumber;

    @Column(name = "amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal amount;

    @Column(name = "due_date", nullable = false)
    private LocalDateTime dueDate;

    @Column(name = "status", nullable = false)
    private String status = "PENDING"; // Read-Only from Bank / Display purposes

    public EppInstallment() {
    }

    public EppInstallment(EPPSchedule eppPlan, int installmentNumber, BigDecimal amount,
                          LocalDateTime dueDate, String status) {
        this.eppPlan = eppPlan;
        this.installmentNumber = installmentNumber;
        this.amount = amount;
        this.dueDate = dueDate;
        this.status = status;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public EPPSchedule getEppPlan() {
        return eppPlan;
    }

    public void setEppPlan(EPPSchedule eppPlan) {
        this.eppPlan = eppPlan;
    }

    public int getInstallmentNumber() {
        return installmentNumber;
    }

    public void setInstallmentNumber(int installmentNumber) {
        this.installmentNumber = installmentNumber;
    }

    public BigDecimal getAmount() {
        return amount;
    }

    public void setAmount(BigDecimal amount) {
        this.amount = amount;
    }

    public LocalDateTime getDueDate() {
        return dueDate;
    }

    public void setDueDate(LocalDateTime dueDate) {
        this.dueDate = dueDate;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EppInstallment that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
