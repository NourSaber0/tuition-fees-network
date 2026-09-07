package com.tuitionnetwork.notifications.service;

import com.tuitionnetwork.notifications.domain.BackOfficeNotification;
import com.tuitionnetwork.notifications.domain.NotifSeverity;
import com.tuitionnetwork.notifications.domain.NotifType;
import com.tuitionnetwork.notifications.repository.BackOfficeNotificationRepository;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * The only way to create a back-office notification — other modules call this
 * (directly or via an event listener) when something operationally noteworthy
 * happens. There is no client-facing create endpoint.
 */
@Component
public class BackOfficeNotificationPublisher {

    private final BackOfficeNotificationRepository repository;

    public BackOfficeNotificationPublisher(BackOfficeNotificationRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public BackOfficeNotification publish(NotifType type, NotifSeverity severity, String title, String body,
                                         String meta, String actionLabel, String actionScreen, String actionEntityId) {
        BackOfficeNotification n = new BackOfficeNotification(type, severity, title, body);
        n.setMeta(meta);
        n.setActionLabel(actionLabel);
        n.setActionScreen(actionScreen);
        n.setActionEntityId(actionEntityId);
        return repository.save(n);
    }

    public BackOfficeNotification failedPayment(String title, String body, String transactionId) {
        return publish(NotifType.FAILED_PAYMENT, NotifSeverity.HIGH, title, body,
                "Transaction: " + transactionId, "View Transaction", "transactions", transactionId);
    }

    public BackOfficeNotification reconException(String title, String body, String exceptionRef) {
        return publish(NotifType.RECON_EXCEPTION, NotifSeverity.HIGH, title, body,
                "Exception: " + exceptionRef, "Investigate Exception", "reconciliation", exceptionRef);
    }

    public BackOfficeNotification newInstitution(String title, String body, String institutionId) {
        return publish(NotifType.NEW_INSTITUTION, NotifSeverity.MEDIUM, title, body,
                null, "Review Application", "schools", institutionId);
    }

    public BackOfficeNotification institutionIssue(String title, String body, String institutionId) {
        return publish(NotifType.INSTITUTION_ISSUE, NotifSeverity.MEDIUM, title, body,
                null, "View Institution", "schools", institutionId);
    }

    public BackOfficeNotification systemAlert(NotifSeverity severity, String title, String body) {
        return publish(NotifType.SYSTEM_ALERT, severity, title, body, null, null, null, null);
    }
}
