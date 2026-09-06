package com.tuitionnetwork.notifications;

import com.tuitionnetwork.notifications.domain.Notification;
import com.tuitionnetwork.notifications.repository.NotificationRepository;
import com.tuitionnetwork.payments.event.PaymentCapturedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

@Component
public class PaymentNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationService.class);

    private final NotificationRepository notificationRepository;

    @Autowired
    public PaymentNotificationService(@Autowired(required = false) NotificationRepository notificationRepository) {
        this.notificationRepository = notificationRepository;
    }

    public PaymentNotificationService() {
        this(null);
    }

    @ApplicationModuleListener
    public void onPaymentCaptured(PaymentCapturedEvent event) {
        String smsMessage = String.format(
                "[SMS GATEWAY SIMULATION] To Guardian [%s]: Your payment of %s EGP was successfully captured. " +
                "Auth: %s, Transaction Ref: %s. Thank you for using Tuition Network Egypt.",
                event.guardianId(),
                event.totalAmount(),
                event.authCode(),
                event.transactionReference()
        );

        log.info(smsMessage);
        System.out.println(smsMessage);

        if (notificationRepository != null && event.guardianId() != null) {
            Notification notification = new Notification(event.guardianId(), "SMS", smsMessage, "SENT");
            notificationRepository.save(notification);
        }
    }
}
