package com.tuitionnetwork.reconciliation.service;

import com.tuitionnetwork.reconciliation.dto.SchoolReconciliationSummaryDto;
import com.tuitionnetwork.reconciliation.dto.SchoolReconciliationTransactionListResponse;
import com.tuitionnetwork.reconciliation.dto.SchoolSettlementListResponse;

import java.time.LocalDate;
import java.util.UUID;

public interface SchoolReconciliationService {

    SchoolReconciliationSummaryDto getSummary(UUID institutionId);

    SchoolReconciliationTransactionListResponse getTransactions(
            UUID institutionId,
            LocalDate dateFrom,
            LocalDate dateTo,
            String status,
            String studentId,
            String paymentId,
            int page,
            int pageSize
    );

    SchoolSettlementListResponse getSettlements(
            UUID institutionId,
            LocalDate dateFrom,
            LocalDate dateTo,
            String status,
            int page,
            int pageSize
    );
}
