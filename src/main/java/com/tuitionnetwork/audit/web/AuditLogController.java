package com.tuitionnetwork.audit.web;

import com.tuitionnetwork.audit.dto.AuditLogDto;
import com.tuitionnetwork.audit.dto.AuditLogStatsDto;
import com.tuitionnetwork.audit.service.AuditLogService;
import com.tuitionnetwork.identity.security.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/audit-logs")
public class AuditLogController {

    private final AuditLogService service;

    @Autowired
    public AuditLogController(AuditLogService service) {
        this.service = service;
    }

    @GetMapping
    @PreAuthorize("hasAuthority(T(com.tuitionnetwork.identity.security.UserRole).ROLE_BACK_OFFICE)")
    public Page<AuditLogDto> search(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String actorType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20) Pageable pageable
    ) {
        return service.search(actorId, actorType, action, from, to, pageable);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAuthority(T(com.tuitionnetwork.identity.security.UserRole).ROLE_BACK_OFFICE)")
    public ResponseEntity<AuditLogDto> getById(@PathVariable UUID id) {
        AuditLogDto dto = service.getById(id);
        if (dto == null) return ResponseEntity.notFound().build();
        return ResponseEntity.ok(dto);
    }

    @GetMapping("/stats")
    @PreAuthorize("hasAuthority(T(com.tuitionnetwork.identity.security.UserRole).ROLE_BACK_OFFICE)")
    public AuditLogStatsDto stats(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        return service.stats(from, to);
    }

    @GetMapping("/roles")
    @PreAuthorize("hasAuthority(T(com.tuitionnetwork.identity.security.UserRole).ROLE_BACK_OFFICE)")
    public List<String> roles() {
        return service.roles();
    }

    @GetMapping("/export")
    @PreAuthorize("hasAuthority(T(com.tuitionnetwork.identity.security.UserRole).ROLE_BACK_OFFICE)")
    public ResponseEntity<byte[]> export(
            @RequestParam(required = false) UUID actorId,
            @RequestParam(required = false) String actorType,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to
    ) {
        byte[] csv = service.export(actorId, actorType, action, from, to);
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=utf-8"));
        headers.setContentDispositionFormData("attachment", "audit-logs.csv");
        headers.setContentLength(csv.length);
        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }
}
