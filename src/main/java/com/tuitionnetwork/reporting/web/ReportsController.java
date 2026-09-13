package com.tuitionnetwork.reporting.web;

import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.reporting.dto.GenerateReportRequest;
import com.tuitionnetwork.reporting.dto.ReportCatalogueEntry;
import com.tuitionnetwork.reporting.dto.ReportHistoryEntry;
import com.tuitionnetwork.reporting.dto.ReportJobResponse;
import com.tuitionnetwork.reporting.service.ReportsService;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
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
 * Reporting module (Phase 7 Bank Back-Office & Phase 8 School Portal).
 */
@RestController
@RequestMapping({"/api/v1/reports", "/reports"})
@PreAuthorize("hasAnyRole('BACK_OFFICE', 'SCHOOL_ADMIN', 'SCHOOL_FINANCE', 'INSTITUTION_ADMIN')")
public class ReportsController {

    private final ReportsService reportsService;
    private final InstitutionRepository institutionRepository;

    public ReportsController(
            ReportsService reportsService,
            @Autowired(required = false) InstitutionRepository institutionRepository) {
        this.reportsService = reportsService;
        this.institutionRepository = institutionRepository;
    }

    @GetMapping({"/catalogue", "/templates"})
    public ResponseEntity<List<ReportCatalogueEntry>> catalogue(
            @RequestParam(value = "institutionId", required = false) UUID requestedInstitutionId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        if (isSchoolRole()) {
            UUID schoolId = resolveAndValidateSchoolId(requestedInstitutionId, principal);
            return ResponseEntity.ok(reportsService.catalogue(schoolId));
        }
        return ResponseEntity.ok(reportsService.catalogue());
    }

    @PostMapping("/generate")
    public ResponseEntity<ReportJobResponse> generate(
            @Valid @RequestBody GenerateReportRequest request,
            @RequestParam(value = "institutionId", required = false) UUID requestedInstitutionId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        if (isSchoolRole()) {
            UUID schoolId = resolveAndValidateSchoolId(requestedInstitutionId, principal);
            ReportJobResponse job = reportsService.generateForSchool(request, schoolId);
            return ResponseEntity.status(HttpStatus.ACCEPTED).body(job);
        }
        return ResponseEntity.ok(reportsService.generate(request));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ReportJobResponse> getJob(
            @PathVariable("jobId") String jobIdStr,
            @RequestParam(value = "institutionId", required = false) UUID requestedInstitutionId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        UUID jobId = parseJobId(jobIdStr);
        if (isSchoolRole()) {
            UUID schoolId = resolveAndValidateSchoolId(requestedInstitutionId, principal);
            return ResponseEntity.ok(reportsService.getJobForSchool(jobId, schoolId));
        }
        return ResponseEntity.ok(reportsService.getJob(jobId));
    }

    @GetMapping("/jobs/{jobId}/download")
    public ResponseEntity<byte[]> download(
            @PathVariable("jobId") String jobIdStr,
            @RequestParam(value = "institutionId", required = false) UUID requestedInstitutionId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        UUID jobId = parseJobId(jobIdStr);
        ReportsService.DownloadPayload payload;
        if (isSchoolRole()) {
            UUID schoolId = resolveAndValidateSchoolId(requestedInstitutionId, principal);
            payload = reportsService.downloadForSchool(jobId, schoolId);
        } else {
            payload = reportsService.download(jobId);
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + payload.filename() + "\"")
                .contentType(MediaType.parseMediaType(payload.contentType()))
                .body(payload.content());
    }

    @GetMapping("/history")
    public ResponseEntity<PageResponse<ReportHistoryEntry>> history(
            @RequestParam(value = "reportId", required = false) String reportId,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "institutionId", required = false) UUID requestedInstitutionId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        int resolvedSize = pageSize != null ? pageSize : (size != null ? size : 25);
        if (isSchoolRole()) {
            UUID schoolId = resolveAndValidateSchoolId(requestedInstitutionId, principal);
            return ResponseEntity.ok(reportsService.historyForSchool(schoolId, reportId, page, resolvedSize));
        }
        return ResponseEntity.ok(reportsService.history(reportId, page, resolvedSize));
    }

    private UUID parseJobId(String jobIdStr) {
        try {
            return UUID.fromString(jobIdStr);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Report job not found: " + jobIdStr);
        }
    }

    private UUID resolveAndValidateSchoolId(UUID requestedInstitutionId, SecurityUserPrincipal principal) {
        UUID principalSchoolId = (principal != null) ? principal.institutionId() : null;

        if (principalSchoolId != null) {
            if (requestedInstitutionId != null && !requestedInstitutionId.equals(principalSchoolId)) {
                throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                        "cross_school_access: Access denied to other school's report");
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
