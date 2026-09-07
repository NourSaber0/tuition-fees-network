package com.tuitionnetwork.ingestion.web;

import com.tuitionnetwork.ingestion.dto.FeeSubmissionDetailDto;
import com.tuitionnetwork.ingestion.dto.FeeSubmissionSummaryDto;
import com.tuitionnetwork.ingestion.service.IngestionQueryService;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * Back-office read of an institution's fee-upload history (US-14).
 * The write side (CSV upload) lives in {@link CsvIngestionController} and is
 * scoped to {@code ROLE_INSTITUTION_ADMIN}.
 */
@RestController
@RequestMapping("/api/v1/institutions/{id}/fee-submissions")
@PreAuthorize("hasRole('BACK_OFFICE')")
public class InstitutionFeeSubmissionsController {

    private final IngestionQueryService ingestionQueryService;

    public InstitutionFeeSubmissionsController(IngestionQueryService ingestionQueryService) {
        this.ingestionQueryService = ingestionQueryService;
    }

    @GetMapping
    public ResponseEntity<List<FeeSubmissionSummaryDto>> list(@PathVariable("id") UUID institutionId) {
        return ResponseEntity.ok(ingestionQueryService.listForInstitution(institutionId));
    }

    @GetMapping("/{submissionId}")
    public ResponseEntity<FeeSubmissionDetailDto> get(@PathVariable("id") UUID institutionId,
                                                      @PathVariable("submissionId") UUID submissionId) {
        return ResponseEntity.ok(ingestionQueryService.getForInstitution(institutionId, submissionId));
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
}
