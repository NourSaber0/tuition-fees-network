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
  institution: string;
  institutionType?: string;
  date: string;
  txCount?: number;
  bankAmountEGP?: number;
  systemAmountEGP?: number;
  schoolAmountEGP?: number;
  status: "Matched" | "Exception" | "Pending" | string;
  createdAt: string;
  totalTransactions?: number;
  matchedCount?: number;
  exceptionCount?: number;
}

export interface ReconciliationRunTransactionDto {
  id: string;
  txRef: string;
  studentName?: string;
  institution?: string;
  amountEGP?: number;
  method?: string;
  status?: string;
  timestamp?: string;
}

export interface ReconciliationRunDetailDto extends ReconciliationRunDto {
  transactions: ReconciliationRunTransactionDto[];
}

export interface ReconciliationExceptionDto {
  id: string;
  reconRowId?: string;
  paymentId?: string;
  txRef?: string;
  institution: string;
  institutionType?: string;
  bankAmountEGP?: number;
  systemAmountEGP?: number;
  schoolAmountEGP?: number;
  differenceEGP?: number;
  type: string;
  date: string;
  status: "Open" | "Under Investigation" | "Escalated" | "Resolved" | string;
  assignedTo?: string;
  priority: "High" | "Medium" | "Low" | string;
  txStatus?: string;
  payMethod?: string;
  bankRef?: string;
  bankStatus?: string;
  settlementDate?: string;
  feeRef?: string;
  collectionDate?: string;
  reason?: string;
  resolutionAction?: string;
  supportingReference?: string;
  createdAt: string;
  resolvedAt?: string;
}

export interface ComparisonRowDto {
  source: string;
  reference?: string;
  amountEGP?: number;
  status?: string;
  timestamp?: string;
}

export interface WorkflowStepDto {
  step: string;
  done: boolean;
  desc?: string;
}

export interface SlaDto {
  percent: number;
  timeLeft?: string;
}

export interface ReconciliationExceptionDetailDto extends ReconciliationExceptionDto {
  notes?: string;
  comparisonRows?: ComparisonRowDto[];
  workflow?: WorkflowStepDto[];
  sla?: SlaDto;
}

export interface ResolutionRequest {
  status?: string;
  assignedTo?: string;
  reason?: string;
  resolutionAction?: string;
  supportingReference?: string;
  notes?: string;
}

export interface AssignRequest {
  assignedTo: string;
}

export interface TriggerRunRequest {
  date?: string;
  institutionId?: string;
}
