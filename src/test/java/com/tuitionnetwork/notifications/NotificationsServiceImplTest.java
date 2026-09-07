package com.tuitionnetwork.notifications;

import com.tuitionnetwork.notifications.domain.BackOfficeNotification;
import com.tuitionnetwork.notifications.domain.NotifSeverity;
import com.tuitionnetwork.notifications.domain.NotifType;
import com.tuitionnetwork.notifications.dto.NotificationListResponse;
import com.tuitionnetwork.notifications.repository.BackOfficeNotificationRepository;
import com.tuitionnetwork.notifications.service.NotificationsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationsServiceImplTest {

    private BackOfficeNotificationRepository repository;
    private NotificationsServiceImpl service;

    @BeforeEach
    void setUp() {
        repository = mock(BackOfficeNotificationRepository.class);
        service = new NotificationsServiceImpl(repository);
        when(repository.save(any(BackOfficeNotification.class))).thenAnswer(inv -> inv.getArgument(0));
    }

    private BackOfficeNotification notif(NotifType type, boolean read) {
        BackOfficeNotification n = new BackOfficeNotification(type, NotifSeverity.HIGH, "t", "b");
        n.setId(UUID.randomUUID());
        n.setReadFlag(read);
        return n;
    }

    @Test
    void list_mapsFeedAndAttachesUnreadCount() {
        when(repository.feed(isNull(), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(notif(NotifType.FAILED_PAYMENT, false),
                        notif(NotifType.SYSTEM_ALERT, true)), PageRequest.of(0, 25), 2));
        when(repository.countByReadFlagFalse()).thenReturn(5L);

        NotificationListResponse resp = service.list(null, false, 0, 25);

        assertEquals(2, resp.data().size());
        assertEquals(2, resp.total());
        assertEquals(5L, resp.unreadCount());
        assertEquals(0, resp.page());
    }

    @Test
    void list_unreadOnly_passesFlagThrough() {
        when(repository.feed(isNull(), eq(true), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));
        when(repository.countByReadFlagFalse()).thenReturn(0L);

        service.list(null, true, 0, 25);

        verify(repository).feed(isNull(), eq(true), any());
    }

    @Test
    void list_byType_passesTypeThrough() {
        when(repository.feed(eq(NotifType.RECON_EXCEPTION), eq(false), any()))
                .thenReturn(new PageImpl<>(List.of(), PageRequest.of(0, 25), 0));
        when(repository.countByReadFlagFalse()).thenReturn(0L);

        service.list(NotifType.RECON_EXCEPTION, false, 0, 25);

        verify(repository).feed(eq(NotifType.RECON_EXCEPTION), eq(false), any());
    }

    @Test
    void unreadCount_returnsRepositoryCount() {
        when(repository.countByReadFlagFalse()).thenReturn(7L);
        assertEquals(7L, service.unreadCount());
    }

    @Test
    void markRead_setsFlagAndSaves() {
        BackOfficeNotification n = notif(NotifType.FAILED_PAYMENT, false);
        when(repository.findById(n.getId())).thenReturn(Optional.of(n));

        service.markRead(n.getId());

        assertEquals(true, n.isReadFlag());
        verify(repository).save(n);
    }

    @Test
    void markRead_alreadyRead_doesNotSaveAgain() {
        BackOfficeNotification n = notif(NotifType.FAILED_PAYMENT, true);
        when(repository.findById(n.getId())).thenReturn(Optional.of(n));

        service.markRead(n.getId());

        verify(repository, never()).save(any());
    }

    @Test
    void markRead_unknown_notFound() {
        UUID id = UUID.randomUUID();
        when(repository.findById(id)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.markRead(id));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void markAllRead_returnsUpdatedCount() {
        when(repository.markAllRead()).thenReturn(4);
        assertEquals(4, service.markAllRead());
    }

    @Test
    void dismiss_deletesWhenPresent() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(true);
        service.dismiss(id);
        verify(repository).deleteById(id);
    }

    @Test
    void dismiss_unknown_notFound() {
        UUID id = UUID.randomUUID();
        when(repository.existsById(id)).thenReturn(false);
        assertThrows(ResponseStatusException.class, () -> service.dismiss(id));
        verify(repository, never()).deleteById(any());
    }
}
