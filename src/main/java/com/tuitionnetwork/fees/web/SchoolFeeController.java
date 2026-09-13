package com.tuitionnetwork.fees.web;

import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.fees.dto.*;
import com.tuitionnetwork.fees.service.SchoolFeeService;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import jakarta.validation.Valid;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/fees", "/fees"})
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SCHOOL_FINANCE', 'INSTITUTION_ADMIN')")
public class SchoolFeeController {

    private final SchoolFeeService schoolFeeService;

    @Autowired
    public SchoolFeeController(SchoolFeeService schoolFeeService) {
        this.schoolFeeService = schoolFeeService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<FeeItemSummaryDto>> getFees(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "category", required = false) String category,
            @RequestParam(value = "studentId", required = false) UUID studentId,
            @RequestParam(value = "grade", required = false) String grade,
            @RequestParam(value = "dueDateFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateFrom,
            @RequestParam(value = "dueDateTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dueDateTo,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "size", required = false) Integer size,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        int effectivePageSize = pageSize != null ? pageSize : (size != null ? size : 25);
        return ResponseEntity.ok(schoolFeeService.getFees(
                schoolId, search, category, studentId, grade, dueDateFrom, dueDateTo, status, page, effectivePageSize));
    }

    @GetMapping("/stats")
    public ResponseEntity<FeeStatsDto> getFeeStats(
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        return ResponseEntity.ok(schoolFeeService.getFeeStats(schoolId));
    }

    @GetMapping("/{id}")
    public ResponseEntity<FeeDetailDto> getFee(
            @PathVariable("id") UUID feeId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        return ResponseEntity.ok(schoolFeeService.getFeeById(schoolId, feeId));
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SCHOOL_FINANCE')")
    public ResponseEntity<FeeDetailDto> createFee(
            @Valid @RequestBody CreateFeeRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        UUID actorId = principal != null ? principal.userId() : null;
        FeeDetailDto created = schoolFeeService.createFee(schoolId, request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SCHOOL_FINANCE')")
    public ResponseEntity<FeeDetailDto> updateFee(
            @PathVariable("id") UUID feeId,
            @RequestBody UpdateFeeRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        UUID actorId = principal != null ? principal.userId() : null;
        return ResponseEntity.ok(schoolFeeService.updateFee(schoolId, feeId, request, actorId));
    }

    @PostMapping("/{id}/cancel")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<Map<String, String>> cancelFee(
            @PathVariable("id") UUID feeId,
            @RequestBody(required = false) CancelFeeRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        UUID actorId = principal != null ? principal.userId() : null;
        schoolFeeService.cancelFee(schoolId, feeId, request, actorId);
        return ResponseEntity.ok(Map.of(
                "status", "Cancelled",
                "message", "Fee line " + feeId + " has been successfully cancelled"
        ));
    }

    @PostMapping("/{id}/apply-penalty")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<FeeDetailDto> applyManualPenalty(
            @PathVariable("id") UUID feeId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        UUID actorId = principal != null ? principal.userId() : null;
        return ResponseEntity.ok(schoolFeeService.applyManualPenalty(schoolId, feeId, actorId));
    }

    @GetMapping("/{id}/penalty-info")
    public ResponseEntity<FeePenaltyInfoDto> getFeePenaltyInfo(
            @PathVariable("id") UUID feeId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        return ResponseEntity.ok(schoolFeeService.getFeePenaltyInfo(schoolId, feeId));
    }

    private UUID resolveSchoolId(SecurityUserPrincipal principal) {
        if (principal == null || principal.institutionId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing school institution identity");
        }
        return principal.institutionId();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        org.springframework.http.HttpStatusCode status = ex.getStatusCode();
        String code = (status instanceof HttpStatus hs) ? hs.name() : String.valueOf(status.value());
        Map<String, Object> body = new java.util.LinkedHashMap<>();
        body.put("error", ex.getReason() != null ? ex.getReason() : code);
        body.put("message", ex.getReason() != null ? ex.getReason() : code);
        return ResponseEntity.status(status).body(body);
    }
}
