package com.tuitionnetwork.payments.web;

import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.payments.dto.BackOfficePaymentRequest;
import com.tuitionnetwork.payments.dto.BackOfficePaymentResponse;
import com.tuitionnetwork.payments.dto.ReceiptDetailDto;
import com.tuitionnetwork.payments.dto.TransactionDetailDto;
import com.tuitionnetwork.payments.service.BackOfficePaymentService;
import com.tuitionnetwork.payments.service.SchoolPaymentService;
import com.tuitionnetwork.payments.service.TransactionQueryService;
import jakarta.validation.Valid;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping({"/api/v1/payments", "/payments"})
public class BackOfficePaymentController {

    private final BackOfficePaymentService backOfficePaymentService;
    private final TransactionQueryService transactionQueryService;
    private final SchoolPaymentService schoolPaymentService;
    private final InstitutionRepository institutionRepository;

    public BackOfficePaymentController(
            BackOfficePaymentService backOfficePaymentService,
            TransactionQueryService transactionQueryService,
            SchoolPaymentService schoolPaymentService,
            InstitutionRepository institutionRepository) {
        this.backOfficePaymentService = backOfficePaymentService;
        this.transactionQueryService = transactionQueryService;
        this.schoolPaymentService = schoolPaymentService;
        this.institutionRepository = institutionRepository;
    }

    @PostMapping
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<BackOfficePaymentResponse> processPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody BackOfficePaymentRequest request) {

        BackOfficePaymentResponse response = backOfficePaymentService.processPayment(request, idempotencyKeyHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/retry")
    @PreAuthorize("hasRole('BACK_OFFICE')")
    public ResponseEntity<TransactionDetailDto> retryPayment(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader) {

        TransactionDetailDto retried = backOfficePaymentService.retryPayment(id, idempotencyKeyHeader);
        return ResponseEntity.ok(retried);
    }

    @GetMapping
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SCHOOL_FINANCE', 'INSTITUTION_ADMIN', 'BACK_OFFICE')")
    public ResponseEntity<?> getPayments(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "feeCategory", required = false) String feeCategory,
            @RequestParam(value = "studentId", required = false) String studentId,
            @RequestParam(value = "method", required = false) String method,
            @RequestParam(value = "page", defaultValue = "0") int page,
            @RequestParam(value = "size", required = false) Integer size,
            @RequestParam(value = "pageSize", required = false) Integer pageSize,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        int effectivePageSize = pageSize != null ? pageSize : (size != null ? size : 25);
        UUID schoolId = resolveSchoolId(principal);

        return ResponseEntity.ok(schoolPaymentService.getPayments(
                schoolId, search, dateFrom, dateTo, status, feeCategory, studentId, method, page, effectivePageSize));
    }

    @GetMapping("/export")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SCHOOL_FINANCE', 'INSTITUTION_ADMIN', 'BACK_OFFICE')")
    public ResponseEntity<byte[]> exportPayments(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "dateFrom", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateFrom,
            @RequestParam(value = "dateTo", required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate dateTo,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "feeCategory", required = false) String feeCategory,
            @RequestParam(value = "studentId", required = false) String studentId,
            @RequestParam(value = "method", required = false) String method,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        byte[] csv = schoolPaymentService.exportPaymentsCsv(
                schoolId, search, dateFrom, dateTo, status, feeCategory, studentId, method);

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.parseMediaType("text/csv; charset=UTF-8"));
        headers.setContentDisposition(ContentDisposition.attachment().filename("payments-export.csv").build());

        return new ResponseEntity<>(csv, headers, HttpStatus.OK);
    }

    @GetMapping("/{id}")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SCHOOL_FINANCE', 'INSTITUTION_ADMIN', 'BACK_OFFICE')")
    public ResponseEntity<?> getPayment(
            @PathVariable("id") String id,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);
        if (schoolId != null) {
            return ResponseEntity.ok(schoolPaymentService.getPaymentDetail(schoolId, id));
        }

        try {
            UUID uuid = UUID.fromString(id);
            return ResponseEntity.ok(transactionQueryService.getTransactionDetail(uuid));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(schoolPaymentService.getPaymentDetail(null, id));
        }
    }

    @GetMapping("/{id}/receipt")
    @PreAuthorize("hasAnyRole('SCHOOL_ADMIN', 'SCHOOL_FINANCE', 'INSTITUTION_ADMIN', 'BACK_OFFICE')")
    public ResponseEntity<?> getReceipt(
            @PathVariable("id") String id,
            @RequestParam(value = "format", required = false) String format,
            @RequestHeader(value = "Accept", required = false) String acceptHeader,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID schoolId = resolveSchoolId(principal);

        if ("pdf".equalsIgnoreCase(format) || (acceptHeader != null && acceptHeader.contains("application/pdf"))) {
            byte[] pdf = schoolPaymentService.generatePaymentReceiptPdf(schoolId, id);
            HttpHeaders headers = new HttpHeaders();
            headers.setContentType(MediaType.APPLICATION_PDF);
            headers.setContentDisposition(ContentDisposition.inline().filename("receipt-" + id + ".pdf").build());
            return new ResponseEntity<>(pdf, headers, HttpStatus.OK);
        }

        if (schoolId != null) {
            return ResponseEntity.ok(schoolPaymentService.getPaymentReceipt(schoolId, id));
        }

        try {
            UUID uuid = UUID.fromString(id);
            return ResponseEntity.ok(transactionQueryService.getPaymentReceipt(uuid));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.ok(schoolPaymentService.getPaymentReceipt(null, id));
        }
    }

    private UUID resolveSchoolId(SecurityUserPrincipal principal) {
        if (principal != null && principal.institutionId() != null) {
            return principal.institutionId();
        }
        Authentication auth = SecurityContextHolder.getContext().getAuthentication();
        if (auth != null && auth.getAuthorities() != null && auth.getAuthorities().stream().anyMatch(a ->
                a.getAuthority().equals("ROLE_SCHOOL_ADMIN") ||
                a.getAuthority().equals("ROLE_SCHOOL_FINANCE") ||
                a.getAuthority().equals("ROLE_INSTITUTION_ADMIN"))) {
            return institutionRepository.findAll().stream()
                    .filter(i -> i.getAccountStatus() != null && i.getAccountStatus().name().equalsIgnoreCase("ACTIVE"))
                    .map(Institution::getId)
                    .findFirst()
                    .orElse(null);
        }
        return null;
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getReason() != null ? ex.getReason() : ex.getStatusCode().toString());
        body.put("message", ex.getReason() != null ? ex.getReason() : ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }

    @ExceptionHandler(com.tuitionnetwork.common.exceptions.PendingBusinessRuleException.class)
    public ResponseEntity<Map<String, Object>> handlePendingBusinessRule(
            com.tuitionnetwork.common.exceptions.PendingBusinessRuleException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "PENDING_BUSINESS_RULE");
        body.put("message", ex.getMessage());
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(body);
    }

    @ExceptionHandler({
            org.springframework.orm.ObjectOptimisticLockingFailureException.class,
            jakarta.persistence.OptimisticLockException.class,
            org.springframework.dao.PessimisticLockingFailureException.class,
            org.springframework.dao.CannotAcquireLockException.class
    })
    public ResponseEntity<Map<String, Object>> handleLockFailure(Exception ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "CONCURRENT_MODIFICATION_CONFLICT");
        body.put("message", "This fee is being settled by another request. Please refresh and try again.");
        return ResponseEntity.status(HttpStatus.CONFLICT).body(body);
    }
}
