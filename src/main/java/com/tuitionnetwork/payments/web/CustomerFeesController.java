package com.tuitionnetwork.payments.web;

import com.tuitionnetwork.payments.dto.CibAccountDto;
import com.tuitionnetwork.payments.dto.CustomerFeesResponse;
import com.tuitionnetwork.payments.service.TransactionQueryService;
import com.tuitionnetwork.payments.spi.BankGatewayAdapterInterface;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/v1/customers")
@PreAuthorize("hasRole('BACK_OFFICE')")
public class CustomerFeesController {

    private final TransactionQueryService transactionQueryService;
    private final BankGatewayAdapterInterface bankGatewayAdapter;

    public CustomerFeesController(TransactionQueryService transactionQueryService,
                                  BankGatewayAdapterInterface bankGatewayAdapter) {
        this.transactionQueryService = transactionQueryService;
        this.bankGatewayAdapter = bankGatewayAdapter;
    }

    @GetMapping("/fees")
    public ResponseEntity<CustomerFeesResponse> getCustomerFees(@RequestParam("nationalId") String nationalId) {
        return ResponseEntity.ok(transactionQueryService.lookupCustomerFees(nationalId));
    }

    /**
     * The customer's CIB accounts (US: pick an account before paying). Called right
     * after the national-ID lookup succeeds, before the operator selects fees.
     */
    @GetMapping("/accounts")
    public ResponseEntity<List<CibAccountDto>> getCustomerAccounts(@RequestParam("nationalId") String nationalId) {
        return ResponseEntity.ok(bankGatewayAdapter.listAccounts(nationalId));
    }

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<Map<String, Object>> handleResponseStatus(ResponseStatusException ex) {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", ex.getStatusCode().toString());
        body.put("message", ex.getReason() != null ? ex.getReason() : ex.getMessage());
        return ResponseEntity.status(ex.getStatusCode()).body(body);
    }
}
