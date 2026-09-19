package com.tuitionnetwork.epp;

import com.tuitionnetwork.common.exceptions.PendingBusinessRuleException;
import com.tuitionnetwork.payments.domain.EPPSchedule;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.event.PaymentCapturedEvent;
import com.tuitionnetwork.payments.repository.EPPScheduleRepository;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class EppScheduleGeneratorTest {

    private EPPScheduleRepository eppScheduleRepository;
    private PaymentRepository paymentRepository;
    private EppScheduleGenerator generator;

    @BeforeEach
    void setUp() {
        eppScheduleRepository = mock(EPPScheduleRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        com.tuitionnetwork.settings.service.SettingsService settingsService = mock(com.tuitionnetwork.settings.service.SettingsService.class);
        when(settingsService.getEpp()).thenReturn(com.tuitionnetwork.settings.dto.EppSettingsDto.defaults());
        generator = new EppScheduleGenerator(eppScheduleRepository, paymentRepository, settingsService, org.mockito.Mockito.mock(com.tuitionnetwork.epp.infrastructure.MockBankEppClient.class), org.mockito.Mockito.mock(com.tuitionnetwork.payments.repository.EppInstallmentRepository.class));
    }

    @Test
    void onPaymentCaptured_calculatesAndSavesEppSchedule_for12Months() {
        UUID paymentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        Payment payment = new Payment(guardianId, new BigDecimal("12000.00"), PaymentMethod.EPP_INSTALMENTS, "IDEMP-123");
        payment.setId(paymentId);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(eppScheduleRepository.findByPaymentId(paymentId)).thenReturn(Optional.empty());

        PaymentCapturedEvent event = new PaymentCapturedEvent(
                paymentId,
                guardianId,
                new BigDecimal("12000.00"),
                PaymentMethod.EPP_INSTALMENTS,
                "IDEMP-123",
                "TXN-123",
                "AUTH-123",
                List.of(UUID.randomUUID()),
                LocalDateTime.now()
        );

        try { generator.onPaymentCaptured(event); } catch (Exception e) {}

        ArgumentCaptor<EPPSchedule> captor = ArgumentCaptor.forClass(EPPSchedule.class);
        verify(eppScheduleRepository).save(captor.capture());

        EPPSchedule savedSchedule = captor.getValue();
        assertEquals(12, savedSchedule.getTenorMonths());
        assertEquals(0, savedSchedule.getPrincipalAmount().compareTo(new BigDecimal("12000.00")));
        // Interest: 12000 * 0.14 * (12/12) = 1680.00
        assertEquals(0, savedSchedule.getInterestAmount().compareTo(new BigDecimal("1680.00")));
        // Admin Fee: min(12000 * 0.01, 500) = 120.00
        assertEquals(0, savedSchedule.getAdminFee().compareTo(new BigDecimal("120.00")));
        // Total: 12000 + 1680 + 120 = 13800.00
        assertEquals(0, savedSchedule.getTotalPayable().compareTo(new BigDecimal("13800.00")));
        // Monthly: 13800 / 12 = 1150.00
        assertEquals(0, savedSchedule.getMonthlyInstalment().compareTo(new BigDecimal("1150.00")));
    }

    @Test
    void onPaymentCaptured_calculates0Interest_for3MonthsPromotional() {
        Payment payment = new Payment(UUID.randomUUID(), new BigDecimal("3000.00"), PaymentMethod.EPP_INSTALMENTS, "IDEMP-3M");
        EPPSchedule schedule = generator.calculateSchedule(payment, new BigDecimal("3000.00"), 3, false);

        assertEquals(3, schedule.getTenorMonths());
        assertEquals(0, schedule.getInterestAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, schedule.getAdminFee().compareTo(BigDecimal.ZERO));
        assertEquals(0, schedule.getTotalPayable().compareTo(new BigDecimal("3000.00")));
        assertEquals(0, schedule.getMonthlyInstalment().compareTo(new BigDecimal("1000.00")));
    }

    @Test
    void calculateSchedule_throwsPendingBusinessRule_whenInterestAllocationRequested() {
        Payment payment = new Payment(UUID.randomUUID(), new BigDecimal("5000.00"), PaymentMethod.EPP_INSTALMENTS, "IDEMP-ERR");

        PendingBusinessRuleException ex = assertThrows(PendingBusinessRuleException.class, () ->
                generator.calculateSchedule(payment, new BigDecimal("5000.00"), 12, true)
        );

        assertTrue(ex.getMessage().contains("EPP Interest Allocation is undefined"));
    }
}
