package com.tuitionnetwork.payments.service;

import com.tuitionnetwork.payments.dto.ReceiptDetailDto;
import com.tuitionnetwork.payments.dto.SchoolPaymentDetailDto;
import com.tuitionnetwork.payments.dto.SchoolPaymentListResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface SchoolPaymentService {

    SchoolPaymentListResponse getPayments(
            UUID institutionId,
            String search,
            LocalDate dateFrom,
            LocalDate dateTo,
            String status,
            String feeCategory,
            String studentId,
            String method,
            int page,
            int pageSize
    );

    SchoolPaymentDetailDto getPaymentDetail(UUID institutionId, String paymentIdOrRef);

    ReceiptDetailDto getPaymentReceipt(UUID institutionId, String paymentIdOrRef);

    byte[] generatePaymentReceiptPdf(UUID institutionId, String paymentIdOrRef);

    byte[] exportPaymentsCsv(
            UUID institutionId,
            String search,
            LocalDate dateFrom,
            LocalDate dateTo,
            String status,
            String feeCategory,
            String studentId,
            String method
    );
}
