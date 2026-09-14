package com.tuitionnetwork.t24.web;

import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.t24.dto.T24BillingDto.RequestBillingRequest;
import com.tuitionnetwork.t24.dto.T24BillingDto.RequestBillingResponse;
import com.tuitionnetwork.t24.dto.T24BillingDto.RetrieveBillingResponse;
import com.tuitionnetwork.t24.dto.T24BillingDto.UpdateBillingRequest;
import com.tuitionnetwork.t24.dto.T24BillingDto.UpdateBillingResponse;
import com.tuitionnetwork.t24.service.T24CustomerBillingService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

/**
 * REST Adapter Controller exposing T24 CustomerBilling SOAP operations behind clean REST endpoints.
 * Originating from Postman workspace 'MOCKFees'.
 */
@RestController
@RequestMapping("/api/v1/t24/billing")
@PreAuthorize("hasAnyRole('BACK_OFFICE', 'SCHOOL_ADMIN', 'SCHOOL_FINANCE', 'INSTITUTION_ADMIN', 'GUARDIAN')")
public class T24CustomerBillingController {

    private final T24CustomerBillingService service;

    @Autowired
    public T24CustomerBillingController(T24CustomerBillingService service) {
        this.service = service;
    }

    /**
     * Operation 1: RetrieveCustomerBillingProcedure
     * Fetches customer's outstanding dues from T24 core banking / mock.
     */
    @GetMapping("/retrieve")
    public ResponseEntity<RetrieveBillingResponse> retrieveBilling(
            @RequestParam(value = "nationalId", required = false) String queryNationalId,
            @RequestParam(value = "accountNumber", required = false) String accountNumber,
            @RequestHeader(value = "X-Guardian-National-Id", required = false) String headerNationalId,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        String resolvedNationalId = queryNationalId != null && !queryNationalId.isBlank()
                ? queryNationalId.trim()
                : (headerNationalId != null ? headerNationalId.trim() : null);

        UUID actorId = principal != null ? principal.userId() : null;
        RetrieveBillingResponse response = service.retrieveCustomerDues(resolvedNationalId, accountNumber, actorId);
        return ResponseEntity.ok(response);
    }

    /**
     * Operation 2: RequestCustomerBillingProcedure
     * Registers a new customer billing record in T24 (called on CSV fee upload).
     */
    @PostMapping("/request")
    public ResponseEntity<RequestBillingResponse> requestBilling(
            @RequestBody RequestBillingRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID actorId = principal != null ? principal.userId() : null;
        RequestBillingResponse response = service.registerFeeInT24(request, actorId);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Operation 3: UpdateCustomerBillingProcedure
     * Updates an existing billing record's balance in T24 (called on payment capture).
     */
    @RequestMapping(value = "/update", method = {RequestMethod.POST, RequestMethod.PUT})
    public ResponseEntity<UpdateBillingResponse> updateBilling(
            @RequestBody UpdateBillingRequest request,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {

        UUID actorId = principal != null ? principal.userId() : null;
        UpdateBillingResponse response = service.syncPaymentSettlement(request, actorId);
        return ResponseEntity.ok(response);
    }

    /**
     * Serves the WSDL service description for client integration inspection.
     */
    @GetMapping(value = "/wsdl", produces = MediaType.APPLICATION_XML_VALUE)
    public ResponseEntity<String> getWsdl() {
        return ResponseEntity.ok(service.getWsdlDescription());
    }
}
