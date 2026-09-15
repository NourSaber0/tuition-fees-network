package com.tuitionnetwork.mockbank.web;

import com.tuitionnetwork.mockbank.dto.CustomerLookupResponse;
import com.tuitionnetwork.mockbank.dto.MockBankException;
import com.tuitionnetwork.mockbank.store.MockBankStore;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/customers")
public class MockCustomerController {

    private final MockBankStore store;

    public MockCustomerController(MockBankStore store) {
        this.store = store;
    }

    @GetMapping
    public ResponseEntity<CustomerLookupResponse> lookupCustomer(@RequestParam("national_id") String nationalId) {
        if (nationalId == null || nationalId.length() != 14 || !nationalId.matches("\\d+")) {
            throw new MockBankException(HttpStatus.BAD_REQUEST, "INVALID_NATIONAL_ID", "Invalid national ID format");
        }

        return store.getCustomerByNationalId(nationalId)
                .map(ResponseEntity::ok)
                .orElseThrow(() -> new MockBankException(HttpStatus.NOT_FOUND, "CUSTOMER_NOT_FOUND", "Customer not found in the bank register"));
    }
}
