package com.tuitionnetwork.reconciliation.web;

import com.tuitionnetwork.reconciliation.dto.*;
import com.tuitionnetwork.reconciliation.service.ReconciliationService;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/reconciliation")
public class ReconciliationController {
    private final ReconciliationService service;

    public ReconciliationController(ReconciliationService service) { this.service = service; }

    @GetMapping("/summary")
    public ReconciliationSummaryDto summary() { return service.getSummary(); }

    @GetMapping("/runs")
    public Page<ReconciliationRunDto> listRuns(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size) {
        return service.listRuns(PageRequest.of(page, size));
    }

    @GetMapping("/runs/{id}")
    public ResponseEntity<ReconciliationRunDto> getRun(@PathVariable UUID id) {
        Optional<ReconciliationRunDto> run = service.getRun(id);
        return run.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PostMapping("/runs")
    public ReconciliationRunDto triggerRun() { return service.triggerRun(); }

    @GetMapping("/exceptions")
    public Page<ReconciliationExceptionDto> listExceptions(@RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "25") int size, @RequestParam(required = false) String status, @RequestParam(required = false) String priority) {
        return service.listExceptions(PageRequest.of(page, size), status, priority);
    }

    @GetMapping("/exceptions/{id}")
    public ResponseEntity<ReconciliationExceptionDto> getException(@PathVariable UUID id) {
        Optional<ReconciliationExceptionDto> dto = service.getException(id);
        return dto.map(ResponseEntity::ok).orElseGet(() -> ResponseEntity.notFound().build());
    }

    @PatchMapping("/exceptions/{id}")
    public ResponseEntity<ReconciliationExceptionDto> resolveException(@PathVariable UUID id, @RequestBody ResolutionRequest request) {
        try {
            return ResponseEntity.ok(service.resolveException(id, request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @PostMapping("/exceptions/{id}/assign")
    public ResponseEntity<ReconciliationExceptionDto> assignException(@PathVariable UUID id, @RequestBody AssignRequest request) {
        try {
            return ResponseEntity.ok(service.assignException(id, request));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.status(HttpStatus.NOT_FOUND).build();
        }
    }

    @GetMapping("/assignees")
    public List<String> listAssignees() { return service.listAssignees(); }

    @GetMapping("/export")
    public ResponseEntity<byte[]> exportReport() {
        byte[] csv = service.exportReport();
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.TEXT_PLAIN);
        headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=reconciliation.csv");
        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }
}
