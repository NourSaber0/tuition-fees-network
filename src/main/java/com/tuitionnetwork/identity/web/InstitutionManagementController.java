package com.tuitionnetwork.identity.web;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.identity.domain.AccountStatus;
import com.tuitionnetwork.identity.domain.InstitutionType;
import com.tuitionnetwork.identity.domain.RegistrationStatus;
import com.tuitionnetwork.identity.dto.InstitutionApplicationDto;
import com.tuitionnetwork.identity.dto.InstitutionDetailDto;
import com.tuitionnetwork.identity.dto.InstitutionIntegrationDto;
import com.tuitionnetwork.identity.dto.InstitutionStudentDto;
import com.tuitionnetwork.identity.dto.InstitutionSummaryDto;
import com.tuitionnetwork.identity.dto.RegisterInstitutionRequest;
import com.tuitionnetwork.identity.dto.RejectInstitutionRequest;
import com.tuitionnetwork.identity.service.InstitutionManagementService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Back-office institution management (US-05 .. US-11).
 * All endpoints require {@code ROLE_BACK_OFFICE}; every mutation is audited.
 */
@RestController
@RequestMapping("/api/v1/institutions")
@PreAuthorize("hasRole('BACK_OFFICE')")
public class InstitutionManagementController {

    private static final String ACTOR_TYPE = "BACK_OFFICE";

    private final InstitutionManagementService institutionManagementService;
    private final AuditLogRepository auditLogRepository;

    @Autowired
    public InstitutionManagementController(InstitutionManagementService institutionManagementService,
                                           @Autowired(required = false) AuditLogRepository auditLogRepository) {
        this.institutionManagementService = institutionManagementService;
        this.auditLogRepository = auditLogRepository;
    }

    @GetMapping
    public ResponseEntity<PageResponse<InstitutionSummaryDto>> list(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "type", required = false) InstitutionType type,
            @RequestParam(value = "regStatus", required = false) RegistrationStatus regStatus,
            @RequestParam(value = "accountStatus", required = false) AccountStatus accountStatus,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {

        int resolvedSize = pageSize != null ? pageSize : (size != null ? size : 25);
        return ResponseEntity.ok(
                institutionManagementService.list(search, type, regStatus, accountStatus, page, resolvedSize));
    }

    @PostMapping
    public ResponseEntity<InstitutionDetailDto> register(@Valid @RequestBody RegisterInstitutionRequest request) {
        InstitutionDetailDto created = institutionManagementService.register(request);
        audit("REGISTER_INSTITUTION", created);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/{id}")
    public ResponseEntity<InstitutionDetailDto> get(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(institutionManagementService.get(id));
    }

    @GetMapping("/{id}/students")
    public ResponseEntity<List<InstitutionStudentDto>> students(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(institutionManagementService.students(id));
    }

    @GetMapping("/{id}/application")
    public ResponseEntity<InstitutionApplicationDto> application(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(institutionManagementService.application(id));
    }

    @GetMapping("/{id}/integration")
    public ResponseEntity<InstitutionIntegrationDto> integration(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(institutionManagementService.integration(id));
    }

    @PostMapping("/{id}/approve")
    public ResponseEntity<InstitutionDetailDto> approve(@PathVariable("id") UUID id) {
        InstitutionDetailDto result = institutionManagementService.approve(id);
        audit("APPROVE_INSTITUTION", result);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/reject")
    public ResponseEntity<InstitutionDetailDto> reject(@PathVariable("id") UUID id,
                                                       @Valid @RequestBody RejectInstitutionRequest request) {
        InstitutionDetailDto result = institutionManagementService.reject(id, request.reason());
        audit("REJECT_INSTITUTION", result);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<InstitutionDetailDto> activate(@PathVariable("id") UUID id) {
        InstitutionDetailDto result = institutionManagementService.activate(id);
        audit("ACTIVATE_INSTITUTION", result);
        return ResponseEntity.ok(result);
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<InstitutionDetailDto> deactivate(@PathVariable("id") UUID id) {
        InstitutionDetailDto result = institutionManagementService.deactivate(id);
        audit("DEACTIVATE_INSTITUTION", result);
        return ResponseEntity.ok(result);
    }

    private void audit(String action, InstitutionDetailDto institution) {
        if (auditLogRepository == null) {
            return;
        }
        auditLogRepository.save(new AuditLog(
                null,
                ACTOR_TYPE,
                action,
                "Institution " + institution.id() + " (" + institution.name() + ") -> reg="
                        + institution.registrationStatus() + ", account=" + institution.accountStatus()));
    }

    // ── Error mapping (kept flat: { "error": <code>, "message": <text> }) ──────

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
