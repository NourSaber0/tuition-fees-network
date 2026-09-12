package com.tuitionnetwork.settings.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.common.dto.ApiErrorResponse;
import com.tuitionnetwork.identity.dto.auth.MessageResponse;
import com.tuitionnetwork.identity.security.AuthException;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.settings.dto.ChangePasswordRequest;
import com.tuitionnetwork.settings.dto.CreateFeeTypeRequest;
import com.tuitionnetwork.settings.dto.EppSettingsDto;
import com.tuitionnetwork.settings.dto.FeeTypeSettingDto;
import com.tuitionnetwork.settings.dto.InstitutionSettingsDto;
import com.tuitionnetwork.settings.dto.NotificationSettingsDto;
import com.tuitionnetwork.settings.dto.PaymentStatusInfo;
import com.tuitionnetwork.settings.dto.SchoolNotificationSettingsRequest;
import com.tuitionnetwork.settings.dto.SchoolProfileDto;
import com.tuitionnetwork.settings.dto.UpdateFeeTypeRequest;
import com.tuitionnetwork.settings.service.SchoolSettingsService;
import com.tuitionnetwork.settings.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * System settings (Phase 11). Accessible to back-office and school users for their respective scopes.
 */
@RestController
@RequestMapping({"/api/v1/settings", "/settings"})
@PreAuthorize("hasAnyRole('BACK_OFFICE', 'SCHOOL_ADMIN', 'SCHOOL_FINANCE')")
public class SettingsController {

    private final SettingsService settingsService;
    private final SchoolSettingsService schoolSettingsService;
    private final ObjectMapper objectMapper;

    @Autowired
    public SettingsController(SettingsService settingsService,
                              SchoolSettingsService schoolSettingsService,
                              @Autowired(required = false) ObjectMapper objectMapper) {
        this.settingsService = settingsService;
        this.schoolSettingsService = schoolSettingsService;
        this.objectMapper = objectMapper != null ? objectMapper : new ObjectMapper();
    }

    // ── Fee types ────────────────────────────────────────────────────────────

    @GetMapping("/fee-types")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<List<FeeTypeSettingDto>> getFeeTypes() {
        return ResponseEntity.ok(settingsService.getFeeTypes());
    }

    @PostMapping("/fee-types")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<FeeTypeSettingDto> createFeeType(@Valid @RequestBody CreateFeeTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(settingsService.createFeeType(request));
    }

    @PatchMapping("/fee-types/{id}")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<FeeTypeSettingDto> updateFeeType(@PathVariable("id") UUID id,
                                                           @RequestBody UpdateFeeTypeRequest request) {
        return ResponseEntity.ok(settingsService.updateFeeType(id, request));
    }

    // ── Payment statuses ─────────────────────────────────────────────────────

    @GetMapping("/payment-statuses")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<List<PaymentStatusInfo>> getPaymentStatuses() {
        return ResponseEntity.ok(settingsService.getPaymentStatuses());
    }

    // ── EPP configuration ────────────────────────────────────────────────────

    @GetMapping("/epp")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<EppSettingsDto> getEpp() {
        return ResponseEntity.ok(settingsService.getEpp());
    }

    @PutMapping("/epp")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<EppSettingsDto> updateEpp(@RequestBody EppSettingsDto settings) {
        return ResponseEntity.ok(settingsService.updateEpp(settings));
    }

    // ── Notification settings ────────────────────────────────────────────────

    @GetMapping("/notifications")
    public ResponseEntity<?> getNotifications(@AuthenticationPrincipal SecurityUserPrincipal principal) {
        if (principal != null && principal.isSchoolUser()) {
            return ResponseEntity.ok(schoolSettingsService.getNotificationSettings(principal.institutionId()));
        }
        return ResponseEntity.ok(settingsService.getNotifications());
    }

    @PutMapping("/notifications")
    public ResponseEntity<?> updateNotifications(@RequestBody Map<String, Object> body,
                                                 @AuthenticationPrincipal SecurityUserPrincipal principal) {
        if (principal != null && principal.isSchoolUser()) {
            if (!principal.isSchoolAdmin()) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Only school-admin can update notification settings");
            }
            SchoolNotificationSettingsRequest req = objectMapper.convertValue(body, SchoolNotificationSettingsRequest.class);
            return ResponseEntity.ok(schoolSettingsService.updateNotificationSettings(principal.institutionId(), req, principal.userId()));
        }
        NotificationSettingsDto settings = objectMapper.convertValue(body, NotificationSettingsDto.class);
        return ResponseEntity.ok(settingsService.updateNotifications(settings));
    }

    // ── School Profile (Phase 11.1) ──────────────────────────────────────────

    @GetMapping("/profile")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SCHOOL_FINANCE')")
    public ResponseEntity<SchoolProfileDto> getProfile(@AuthenticationPrincipal SecurityUserPrincipal principal) {
        if (principal == null || principal.institutionId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "School context required");
        }
        return ResponseEntity.ok(schoolSettingsService.getSchoolProfile(principal.institutionId()));
    }

    // ── Change Password (Phase 11.3) ─────────────────────────────────────────

    @PostMapping("/change-password")
    public ResponseEntity<MessageResponse> changePassword(@RequestBody ChangePasswordRequest request,
                                                           @AuthenticationPrincipal SecurityUserPrincipal principal) {
        if (principal == null) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Unauthenticated");
        }
        return ResponseEntity.ok(schoolSettingsService.changePassword(principal.userId(), request));
    }

    // ── Institution / onboarding settings ────────────────────────────────────

    @GetMapping("/institutions")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<InstitutionSettingsDto> getInstitutionSettings() {
        return ResponseEntity.ok(settingsService.getInstitutionSettings());
    }

    @PutMapping("/institutions")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<InstitutionSettingsDto> updateInstitutionSettings(
            @RequestBody InstitutionSettingsDto settings) {
        return ResponseEntity.ok(settingsService.updateInstitutionSettings(settings));
    }

    // ── Error mapping ────────────────────────────────────────────────────────

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthException(AuthException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getDetails()));
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

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "MALFORMED_REQUEST");
        body.put("message", "Request body could not be parsed.");
        return ResponseEntity.badRequest().body(body);
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fields = new LinkedHashMap<>();
        ex.getBindingResult().getFieldErrors().forEach(fe ->
                fields.putIfAbsent(fe.getField(),
                        fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "invalid"));
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "VALIDATION_FAILED");
        body.put("message", "One or more fields are invalid.");
        body.put("fields", fields);
        return ResponseEntity.badRequest().body(body);
    }
}
