package com.tuitionnetwork.notifications.service;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.notifications.domain.SchoolNotification;
import com.tuitionnetwork.notifications.dto.SchoolNotificationDto;
import com.tuitionnetwork.notifications.dto.SchoolNotificationListResponse;
import com.tuitionnetwork.notifications.dto.SchoolNotificationPreferencesDto;
import com.tuitionnetwork.notifications.repository.SchoolNotificationRepository;
import com.tuitionnetwork.settings.dto.SchoolNotificationSettingsDto;
import com.tuitionnetwork.settings.dto.SchoolNotificationSettingsRequest;
import com.tuitionnetwork.settings.service.SchoolSettingsService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.*;

@Service
@Transactional
public class SchoolNotificationServiceImpl implements SchoolNotificationService {

    private final SchoolNotificationRepository notificationRepository;
    private final InstitutionRepository institutionRepository;
    private final SchoolSettingsService schoolSettingsService;
    private final AuditLogRepository auditLogRepository;

    @Autowired
    public SchoolNotificationServiceImpl(
            SchoolNotificationRepository notificationRepository,
            InstitutionRepository institutionRepository,
            @Autowired(required = false) SchoolSettingsService schoolSettingsService,
            @Autowired(required = false) AuditLogRepository auditLogRepository) {
        this.notificationRepository = notificationRepository;
        this.institutionRepository = institutionRepository;
        this.schoolSettingsService = schoolSettingsService;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolNotificationListResponse getNotifications(
            UUID institutionId,
            String type,
            Boolean read,
            int page,
            int pageSize) {

        requireInstitution(institutionId);

        int resolvedPage = Math.max(0, page);
        int resolvedSize = pageSize > 0 ? pageSize : 25;
        Pageable pageable = PageRequest.of(resolvedPage, resolvedSize);

        Page<SchoolNotification> pageResult;

        boolean hasType = type != null && !type.isBlank();
        boolean hasRead = read != null;

        if (hasType && hasRead) {
            pageResult = notificationRepository.findByInstitutionIdAndTypeIgnoreCaseAndReadFlagOrderByCreatedAtDesc(
                    institutionId, type.trim(), read, pageable);
        } else if (hasType) {
            pageResult = notificationRepository.findByInstitutionIdAndTypeIgnoreCaseOrderByCreatedAtDesc(
                    institutionId, type.trim(), pageable);
        } else if (hasRead) {
            pageResult = notificationRepository.findByInstitutionIdAndReadFlagOrderByCreatedAtDesc(
                    institutionId, read, pageable);
        } else {
            pageResult = notificationRepository.findByInstitutionIdOrderByCreatedAtDesc(
                    institutionId, pageable);
        }

        long unreadCount = notificationRepository.countByInstitutionIdAndReadFlagFalse(institutionId);

        List<SchoolNotificationDto> dtos = pageResult.getContent().stream()
                .map(this::toDto)
                .toList();

        return new SchoolNotificationListResponse(
                dtos,
                unreadCount,
                pageResult.getTotalElements(),
                resolvedPage,
                resolvedSize,
                pageResult.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public long getUnreadCount(UUID institutionId) {
        requireInstitution(institutionId);
        return notificationRepository.countByInstitutionIdAndReadFlagFalse(institutionId);
    }

    @Override
    public SchoolNotificationDto markAsRead(UUID institutionId, String idOrRef) {
        requireInstitution(institutionId);
        SchoolNotification notif = findByIdOrRef(institutionId, idOrRef);
        notif.setReadFlag(true);
        notif.setReadAt(LocalDateTime.now());
        SchoolNotification saved = notificationRepository.save(notif);
        return toDto(saved);
    }

    @Override
    public int markAllAsRead(UUID institutionId) {
        requireInstitution(institutionId);
        return notificationRepository.markAllReadForInstitution(institutionId, LocalDateTime.now());
    }

    @Override
    public void dismiss(UUID institutionId, String idOrRef) {
        requireInstitution(institutionId);
        SchoolNotification notif = findByIdOrRef(institutionId, idOrRef);
        notificationRepository.delete(notif);
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolNotificationListResponse getReminders(
            UUID institutionId,
            String status,
            int page,
            int pageSize) {

        requireInstitution(institutionId);

        int resolvedPage = Math.max(0, page);
        int resolvedSize = pageSize > 0 ? pageSize : 25;
        Pageable pageable = PageRequest.of(resolvedPage, resolvedSize);

        Page<SchoolNotification> pageResult;
        if (status != null && !status.isBlank()) {
            pageResult = notificationRepository.findByInstitutionIdAndTypeIgnoreCaseAndNotificationStatusIgnoreCaseOrderByCreatedAtDesc(
                    institutionId, "reminder", status.trim(), pageable);
        } else {
            pageResult = notificationRepository.findByInstitutionIdAndTypeIgnoreCaseOrderByCreatedAtDesc(
                    institutionId, "reminder", pageable);
        }

        long unreadCount = notificationRepository.countByInstitutionIdAndReadFlagFalse(institutionId);

        List<SchoolNotificationDto> dtos = pageResult.getContent().stream()
                .map(this::toDto)
                .toList();

        return new SchoolNotificationListResponse(
                dtos,
                unreadCount,
                pageResult.getTotalElements(),
                resolvedPage,
                resolvedSize,
                pageResult.getTotalPages()
        );
    }

    @Override
    @Transactional(readOnly = true)
    public SchoolNotificationPreferencesDto getPreferences(UUID institutionId) {
        requireInstitution(institutionId);
        if (schoolSettingsService != null) {
            try {
                SchoolNotificationSettingsDto settings = schoolSettingsService.getNotificationSettings(institutionId);
                if (settings != null && settings.channels() != null) {
                    return new SchoolNotificationPreferencesDto(settings.channels());
                }
            } catch (Exception ignored) {}
        }
        return SchoolNotificationPreferencesDto.of(true, true);
    }

    @Override
    public SchoolNotificationPreferencesDto updatePreferences(
            UUID institutionId,
            SchoolNotificationPreferencesDto preferences,
            UUID actorId) {

        requireInstitution(institutionId);
        Map<String, Boolean> channels = preferences != null && preferences.channels() != null
                ? preferences.channels()
                : Map.of("inApp", true, "email", true);

        if (schoolSettingsService != null) {
            try {
                SchoolNotificationSettingsRequest req = new SchoolNotificationSettingsRequest(channels);
                SchoolNotificationSettingsDto updated = schoolSettingsService.updateNotificationSettings(institutionId, req, actorId);
                if (updated != null && updated.channels() != null) {
                    return new SchoolNotificationPreferencesDto(updated.channels());
                }
            } catch (Exception ignored) {}
        }

        return new SchoolNotificationPreferencesDto(channels);
    }

    @Override
    public SchoolNotification createNotification(
            UUID institutionId,
            String type,
            String title,
            String description,
            String studentName,
            UUID studentId,
            String feeType,
            Long feeAmountEGP,
            LocalDate dueDate,
            Integer daysUntilDue,
            String notificationStatus,
            String relatedId) {

        requireInstitution(institutionId);

        String ref = generateNotificationRef(institutionId);
        SchoolNotification n = new SchoolNotification(institutionId, ref, type, title, description);
        n.setStudentName(studentName);
        n.setStudentId(studentId);
        n.setFeeType(feeType);
        n.setFeeAmountEGP(feeAmountEGP);
        n.setDueDate(dueDate);
        n.setDaysUntilDue(daysUntilDue);
        n.setNotificationStatus(notificationStatus != null ? notificationStatus : "Sent");
        n.setRelatedId(relatedId);
        n.setCreatedAt(LocalDateTime.now());

        SchoolNotification saved = notificationRepository.save(n);

        if (auditLogRepository != null) {
            try {
                auditLogRepository.save(new AuditLog(
                        null,
                        "SYSTEM",
                        "CREATE_NOTIFICATION",
                        "Created " + type + " notification " + ref + " for school " + institutionId
                ));
            } catch (Exception ignored) {}
        }

        return saved;
    }

    private static final java.util.concurrent.atomic.AtomicLong REF_COUNTER = new java.util.concurrent.atomic.AtomicLong(100);

    private SchoolNotification findByIdOrRef(UUID institutionId, String idOrRef) {
        if (idOrRef == null || idOrRef.isBlank()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification ID required");
        }

        String cleaned = idOrRef.trim();

        // 1. Try by notificationRef for this institution first
        Optional<SchoolNotification> byRefThisInst = notificationRepository.findByNotificationRefAndInstitutionId(cleaned, institutionId);
        if (byRefThisInst.isPresent()) {
            return byRefThisInst.get();
        }

        // Check if it belongs to another school (403 Forbidden)
        List<SchoolNotification> anyByRef = notificationRepository.findAllByNotificationRef(cleaned);
        if (!anyByRef.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "cross_school_access: Notification belongs to another school");
        }

        // 2. Try by UUID
        try {
            UUID uuid = UUID.fromString(cleaned);
            Optional<SchoolNotification> byIdThisInst = notificationRepository.findByIdAndInstitutionId(uuid, institutionId);
            if (byIdThisInst.isPresent()) {
                return byIdThisInst.get();
            }
            Optional<SchoolNotification> anyById = notificationRepository.findById(uuid);
            if (anyById.isPresent()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "cross_school_access: Notification belongs to another school");
            }
        } catch (IllegalArgumentException ignored) {}

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Notification not found: " + cleaned);
    }

    private SchoolNotificationDto toDto(SchoolNotification n) {
        LocalDate d = n.getCreatedAt() != null ? n.getCreatedAt().toLocalDate() : LocalDate.now();
        String t = n.getCreatedAt() != null
                ? String.format("%02d:%02d", n.getCreatedAt().getHour(), n.getCreatedAt().getMinute())
                : "08:00";

        return new SchoolNotificationDto(
                n.getNotificationRef() != null ? n.getNotificationRef() : n.getId().toString(),
                n.getType(),
                n.getTitle(),
                n.getDescription(),
                n.getStudentName(),
                n.getFeeType(),
                n.getFeeAmountEGP(),
                n.getDueDate(),
                n.getDaysUntilDue(),
                n.getNotificationStatus(),
                d,
                t,
                n.isReadFlag(),
                n.getRelatedId()
        );
    }

    private Institution requireInstitution(UUID institutionId) {
        if (institutionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "institution_id_required: Institution ID is required");
        }
        return institutionRepository.findById(institutionId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "institution_not_found: Institution not found: " + institutionId));
    }

    private String generateNotificationRef(UUID institutionId) {
        return String.format("NTF-%04d", REF_COUNTER.incrementAndGet());
    }
}
