package com.tuitionnetwork.payments.service;

import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.common.exceptions.PendingBusinessRuleException;
import com.tuitionnetwork.payments.domain.CardBinClassifier;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.dto.PaymentAllocationResultDto;
import com.tuitionnetwork.payments.dto.PaymentSettleRequest;
import com.tuitionnetwork.payments.dto.PaymentSettleResponse;
import com.tuitionnetwork.payments.dto.SelectedDueDto;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.payments.spi.BankGatewayAdapterInterface;
import com.tuitionnetwork.payments.spi.EppPlanResponse;
import com.tuitionnetwork.payments.spi.GatewayResponse;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
public class PaymentSettlementService {

    private final PaymentRepository paymentRepository;
    private final FeeLineRepository feeLineRepository;
    private final BankGatewayAdapterInterface bankGatewayAdapter;
    private final PaymentTransactionExecutor transactionExecutor;

    public PaymentSettlementService(PaymentRepository paymentRepository,
                                    FeeLineRepository feeLineRepository,
                                    BankGatewayAdapterInterface bankGatewayAdapter,
                                    PaymentTransactionExecutor transactionExecutor) {
        this.paymentRepository = paymentRepository;
        this.feeLineRepository = feeLineRepository;
        this.bankGatewayAdapter = bankGatewayAdapter;
        this.transactionExecutor = transactionExecutor;
    }

    public PaymentSettleResponse settlePayment(PaymentSettleRequest request, String headerIdempotencyKey) {
        String idempotencyKey = (headerIdempotencyKey != null && !headerIdempotencyKey.isBlank())
                ? headerIdempotencyKey
                : (request.idempotencyKey() != null ? request.idempotencyKey() : UUID.randomUUID().toString());

        // 1. Check Idempotency & Tamper Protection
        Optional<Payment> existingPayment = paymentRepository.findByIdempotencyKey(idempotencyKey);
        if (existingPayment.isPresent()) {
            Payment payment = existingPayment.get();

            // Guardrail: Detect Payload Tampering against an existing key
            if (request.totalAmount() != null && payment.getTotalAmount().compareTo(request.totalAmount()) != 0) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Idempotency Payload Tamper Violation: Existing transaction for key '" + idempotencyKey +
                        "' has total amount " + payment.getTotalAmount() + " EGP, but incoming request has " + request.totalAmount() + " EGP.");
            }

            List<PaymentAllocationResultDto> allocations = payment.getAllocations().stream()
                    .map(a -> new PaymentAllocationResultDto(a.getId(), a.getFeeLine().getId(), a.getAmountApplied()))
                    .toList();
            return new PaymentSettleResponse(
                    payment.getId(),
                    payment.getStatus(),
                    payment.getTransactionReference(),
                    payment.getAuthCode(),
                    payment.getTotalAmount(),
                    payment.getPaymentMethod(),
                    payment.getIdempotencyKey(),
                    allocations,
                    payment.getCreatedAt()
            );
        }

        // 2. Guardrail Check: EPP Financing Restrictions (Cardless EPP & Debit Card EPP Block)
        if (request.paymentMethod() == PaymentMethod.EPP_INSTALMENTS) {
            if (request.cardNumber() == null || request.cardNumber().trim().isEmpty()) {
                throw new PendingBusinessRuleException(
                        "Pending Business Rule: Cardless EPP Initiation is undefined. " +
                        "A valid credit card must be linked to initiate an Equal Payment Plan."
                );
            }
            if (isDebitCard(request.cardNumber())) {
                throw new PendingBusinessRuleException(
                        "Debit Card EPP Block: Equal Payment Plans (EPP) are strictly restricted to Credit Cards. " +
                        "Debit cards are not eligible for installment financing."
                );
            }
        }

        // 3. Load FeeLines & Guardrail Checks: Overpayment & Post-Deadline Partial Payments
        Map<UUID, FeeLine> feeLinesById = new HashMap<>();
        if (request.selectedDues() != null) {
            for (SelectedDueDto due : request.selectedDues()) {
                FeeLine feeLine = feeLineRepository.findById(due.feeLineId())
                        .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fee line not found with ID: " + due.feeLineId()));

                feeLinesById.put(due.feeLineId(), feeLine);

                BigDecimal remaining = feeLine.getRemainingAmount() != null ? feeLine.getRemainingAmount() : feeLine.getTotalAmount();

                // Guardrail: Overpayment Check (Paid amount must NEVER exceed remaining balance)
                if (due.amountToPay().compareTo(remaining) > 0) {
                    throw new PendingBusinessRuleException(
                            "Overpayment Guardrail: Payment amount (" + due.amountToPay() + " EGP) " +
                            "exceeds remaining fee balance (" + remaining + " EGP) for FeeLine ID " + feeLine.getId() + "."
                    );
                }

                boolean isPartialPayment = due.amountToPay().compareTo(remaining) < 0;
                boolean isPastDue = feeLine.getDueDate() != null && feeLine.getDueDate().isBefore(LocalDate.now());

                if (isPartialPayment && isPastDue) {
                    throw new PendingBusinessRuleException(
                            "Pending Business Rule: Post-Deadline Partial Payments are undefined for overdue fee line " +
                            "(FeeLine ID: " + feeLine.getId() + ", Due Date: " + feeLine.getDueDate() + ")."
                    );
                }
            }
        }

        // 4. Call Bank Gateway Adapter OUTSIDE of any database transaction
        GatewayResponse gatewayResponse = bankGatewayAdapter.chargeCard(request.totalAmount(), idempotencyKey);
        if (gatewayResponse.status() != PaymentStatus.CAPTURED && gatewayResponse.status() != PaymentStatus.AUTHORIZED) {
            throw new IllegalStateException("Payment authorization failed at bank gateway: " + gatewayResponse.message());
        }

        EppPlanResponse eppPlan = null;
        if (request.paymentMethod() == PaymentMethod.EPP_INSTALMENTS) {
            int tenor = (request.eppSelection() != null && request.eppSelection().tenorMonths() != null)
                    ? request.eppSelection().tenorMonths()
                    : 12;
            eppPlan = bankGatewayAdapter.generateEppSchedule(request.totalAmount(), tenor);
        }

        // 5. If successful, open @Transactional block to update FeeLines, save Payment & Allocations, log states, and publish event
        return transactionExecutor.executeCapturedPayment(request, idempotencyKey, gatewayResponse, eppPlan, feeLinesById);
    }

    private boolean isDebitCard(String cardNumber) {
        return CardBinClassifier.isDebitCard(cardNumber);
    }
}
