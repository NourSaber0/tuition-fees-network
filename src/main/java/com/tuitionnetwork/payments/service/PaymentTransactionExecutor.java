package com.tuitionnetwork.payments.service;

import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

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

    @Transactional
    public PaymentSettleResponse executeCapturedPayment(PaymentSettleRequest request,
                                                        String idempotencyKey,
                                                        GatewayResponse gatewayResponse,
                                                        EppPlanResponse eppPlan,
                                                        Map<UUID, FeeLine> feeLinesById) {
        UUID guardianId = request.guardianId() != null
                ? request.guardianId()
                : UUID.fromString("11111111-1111-1111-1111-111111111111");

        // 1. Create Payment Aggregate
        Payment payment = new Payment(guardianId, request.totalAmount(), request.paymentMethod(), idempotencyKey);
        payment.setStatus(gatewayResponse.status());
        payment.setAuthCode(gatewayResponse.authCode());
        payment.setTransactionReference(gatewayResponse.transactionReference());

        List<UUID> affectedFeeLineIds = new ArrayList<>();
        List<PaymentAllocationResultDto> allocationResults = new ArrayList<>();

        // 2. Update FeeLine status & Save PaymentAllocation records
        if (request.selectedDues() != null) {
            for (SelectedDueDto due : request.selectedDues()) {
                FeeLine feeLine = feeLinesById.get(due.feeLineId());
                if (feeLine == null) {
                    feeLine = feeLineRepository.findById(due.feeLineId())
                            .orElseThrow(() -> new IllegalArgumentException("Fee line not found: " + due.feeLineId()));
                }

                BigDecimal amountToApply = due.amountToPay();
                BigDecimal currentPaid = feeLine.getPaidAmount() != null ? feeLine.getPaidAmount() : BigDecimal.ZERO;
                BigDecimal currentRemaining = feeLine.getRemainingAmount() != null ? feeLine.getRemainingAmount() : feeLine.getTotalAmount();

                BigDecimal updatedPaid = currentPaid.add(amountToApply);
                BigDecimal updatedRemaining = currentRemaining.subtract(amountToApply);
                if (updatedRemaining.compareTo(BigDecimal.ZERO) < 0) {
                    updatedRemaining = BigDecimal.ZERO;
                }

                feeLine.setPaidAmount(updatedPaid);
                feeLine.setRemainingAmount(updatedRemaining);

                if (updatedRemaining.compareTo(BigDecimal.ZERO) == 0) {
                    feeLine.setStatus(FeeStatus.PAID);
                } else {
                    feeLine.setStatus(FeeStatus.PARTIALLY_PAID);
                }

                feeLineRepository.save(feeLine);
                affectedFeeLineIds.add(feeLine.getId());

                PaymentAllocation allocation = new PaymentAllocation(payment, feeLine, amountToApply);
                payment.addAllocation(allocation);
            }
        }

        // 3. Write to PaymentStateLog
        PaymentStateLog stateLog = new PaymentStateLog(
                payment,
                PaymentStatus.PENDING,
                gatewayResponse.status(),
                gatewayResponse.responseCode(),
                gatewayResponse.message()
        );
        payment.addStateLog(stateLog);

        // Optional: Save EPPSchedule if EPP payment method
        if (request.paymentMethod() == PaymentMethod.EPP_INSTALMENTS && eppPlan != null) {
            EPPSchedule eppSchedule = new EPPSchedule(
                    payment,
                    eppPlan.tenorMonths(),
                    eppPlan.principal(),
                    eppPlan.annualInterestRate(),
                    eppPlan.interestAmount(),
                    eppPlan.adminFee(),
                    eppPlan.totalPayable(),
                    eppPlan.monthlyInstalment()
            );
            payment.setEppSchedule(eppSchedule);
        }

        Payment savedPayment = paymentRepository.save(payment);

        for (PaymentAllocation allocation : savedPayment.getAllocations()) {
            allocationResults.add(new PaymentAllocationResultDto(
                    allocation.getId(),
                    allocation.getFeeLine().getId(),
                    allocation.getAmountApplied()
            ));
        }

        Integer eppTenor = null;
        if (eppPlan != null) {
            eppTenor = eppPlan.tenorMonths();
        } else if (request.eppSelection() != null) {
            eppTenor = request.eppSelection().tenorMonths();
        }

        // 4. Publish Spring Application Event (PaymentCapturedEvent)
        PaymentCapturedEvent event = new PaymentCapturedEvent(
                savedPayment.getId(),
                savedPayment.getGuardianId(),
                savedPayment.getTotalAmount(),
                savedPayment.getPaymentMethod(),
                savedPayment.getIdempotencyKey(),
                savedPayment.getTransactionReference(),
                savedPayment.getAuthCode(),
                affectedFeeLineIds,
                eppTenor,
                savedPayment.getCreatedAt()
        );
        eventPublisher.publishEvent(event);

        return new PaymentSettleResponse(
                savedPayment.getId(),
                savedPayment.getStatus(),
                savedPayment.getTransactionReference(),
                savedPayment.getAuthCode(),
                savedPayment.getTotalAmount(),
                savedPayment.getPaymentMethod(),
                savedPayment.getIdempotencyKey(),
                allocationResults,
                savedPayment.getCreatedAt()
        );
    }
}
