package com.tuitionnetwork.dashboard.dto;

public record SchoolDashboardKpis(
        SchoolKpiValue totalCollectedEGP,
        SchoolKpiValue outstandingEGP,
        SchoolOverdueKpi overdueEGP,
        SchoolFeeUploadStatus feeUploadStatus
) {}
