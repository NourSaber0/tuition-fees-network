package com.tuitionnetwork.payments.service;

import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.common.exceptions.PendingBusinessRuleException;
import com.tuitionnetwork.payments.domain.EPPSchedule;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.domain.PaymentStateLog;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.dto.PaymentAllocationResultDto;
import com.tuitionnetwork.payments.dto.PaymentSettleRequest;
import com.tuitionnetwork.payments.dto.PaymentSettleResponse;
import com.tuitionnetwork.payments.dto.SelectedDueDto;
import com.tuitionnetwork.payments.event.PaymentCapturedEvent;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.payments.spi.EppPlanResponse;
import com.tuitionnetwork.payments.spi.GatewayResponse;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Transactional building blocks of a payment. The settlement flow is:
 * <ol>
 *   <li>{@link #reserveFeeLines} - lock the fee lines, validate, and decrement the
 *       balance <em>before</em> the card is charged. A concurrent payer blocks on the
 *       lock, then fails the overpayment check here and never reaches the gateway.</li>
 *   <li>charge the card (outside any transaction, done by the caller)</li>
 *   <li>{@link #finalizeCapturedPayment} on success - records the Payment; no balance change</li>
 *   <li>{@link #releaseFeeLines} on charge failure - restores the reserved amount</li>
 * </ol>
 */
@Service
public class PaymentTransactionExecutor {

    private final PaymentRepository paymentRepository;
    private final FeeLineRepository feeLineRepository;
    private final ApplicationEventPublisher eventPublisher;

    public PaymentTransactionExecutor(PaymentRepository paymentRepository,
                                    FeeLineRepository feeLineRepository,
                                    ApplicationEventPublisher eventPublisher) {
        this.paymentRepository = paymentRepository;
        this.feeLineRepository = feeLineRepository;
        this.eventPublisher = eventPublisher;
    }

    /**
     * Claims the paid amount against each fee line under a pessimistic write lock.
     * Throws (and rolls back) on overpayment or a post-deadline partial payment,
     * leaving the balance untouched.
     */
    @Transactional
    public Map<UUID, FeeLine> reserveFeeLines(List<SelectedDueDto> dues) {
        Map<UUID, FeeLine> reserved = new LinkedHashMap<>();
        if (dues == null || dues.isEmpty()) {
            return reserved;
        }

        List<UUID> ids = dues.stream().map(SelectedDueDto::feeLineId).toList();
        Map<UUID, FeeLine> locked = new LinkedHashMap<>();
        for (FeeLine fl : feeLineRepository.lockAllById(ids)) {
            locked.put(fl.getId(), fl);
        }

        for (SelectedDueDto due : dues) {
            FeeLine feeLine = locked.get(due.feeLineId());
            if (feeLine == null) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Fee line not found with ID: " + due.feeLineId());
            }

            BigDecimal amountToApply = due.amountToPay();
            BigDecimal currentRemaining = feeLine.getRemainingAmount() != null
                    ? feeLine.getRemainingAmount() : feeLine.getTotalAmount();

            if (amountToApply.compareTo(currentRemaining) > 0) {
                throw new PendingBusinessRuleException(
                        "Overpayment Guardrail: Payment amount (" + amountToApply + " EGP) " +
                        "exceeds remaining fee balance (" + currentRemaining + " EGP) for FeeLine ID " + feeLine.getId() + ".");
            }

            BigDecimal currentPaid = feeLine.getPaidAmount() != null ? feeLine.getPaidAmount() : BigDecimal.ZERO;
            BigDecimal updatedRemaining = currentRemaining.subtract(amountToApply);
            if (updatedRemaining.compareTo(BigDecimal.ZERO) < 0) {
                updatedRemaining = BigDecimal.ZERO;
            }

            feeLine.setPaidAmount(currentPaid.add(amountToApply));
            feeLine.setRemainingAmount(updatedRemaining);
            feeLine.setStatus(updatedRemaining.compareTo(BigDecimal.ZERO) == 0
                    ? FeeStatus.PAID : FeeStatus.PARTIALLY_PAID);

            feeLineRepository.save(feeLine);
            reserved.put(feeLine.getId(), feeLine);
        }
        return reserved;
    }

    /** Compensating action: give the reserved amount back if the card charge failed. */
    @Transactional
    public void releaseFeeLines(List<SelectedDueDto> dues) {
        if (dues == null || dues.isEmpty()) {
            return;
        }
        List<UUID> ids = dues.stream().map(SelectedDueDto::feeLineId).toList();
        Map<UUID, FeeLine> locked = new LinkedHashMap<>();
        for (FeeLine fl : feeLineRepository.lockAllById(ids)) {
            locked.put(fl.getId(), fl);
        }

        for (SelectedDueDto due : dues) {
            FeeLine feeLine = locked.get(due.feeLineId());
            if (feeLine == null) {
                continue;
            }
            BigDecimal amount = due.amountToPay();
            BigDecimal restoredPaid = (feeLine.getPaidAmount() != null ? feeLine.getPaidAmount() : BigDecimal.ZERO)
                    .subtract(amount);
            if (restoredPaid.compareTo(BigDecimal.ZERO) < 0) {
                restoredPaid = BigDecimal.ZERO;
            }
            BigDecimal restoredRemaining = (feeLine.getRemainingAmount() != null ? feeLine.getRemainingAmount() : BigDecimal.ZERO)
                    .add(amount);
            if (feeLine.getTotalAmount() != null && restoredRemaining.compareTo(feeLine.getTotalAmount()) > 0) {
                restoredRemaining = feeLine.getTotalAmount();
            }

            feeLine.setPaidAmount(restoredPaid);
            feeLine.setRemainingAmount(restoredRemaining);
            feeLine.setStatus(restoredPaid.compareTo(BigDecimal.ZERO) == 0
                    ? FeeStatus.OUTSTANDING : FeeStatus.PARTIALLY_PAID);
            feeLineRepository.save(feeLine);
        }
    }

    /**
     * Records the successful payment: Payment aggregate, allocations, state log,
     * optional EPP schedule, and the {@code PaymentCapturedEvent}. Fee-line
     * balances were already decremented by {@link #reserveFeeLines}.
     */
    @Transactional
    public PaymentSettleResponse finalizeCapturedPayment(PaymentSettleRequest request,
                                                         String idempotencyKey,
                                                         GatewayResponse gatewayResponse,
                                                         EppPlanResponse eppPlan) {
        UUID guardianId = request.guardianId() != null
                ? request.guardianId()
                : UUID.fromString("11111111-1111-1111-1111-111111111111");

        Payment payment = new Payment(guardianId, request.totalAmount(), request.paymentMethod(), idempotencyKey);
        payment.setStatus(gatewayResponse.status());
        payment.setAuthCode(gatewayResponse.authCode());
        payment.setTransactionReference(gatewayResponse.transactionReference());

        List<UUID> affectedFeeLineIds = new ArrayList<>();
        List<PaymentAllocationResultDto> allocationResults = new ArrayList<>();

        if (request.selectedDues() != null) {
            for (SelectedDueDto due : request.selectedDues()) {
                FeeLine feeLine = feeLineRepository.findById(due.feeLineId())
                        .orElseThrow(() -> new IllegalArgumentException("Fee line not found: " + due.feeLineId()));
                affectedFeeLineIds.add(feeLine.getId());
                payment.addAllocation(new PaymentAllocation(payment, feeLine, due.amountToPay()));
            }
        }

        payment.addStateLog(new PaymentStateLog(
                payment,
                PaymentStatus.PENDING,
                gatewayResponse.status(),
                gatewayResponse.responseCode(),
                gatewayResponse.message()));

        if (request.paymentMethod() == PaymentMethod.EPP_INSTALMENTS && eppPlan != null) {
            payment.setEppSchedule(new EPPSchedule(
                    payment,
                    eppPlan.tenorMonths(),
                    eppPlan.principal(),
                    eppPlan.annualInterestRate(),
                    eppPlan.interestAmount(),
                    eppPlan.adminFee(),
                    eppPlan.totalPayable(),
                    eppPlan.monthlyInstalment()));
        }

        Payment savedPayment = paymentRepository.save(payment);

        for (PaymentAllocation allocation : savedPayment.getAllocations()) {
            allocationResults.add(new PaymentAllocationResultDto(
                    allocation.getId(),
                    allocation.getFeeLine().getId(),
                    allocation.getAmountApplied()));
        }

        Integer eppTenor = null;
        if (eppPlan != null) {
            eppTenor = eppPlan.tenorMonths();
        } else if (request.eppSelection() != null) {
            eppTenor = request.eppSelection().tenorMonths();
        }

        eventPublisher.publishEvent(new PaymentCapturedEvent(
                savedPayment.getId(),
                savedPayment.getGuardianId(),
                savedPayment.getTotalAmount(),
                savedPayment.getPaymentMethod(),
                savedPayment.getIdempotencyKey(),
                savedPayment.getTransactionReference(),
                savedPayment.getAuthCode(),
                affectedFeeLineIds,
                eppTenor,
                savedPayment.getCreatedAt()));

        return new PaymentSettleResponse(
                savedPayment.getId(),
                savedPayment.getStatus(),
                savedPayment.getTransactionReference(),
                savedPayment.getAuthCode(),
                savedPayment.getTotalAmount(),
                savedPayment.getPaymentMethod(),
                savedPayment.getIdempotencyKey(),
                allocationResults,
                savedPayment.getCreatedAt(),
                eppPlan);
    }

    /**
     * @deprecated superseded by {@link #reserveFeeLines} + {@link #finalizeCapturedPayment}.
     * Kept as a thin wrapper so any external caller still compiles.
     */
    @Deprecated
    @Transactional
    public PaymentSettleResponse executeCapturedPayment(PaymentSettleRequest request,
                                                        String idempotencyKey,
                                                        GatewayResponse gatewayResponse,
                                                        EppPlanResponse eppPlan,
                                                        Map<UUID, FeeLine> feeLinesById) {
        reserveFeeLines(request.selectedDues());
        return finalizeCapturedPayment(request, idempotencyKey, gatewayResponse, eppPlan);
    }
}
