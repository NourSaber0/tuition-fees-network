package com.tuitionnetwork.ingestion.web;

import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.ingestion.dto.FeeUploadDetailDto;
import com.tuitionnetwork.ingestion.dto.FeeUploadResponseDto;
import com.tuitionnetwork.ingestion.dto.FeeUploadRowDto;
import com.tuitionnetwork.ingestion.dto.FeeUploadSummaryDto;
import com.tuitionnetwork.ingestion.service.SchoolFeeUploadService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/fee-uploads", "/fee-uploads", "/api/v1/fee-upload", "/fee-upload"})
@PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SCHOOL_FINANCE', 'INSTITUTION_ADMIN')")
public class SchoolFeeUploadController {

    private final SchoolFeeUploadService schoolFeeUploadService;

    @Autowired
    public SchoolFeeUploadController(SchoolFeeUploadService schoolFeeUploadService) {
        this.schoolFeeUploadService = schoolFeeUploadService;
    }

    /**
     * 5.1 GET /fee-uploads/template?format=csv
     */
    @GetMapping({"/template", "/download-template"})
    public ResponseEntity<byte[]> getTemplate(
            @RequestParam(value = "format", defaultValue = "csv") String format) {
        String templateCsv = schoolFeeUploadService.getCsvTemplate();
        byte[] bytes = templateCsv.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fee_upload_template.csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    /**
     * 5.2 POST /fee-uploads
     */
    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FeeUploadResponseDto> uploadFees(
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        UUID actorId = principal != null ? principal.userId() : null;

        FeeUploadResponseDto response = schoolFeeUploadService.processUpload(schoolId, file, actorId);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * 5.3 GET /fee-uploads/{uploadId}
     */
    @GetMapping("/{uploadId}")
    public ResponseEntity<FeeUploadDetailDto> getUploadDetail(
            @PathVariable("uploadId") String uploadId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        return ResponseEntity.ok(schoolFeeUploadService.getUploadDetail(schoolId, uploadId));
    }

    /**
     * 5.4 GET /fee-uploads/{uploadId}/rows?status=
     */
    @GetMapping("/{uploadId}/rows")
    public ResponseEntity<List<FeeUploadRowDto>> getUploadRows(
            @PathVariable("uploadId") String uploadId,
            @RequestParam(value = "status", required = false) String status,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        return ResponseEntity.ok(schoolFeeUploadService.getUploadRows(schoolId, uploadId, status));
    }

    /**
     * 5.5 GET /fee-uploads/{uploadId}/errors
     */
    @GetMapping("/{uploadId}/errors")
    public ResponseEntity<List<FeeUploadRowDto>> getUploadErrors(
            @PathVariable("uploadId") String uploadId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        return ResponseEntity.ok(schoolFeeUploadService.getUploadErrors(schoolId, uploadId));
    }

    /**
     * 5.6 GET /fee-uploads/{uploadId}/errors/export?format=csv
     */
    @GetMapping("/{uploadId}/errors/export")
    public ResponseEntity<byte[]> exportUploadErrors(
            @PathVariable("uploadId") String uploadId,
            @RequestParam(value = "format", defaultValue = "csv") String format,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        String csvContent = schoolFeeUploadService.exportUploadErrorsCsv(schoolId, uploadId);
        byte[] bytes = csvContent.getBytes(StandardCharsets.UTF_8);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"fee_upload_errors_" + uploadId + ".csv\"")
                .contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(bytes);
    }

    /**
     * 5.7 POST /fee-uploads/{uploadId}/resubmit
     */
    @PostMapping(value = "/{uploadId}/resubmit", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<FeeUploadDetailDto> resubmitUpload(
            @PathVariable("uploadId") String uploadId,
            @RequestParam("file") MultipartFile file,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        UUID actorId = principal != null ? principal.userId() : null;

        FeeUploadDetailDto updated = schoolFeeUploadService.resubmitUpload(schoolId, uploadId, file, actorId);
        return ResponseEntity.ok(updated);
    }

    /**
     * 5.8 GET /fee-uploads (and /fee-upload/history)
     */
    @GetMapping({"", "/history"})
    public ResponseEntity<List<FeeUploadSummaryDto>> getUploadHistory(
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        return ResponseEntity.ok(schoolFeeUploadService.getUploadHistory(schoolId));
    }

    private UUID resolveSchoolId(SecurityUserPrincipal principal) {
        if (principal == null || principal.institutionId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Missing school institution identity");
        }
        return principal.institutionId();
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        HttpStatusCode status = ex.getStatusCode();
        String code = (status instanceof HttpStatus hs) ? hs.name() : String.valueOf(status.value());
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getReason() != null ? ex.getReason() : code);
        body.put("message", ex.getReason() != null ? ex.getReason() : code);
        return ResponseEntity.status(status).body(body);
    }
}
