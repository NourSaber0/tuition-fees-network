package com.tuitionnetwork.payments.web;

import com.tuitionnetwork.payments.dto.BackOfficePaymentRequest;
import com.tuitionnetwork.payments.dto.BackOfficePaymentResponse;
import com.tuitionnetwork.payments.dto.ReceiptDetailDto;
import com.tuitionnetwork.payments.dto.TransactionDetailDto;
import com.tuitionnetwork.payments.service.BackOfficePaymentService;
import com.tuitionnetwork.payments.service.TransactionQueryService;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/payments")
@PreAuthorize("hasRole('BACK_OFFICE')")
public class BackOfficePaymentController {

    private final BackOfficePaymentService backOfficePaymentService;
    private final TransactionQueryService transactionQueryService;

    public BackOfficePaymentController(BackOfficePaymentService backOfficePaymentService,
                                       TransactionQueryService transactionQueryService) {
        this.backOfficePaymentService = backOfficePaymentService;
        this.transactionQueryService = transactionQueryService;
    }

    @PostMapping
    public ResponseEntity<BackOfficePaymentResponse> processPayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @Valid @RequestBody BackOfficePaymentRequest request) {

        BackOfficePaymentResponse response = backOfficePaymentService.processPayment(request, idempotencyKeyHeader);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<TransactionDetailDto> getPayment(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(transactionQueryService.getTransactionDetail(id));
    }

    @PostMapping("/{id}/retry")
    public ResponseEntity<TransactionDetailDto> retryPayment(
            @PathVariable("id") UUID id,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader) {

        TransactionDetailDto retried = backOfficePaymentService.retryPayment(id, idempotencyKeyHeader);
        return ResponseEntity.ok(retried);
    }

    @GetMapping("/{id}/receipt")
    public ResponseEntity<ReceiptDetailDto> getReceipt(@PathVariable("id") UUID id) {
        return ResponseEntity.ok(transactionQueryService.getPaymentReceipt(id));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getStatusCode().toString());
        body.put("message", ex.getReason() != null ? ex.getReason() : ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }
}
