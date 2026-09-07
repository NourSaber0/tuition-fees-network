package com.tuitionnetwork.payments.service;

import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.payments.dto.CustomerFeesResponse;
import com.tuitionnetwork.payments.dto.ReceiptDetailDto;
import com.tuitionnetwork.payments.dto.TransactionDetailDto;
import com.tuitionnetwork.payments.dto.TransactionDto;
import com.tuitionnetwork.payments.dto.TransactionTabCountsDto;

import java.time.LocalDate;
import java.util.UUID;

public interface TransactionQueryService {

    PageResponse<TransactionDto> listTransactions(
            String status,
            String search,
            String institution,
            String institutionType,
            String method,
            LocalDate dateFrom,
            LocalDate dateTo,
            String priority,
            String dueBucket,
            int page,
            int pageSize
    );

    TransactionTabCountsDto getTabCounts();

    TransactionDetailDto getTransactionDetail(UUID id);

    byte[] exportTransactionsCsv(
            String status,
            String search,
            String institution,
            String institutionType,
            String method,
            LocalDate dateFrom,
            LocalDate dateTo
    );

    CustomerFeesResponse lookupCustomerFees(String nationalId);

    ReceiptDetailDto getPaymentReceipt(UUID paymentId);
}
