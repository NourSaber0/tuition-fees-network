package com.tuitionnetwork.epp;

import com.tuitionnetwork.common.exceptions.PendingBusinessRuleException;
import com.tuitionnetwork.epp.infrastructure.MockBankEppClient;
import com.tuitionnetwork.payments.domain.EPPSchedule;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.event.PaymentCapturedEvent;
import com.tuitionnetwork.payments.repository.EPPScheduleRepository;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.payments.repository.EppInstallmentRepository;
import com.tuitionnetwork.payments.domain.EppInstallment;
import java.time.LocalDateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import com.tuitionnetwork.settings.service.SettingsService;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.Optional;

@Component
public class EppScheduleGenerator {

    private static final Logger log = LoggerFactory.getLogger(EppScheduleGenerator.class);

    private final EPPScheduleRepository eppScheduleRepository;
    private final PaymentRepository paymentRepository;
    private final SettingsService settingsService;
    private final MockBankEppClient mockBankEppClient;
    private final EppInstallmentRepository eppInstallmentRepository;

    public EppScheduleGenerator(EPPScheduleRepository eppScheduleRepository,
                                PaymentRepository paymentRepository,
                                SettingsService settingsService,
                                MockBankEppClient mockBankEppClient,
                                EppInstallmentRepository eppInstallmentRepository) {
        this.eppScheduleRepository = eppScheduleRepository;
        this.paymentRepository = paymentRepository;
        this.settingsService = settingsService;
        this.mockBankEppClient = mockBankEppClient;
        this.eppInstallmentRepository = eppInstallmentRepository;
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

        for (int i = 1; i <= tenorMonths; i++) {
            EppInstallment installment = new EppInstallment(
                    schedule,
                    i,
                    schedule.getMonthlyInstalment(),
                    LocalDateTime.now().plusMonths(i),
                    "PENDING"
            );
            eppInstallmentRepository.save(installment);
        }

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

        MockBankEppClient.EppPlanResponse planResponse = mockBankEppClient.createPlan(payment.getTransactionReference(), tenorMonths);
        
        BigDecimal totalRepayment = new BigDecimal(planResponse.total_repayment());
        BigDecimal interestAmount = totalRepayment.subtract(principal);
        BigDecimal monthlyInstalment = new BigDecimal(planResponse.monthly_installment());

        return new EPPSchedule(
                payment,
                planResponse.tenor_months(),
                principal,
                BigDecimal.ZERO,
                interestAmount,
                BigDecimal.ZERO,
                totalRepayment,
                monthlyInstalment
        );
    }
}
