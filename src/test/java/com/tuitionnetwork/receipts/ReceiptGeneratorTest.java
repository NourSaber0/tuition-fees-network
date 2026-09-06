package com.tuitionnetwork.receipts;

import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.domain.Receipt;
import com.tuitionnetwork.payments.event.PaymentCapturedEvent;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.payments.repository.ReceiptRepository;
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

class ReceiptGeneratorTest {

    private ReceiptRepository receiptRepository;
    private PaymentRepository paymentRepository;
    private ReceiptGenerator receiptGenerator;

    @BeforeEach
    void setUp() {
        receiptRepository = mock(ReceiptRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        receiptGenerator = new ReceiptGenerator(receiptRepository, paymentRepository);
    }

    @Test
    void onPaymentCaptured_generatesPdfUrlAndCryptoSignature_savesReceipt() {
        UUID paymentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        Payment payment = new Payment(guardianId, new BigDecimal("4500.00"), PaymentMethod.CREDIT_CARD, "IDEMP-REC-1");
        payment.setId(paymentId);

        when(paymentRepository.findById(paymentId)).thenReturn(Optional.of(payment));
        when(receiptRepository.findByPaymentId(paymentId)).thenReturn(Optional.empty());

        PaymentCapturedEvent event = new PaymentCapturedEvent(
                paymentId,
                guardianId,
                new BigDecimal("4500.00"),
                PaymentMethod.CREDIT_CARD,
                "IDEMP-REC-1",
                "TXN-REC-888",
                "AUTH-REC-999",
                List.of(UUID.randomUUID()),
                LocalDateTime.now()
        );

        receiptGenerator.onPaymentCaptured(event);

        ArgumentCaptor<Receipt> captor = ArgumentCaptor.forClass(Receipt.class);
        verify(receiptRepository).save(captor.capture());

        Receipt savedReceipt = captor.getValue();
        assertNotNull(savedReceipt);
        assertEquals(payment, savedReceipt.getPayment());
        assertEquals("https://cdn.tuitionnetwork.eg/receipts/receipt-" + paymentId + ".pdf", savedReceipt.getFileUrl());
        assertTrue(savedReceipt.getCryptoSignature().startsWith("SIG-SHA256-"));
        assertNotNull(savedReceipt.getIssuedAt());
    }
}
