package com.tuitionnetwork.settings.web;

import com.tuitionnetwork.settings.dto.CreateFeeTypeRequest;
import com.tuitionnetwork.settings.dto.EppSettingsDto;
import com.tuitionnetwork.settings.dto.FeeTypeSettingDto;
import com.tuitionnetwork.settings.dto.InstitutionSettingsDto;
import com.tuitionnetwork.settings.dto.NotificationSettingsDto;
import com.tuitionnetwork.settings.dto.PaymentStatusInfo;
import com.tuitionnetwork.settings.dto.UpdateFeeTypeRequest;
import com.tuitionnetwork.settings.service.SettingsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.security.access.prepost.PreAuthorize;
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
 * System settings (Phase 11). Read-open to back-office; mutations are audited.
 * NOTE: the API contract restricts writes to bank-admin specifically — the
 * codebase only has a coarse ROLE_BACK_OFFICE today, so that is what gates here
 * (same as dashboard / reports / institutions). Finer gating is a follow-up.
 */
@RestController
@RequestMapping({"/api/v1/settings", "/settings"})
@PreAuthorize("hasRole('BACK_OFFICE')")
public class SettingsController {

    private final SettingsService settingsService;

    public SettingsController(SettingsService settingsService) {
        this.settingsService = settingsService;
    }

    // ── Fee types ────────────────────────────────────────────────────────────

    @GetMapping("/fee-types")
    public ResponseEntity<List<FeeTypeSettingDto>> getFeeTypes() {
        return ResponseEntity.ok(settingsService.getFeeTypes());
    }

    @PostMapping("/fee-types")
    public ResponseEntity<FeeTypeSettingDto> createFeeType(@Valid @RequestBody CreateFeeTypeRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(settingsService.createFeeType(request));
    }

    @PatchMapping("/fee-types/{id}")
    public ResponseEntity<FeeTypeSettingDto> updateFeeType(@PathVariable("id") UUID id,
                                                           @RequestBody UpdateFeeTypeRequest request) {
        return ResponseEntity.ok(settingsService.updateFeeType(id, request));
    }

    // ── Payment statuses ─────────────────────────────────────────────────────

    @GetMapping("/payment-statuses")
    public ResponseEntity<List<PaymentStatusInfo>> getPaymentStatuses() {
        return ResponseEntity.ok(settingsService.getPaymentStatuses());
    }

    // ── EPP configuration ────────────────────────────────────────────────────

    @GetMapping("/epp")
    public ResponseEntity<EppSettingsDto> getEpp() {
        return ResponseEntity.ok(settingsService.getEpp());
    }

    @PutMapping("/epp")
    public ResponseEntity<EppSettingsDto> updateEpp(@RequestBody EppSettingsDto settings) {
        return ResponseEntity.ok(settingsService.updateEpp(settings));
    }

    // ── Notification settings ────────────────────────────────────────────────

    @GetMapping("/notifications")
    public ResponseEntity<NotificationSettingsDto> getNotifications() {
        return ResponseEntity.ok(settingsService.getNotifications());
    }

    @PutMapping("/notifications")
    public ResponseEntity<NotificationSettingsDto> updateNotifications(@RequestBody NotificationSettingsDto settings) {
        return ResponseEntity.ok(settingsService.updateNotifications(settings));
    }

    // ── Institution / onboarding settings ────────────────────────────────────

    @GetMapping("/institutions")
    public ResponseEntity<InstitutionSettingsDto> getInstitutionSettings() {
        return ResponseEntity.ok(settingsService.getInstitutionSettings());
    }

    @PutMapping("/institutions")
    public ResponseEntity<InstitutionSettingsDto> updateInstitutionSettings(
            @RequestBody InstitutionSettingsDto settings) {
        return ResponseEntity.ok(settingsService.updateInstitutionSettings(settings));
    }

    // ── Error mapping ────────────────────────────────────────────────────────

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
