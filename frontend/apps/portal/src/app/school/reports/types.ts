// ---------------------------------------------------------------------------
// SP-P8 – School Portal Reports
// TypeScript types — school-scoped shape (simpler than bank BO-P7).
// No singleDate, no contextFilters, no available/unavailableReason, no institutionId.
// ---------------------------------------------------------------------------

/** One entry from GET /reports/catalogue */
export interface SchoolReportCatalogueEntry {
  id: string;
  title: string;
  description?: string | null;
  category: string;
  formats: string[];
  lastGeneratedAt: string | null;
}

/** School-specific optional filters sent with POST /reports/generate */
export interface SchoolReportFilters {
  feeCategory?: string;
  paymentStatus?: string;
}

/** Request body for POST /reports/generate */
export interface SchoolGenerateReportRequest {
  reportId: string;
  dateFrom: string;
  dateTo: string;
  format: string;
  filters?: SchoolReportFilters;
}

/** 202 Accepted / job poll response — GET /reports/jobs/{jobId} */
export interface SchoolReportJobResponse {
  jobId: string;
  reportId?: string;
  /** "processing" | "ready" | "failed" */
  status: string;
  filename?: string;
  downloadUrl?: string;
  preview?: SchoolReportPreview | null;
  createdAt?: string;
}

export interface SchoolReportColumn {
  key: string;
  label: string;
  align: "left" | "right" | "center" | null;
}

export interface SchoolReportPreview {
  columns: SchoolReportColumn[];
  rows: Record<string, unknown>[];
  total: string | null;
  note: string | null;
}

/** One row from GET /reports/history */
export interface SchoolReportHistoryEntry {
  jobId: string;
  reportId: string;
  format: string;
  filename: string;
  rowCount: number;
  createdAt: string;
}

