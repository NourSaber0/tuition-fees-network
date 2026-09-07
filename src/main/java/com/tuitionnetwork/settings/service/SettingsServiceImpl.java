package com.tuitionnetwork.settings.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.settings.domain.FeeTypeSetting;
import com.tuitionnetwork.settings.domain.SystemSetting;
import com.tuitionnetwork.settings.dto.CreateFeeTypeRequest;
import com.tuitionnetwork.settings.dto.EppSettingsDto;
import com.tuitionnetwork.settings.dto.FeeTypeSettingDto;
import com.tuitionnetwork.settings.dto.InstitutionSettingsDto;
import com.tuitionnetwork.settings.dto.NotificationSettingsDto;
import com.tuitionnetwork.settings.dto.PaymentStatusInfo;
import com.tuitionnetwork.settings.dto.UpdateFeeTypeRequest;
import com.tuitionnetwork.settings.repository.FeeTypeSettingRepository;
import com.tuitionnetwork.settings.repository.SystemSettingRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Supplier;

@Service
public class SettingsServiceImpl implements SettingsService {

    private static final String KEY_EPP = "epp";
    private static final String KEY_NOTIFICATIONS = "notifications";
    private static final String KEY_INSTITUTIONS = "institutions";
    private static final String ACTOR_TYPE = "BACK_OFFICE";

    private static final List<FeeTypeSetting> DEFAULT_FEE_TYPES = List.of(
            new FeeTypeSetting("Tuition Fee", "TUITION", false, true),
            new FeeTypeSetting("Activity Fee", "ACTIVITY", false, true),
            new FeeTypeSetting("Bus Transportation", "BUS", false, true),
            new FeeTypeSetting("Uniform Fee", "UNIFORM", false, true),
            new FeeTypeSetting("Registration Fee", "REGFEE", false, true),
            new FeeTypeSetting("Book Fee", "BOOKS", false, true),
            new FeeTypeSetting("Laboratory Fee", "LAB", false, false));

    private final SystemSettingRepository systemSettingRepository;
    private final FeeTypeSettingRepository feeTypeSettingRepository;
    private final AuditLogRepository auditLogRepository;
    private final ObjectMapper objectMapper;

    public SettingsServiceImpl(SystemSettingRepository systemSettingRepository,
                               FeeTypeSettingRepository feeTypeSettingRepository,
                               @Autowired(required = false) AuditLogRepository auditLogRepository,
                               ObjectMapper objectMapper) {
        this.systemSettingRepository = systemSettingRepository;
        this.feeTypeSettingRepository = feeTypeSettingRepository;
        this.auditLogRepository = auditLogRepository;
        this.objectMapper = objectMapper;
    }

    // ── Fee types ─────────────────────────────────────────────────────────────

    @Override
    @Transactional
    public List<FeeTypeSettingDto> getFeeTypes() {
        if (feeTypeSettingRepository.count() == 0) {
            feeTypeSettingRepository.saveAll(DEFAULT_FEE_TYPES.stream()
                    .map(f -> new FeeTypeSetting(f.getName(), f.getCode(), f.isTaxable(), f.isActive()))
                    .toList());
        }
        return feeTypeSettingRepository.findAllByOrderByNameAsc().stream()
                .map(FeeTypeSettingDto::from)
                .toList();
    }

