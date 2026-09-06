package com.tuitionnetwork.payments;

import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.common.exceptions.PendingBusinessRuleException;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.dto.PaymentAllocationResultDto;
import com.tuitionnetwork.payments.dto.PaymentSettleRequest;
import com.tuitionnetwork.payments.dto.PaymentSettleResponse;
import com.tuitionnetwork.payments.dto.SelectedDueDto;
import com.tuitionnetwork.payments.event.PaymentCapturedEvent;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.payments.service.PaymentSettlementService;
import com.tuitionnetwork.payments.service.PaymentTransactionExecutor;
import com.tuitionnetwork.payments.spi.BankGatewayAdapterInterface;
import com.tuitionnetwork.payments.spi.GatewayResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.context.ApplicationEventPublisher;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class PaymentSettlementServiceTest {

    private PaymentRepository paymentRepository;
    private FeeLineRepository feeLineRepository;
    private BankGatewayAdapterInterface bankGatewayAdapter;
    private ApplicationEventPublisher eventPublisher;
    private PaymentTransactionExecutor transactionExecutor;
    private PaymentSettlementService settlementService;

    @BeforeEach
    void setUp() {
        paymentRepository = mock(PaymentRepository.class);
        feeLineRepository = mock(FeeLineRepository.class);
        bankGatewayAdapter = mock(BankGatewayAdapterInterface.class);
        eventPublisher = mock(ApplicationEventPublisher.class);

        transactionExecutor = new PaymentTransactionExecutor(paymentRepository, feeLineRepository, eventPublisher);
        settlementService = new PaymentSettlementService(paymentRepository, feeLineRepository, bankGatewayAdapter, transactionExecutor);
    }

    @Test
    void settlePayment_throwsPendingBusinessRule_whenCardlessEppRequested() {
        PaymentSettleRequest request = new PaymentSettleRequest(
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                PaymentMethod.EPP_INSTALMENTS,
                List.of(new SelectedDueDto(UUID.randomUUID(), new BigDecimal("1000.00"))),
                new BigDecimal("1000.00"),
                null,
                null, // No card provided!
                null
        );

        PendingBusinessRuleException ex = assertThrows(PendingBusinessRuleException.class, () ->
                settlementService.settlePayment(request, null)
        );

        assertTrue(ex.getMessage().contains("Cardless EPP Initiation is undefined"));
        verifyNoInteractions(bankGatewayAdapter);
    }

    @Test
    void settlePayment_throwsPendingBusinessRule_whenPostDeadlinePartialPaymentAttempted() {
        UUID feeLineId = UUID.randomUUID();
        FeeLine overdueFeeLine = new FeeLine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                FeeType.TUITION,
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                "Term 1 · 2025",
                LocalDate.now().minusDays(10) // Past due deadline!
        );
        overdueFeeLine.setId(feeLineId);

        when(feeLineRepository.findById(feeLineId)).thenReturn(Optional.of(overdueFeeLine));

        PaymentSettleRequest request = new PaymentSettleRequest(
                UUID.randomUUID().toString(),
                UUID.randomUUID(),
                PaymentMethod.CREDIT_CARD,
                List.of(new SelectedDueDto(feeLineId, new BigDecimal("2000.00"))), // Partial payment of 2000 out of 5000
                new BigDecimal("2000.00"),
                null,
                "4111222233334444",
                null
        );

        PendingBusinessRuleException ex = assertThrows(PendingBusinessRuleException.class, () ->
                settlementService.settlePayment(request, null)
        );

        assertTrue(ex.getMessage().contains("Post-Deadline Partial Payments are undefined"));
        verifyNoInteractions(bankGatewayAdapter);
    }

    @Test
    void settlePayment_successfulCreditCardPayment_updatesFeeLineAndPublishesEvent() {
        UUID feeLineId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        String idempotencyKey = UUID.randomUUID().toString();

        FeeLine feeLine = new FeeLine(
                UUID.randomUUID(),
                UUID.randomUUID(),
                FeeType.TUITION,
                new BigDecimal("6000.00"),
                new BigDecimal("6000.00"),
                "Term 2 · 2026",
                LocalDate.now().plusMonths(1)
        );
        feeLine.setId(feeLineId);

        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.empty());
        when(feeLineRepository.findById(feeLineId)).thenReturn(Optional.of(feeLine));
        when(bankGatewayAdapter.chargeCard(eq(new BigDecimal("6000.00")), eq(idempotencyKey)))
                .thenReturn(new GatewayResponse(PaymentStatus.CAPTURED, "AUTH-123", "TXN-999"));

        when(paymentRepository.save(any(Payment.class))).thenAnswer(invocation -> {
            Payment p = invocation.getArgument(0);
            p.setId(UUID.randomUUID());
            return p;
        });

        PaymentSettleRequest request = new PaymentSettleRequest(
                idempotencyKey,
                guardianId,
                PaymentMethod.CREDIT_CARD,
                List.of(new SelectedDueDto(feeLineId, new BigDecimal("6000.00"))),
                new BigDecimal("6000.00"),
                null,
                "4111222233334444",
                null
        );

        PaymentSettleResponse response = settlementService.settlePayment(request, idempotencyKey);

        assertNotNull(response);
        assertEquals(PaymentStatus.CAPTURED, response.status());
        assertEquals("AUTH-123", response.authCode());
        assertEquals("TXN-999", response.transactionReference());
        assertEquals(FeeStatus.PAID, feeLine.getStatus());
        assertEquals(0, feeLine.getRemainingAmount().compareTo(BigDecimal.ZERO));
        assertEquals(0, feeLine.getPaidAmount().compareTo(new BigDecimal("6000.00")));

        // Verify ApplicationEventPublisher published PaymentCapturedEvent
        ArgumentCaptor<PaymentCapturedEvent> eventCaptor = ArgumentCaptor.forClass(PaymentCapturedEvent.class);
        verify(eventPublisher).publishEvent(eventCaptor.capture());
        PaymentCapturedEvent capturedEvent = eventCaptor.getValue();
        assertEquals(guardianId, capturedEvent.guardianId());
        assertEquals(new BigDecimal("6000.00"), capturedEvent.totalAmount());
        assertEquals("TXN-999", capturedEvent.transactionReference());
    }

    @Test
    void settlePayment_idempotentSecondCall_returnsExistingPaymentWithoutCharging() {
        String idempotencyKey = "IDEMP-KEY-123";
        Payment existingPayment = new Payment(
                UUID.randomUUID(),
                new BigDecimal("3000.00"),
                PaymentMethod.CREDIT_CARD,
                idempotencyKey
        );
        existingPayment.setId(UUID.randomUUID());
        existingPayment.setStatus(PaymentStatus.CAPTURED);
        existingPayment.setAuthCode("AUTH-ORIGINAL");
        existingPayment.setTransactionReference("TXN-ORIGINAL");
        existingPayment.setCreatedAt(LocalDateTime.now());

        when(paymentRepository.findByIdempotencyKey(idempotencyKey)).thenReturn(Optional.of(existingPayment));

        PaymentSettleRequest request = new PaymentSettleRequest(
                idempotencyKey,
                existingPayment.getGuardianId(),
                PaymentMethod.CREDIT_CARD,
                List.of(),
                new BigDecimal("3000.00"),
                null,
                "4111222233334444",
                null
        );

        PaymentSettleResponse response = settlementService.settlePayment(request, idempotencyKey);

        assertEquals("AUTH-ORIGINAL", response.authCode());
        assertEquals("TXN-ORIGINAL", response.transactionReference());
        verifyNoInteractions(bankGatewayAdapter);
        verifyNoInteractions(eventPublisher);
    }
}
