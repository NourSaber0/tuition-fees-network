package com.tuitionnetwork.payments.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.UUID;

@Entity
@Table(name = "payment_state_log")
public class PaymentStateLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "payment_id", nullable = false)
    private Payment payment;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_from")
    private PaymentStatus statusFrom;

    @Enumerated(EnumType.STRING)
    @Column(name = "status_to", nullable = false)
    private PaymentStatus statusTo;

    @Column(name = "gateway_response_code")
    private String gatewayResponseCode;

    @Column(name = "gateway_message")
    private String gatewayMessage;

    @Column(name = "transitioned_at", nullable = false)
    private LocalDateTime transitionedAt;

    public PaymentStateLog() {
    }

    public PaymentStateLog(Payment payment, PaymentStatus statusFrom, PaymentStatus statusTo,
                           String gatewayResponseCode, String gatewayMessage) {
        this.payment = payment;
        this.statusFrom = statusFrom;
        this.statusTo = statusTo;
        this.gatewayResponseCode = gatewayResponseCode;
        this.gatewayMessage = gatewayMessage;
        this.transitionedAt = LocalDateTime.now();
    }

    @PrePersist
    protected void onTransition() {
        if (this.transitionedAt == null) {
            this.transitionedAt = LocalDateTime.now();
        }
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

    public PaymentStatus getStatusFrom() {
        return statusFrom;
    }

    public void setStatusFrom(PaymentStatus statusFrom) {
        this.statusFrom = statusFrom;
    }

    public PaymentStatus getStatusTo() {
        return statusTo;
    }

    public void setStatusTo(PaymentStatus statusTo) {
        this.statusTo = statusTo;
    }

    public String getGatewayResponseCode() {
        return gatewayResponseCode;
    }

    public void setGatewayResponseCode(String gatewayResponseCode) {
        this.gatewayResponseCode = gatewayResponseCode;
    }

    public String getGatewayMessage() {
        return gatewayMessage;
    }

    public void setGatewayMessage(String gatewayMessage) {
        this.gatewayMessage = gatewayMessage;
    }

    public LocalDateTime getTransitionedAt() {
        return transitionedAt;
    }

    public void setTransitionedAt(LocalDateTime transitionedAt) {
        this.transitionedAt = transitionedAt;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof PaymentStateLog that)) return false;
        return Objects.equals(id, that.id);
    }

    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
}
