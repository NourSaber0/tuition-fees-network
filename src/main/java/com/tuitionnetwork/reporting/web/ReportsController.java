package com.tuitionnetwork.reporting.web;

import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.reporting.dto.GenerateReportRequest;
import com.tuitionnetwork.reporting.dto.ReportCatalogueEntry;
import com.tuitionnetwork.reporting.dto.ReportHistoryEntry;
import com.tuitionnetwork.reporting.dto.ReportJobResponse;
import com.tuitionnetwork.reporting.service.ReportsService;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
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
 * Reporting module (Phase 7). {@code POST /generate} is synchronous: it returns a
 * {@code READY} job with an inline preview; the client can still poll
 * {@code GET /jobs/{id}} and pull the file from {@code /jobs/{id}/download}.
 */
@RestController
@RequestMapping("/api/v1/reports")
@PreAuthorize("hasRole('BACK_OFFICE')")
public class ReportsController {

    private final ReportsService reportsService;

    public ReportsController(ReportsService reportsService) {
        this.reportsService = reportsService;
    }

    @GetMapping("/catalogue")
    public ResponseEntity<List<ReportCatalogueEntry>> catalogue() {
        return ResponseEntity.ok(reportsService.catalogue());
    }

    @PostMapping("/generate")
    public ResponseEntity<ReportJobResponse> generate(@Valid @RequestBody GenerateReportRequest request) {
        return ResponseEntity.ok(reportsService.generate(request));
    }

    @GetMapping("/jobs/{jobId}")
    public ResponseEntity<ReportJobResponse> getJob(@PathVariable("jobId") UUID jobId) {
        return ResponseEntity.ok(reportsService.getJob(jobId));
    }

    @GetMapping("/jobs/{jobId}/download")
    public ResponseEntity<byte[]> download(@PathVariable("jobId") UUID jobId) {
        ReportsService.DownloadPayload payload = reportsService.download(jobId);
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
            @RequestParam(value = "pageSize", required = false) Integer pageSize) {
        int resolvedSize = pageSize != null ? pageSize : (size != null ? size : 25);
        return ResponseEntity.ok(reportsService.history(reportId, page, resolvedSize));
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
