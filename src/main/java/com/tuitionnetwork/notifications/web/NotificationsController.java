package com.tuitionnetwork.notifications.web;

import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.notifications.domain.NotifType;
import com.tuitionnetwork.notifications.dto.NotificationListResponse;
import com.tuitionnetwork.notifications.dto.SchoolNotificationDto;
import com.tuitionnetwork.notifications.dto.SchoolNotificationListResponse;
import com.tuitionnetwork.notifications.dto.SchoolNotificationPreferencesDto;
import com.tuitionnetwork.notifications.dto.UnreadCountResponse;
import com.tuitionnetwork.notifications.service.NotificationsService;
import com.tuitionnetwork.notifications.service.SchoolNotificationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Notifications module (Phase 8 Bank Back-Office & Phase 9 School Portal).
 */
@RestController
@RequestMapping({"/api/v1/notifications", "/notifications"})
@PreAuthorize("hasAnyRole('BACK_OFFICE', 'SCHOOL_ADMIN', 'SCHOOL_FINANCE', 'INSTITUTION_ADMIN')")
public class NotificationsController {

    private final NotificationsService notificationsService;
    private final SchoolNotificationService schoolNotificationService;
    private final InstitutionRepository institutionRepository;

    public NotificationsController(
            NotificationsService notificationsService,
            @Autowired(required = false) SchoolNotificationService schoolNotificationService,
            @Autowired(required = false) InstitutionRepository institutionRepository) {
        this.notificationsService = notificationsService;
        this.schoolNotificationService = schoolNotificationService;
        this.institutionRepository = institutionRepository;
    }

    @GetMapping
    public ResponseEntity<?> list(
            @RequestParam(value = "type", required = false) String type,
            @RequestParam(value = "read", required = false) Boolean read,
            @RequestParam(value = "unread", required = false) Boolean unread,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "institutionId", required = false) UUID requestedInstitutionId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        int resolvedSize = pageSize != null ? pageSize : (size != null ? size : 25);

        if (isSchoolRole()) {
            UUID schoolId = resolveAndValidateSchoolId(requestedInstitutionId, principal);
            Boolean resolvedRead = read != null ? read : (unread != null ? !unread : null);
            return ResponseEntity.ok(schoolNotificationService.getNotifications(schoolId, type, resolvedRead, page, resolvedSize));
        }

