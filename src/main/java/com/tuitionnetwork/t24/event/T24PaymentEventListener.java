package com.tuitionnetwork.t24.event;

import com.tuitionnetwork.payments.event.PaymentCapturedEvent;
import com.tuitionnetwork.t24.dto.T24BillingDto.UpdateBillingRequest;
import com.tuitionnetwork.t24.service.T24CustomerBillingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;

/**
 * Listens for internal payment capture events and asynchronously (after the settling
 * transaction commits) notifies T24 via UpdateCustomerBillingProcedure to reduce outstanding
 * billing. Runs off the request thread, same as ReceiptGenerator/PaymentNotificationService/
 * EppScheduleGenerator - a slow or unreachable T24 endpoint must never delay a payment response.
 */
@Component
public class T24PaymentEventListener {

    private static final Logger log = LoggerFactory.getLogger(T24PaymentEventListener.class);
    private final T24CustomerBillingService billingService;

    public T24PaymentEventListener(T24CustomerBillingService billingService) {
        this.billingService = billingService;
    }

    @ApplicationModuleListener
    public void onPaymentCaptured(PaymentCapturedEvent event) {
        if (event == null) return;
        log.info("T24 Adapter notified of PaymentCapturedEvent for paymentId={}, amount={}",
                event.paymentId(), event.totalAmount());
        try {
            String billingId = "BILL-" + (event.paymentId() != null
                    ? event.paymentId().toString().substring(0, 8).toUpperCase()
                    : "DEFAULT");
            BigDecimal paidAmount = event.totalAmount() != null ? event.totalAmount() : BigDecimal.ZERO;
            String txnRef = event.transactionReference() != null ? event.transactionReference() : "TXN-" + System.currentTimeMillis();

            billingService.syncPaymentSettlement(
                    new UpdateBillingRequest(billingId, paidAmount, txnRef),
                    event.guardianId()
            );
            log.info("Successfully updated T24 billing item {} for payment {}", billingId, event.paymentId());
        } catch (Exception e) {
            log.warn("Non-blocking error propagating payment {} to T24 customer billing: {}",
                    event.paymentId(), e.getMessage());
        }
    }
}
