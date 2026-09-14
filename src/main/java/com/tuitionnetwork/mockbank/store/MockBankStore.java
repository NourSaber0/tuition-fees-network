package com.tuitionnetwork.mockbank.store;

import com.tuitionnetwork.mockbank.dto.CardPaymentResponse;
import com.tuitionnetwork.mockbank.dto.EppPlanDetailResponse;
import com.tuitionnetwork.mockbank.dto.MoiVerificationResponse;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Component
public class MockBankStore {

    private final Map<String, MoiVerificationResponse> verifications = new ConcurrentHashMap<>();
    private final Map<String, CardPaymentResponse> cardPayments = new ConcurrentHashMap<>();
    private final Map<String, EppPlanDetailResponse> eppPlans = new ConcurrentHashMap<>();
    private final Set<String> convertedPaymentIds = ConcurrentHashMap.newKeySet();
    private final Map<String, CardPaymentResponse> idempotencyCache = new ConcurrentHashMap<>();

    // --- MOI ---
    public void saveVerification(MoiVerificationResponse response) {
        verifications.put(response.verificationId(), response);
    }

    public Optional<MoiVerificationResponse> getVerification(String id) {
        return Optional.ofNullable(verifications.get(id));
    }

    public List<MoiVerificationResponse> listVerifications() {
        return new ArrayList<>(verifications.values());
    }

    // --- Card Payments ---
    public void savePayment(CardPaymentResponse response) {
        cardPayments.put(response.paymentId(), response);
    }

    public Optional<CardPaymentResponse> getPayment(String id) {
        return Optional.ofNullable(cardPayments.get(id));
    }

    public List<CardPaymentResponse> listPayments(String status) {
        if (status == null || status.isBlank()) {
            return new ArrayList<>(cardPayments.values());
        }
        return cardPayments.values().stream()
                .filter(p -> status.equalsIgnoreCase(p.status()))
                .toList();
    }

    public void saveIdempotentPayment(String idempotencyKey, CardPaymentResponse response) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            idempotencyCache.put(idempotencyKey, response);
        }
    }

    public Optional<CardPaymentResponse> getIdempotentPayment(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(idempotencyCache.get(idempotencyKey));
    }

    // --- EPP Plans ---
    public void saveEppPlan(EppPlanDetailResponse plan) {
        eppPlans.put(plan.planId(), plan);
        if (plan.paymentId() != null) {
            convertedPaymentIds.add(plan.paymentId());
        }
    }

    public Optional<EppPlanDetailResponse> getEppPlan(String planId) {
        return Optional.ofNullable(eppPlans.get(planId));
    }

    public List<EppPlanDetailResponse> listEppPlans() {
        return new ArrayList<>(eppPlans.values());
    }

    public boolean isPaymentConverted(String paymentId) {
        return convertedPaymentIds.contains(paymentId);
    }

    public Optional<EppPlanDetailResponse> cancelEppPlan(String planId) {
        EppPlanDetailResponse existing = eppPlans.get(planId);
        if (existing == null) {
            return Optional.empty();
        }
        EppPlanDetailResponse cancelled = new EppPlanDetailResponse(
                existing.planId(),
                "CANCELLED",
                existing.paymentId(),
                existing.principal(),
                existing.tenorMonths(),
                existing.annualRate(),
                existing.interestAmount(),
                existing.adminFee(),
                existing.totalPayable(),
                existing.monthlyInstallment(),
                existing.firstDueDate(),
                existing.lastDueDate(),
                existing.schedule()
        );
        eppPlans.put(planId, cancelled);
        return Optional.of(cancelled);
    }

    // --- Reset ---
    public void reset() {
        verifications.clear();
        cardPayments.clear();
        eppPlans.clear();
        convertedPaymentIds.clear();
        idempotencyCache.clear();
    }
}
