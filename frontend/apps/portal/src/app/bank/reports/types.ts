export interface ReportCatalogueEntry {
  id: string;
  title: string;
  description: string;
  category: string;
  formats: string[];
  singleDate: boolean;
  contextFilters: string[];
  available: boolean;
  unavailableReason: string | null;
  lastGeneratedAt: string | null;
}

export interface ReportFilters {
  institutionId?: string;
  feeType?: string;
  paymentStatus?: string;
  paymentMethod?: string;
  eppTenor?: number;
  eppStatus?: string;
  reconStatus?: string;
}

export interface GenerateReportRequest {
  reportId: string;
  dateFrom?: string;
  dateTo?: string;
  date?: string;
  format?: string;
  filters?: ReportFilters;
}

export interface ReportColumn {
  key: string;
  label: string;
  align: "left" | "right" | "center" | null;
}

export interface ReportPreview {
  columns: ReportColumn[];
  rows: Record<string, unknown>[];
  total: string | null;
  note: string | null;
}

export interface ReportJobResponse {
  jobId: string;
  reportId: string;
  status: string;
  filename: string;
  downloadUrl: string;
  preview: ReportPreview;
  createdAt: string;
}

export interface ReportHistoryEntry {
  jobId: string;
  reportId: string;
  format: string;
  filename: string;
  rowCount: number;
  createdAt: string;
}

export interface InstitutionOption {
  id: string;
  name: string;
}
