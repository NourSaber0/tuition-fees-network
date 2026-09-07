package com.tuitionnetwork.reconciliation.web;

import com.tuitionnetwork.common.dto.ApiErrorResponse;
import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.reconciliation.dto.*;
import com.tuitionnetwork.reconciliation.exception.ReconciliationValidationException;
import com.tuitionnetwork.reconciliation.service.ReconciliationService;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliation")
@PreAuthorize("hasRole('BACK_OFFICE')")
public class ReconciliationController {

    private final ReconciliationService service;

    public ReconciliationController(ReconciliationService service) {
        this.service = service;
    }

    @ExceptionHandler(ReconciliationValidationException.class)
    public ResponseEntity<ApiErrorResponse> handleValidationException(ReconciliationValidationException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(ApiErrorResponse.of(ex.getCode(), ex.getMessage()));
    }

    @GetMapping("/summary")
    public ResponseEntity<ReconciliationSummaryDto> summary() {
        return ResponseEntity.ok(service.getSummary());
    }

    @GetMapping("/runs")
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
    public ResponseEntity<ReconciliationRunDetailDto> getRun(@PathVariable UUID id) {
        Optional<ReconciliationRunDetailDto> run = service.getRun(id);
        return run.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/runs")
    public ResponseEntity<ReconciliationRunDto> triggerRun(@RequestBody(required = false) TriggerRunRequest request) {
        ReconciliationRunDto dto = service.triggerRun(request);
        return ResponseEntity.status(HttpStatus.ACCEPTED).body(dto);
    }

    @GetMapping("/exceptions")
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
    public ResponseEntity<ReconciliationExceptionDetailDto> getException(@PathVariable UUID id) {
        Optional<ReconciliationExceptionDetailDto> dto = service.getException(id);
        return dto.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/exceptions/{id}")
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
    public ResponseEntity<List<String>> listAssignees() {
        return ResponseEntity.ok(service.listAssignees());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportReport(@RequestParam(defaultValue = "csv") String format) {
        byte[] csv = service.exportReport(format);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reconciliation.csv");
        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }
}
