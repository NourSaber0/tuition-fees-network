package com.tuitionnetwork.notifications;

import com.tuitionnetwork.notifications.domain.BackOfficeNotification;
import com.tuitionnetwork.notifications.domain.NotifSeverity;
import com.tuitionnetwork.notifications.domain.NotifType;
import com.tuitionnetwork.notifications.repository.BackOfficeNotificationRepository;
import com.tuitionnetwork.notifications.service.BackOfficeNotificationPublisher;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import org.springframework.context.ApplicationEventPublisher;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class BackOfficeNotificationPublisherTest {

    private BackOfficeNotificationRepository repository;
    private ApplicationEventPublisher eventPublisher;
    private BackOfficeNotificationPublisher publisher;

    @BeforeEach
    void setUp() {
        repository = mock(BackOfficeNotificationRepository.class);
        eventPublisher = mock(ApplicationEventPublisher.class);
        publisher = new BackOfficeNotificationPublisher(repository, eventPublisher);
        when(repository.save(any(BackOfficeNotification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void failedPayment_setsHighSeverityAndTransactionAction() {
        publisher.failedPayment("Payment Failed", "EGP 22,000 declined", "TX-20260831-0004");

        ArgumentCaptor<BackOfficeNotification> captor = ArgumentCaptor.forClass(BackOfficeNotification.class);
        verify(repository).save(captor.capture());
        BackOfficeNotification n = captor.getValue();

        assertEquals(NotifType.FAILED_PAYMENT, n.getNotifType());
        assertEquals(NotifSeverity.HIGH, n.getSeverity());
        assertEquals("transactions", n.getActionScreen());
        assertEquals("TX-20260831-0004", n.getActionEntityId());
        assertEquals("Transaction: TX-20260831-0004", n.getMeta());
        verify(eventPublisher).publishEvent(n);
    }

    @Test
    void reconException_linksToReconciliationScreen() {
        publisher.reconException("Recon Exception", "EGP 9,400 discrepancy", "EXC-001");

        ArgumentCaptor<BackOfficeNotification> captor = ArgumentCaptor.forClass(BackOfficeNotification.class);
        verify(repository).save(captor.capture());
        assertEquals("reconciliation", captor.getValue().getActionScreen());
        assertEquals("Exception: EXC-001", captor.getValue().getMeta());
        verify(eventPublisher).publishEvent(captor.getValue());
    }

    @Test
    void systemAlert_hasNoAction() {
        publisher.systemAlert(NotifSeverity.LOW, "Gateway latency", "Resolved after 15 min");

        ArgumentCaptor<BackOfficeNotification> captor = ArgumentCaptor.forClass(BackOfficeNotification.class);
        verify(repository).save(captor.capture());
        BackOfficeNotification n = captor.getValue();
        assertEquals(NotifType.SYSTEM_ALERT, n.getNotifType());
        assertNull(n.getActionLabel());
        assertNull(n.getActionScreen());
        verify(eventPublisher).publishEvent(n);
    }

    @Test
    void singleArgConstructor_worksWithoutEventPublisher() {
        BackOfficeNotificationPublisher pub = new BackOfficeNotificationPublisher(repository);
        BackOfficeNotification n = pub.systemAlert(NotifSeverity.LOW, "title", "body");
        verify(repository).save(any(BackOfficeNotification.class));
    }
}
