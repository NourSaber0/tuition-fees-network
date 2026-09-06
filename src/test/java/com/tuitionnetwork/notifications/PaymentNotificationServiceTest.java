package com.tuitionnetwork.notifications;

import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.event.PaymentCapturedEvent;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;

class PaymentNotificationServiceTest {

    @Test
    void onPaymentCaptured_logsSmsSimulationMessageWithoutError() {
        PaymentNotificationService service = new PaymentNotificationService();

        PaymentCapturedEvent event = new PaymentCapturedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("2500.00"),
                PaymentMethod.CREDIT_CARD,
                "IDEMP-SMS-1",
                "TXN-SMS-999",
                "AUTH-SMS-111",
                List.of(UUID.randomUUID()),
                LocalDateTime.now()
        );

        assertDoesNotThrow(() -> service.onPaymentCaptured(event));
    }
}
