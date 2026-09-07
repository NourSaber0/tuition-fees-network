package com.tuitionnetwork.notifications.service;

import com.tuitionnetwork.notifications.domain.BackOfficeNotification;
import com.tuitionnetwork.notifications.domain.NotifType;
import com.tuitionnetwork.notifications.dto.BackOfficeNotificationDto;
import com.tuitionnetwork.notifications.dto.NotificationListResponse;
import com.tuitionnetwork.notifications.repository.BackOfficeNotificationRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@Service
public class NotificationsServiceImpl implements NotificationsService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BackOfficeNotificationRepository repository;

    public NotificationsServiceImpl(BackOfficeNotificationRepository repository) {
        this.repository = repository;
    }

    @Override
    @Transactional(readOnly = true)
    public NotificationListResponse list(NotifType type, boolean unreadOnly, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);

        Page<BackOfficeNotification> result = repository.feed(type, unreadOnly, PageRequest.of(safePage, safeSize));
        List<BackOfficeNotificationDto> data = result.getContent().stream()
                .map(BackOfficeNotificationDto::from)
                .toList();

        return NotificationListResponse.of(result, data, repository.countByReadFlagFalse());
    }

    @Override
    @Transactional(readOnly = true)
    public long unreadCount() {
        return repository.countByReadFlagFalse();
    }

    @Override
    @Transactional
    public void markRead(UUID id) {
        BackOfficeNotification n = repository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Notification not found: " + id));
        if (!n.isReadFlag()) {
            n.setReadFlag(true);
            repository.save(n);
        }
    }

    @Override
    @Transactional
    public int markAllRead() {
        return repository.markAllRead();
    }

    @Override
    @Transactional
    public void dismiss(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found: " + id);
        }
        repository.deleteById(id);
    }
}
