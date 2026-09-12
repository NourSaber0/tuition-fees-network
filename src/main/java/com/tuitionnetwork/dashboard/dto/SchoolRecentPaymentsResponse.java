package com.tuitionnetwork.dashboard.dto;

import java.util.List;

public record SchoolRecentPaymentsResponse(
        List<SchoolRecentPaymentDto> data
) {}
