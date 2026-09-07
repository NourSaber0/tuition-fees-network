package com.tuitionnetwork.dashboard.service;

import com.tuitionnetwork.dashboard.dto.DashboardSummaryResponse;
import com.tuitionnetwork.dashboard.dto.InstitutionStatusResponse;
import com.tuitionnetwork.dashboard.dto.RecentTransactionsResponse;
import com.tuitionnetwork.dashboard.dto.WeeklyCollectionsResponse;

import java.time.LocalDate;

public interface DashboardService {

    DashboardSummaryResponse getSummary();

    WeeklyCollectionsResponse getWeeklyCollections(LocalDate weekOf);

    InstitutionStatusResponse getInstitutionStatus();

    RecentTransactionsResponse getRecentTransactions(int limit);
}