        // Back-office flow
        NotifType resolvedType = null;
        if (type != null && !type.isBlank()) {
            for (NotifType t : NotifType.values()) {
                if (t.name().equalsIgnoreCase(type.trim())) {
                    resolvedType = t;
                    break;
                }
            }
        }
        boolean boUnread = unread != null ? unread : (read != null && !read);
        return ResponseEntity.ok(notificationsService.list(resolvedType, boUnread, page, resolvedSize));
    }

    @GetMapping("/unread-count")
    public ResponseEntity<?> unreadCount(
            @RequestParam(value = "institutionId", required = false) UUID requestedInstitutionId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        if (isSchoolRole()) {
            UUID schoolId = resolveAndValidateSchoolId(requestedInstitutionId, principal);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("count", schoolNotificationService.getUnreadCount(schoolId));
            return ResponseEntity.ok(body);
        }
        return ResponseEntity.ok(new UnreadCountResponse(notificationsService.unreadCount()));
    }

    @RequestMapping(value = "/{id}/read", method = {RequestMethod.POST, RequestMethod.PATCH})
    public ResponseEntity<Map<String, Object>> markRead(
            @PathVariable("id") String idStr,
            @RequestParam(value = "institutionId", required = false) UUID requestedInstitutionId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        if (isSchoolRole()) {
            UUID schoolId = resolveAndValidateSchoolId(requestedInstitutionId, principal);
            SchoolNotificationDto dto = schoolNotificationService.markAsRead(schoolId, idStr);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("id", dto.id());
            body.put("read", true);
            return ResponseEntity.ok(body);
        }

        UUID id = parseUuid(idStr);
        notificationsService.markRead(id);
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("id", id);
        body.put("read", true);
        return ResponseEntity.ok(body);
    }

    @PostMapping("/read-all")
    public ResponseEntity<Map<String, Object>> markAllRead(
            @RequestParam(value = "institutionId", required = false) UUID requestedInstitutionId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        if (isSchoolRole()) {
            UUID schoolId = resolveAndValidateSchoolId(requestedInstitutionId, principal);
            int updated = schoolNotificationService.markAllAsRead(schoolId);
            Map<String, Object> body = new LinkedHashMap<>();
            body.put("updated", updated);
            return ResponseEntity.ok(body);
        }

        int updated = notificationsService.markAllRead();
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("updated", updated);
        return ResponseEntity.ok(body);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> dismiss(
            @PathVariable("id") String idStr,
            @RequestParam(value = "institutionId", required = false) UUID requestedInstitutionId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        if (isSchoolRole()) {
            UUID schoolId = resolveAndValidateSchoolId(requestedInstitutionId, principal);
            schoolNotificationService.dismiss(schoolId, idStr);
            return ResponseEntity.noContent().build();
        }

        UUID id = parseUuid(idStr);
        notificationsService.dismiss(id);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/reminders")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SCHOOL_FINANCE', 'INSTITUTION_ADMIN')")
    public ResponseEntity<SchoolNotificationListResponse> listReminders(
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "institutionId", required = false) UUID requestedInstitutionId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        int resolvedSize = pageSize != null ? pageSize : (size != null ? size : 25);
        UUID schoolId = resolveAndValidateSchoolId(requestedInstitutionId, principal);
        return ResponseEntity.ok(schoolNotificationService.getReminders(schoolId, status, page, resolvedSize));
    }

    @GetMapping("/preferences")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SCHOOL_FINANCE', 'INSTITUTION_ADMIN')")
    public ResponseEntity<SchoolNotificationPreferencesDto> getPreferences(
            @RequestParam(value = "institutionId", required = false) UUID requestedInstitutionId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        UUID schoolId = resolveAndValidateSchoolId(requestedInstitutionId, principal);
        return ResponseEntity.ok(schoolNotificationService.getPreferences(schoolId));
    }

    @PutMapping("/preferences")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SCHOOL_FINANCE', 'INSTITUTION_ADMIN')")
    public ResponseEntity<SchoolNotificationPreferencesDto> updatePreferences(
            @RequestBody SchoolNotificationPreferencesDto request,
            @RequestParam(value = "institutionId", required = false) UUID requestedInstitutionId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        UUID schoolId = resolveAndValidateSchoolId(requestedInstitutionId, principal);
        UUID actorId = principal != null ? principal.userId() : null;
        return ResponseEntity.ok(schoolNotificationService.updatePreferences(schoolId, request, actorId));
    }

    @GetMapping(value = "/stream", produces = MediaType.TEXT_EVENT_STREAM_VALUE)
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public SseEmitter stream() {
        return notificationsService.subscribe();
    }

    private UUID parseUuid(String idStr) {
        try {
            return UUID.fromString(idStr);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found: " + idStr);
        }
    }

    private UUID resolveAndValidateSchoolId(UUID requestedInstitutionId, SecurityUserPrincipal principal) {
        UUID principalSchoolId = (principal != null) ? principal.institutionId() : null;

        if (principalSchoolId != null) {
            if (requestedInstitutionId != null && !requestedInstitutionId.equals(principalSchoolId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "cross_school_access: Access denied to other school's notifications");
            }
            return principalSchoolId;
        }

        if (requestedInstitutionId != null) {
            return requestedInstitutionId;
        }

        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null && auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_SCHOOL_ADMIN") ||
                a.getAuthority().equals("ROLE_SCHOOL_FINANCE") ||
                a.getAuthority().equals("ROLE_INSTITUTION_ADMIN"))) {
            if (institutionRepository != null) {
                return institutionRepository.findAll().stream()
                        .filter(i -> i.getAccountStatus() != null && i.getAccountStatus().name().equalsIgnoreCase("ACTIVE"))
                        .map(Institution::getId)
                        .findFirst()
                        .orElse(null);
            }
        }
        return null;
    }

    private boolean isSchoolRole() {
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth == null || auth.getAuthorities() == null) {
            return false;
        }
        return auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_SCHOOL_ADMIN") ||
                a.getAuthority().equals("ROLE_SCHOOL_FINANCE") ||
                a.getAuthority().equals("ROLE_INSTITUTION_ADMIN"));
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
