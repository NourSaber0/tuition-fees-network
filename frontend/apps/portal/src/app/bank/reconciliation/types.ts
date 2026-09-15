export interface ReconciliationSummaryDto {
  totalTransactions: number;
  matched: number;
  pending: number;
  exceptions: number;
  totalRuns: number;
  totalExceptions: number;
  pendingExceptions: number;
}

export interface ReconciliationRunDto {
  id: string;
  institution: string | null;
  institutionType: string | null;
  date: string | null;
  txCount: number | null;
  bankAmountEGP: number | null;
  systemAmountEGP: number | null;
  schoolAmountEGP: number | null;
  status: string;
  createdAt: string | null;
  totalTransactions: number | null;
  matchedCount: number | null;
  exceptionCount: number | null;
}

export interface ReconciliationRunTransactionDto {
  id: string;
  txRef: string;
  studentName: string;
  institution: string;
  amountEGP: number;
  method: string;
  status: string;
  timestamp: string;
}

export interface ReconciliationRunDetailDto extends ReconciliationRunDto {
  transactions: ReconciliationRunTransactionDto[];
}

export interface ReconciliationExceptionDto {
  id: string;
  reconRowId: string | null;
  paymentId: string | null;
  txRef: string | null;
  institution: string | null;
  institutionType: string | null;
  bankAmountEGP: number | null;
  systemAmountEGP: number | null;
  schoolAmountEGP: number | null;
  differenceEGP: number | null;
  type: string | null;
  date: string | null;
  status: string;
  assignedTo: string | null;
  priority: string | null;
  txStatus: string | null;
  payMethod: string | null;
  bankRef: string | null;
  bankStatus: string | null;
  settlementDate: string | null;
  feeRef: string | null;
  collectionDate: string | null;
  reason: string | null;
  resolutionAction: string | null;
  supportingReference: string | null;
  createdAt: string | null;
  resolvedAt: string | null;
}

export interface ComparisonRowDto {
  source: string;
  reference: string | null;
  amountEGP: number | null;
  status: string | null;
  timestamp: string | null;
}

export interface WorkflowStepDto {
  step: string;
  done: boolean;
  desc: string;
}

export interface SlaDto {
  percent: number;
  timeLeft: string;
}

export interface ReconciliationExceptionDetailDto extends ReconciliationExceptionDto {
  notes: string | null;
  comparisonRows: ComparisonRowDto[];
  workflow: WorkflowStepDto[];
  sla: SlaDto;
}

export interface ResolutionRequest {
  status?: string;
  assignedTo?: string;
  reason?: string;
  resolutionAction?: string;
  supportingReference?: string;
  notes?: string;
}
