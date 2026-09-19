// ---------------------------------------------------------------------------
// SP-P6 – School Portal Payments (view-only)
// TypeScript types matching the backend DTOs exactly.
// ---------------------------------------------------------------------------

/** Matches StudentPaymentItemDto (used in list rows) */
export interface SchoolPaymentItem {
  id: string;
  studentId: string;
  studentName: string;
  feeId: string;
  feeName: string;
  feeDueDate: string; // LocalDate serialised as "YYYY-MM-DD"
  feePriority: number;
  amountEGP: number;
  method: string;
  date: string;
  time: string;
  status: string;
  reconciliation: string;
  isPartial: boolean;
  originalFeeAmountEGP: number;
  remainingAfterEGP: number;
}

/** Matches SchoolPaymentAllocationItemDto (one row in the multi-fee split) */
export interface SchoolPaymentAllocationItem {
  feeId: string;
  feeName: string;
  feeCategory: string;
  dueDate: string; // LocalDate serialised as "YYYY-MM-DD"
  originalAmountEGP: number;
  previouslyPaidEGP: number;
  outstandingEGP: number;
  allocatedEGP: number;
  remainingAfterEGP: number;
  feeStatus: string;
  priority: number;
  isOverdue: boolean;
}

/** Matches SchoolPaymentDetailDto (used in the detail drawer / modal) */
export interface SchoolPaymentDetail {
  id: string;
  studentId: string;
  studentName: string;
  amountEGP: number;
  currency: string;
  method: string;
  date: string;
  time: string;
  status: string;
  reconciliation: string;
  allocation: SchoolPaymentAllocationItem[];
}

/**
 * Status counts across the full filtered result set (not just the current
 * page) — powers the Total/Successful/Pending/Failed summary pills.
 *
 * NOTE: This field is optional pending backend support. Until the API
 * returns it, the page falls back to counting only the currently loaded
 * page, which undercounts whenever results span more than one page.
 */
export interface SchoolPaymentStatusCounts {
  successful: number;
  pending: number;
  failed: number;
}

/** Matches SchoolPaymentListResponse (paginated list wrapper) */
export interface SchoolPaymentsResponse {
  data: SchoolPaymentItem[];
  total: number;
  page: number;       // 0-indexed (backend convention)
  pageSize: number;
  totalPages: number;
  statusCounts?: SchoolPaymentStatusCounts;
}

// ---------------------------------------------------------------------------
// Filter shape used exclusively on the frontend
// ---------------------------------------------------------------------------
export interface PaymentFilters {
  search: string;
  status: string;        // "" = all
  method: string;        // "" = all — NEW: replaces the old feeCategory filter
  dateFrom: string;
  dateTo: string;
  studentId: string;
}

export interface StudentSearchItem {
  id: string;
  studentRef: string;
  name: string;
  grade: string;
  section: string;
  status: string;
}

export interface StudentFeeItem {
  feeId: string;
  name: string;
  category: string;
  term: string;
  dueDate: string;
  originalAmountEGP: number;
  paidEGP: number;
  remainingEGP: number;
  status: string;
}

export interface StudentFeesResponse {
  data: StudentFeeItem[];
  totals?: {
    totalFeesEGP: number;
    totalPaidEGP: number;
    totalOutstandingEGP: number;
    overdueCount: number;
  };
}

export interface SchoolPosPaymentResponse {
  transactionId: string;
  status: string;
  amountPaidEGP: number;
  isPartial: boolean;
  remainingBalanceEGP: number;
  method: string;
  bankRef?: string;
  authCode?: string;
  receiptRef?: string;
  originalFeeEGP?: number;
  penaltyEGP?: number;
  totalCollectedEGP?: number;
  penaltyAppliedAt?: string;
}