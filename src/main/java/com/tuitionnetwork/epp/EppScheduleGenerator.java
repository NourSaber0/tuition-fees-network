package com.tuitionnetwork.epp;

import com.tuitionnetwork.common.exceptions.PendingBusinessRuleException;
import com.tuitionnetwork.payments.domain.EPPSchedule;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.event.PaymentCapturedEvent;
import com.tuitionnetwork.payments.repository.EPPScheduleRepository;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Optional;

@Component
public class EppScheduleGenerator {

    private static final Logger log = LoggerFactory.getLogger(EppScheduleGenerator.class);

    private static final BigDecimal ANNUAL_RATE = new BigDecimal("0.14");
    private static final BigDecimal ADMIN_FEE_RATE = new BigDecimal("0.01");
    private static final BigDecimal ADMIN_FEE_CAP = new BigDecimal("500.00");

    private final EPPScheduleRepository eppScheduleRepository;
    private final PaymentRepository paymentRepository;

    public EppScheduleGenerator(EPPScheduleRepository eppScheduleRepository,
                                PaymentRepository paymentRepository) {
        this.eppScheduleRepository = eppScheduleRepository;
        this.paymentRepository = paymentRepository;
    }

    @ApplicationModuleListener
    public void onPaymentCaptured(PaymentCapturedEvent event) {
        if (event.paymentMethod() != PaymentMethod.EPP_INSTALMENTS) {
            return;
        }

        log.info("Processing EPP schedule generation for payment: {}", event.paymentId());

        // Verify Payment Method: EPP MUST be backed by Credit Card (debit cards cannot carry an instalment plan)
        if (event.paymentMethod() == PaymentMethod.CIB_ACCOUNT) {
            throw new IllegalArgumentException("Debit cards / Direct Debit cannot carry an EPP instalment plan");
        }

        Optional<Payment> paymentOpt = paymentRepository.findById(event.paymentId());
        if (paymentOpt.isEmpty()) {
            log.warn("Payment {} not found during EPP schedule generation", event.paymentId());
            return;
        }
        Payment payment = paymentOpt.get();

        // Check if schedule already exists
        if (eppScheduleRepository.findByPaymentId(event.paymentId()).isPresent()) {
            log.info("EPP schedule already exists for payment {}", event.paymentId());
            return;
        }

        int tenorMonths = (event.eppTenorMonths() != null && event.eppTenorMonths() > 0)
                ? event.eppTenorMonths()
                : 12;
        BigDecimal principal = event.totalAmount();

        EPPSchedule schedule = calculateSchedule(payment, principal, tenorMonths, false);
        eppScheduleRepository.save(schedule);

        log.info("Successfully generated EPP schedule for payment {} with tenor {} months",
                event.paymentId(), tenorMonths);
    }

    public EPPSchedule calculateSchedule(Payment payment, BigDecimal principal, int tenorMonths, boolean customAllocationRequested) {
        // Strict Guardrail: EPP Interest Allocation check
        if (customAllocationRequested) {
            throw new PendingBusinessRuleException(
                    "Pending Business Rule: EPP Interest Allocation is undefined. " +
                    "Allocation of interest and admin fees across Family, School, or Bank requires business clarification."
            );
        }

        if (principal == null || principal.compareTo(BigDecimal.ZERO) <= 0) {
            throw new IllegalArgumentException("Principal amount must be greater than zero");
        }

        BigDecimal scaledPrincipal = principal.setScale(2, RoundingMode.HALF_UP);
        BigDecimal annualInterestRate;
        BigDecimal interestAmount;
        BigDecimal adminFee;

        if (tenorMonths == 3) {
            annualInterestRate = BigDecimal.ZERO.setScale(4, RoundingMode.HALF_UP);
            interestAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
            adminFee = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        } else {
            annualInterestRate = ANNUAL_RATE.setScale(4, RoundingMode.HALF_UP);
            interestAmount = scaledPrincipal.multiply(annualInterestRate)
                    .multiply(BigDecimal.valueOf(tenorMonths))
                    .divide(BigDecimal.valueOf(12), 2, RoundingMode.HALF_UP);
            BigDecimal calculatedAdminFee = scaledPrincipal.multiply(ADMIN_FEE_RATE).setScale(2, RoundingMode.HALF_UP);
            adminFee = calculatedAdminFee.min(ADMIN_FEE_CAP);
        }

        BigDecimal totalPayable = scaledPrincipal.add(interestAmount).add(adminFee).setScale(2, RoundingMode.HALF_UP);
        BigDecimal monthlyInstalment = totalPayable.divide(BigDecimal.valueOf(tenorMonths), 2, RoundingMode.HALF_UP);

        return new EPPSchedule(
                payment,
                tenorMonths,
                scaledPrincipal,
                annualInterestRate,
                interestAmount,
                adminFee,
                totalPayable,
                monthlyInstalment
        );
    }
}
