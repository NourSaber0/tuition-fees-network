package com.tuitionnetwork.t24.service;

import com.tuitionnetwork.t24.dto.T24BillingDto.RequestBillingRequest;
import com.tuitionnetwork.t24.dto.T24BillingDto.RequestBillingResponse;
import com.tuitionnetwork.t24.dto.T24BillingDto.RetrieveBillingResponse;
import com.tuitionnetwork.t24.dto.T24BillingDto.UpdateBillingRequest;
import com.tuitionnetwork.t24.dto.T24BillingDto.UpdateBillingResponse;

import java.util.UUID;

public interface T24CustomerBillingService {

    RetrieveBillingResponse retrieveCustomerDues(String nationalId, String accountNumber, UUID actorId);

    RequestBillingResponse registerFeeInT24(RequestBillingRequest request, UUID actorId);

    UpdateBillingResponse syncPaymentSettlement(UpdateBillingRequest request, UUID actorId);

    String getWsdlDescription();
}
