// School Portal Dashboard Types
// Mirrors school-specific DTOs from SchoolDashboardController.java

export interface KpiWithTrend {
  value: number;
  trendPct: number;
}

export interface UploadStatusKpi {
  lastUploadStatus: string; // "Completed with Errors" | "Completed" | "Failed" | "Pending"
  lastUploadAt: string; // ISO-8601
  pendingResubmission: boolean;
}

export interface OverdueKpi {
  value: number;
  overdueFeeCount: number;
  trendPct: number;
}

export interface SchoolDashboardKpis {
  totalCollectedEGP: KpiWithTrend;
  outstandingEGP: KpiWithTrend;
  overdueEGP: OverdueKpi;
  feeUploadStatus: UploadStatusKpi;
}

export interface DashboardSummaryResponse {
  asOf: string;
  kpis: SchoolDashboardKpis;
}

export interface RecentPaymentDto {
  id: string; // TX-20260906-0041
  studentId: string; // STU-0231
  studentName: string; // Yousef Adel
  feeId: string; // FEE-0231-01
  feeName: string; // Tuition - Term 1 2026/27
  amountEGP: number;
  date: string; // ISO-8601
  status: "Successful" | "Pending" | "Failed";
  isPartial: boolean;
}

export interface RecentPaymentsResponse {
  data: RecentPaymentDto[];
}

export interface QuickLink {
  id: string;
  label: string;
  href: string;
  icon: string;
  badgeCount?: number; // for notifications, pending uploads, etc.
  badgeLabel?: string;
}

export interface QuickLinksResponse {
  data: QuickLink[];
}
