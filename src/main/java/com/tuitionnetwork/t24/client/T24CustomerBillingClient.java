package com.tuitionnetwork.t24.client;

import com.tuitionnetwork.t24.dto.T24BillingDto.RequestBillingRequest;
import com.tuitionnetwork.t24.dto.T24BillingDto.RequestBillingResponse;
import com.tuitionnetwork.t24.dto.T24BillingDto.RetrieveBillingResponse;
import com.tuitionnetwork.t24.dto.T24BillingDto.UpdateBillingRequest;
import com.tuitionnetwork.t24.dto.T24BillingDto.UpdateBillingResponse;

/**
 * T24 CustomerBilling SOAP client interface.
 */
public interface T24CustomerBillingClient {

    /**
     * Executes T24 RetrieveCustomerBillingProcedure to fetch outstanding billing records for a customer.
     */
    RetrieveBillingResponse retrieveCustomerBilling(String nationalId, String accountNumber);

    /**
     * Executes T24 RequestCustomerBillingProcedure to create a new customer billing record upon fee submission.
     */
    RequestBillingResponse requestCustomerBilling(RequestBillingRequest request);

    /**
     * Executes T24 UpdateCustomerBillingProcedure to update billing balance upon fee payment.
     */
    UpdateBillingResponse updateCustomerBilling(UpdateBillingRequest request);
}
