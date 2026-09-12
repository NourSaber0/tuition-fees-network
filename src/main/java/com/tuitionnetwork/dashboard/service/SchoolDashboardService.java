package com.tuitionnetwork.dashboard.service;

import com.tuitionnetwork.dashboard.dto.SchoolDashboardSummaryResponse;
import com.tuitionnetwork.dashboard.dto.SchoolQuickLinkDto;
import com.tuitionnetwork.dashboard.dto.SchoolRecentPaymentsResponse;
import com.tuitionnetwork.dashboard.dto.WeeklyCollectionsResponse;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

public interface SchoolDashboardService {

    SchoolDashboardSummaryResponse getSchoolSummary(UUID schoolId);

    SchoolRecentPaymentsResponse getRecentPayments(UUID schoolId, int limit);

    List<SchoolQuickLinkDto> getQuickLinks(UUID schoolId, UUID userId);

    WeeklyCollectionsResponse getSchoolWeeklyCollections(UUID schoolId, LocalDate weekOf);
}
