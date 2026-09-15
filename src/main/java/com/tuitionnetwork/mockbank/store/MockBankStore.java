package com.tuitionnetwork.mockbank.store;

import com.tuitionnetwork.mockbank.domain.MockBankCustomers;
import com.tuitionnetwork.mockbank.dto.BackofficePaymentResponse;
import com.tuitionnetwork.mockbank.dto.CardPaymentResponse;
import com.tuitionnetwork.mockbank.dto.CustomerLookupResponse;
import com.tuitionnetwork.mockbank.dto.EppPlanDetailResponse;
import com.tuitionnetwork.mockbank.dto.MoiVerificationResponse;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
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
    
    // Back-office state
    private final Map<String, CustomerLookupResponse> customersByNid = new ConcurrentHashMap<>();
    private final Map<String, CustomerLookupResponse> customersById = new ConcurrentHashMap<>();
    private final Map<String, BackofficePaymentResponse> backofficePayments = new ConcurrentHashMap<>();
    private final Map<String, BackofficePaymentResponse> bopIdempotencyCache = new ConcurrentHashMap<>();

    @PostConstruct
    public void init() {
        resetCustomers();
    }

    private void resetCustomers() {
        customersByNid.clear();
        customersById.clear();
        for (CustomerLookupResponse customer : MockBankCustomers.getInitialCustomers()) {
            customersByNid.put(customer.nationalId(), customer);
            customersById.put(customer.customerId(), customer);
        }
    }

    // --- Customers ---
    public Optional<CustomerLookupResponse> getCustomerByNationalId(String nid) {
        return Optional.ofNullable(customersByNid.get(nid));
    }

    public Optional<CustomerLookupResponse> getCustomerById(String id) {
        return Optional.ofNullable(customersById.get(id));
    }

    public void updateCustomer(CustomerLookupResponse customer) {
        customersByNid.put(customer.nationalId(), customer);
        customersById.put(customer.customerId(), customer);
    }

    // --- Back-office Payments ---
    public void saveBackofficePayment(BackofficePaymentResponse response) {
        backofficePayments.put(response.paymentId(), response);
    }

    public Optional<BackofficePaymentResponse> getBackofficePayment(String id) {
        return Optional.ofNullable(backofficePayments.get(id));
    }

    public List<BackofficePaymentResponse> listBackofficePayments(String customerId) {
        return backofficePayments.values().stream()
                .filter(p -> customerId == null || customerId.equals(p.customerId()))
                .toList();
    }

    public void saveIdempotentBackofficePayment(String idempotencyKey, BackofficePaymentResponse response) {
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            bopIdempotencyCache.put(idempotencyKey, response);
        }
    }

    public Optional<BackofficePaymentResponse> getIdempotentBackofficePayment(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return Optional.empty();
        }
        return Optional.ofNullable(bopIdempotencyCache.get(idempotencyKey));
    }

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
        List<EppPlanDetailResponse.InstallmentItem> updatedSchedule = existing.schedule().stream()
                .map(item -> new EppPlanDetailResponse.InstallmentItem(
                        item.number(),
                        item.dueDate(),
                        item.amount(),
                        "CANCELLED"
                )).toList();
        
        EppPlanDetailResponse cancelled = new EppPlanDetailResponse(
                existing.planId(),
                "CANCELLED",
                existing.paymentId(),
                existing.cardId(),
                existing.customer(),
                existing.principal(),
                existing.tenorMonths(),
                existing.annualRate(),
                existing.interestAmount(),
                existing.adminFee(),
                existing.totalPayable(),
                existing.monthlyInstallment(),
                existing.firstDueDate(),
                existing.lastDueDate(),
                updatedSchedule
        );
        if (existing.paymentId() != null) {
            convertedPaymentIds.remove(existing.paymentId());
        }
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
        backofficePayments.clear();
        bopIdempotencyCache.clear();
        resetCustomers();
    }
}
