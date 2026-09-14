export interface StudentTotals {
  totalFeesEGP: number;
  totalPaidEGP: number;
  totalOutstandingEGP: number;
}

export interface StudentSummary {
  id: string;
  studentRef: string;
  name: string;
  grade: string;
  section: string;
  totalFeesEGP: number;
  paidEGP: number;
  outstandingEGP: number;
  status: string;
  deactivatedDate?: string | null;
  deactivationReason?: string | null;
}

export interface StudentDetail {
  id: string;
  studentRef: string;
  name: string;
  grade: string;
  section: string;
  status: string;
  nationalIdMasked: string;
  parentName: string;
  parentPhone: string;
  parentEmail: string;
  totals: StudentTotals;
  deactivatedDate?: string | null;
  deactivationReason?: string | null;
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
  totals: StudentTotals;
}

export interface StudentPaymentItem {
  id: string;
  studentId: string;
  studentName: string;
  feeId: string;
  feeName: string;
  feeDueDate: string;
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

export interface StudentPaymentsResponse {
  data: StudentPaymentItem[];
}

export interface StudentGuardian {
  id: string;
  name: string;
  email: string;
  phone: string;
  relationship: string;
  primaryGuardian: boolean;
}

export interface StatementTransaction {
  date: string;
  type: string; // INVOICE | PAYMENT
  reference: string;
  description: string;
  debitEGP: number;
  creditEGP: number;
  runningBalanceEGP: number;
}

export interface StudentStatementResponse {
  studentId: string;
  studentRef: string;
  studentName: string;
  schoolName: string;
  grade: string;
  generatedAt: string;
  totalInvoicedEGP: number;
  totalPaidEGP: number;
  currentBalanceEGP: number;
  ledger: StatementTransaction[];
}

export interface EnrollStudentRequest {
  studentRef: string;
  name: string;
  grade?: string;
  section?: string;
  nationalId?: string;
  parentName?: string;
  parentPhone?: string;
  parentEmail?: string;
}

export interface UpdateStudentRequest {
  studentRef?: string;
  name?: string;
  grade?: string;
  section?: string;
  parentName?: string;
  parentPhone?: string;
  parentEmail?: string;
}

export interface DeactivateStudentRequest {
  reason: string;
}

export interface LinkGuardianRequest {
  name: string;
  email?: string;
  phone?: string;
  relationship?: string;
  primaryGuardian?: boolean;
}
