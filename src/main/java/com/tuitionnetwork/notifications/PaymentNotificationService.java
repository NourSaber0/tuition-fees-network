package com.tuitionnetwork.notifications;

import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.notifications.domain.Notification;
import com.tuitionnetwork.notifications.repository.NotificationRepository;
import com.tuitionnetwork.notifications.service.SchoolNotificationService;
import com.tuitionnetwork.payments.event.PaymentCapturedEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
public class PaymentNotificationService {

    private static final Logger log = LoggerFactory.getLogger(PaymentNotificationService.class);

    private final NotificationRepository notificationRepository;

    @Autowired(required = false)
    private SchoolNotificationService schoolNotificationService;

    @Autowired(required = false)
    private FeeLineRepository feeLineRepository;

    @Autowired(required = false)
    private StudentRepository studentRepository;

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

        if (schoolNotificationService != null && feeLineRepository != null && event.affectedFeeLineIds() != null) {
            for (UUID feeId : event.affectedFeeLineIds()) {
                try {
                    feeLineRepository.findById(feeId).ifPresent(fee -> {
                        if (fee.getInstitutionId() != null) {
                            String studentName = "Student";
                            if (studentRepository != null && fee.getStudentId() != null) {
                                studentName = studentRepository.findById(fee.getStudentId())
                                        .map(s -> s.getFullName())
                                        .orElse("Student");
                            }
                            schoolNotificationService.createNotification(
                                    fee.getInstitutionId(),
                                    "payment",
                                    "Payment received",
                                    "Payment of " + event.totalAmount() + " EGP received for " + studentName + "'s " + (fee.getFeeType() != null ? fee.getFeeType().getDisplayName() : "Fee"),
                                    studentName,
                                    fee.getStudentId(),
                                    fee.getFeeType() != null ? fee.getFeeType().getDisplayName() : "Tuition",
                                    event.totalAmount() != null ? event.totalAmount().longValue() : 0L,
                                    fee.getDueDate(),
                                    0,
                                    "Sent",
                                    event.transactionReference()
                            );
                        }
                    });
                } catch (Exception ignored) {}
            }
        }
    }
}
