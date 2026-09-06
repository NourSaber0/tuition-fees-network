package com.tuitionnetwork.payments.web;

import com.tuitionnetwork.common.exceptions.PendingBusinessRuleException;
import com.tuitionnetwork.payments.dto.PaymentSettleRequest;
import com.tuitionnetwork.payments.dto.PaymentSettleResponse;
import com.tuitionnetwork.payments.service.PaymentSettlementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/payments/settle")
public class PaymentController {

    private final PaymentSettlementService paymentSettlementService;

    public PaymentController(PaymentSettlementService paymentSettlementService) {
        this.paymentSettlementService = paymentSettlementService;
    }

    @PostMapping
    @PreAuthorize("hasAnyRole('GUARDIAN', 'BACK_OFFICE')")
    public ResponseEntity<PaymentSettleResponse> settlePayment(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKeyHeader,
            @RequestBody PaymentSettleRequest request) {

        PaymentSettleResponse response = paymentSettlementService.settlePayment(request, idempotencyKeyHeader);
        return ResponseEntity.ok(response);
    }

    @ExceptionHandler(PendingBusinessRuleException.class)
    public ResponseEntity<Map<String, String>> handlePendingBusinessRule(PendingBusinessRuleException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(Map.of(
                        "error", "PENDING_BUSINESS_RULE",
                        "message", ex.getMessage()
                ));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, String>> handleResponseStatus(ResponseStatusException ex) {
        return ResponseEntity.status(ex.getStatusCode())
                .body(Map.of(
                        "error", "REQUEST_ERROR",
                        "message", ex.getReason() != null ? ex.getReason() : ex.getMessage()
                ));
    }

    @ExceptionHandler({org.springframework.orm.ObjectOptimisticLockingFailureException.class, jakarta.persistence.OptimisticLockException.class})
    public ResponseEntity<Map<String, String>> handleOptimisticLockFailure(Exception ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(Map.of(
                        "error", "CONCURRENT_MODIFICATION_CONFLICT",
                        "message", "Fee balance was modified by another transaction. Please retry."
                ));
    }
}
