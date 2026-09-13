package com.tuitionnetwork.students.web;

import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.students.dto.*;
import com.tuitionnetwork.students.service.SchoolStudentService;
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
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/students", "/students"})
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SCHOOL_FINANCE', 'INSTITUTION_ADMIN')")
public class SchoolStudentController {

    private final SchoolStudentService schoolStudentService;

    @Autowired
    public SchoolStudentController(SchoolStudentService schoolStudentService) {
        this.schoolStudentService = schoolStudentService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<StudentSummaryDto>> getStudents(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "grade", required = false) String grade,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "size", required = false) Integer size,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        int effectivePageSize = pageSize != null ? pageSize : (size != null ? size : 25);
        return ResponseEntity.ok(schoolStudentService.getStudents(schoolId, search, grade, page, effectivePageSize));
    }

    @GetMapping("/deactivated")
    public ResponseEntity<PageResponse<StudentSummaryDto>> getDeactivatedStudents(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "deactivatedFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deactivatedFrom,
            @RequestParam(value = "deactivatedTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate deactivatedTo,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "size", required = false) Integer size,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        int effectivePageSize = pageSize != null ? pageSize : (size != null ? size : 25);
        return ResponseEntity.ok(schoolStudentService.getDeactivatedStudents(
                schoolId, search, deactivatedFrom, deactivatedTo, page, effectivePageSize));
    }

    @GetMapping("/search")
    public ResponseEntity<List<StudentSearchDto>> searchActiveStudents(
            @RequestParam(value = "q", required = false) String query,
            @RequestParam(value = "query", required = false) String altQuery,
            @RequestParam(value = "activeOnly", defaultValue = "true") boolean activeOnly,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        String term = query != null ? query : altQuery;
        return ResponseEntity.ok(schoolStudentService.searchActiveStudents(schoolId, term));
    }

    @GetMapping("/{id}")
    public ResponseEntity<StudentDetailDto> getStudent(
            @PathVariable("id") UUID studentId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        return ResponseEntity.ok(schoolStudentService.getStudentById(schoolId, studentId));
    }

    @PostMapping
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<StudentDetailDto> enrollStudent(
            @Valid @RequestBody EnrollStudentRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        UUID actorId = principal != null ? principal.userId() : null;
        StudentDetailDto created = schoolStudentService.enrollStudent(schoolId, request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<StudentDetailDto> updateStudent(
            @PathVariable("id") UUID studentId,
            @RequestBody UpdateStudentRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        UUID actorId = principal != null ? principal.userId() : null;
        return ResponseEntity.ok(schoolStudentService.updateStudent(schoolId, studentId, request, actorId));
    }

    @PostMapping("/{id}/deactivate")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<StudentSummaryDto> deactivateStudent(
            @PathVariable("id") UUID studentId,
            @RequestBody(required = false) DeactivateStudentRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        UUID actorId = principal != null ? principal.userId() : null;
        String reason = request != null ? request.reason() : "Withdrawn";
        return ResponseEntity.ok(schoolStudentService.deactivateStudent(schoolId, studentId, reason, actorId));
    }

    @PostMapping("/{id}/reactivate")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<StudentSummaryDto> reactivateStudent(
            @PathVariable("id") UUID studentId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        UUID actorId = principal != null ? principal.userId() : null;
        return ResponseEntity.ok(schoolStudentService.reactivateStudent(schoolId, studentId, actorId));
    }

    @GetMapping("/{id}/fees")
    public ResponseEntity<StudentFeesResponse> getStudentFees(
            @PathVariable("id") UUID studentId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        return ResponseEntity.ok(schoolStudentService.getStudentFees(schoolId, studentId));
    }

    @GetMapping("/{id}/payments")
    public ResponseEntity<StudentPaymentsResponse> getStudentPayments(
            @PathVariable("id") UUID studentId,
            @RequestParam(value = "dateFrom", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        return ResponseEntity.ok(schoolStudentService.getStudentPayments(schoolId, studentId, dateFrom, dateTo));
    }

    @GetMapping("/{id}/guardians")
    public ResponseEntity<List<StudentGuardianDto>> getStudentGuardians(
            @PathVariable("id") UUID studentId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        return ResponseEntity.ok(schoolStudentService.getStudentGuardians(schoolId, studentId));
    }

    @PostMapping("/{id}/guardians")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<StudentGuardianDto> linkGuardian(
            @PathVariable("id") UUID studentId,
            @Valid @RequestBody LinkGuardianRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        UUID actorId = principal != null ? principal.userId() : null;
        StudentGuardianDto linked = schoolStudentService.linkGuardian(schoolId, studentId, request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(linked);
    }

    @DeleteMapping("/{id}/guardians/{guardianId}")
    @PreAuthorize("hasRole('SCHOOL_ADMIN')")
    public ResponseEntity<Void> unlinkGuardian(
            @PathVariable("id") UUID studentId,
            @PathVariable("guardianId") UUID guardianId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        UUID actorId = principal != null ? principal.userId() : null;
        schoolStudentService.unlinkGuardian(schoolId, studentId, guardianId, actorId);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/{id}/statement")
    public ResponseEntity<StudentStatementResponse> getStudentStatement(
            @PathVariable("id") UUID studentId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        return ResponseEntity.ok(schoolStudentService.getStudentStatement(schoolId, studentId));
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
