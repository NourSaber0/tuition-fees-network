package com.tuitionnetwork.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.OneToOne;
import jakarta.persistence.Table;
import java.math.BigDecimal;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "epp_schedule")
public class EPPSchedule {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @OneToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false, unique = true)
    private Payment payment;

    @Column(name = "tenor_months", nullable = false)
    private Integer tenorMonths;

    @Column(name = "principal_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal principalAmount;

    @Column(name = "annual_interest_rate", precision = 7, scale = 4)
    private BigDecimal annualInterestRate;

    @Column(name = "interest_amount", precision = 19, scale = 2)
    private BigDecimal interestAmount;

    @Column(name = "admin_fee", precision = 19, scale = 2)
    private BigDecimal adminFee;

    @Column(name = "total_payable", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalPayable;

    @Column(name = "monthly_instalment", nullable = false, precision = 19, scale = 2)
    private BigDecimal monthlyInstalment;

    public EPPSchedule() {
    }

    public EPPSchedule(Payment payment, Integer tenorMonths, BigDecimal principalAmount,
                       BigDecimal annualInterestRate, BigDecimal interestAmount, BigDecimal adminFee,
                       BigDecimal totalPayable, BigDecimal monthlyInstalment) {
        this.payment = payment;
        this.tenorMonths = tenorMonths;
        this.principalAmount = principalAmount;
        this.annualInterestRate = annualInterestRate;
        this.interestAmount = interestAmount;
        this.adminFee = adminFee;
        this.totalPayable = totalPayable;
        this.monthlyInstalment = monthlyInstalment;
    }

    public UUID getId() {
        return id;
    }

    public void setId(UUID id) {
        this.id = id;
    }

    public Payment getPayment() {
        return payment;
    }

    public void setPayment(Payment payment) {
        this.payment = payment;
    }

    public Integer getTenorMonths() {
        return tenorMonths;
    }

    public void setTenorMonths(Integer tenorMonths) {
        this.tenorMonths = tenorMonths;
    }

    public BigDecimal getPrincipalAmount() {
        return principalAmount;
    }

    public void setPrincipalAmount(BigDecimal principalAmount) {
        this.principalAmount = principalAmount;
    }

    public BigDecimal getAnnualInterestRate() {
        return annualInterestRate;
    }

    public void setAnnualInterestRate(BigDecimal annualInterestRate) {
        this.annualInterestRate = annualInterestRate;
    }

    public BigDecimal getInterestAmount() {
        return interestAmount;
    }

    public void setInterestAmount(BigDecimal interestAmount) {
        this.interestAmount = interestAmount;
    }

    public BigDecimal getAdminFee() {
        return adminFee;
    }

    public void setAdminFee(BigDecimal adminFee) {
        this.adminFee = adminFee;
    }

    public BigDecimal getTotalPayable() {
        return totalPayable;
    }

    public void setTotalPayable(BigDecimal totalPayable) {
        this.totalPayable = totalPayable;
    }

    public BigDecimal getMonthlyInstalment() {
        return monthlyInstalment;
    }

    public void setMonthlyInstalment(BigDecimal monthlyInstalment) {
        this.monthlyInstalment = monthlyInstalment;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof EPPSchedule that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
