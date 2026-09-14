package com.tuitionnetwork.reconciliation.web;

import com.tuitionnetwork.common.dto.ApiErrorResponse;
import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.reconciliation.dto.*;
import com.tuitionnetwork.reconciliation.exception.ReconciliationValidationException;
import com.tuitionnetwork.reconciliation.service.ReconciliationService;
import com.tuitionnetwork.reconciliation.service.SchoolReconciliationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/reconciliation", "/reconciliation"})
public class ReconciliationController {

    private final ReconciliationService service;
    private final SchoolReconciliationService schoolReconciliationService;
    private final InstitutionRepository institutionRepository;

    public ReconciliationController(
            ReconciliationService service,
            @Autowired(required = false) SchoolReconciliationService schoolReconciliationService,
            @Autowired(required = false) InstitutionRepository institutionRepository) {
        this.service = service;
        this.schoolReconciliationService = schoolReconciliationService;
        this.institutionRepository = institutionRepository;
    }

    @ExceptionHandler(ReconciliationValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(ReconciliationValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ex.getCode(), ex.getMessage()));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString());
        body.put("message", ex.getReason() != null ? ex.getReason() : ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @GetMapping("/summary")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SCHOOL_FINANCE', 'INSTITUTION_ADMIN', 'BACK_OFFICE')")
    public ResponseEntity<?> summary(
            @RequestParam(value = "institutionId", required = false) UUID requestedInstitutionId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        if (isSchoolRole() || requestedInstitutionId != null || (principal != null && principal.institutionId() != null)) {
            UUID schoolId = resolveAndValidateSchoolId(requestedInstitutionId, principal);
            if (schoolReconciliationService != null && schoolId != null) {
                return ResponseEntity.ok(schoolReconciliationService.getSummary(schoolId));
            }
        }
        return ResponseEntity.ok(service.getSummary());
    }

    @GetMapping("/transactions")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SCHOOL_FINANCE', 'INSTITUTION_ADMIN', 'BACK_OFFICE')")
    public ResponseEntity<SchoolReconciliationTransactionListResponse> listTransactions(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String studentId,
            @RequestParam(required = false) String paymentId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(value = "institutionId", required = false) UUID requestedInstitutionId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        int resolvedSize = pageSize != null ? pageSize : (size != null ? size : 25);
        UUID schoolId = resolveAndValidateSchoolId(requestedInstitutionId, principal);
        return ResponseEntity.ok(schoolReconciliationService.getTransactions(
                schoolId, dateFrom, dateTo, status, studentId, paymentId, page, resolvedSize));
    }

    @GetMapping("/settlements")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SCHOOL_FINANCE', 'INSTITUTION_ADMIN', 'BACK_OFFICE')")
    public ResponseEntity<SchoolSettlementListResponse> listSettlements(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer pageSize,
            @RequestParam(value = "institutionId", required = false) UUID requestedInstitutionId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        int resolvedSize = pageSize != null ? pageSize : (size != null ? size : 25);
        UUID schoolId = resolveAndValidateSchoolId(requestedInstitutionId, principal);
        return ResponseEntity.ok(schoolReconciliationService.getSettlements(
                schoolId, dateFrom, dateTo, status, page, resolvedSize));
    }

    @GetMapping("/runs")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<PageResponse<ReconciliationRunDto>> listRuns(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(required = false) String institution,
            @RequestParam(required = false) String status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer pageSize) {

        int resolvedSize = pageSize != null ? pageSize : (size != null ? size : 25);
        return ResponseEntity.ok(service.listRuns(date, institution, status, page, resolvedSize));
    }

    @GetMapping("/runs/{id}")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<ReconciliationRunDetailDto> getRun(@PathVariable UUID id) {
        Optional<ReconciliationRunDetailDto> run = service.getRun(id);
        return run.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/runs")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<ReconciliationRunDto> triggerRun(@RequestBody(required = false) TriggerRunRequest request) {
        ReconciliationRunDto dto = service.triggerRun(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
    }

    @GetMapping("/exceptions")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<PageResponse<ReconciliationExceptionDto>> listExceptions(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String priority,
            @RequestParam(required = false) String assignedTo,
            @RequestParam(required = false, defaultValue = "false") Boolean includeResolved,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(required = false) Integer size,
            @RequestParam(required = false) Integer pageSize) {

        int resolvedSize = pageSize != null ? pageSize : (size != null ? size : 25);
        return ResponseEntity.ok(service.listExceptions(status, priority, assignedTo, includeResolved, page, resolvedSize));
    }

    @GetMapping("/exceptions/{id}")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<ReconciliationExceptionDetailDto> getException(@PathVariable UUID id) {
        Optional<ReconciliationExceptionDetailDto> dto = service.getException(id);
        return dto.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/exceptions/{id}")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<ReconciliationExceptionDto> resolveException(
            @PathVariable UUID id,
            @RequestBody ResolutionRequest request) {
        try {
            return ResponseEntity.ok(service.resolveException(id, request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/exceptions/{id}/assign")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<ReconciliationExceptionDto> assignException(
            @PathVariable UUID id,
            @RequestBody AssignRequest request) {
        try {
            return ResponseEntity.ok(service.assignException(id, request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/assignees")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<List<String>> listAssignees() {
        return ResponseEntity.ok(service.listAssignees());
    }

    @GetMapping("/export")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<byte[]> exportReport(@RequestParam(defaultValue = "csv") String format) {
        byte[] csv = service.exportReport(format);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reconciliation.csv");
        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }

    private UUID resolveAndValidateSchoolId(UUID requestedInstitutionId, SecurityUserPrincipal principal) {
        UUID principalSchoolId = (principal != null) ? principal.institutionId() : null;

        if (principalSchoolId != null) {
            if (requestedInstitutionId != null && !requestedInstitutionId.equals(principalSchoolId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "cross_school_access: Access denied to other school's reconciliation data");
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
}
