// Mirrors com.tuitionnetwork.dashboard.dto.* exactly - see DashboardController.java.

export interface KpiValue {
  value: number;
  trendPct: number;
}

export interface ActiveInstitutionsKpi {
  value: number;
  schools: number;
  universities: number;
  trendPct: number;
}

export interface CollectionKpi {
  value: number;
  prevDayEGP: number;
  trendPct: number;
}

export interface RateKpi {
  value: number;
  ratePct: number;
  trendPct: number;
}

export interface EppPlansKpi {
  value: number;
  outstandingEGP: number;
  trendPct: number;
}

export interface DashboardKpis {
  activeInstitutions: ActiveInstitutionsKpi;
  totalStudents: KpiValue;
  todayTransactions: KpiValue;
  todayCollectionEGP: CollectionKpi;
  successfulPayments: RateKpi;
  failedPayments: RateKpi;
  pendingPayments: KpiValue;
  pendingReconciliation: KpiValue;
  activeEppPlans: EppPlansKpi;
}

export interface DashboardSummaryResponse {
  asOf: string;
  kpis: DashboardKpis;
}

export interface WeeklyCollectionPoint {
  date: string;
  label: string;
  amountEGP: number;
}

export interface WeeklyCollectionsResponse {
  currency: string;
  from: string;
  to: string;
  series: WeeklyCollectionPoint[];
  weekTotalEGP: number;
  dailyAvgEGP: number;
}

export interface InstitutionStatusBreakdown {
  label: string;
  count: number;
  pct: number;
}

export interface InstitutionStatusResponse {
  schools: number;
  universities: number;
  breakdown: InstitutionStatusBreakdown[];
}

export interface TransactionSummaryDto {
  id: string;
  institution: string;
  student: string;
  feeType: string;
  amountEGP: number;
  method: string;
  status: string;
  timestamp: string;
}

export interface RecentTransactionsResponse {
  data: TransactionSummaryDto[];
}

export interface DeadlineQueueItemDto {
  feeLineId: string;
  institution: string;
  student: string;
  feeType: string;
  dueDate: string;
  priority: string;
  daysToDue: number;
  outstandingEGP: number;
  penaltyEGP: number;
  totalDueEGP: number;
}

export interface DeadlineSummaryResponse {
  dueToday: number;
  dueThisWeek: number;
  urgent: number;
  overdue: number;
  penaltiesAppliedEGP: number;
  priorityQueue: DeadlineQueueItemDto[];
}
