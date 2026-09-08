package com.tuitionnetwork.notifications.service;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.notifications.domain.BackOfficeNotification;
import com.tuitionnetwork.notifications.domain.NotifType;
import com.tuitionnetwork.notifications.dto.BackOfficeNotificationDto;
import com.tuitionnetwork.notifications.dto.NotificationListResponse;
import com.tuitionnetwork.notifications.repository.BackOfficeNotificationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CopyOnWriteArrayList;

@Service
public class NotificationsServiceImpl implements NotificationsService {

    private static final int MAX_PAGE_SIZE = 100;

    private final BackOfficeNotificationRepository repository;
    private final AuditLogRepository auditLogRepository;
    private final List<SseEmitter> emitters = new CopyOnWriteArrayList<>();

    public NotificationsServiceImpl(BackOfficeNotificationRepository repository) {
        this(repository, null);
    }

    @Autowired
    public NotificationsServiceImpl(BackOfficeNotificationRepository repository,
                                    @Autowired(required = false) AuditLogRepository auditLogRepository) {
        this.repository = repository;
        this.auditLogRepository = auditLogRepository;
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
            if (auditLogRepository != null) {
                auditLogRepository.save(new AuditLog(
                        null,
                        "BACK_OFFICE",
                        "NOTIFICATION_READ",
                        "Notification " + id + " marked as read"
                ));
            }
        }
    }

    @Override
    @Transactional
    public int markAllRead() {
        int updated = repository.markAllRead();
        if (auditLogRepository != null) {
            auditLogRepository.save(new AuditLog(
                    null,
                    "BACK_OFFICE",
                    "NOTIFICATIONS_READ_ALL",
                    "All notifications marked as read (" + updated + " updated)"
            ));
        }
        return updated;
    }

    @Override
    @Transactional
    public void dismiss(UUID id) {
        if (!repository.existsById(id)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found: " + id);
        }
        repository.deleteById(id);
        if (auditLogRepository != null) {
            auditLogRepository.save(new AuditLog(
                    null,
                    "BACK_OFFICE",
                    "NOTIFICATION_DISMISS",
                    "Notification " + id + " dismissed"
            ));
        }
    }

    @Override
    public SseEmitter subscribe() {
        SseEmitter emitter = new SseEmitter(0L);
        emitters.add(emitter);

        emitter.onCompletion(() -> emitters.remove(emitter));
        emitter.onTimeout(() -> {
            emitter.complete();
            emitters.remove(emitter);
        });
        emitter.onError(e -> emitters.remove(emitter));

        try {
            emitter.send(SseEmitter.event().name("INIT").data("connected"));
        } catch (IOException e) {
            emitters.remove(emitter);
        }

        return emitter;
    }

    @Override
    @EventListener
    public void broadcast(BackOfficeNotification notification) {
        if (emitters.isEmpty() || notification == null) {
            return;
        }
        BackOfficeNotificationDto dto = BackOfficeNotificationDto.from(notification);
        List<SseEmitter> deadEmitters = new ArrayList<>();
        for (SseEmitter emitter : emitters) {
            try {
                emitter.send(SseEmitter.event().name("notification").data(dto));
            } catch (Exception e) {
                deadEmitters.add(emitter);
            }
        }
        emitters.removeAll(deadEmitters);
    }
}
