package com.tuitionnetwork.payments.service;

import com.tuitionnetwork.payments.dto.BackOfficePaymentRequest;
import com.tuitionnetwork.payments.dto.BackOfficePaymentResponse;
import com.tuitionnetwork.payments.dto.TransactionDetailDto;

import java.util.UUID;

public interface BackOfficePaymentService {

    BackOfficePaymentResponse processPayment(BackOfficePaymentRequest request, String idempotencyKeyHeader);

    TransactionDetailDto retryPayment(UUID paymentId, String newIdempotencyKey);
}
