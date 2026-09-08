package com.tuitionnetwork.payments.dto;

import java.util.List;

public record CustomerFeesResponse(
        CustomerDto customer,
        List<CustomerFeeItemDto> fees
) {
}