    @Override
    @Transactional
    public FeeTypeSettingDto createFeeType(CreateFeeTypeRequest request) {
        String code = request.code().trim().toUpperCase();
        feeTypeSettingRepository.findByCodeIgnoreCase(code).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "A fee type with code '" + code + "' already exists.");
        });
        FeeTypeSetting saved = feeTypeSettingRepository.save(new FeeTypeSetting(
                request.name().trim(), code, request.taxableOrDefault(), request.activeOrDefault()));
        audit("CREATE_FEE_TYPE", "FeeType " + code + " (" + saved.getName() + ")");
        return FeeTypeSettingDto.from(saved);
    }

    @Override
    @Transactional
    public FeeTypeSettingDto updateFeeType(UUID id, UpdateFeeTypeRequest request) {
        FeeTypeSetting feeType = feeTypeSettingRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Fee type not found: " + id));

        if (request.name() != null && !request.name().isBlank()) {
            feeType.setName(request.name().trim());
        }
        if (request.code() != null && !request.code().isBlank()) {
            String newCode = request.code().trim().toUpperCase();
            feeTypeSettingRepository.findByCodeIgnoreCase(newCode).ifPresent(other -> {
                if (!other.getId().equals(id)) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT,
                            "A fee type with code '" + newCode + "' already exists.");
                }
            });
            feeType.setCode(newCode);
        }
        if (request.taxable() != null) {
            feeType.setTaxable(request.taxable());
        }
        if (request.active() != null) {
            feeType.setActive(request.active());
        }

        FeeTypeSetting saved = feeTypeSettingRepository.save(feeType);
        audit("UPDATE_FEE_TYPE", "FeeType " + saved.getCode() + " (active=" + saved.isActive() + ")");
        return FeeTypeSettingDto.from(saved);
    }

    // ── Payment statuses (read-only reference) ────────────────────────────────

    @Override
    public List<PaymentStatusInfo> getPaymentStatuses() {
        return PaymentStatusInfo.ALL;
    }

    // ── EPP configuration ────────────────────────────────────────────────────

    @Override
    @Transactional
    public EppSettingsDto getEpp() {
        return readSetting(KEY_EPP, EppSettingsDto.class, EppSettingsDto::defaults);
    }

    @Override
    @Transactional
    public EppSettingsDto updateEpp(EppSettingsDto settings) {
        validateEpp(settings);
        writeSetting(KEY_EPP, settings);
        audit("UPDATE_EPP_SETTINGS", "EPP configuration updated");
        return settings;
    }

    // ── Notification settings ────────────────────────────────────────────────

    @Override
    @Transactional
    public NotificationSettingsDto getNotifications() {
        return readSetting(KEY_NOTIFICATIONS, NotificationSettingsDto.class, NotificationSettingsDto::defaults);
    }

    @Override
    @Transactional
    public NotificationSettingsDto updateNotifications(NotificationSettingsDto settings) {
        if (settings.events() == null || settings.channels() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Both 'events' and 'channels' are required.");
        }
        writeSetting(KEY_NOTIFICATIONS, settings);
        audit("UPDATE_NOTIFICATION_SETTINGS", "Notification configuration updated");
        return settings;
    }

    // ── Institution / onboarding settings ────────────────────────────────────

    @Override
    @Transactional
    public InstitutionSettingsDto getInstitutionSettings() {
        return readSetting(KEY_INSTITUTIONS, InstitutionSettingsDto.class, InstitutionSettingsDto::defaults);
    }

    @Override
    @Transactional
    public InstitutionSettingsDto updateInstitutionSettings(InstitutionSettingsDto settings) {
        if (settings.allowedUploadFormats() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "'allowedUploadFormats' is required.");
        }
        if (settings.maxStudentsPerUpload() < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxStudentsPerUpload must be at least 1.");
        }
        if (settings.postApprovalActivationDelayHours() < 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "postApprovalActivationDelayHours must not be negative.");
        }
        InstitutionSettingsDto.UploadFormats f = settings.allowedUploadFormats();
        if (!f.xlsx() && !f.csv() && !f.xml()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "At least one upload format must be allowed.");
        }
        writeSetting(KEY_INSTITUTIONS, settings);
        audit("UPDATE_INSTITUTION_SETTINGS", "Institution onboarding configuration updated");
        return settings;
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private void validateEpp(EppSettingsDto s) {
        if (s.minAmountEGP() < 0) {
            throw badRequest("minAmountEGP must not be negative.");
        }
        if (s.maxAmountEGP() <= s.minAmountEGP()) {
            throw badRequest("maxAmountEGP must be greater than minAmountEGP.");
        }
        if (s.tenors() == null || s.tenors().values().stream().noneMatch(Boolean.TRUE::equals)) {
            throw badRequest("At least one tenor must be enabled.");
        }
        if (s.interestRatePct() != null) {
            for (Map.Entry<Integer, Integer> e : s.interestRatePct().entrySet()) {
                if (e.getValue() == null || e.getValue() < 0 || e.getValue() > 100) {
                    throw badRequest("interestRatePct for tenor " + e.getKey() + " must be between 0 and 100.");
                }
            }
        }
        if (s.adminFeeRatePct() < 0 || s.adminFeeRatePct() > 100) {
            throw badRequest("adminFeeRatePct must be between 0 and 100.");
        }
        if (s.adminFeeCapEGP() < 0) {
            throw badRequest("adminFeeCapEGP must not be negative.");
        }
        if (s.maxPlansPerStudent() < 1) {
            throw badRequest("maxPlansPerStudent must be at least 1.");
        }
    }

    private <T> T readSetting(String key, Class<T> type, Supplier<T> defaultSupplier) {
        return systemSettingRepository.findBySettingKey(key)
                .map(row -> {
                    try {
                        return objectMapper.readValue(row.getSettingValue(), type);
                    } catch (Exception e) {
                        return null;
                    }
                })
                .orElseGet(() -> {
                    T value = defaultSupplier.get();
                    writeSetting(key, value);
                    return value;
                });
    }

    private void writeSetting(String key, Object value) {
        String json;
        try {
            json = objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR,
                    "Failed to serialise settings for key '" + key + "'.");
        }
        SystemSetting row = systemSettingRepository.findBySettingKey(key)
                .orElseGet(() -> new SystemSetting(key, json));
        row.setSettingValue(json);
        systemSettingRepository.save(row);
    }

    private void audit(String action, String target) {
        if (auditLogRepository != null) {
            auditLogRepository.save(new AuditLog(null, ACTOR_TYPE, action, target));
        }
    }

    private static ResponseStatusException badRequest(String message) {
        return new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
    }
}
