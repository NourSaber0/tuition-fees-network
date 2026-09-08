package com.tuitionnetwork.audit.web;

import com.tuitionnetwork.audit.dto.AuditLogDto;
import com.tuitionnetwork.audit.dto.AuditLogStatsDto;
import com.tuitionnetwork.audit.service.AuditLogService;
import com.tuitionnetwork.common.dto.PageResponse;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
@PreAuthorize("hasRole('BACK_OFFICE')")
public class AuditLogController {

    private final AuditLogService service;

    @Autowired
    public AuditLogController(AuditLogService service) {
        this.service = service;
    }

    @GetMapping
    public ResponseEntity<PageResponse<AuditLogDto>> list(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "actorType", required = false) String actorType,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @RequestParam(value = "size", required = false) Integer size
    ) {
        String resolvedRole = role != null ? role : actorType;
        String resolvedFrom = dateFrom != null ? dateFrom : from;
        String resolvedTo = dateTo != null ? dateTo : to;
        int resolvedSize = pageSize != null ? pageSize : (size != null ? size : 25);

        return ResponseEntity.ok(service.search(search, resolvedRole, severity, resolvedFrom, resolvedTo, page, resolvedSize));
    }

    @GetMapping("/{id}")
    public ResponseEntity<AuditLogDto> getById(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(service.getById(id));
    }

    @GetMapping("/stats")
    public ResponseEntity<AuditLogStatsDto> stats(
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "to", required = false) String to
    ) {
        String resolvedFrom = dateFrom != null ? dateFrom : from;
        String resolvedTo = dateTo != null ? dateTo : to;
        return ResponseEntity.ok(service.stats(resolvedFrom, resolvedTo));
    }

    @GetMapping("/roles")
    public ResponseEntity<List<String>> roles() {
        return ResponseEntity.ok(service.roles());
    }

    @GetMapping("/export")
    public ResponseEntity<byte[]> export(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "actorType", required = false) String actorType,
            @RequestParam(value = "severity", required = false) String severity,
            @RequestParam(value = "dateFrom", required = false) String dateFrom,
            @RequestParam(value = "from", required = false) String from,
            @RequestParam(value = "dateTo", required = false) String dateTo,
            @RequestParam(value = "to", required = false) String to,
            @RequestParam(value = "format", defaultValue = "csv") String format
    ) {
        String resolvedRole = role != null ? role : actorType;
        String resolvedFrom = dateFrom != null ? dateFrom : from;
        String resolvedTo = dateTo != null ? dateTo : to;

        byte[] csv = service.export(search, resolvedRole, severity, resolvedFrom, resolvedTo);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=utf-8"));
        headers.setContentDispositionFormData("attachment", "audit-logs.csv");
        headers.setContentLength(csv.length);
        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
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
