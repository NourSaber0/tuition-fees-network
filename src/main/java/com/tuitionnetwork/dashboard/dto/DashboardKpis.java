package com.tuitionnetwork.dashboard.dto;

public record DashboardKpis(
        ActiveInstitutionsKpi activeInstitutions,
        KpiValue totalStudents,
        KpiValue todayTransactions,
        CollectionKpi todayCollectionEGP,
        RateKpi successfulPayments,
        RateKpi failedPayments,
        KpiValue pendingPayments,
        KpiValue pendingReconciliation,
        EppPlansKpi activeEppPlans
) {
}
