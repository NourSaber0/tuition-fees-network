package com.tuitionnetwork.epp;

import com.tuitionnetwork.common.exceptions.PendingBusinessRuleException;
import com.tuitionnetwork.payments.domain.EPPSchedule;
import com.tuitionnetwork.payments.domain.EppPricing;
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
import java.util.Optional;

@Component
public class EppScheduleGenerator {

    private static final Logger log = LoggerFactory.getLogger(EppScheduleGenerator.class);

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

        EppPricing.Quote quote = EppPricing.calculate(principal, tenorMonths);

        return new EPPSchedule(
                payment,
                quote.tenorMonths(),
                quote.principal(),
                quote.annualInterestRate(),
                quote.interestAmount(),
                quote.adminFee(),
                quote.totalPayable(),
                quote.monthlyInstalment()
        );
    }
}
