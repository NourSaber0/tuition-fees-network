package com.tuitionnetwork.notifications.web;

import com.tuitionnetwork.notifications.domain.NotifType;
import com.tuitionnetwork.notifications.dto.NotificationListResponse;
import com.tuitionnetwork.notifications.dto.UnreadCountResponse;
import com.tuitionnetwork.notifications.service.NotificationsService;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Back-office notification feed (Phase 8). Read + manage; notifications are
 * server-generated (see {@code BackOfficeNotificationPublisher}).
 */
@RestController
@RequestMapping("/api/v1/notifications")
@PreAuthorize("hasRole('BACK_OFFICE')")
public class NotificationsController {

    private final NotificationsService notificationsService;

    public NotificationsController(NotificationsService notificationsService) {
        this.notificationsService = notificationsService;
    }

    @GetMapping
    public ResponseEntity<NotificationListResponse> list(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "unread", defaultValue = "false") boolean unread,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        NotifType resolvedType = null;
        if (type != null && !type.isBlank()) {
            for (NotifType t : NotifType.values()) {
                if (t.name().equalsIgnoreCase(type.trim())) {
                    resolvedType = t;
                    break;
                }
            }
        }
        int resolvedSize = pageSize != null ? pageSize : (size != null ? size : 25);
        return ResponseEntity.ok(notificationsService.list(resolvedType, unread, page, resolvedSize));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<UnreadCountResponse> unreadCount() {
        return ResponseEntity.ok(new UnreadCountResponse(notificationsService.unreadCount()));
    }

    @PostMapping("/{id}/read")
    public ResponseEntity<Map<String, Object>> markRead(@PathVariable("id") UUID id) {
        notificationsService.markRead(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("read", true);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllRead() {
        int updated = notificationsService.markAllRead();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("updated", updated);
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> dismiss(@PathVariable("id") UUID id) {
        notificationsService.dismiss(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter stream() {
        return notificationsService.subscribe();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String code = (status instanceof HttpStatus hs) ? hs.name() : String.valueOf(status.value());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", code);
        body.put("message", ex.getReason() != null ? ex.getReason() : code);
        return ResponseEntity.status(status).body(body);
    }
}
