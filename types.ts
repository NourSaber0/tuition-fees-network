export type ReconciliationStatus =
    | "Reconciled"
    | "Pending"
    | "Unreconciled";

export interface ReconciliationSummary {
    totalReconciled: number;
    totalUnreconciled: number;
    totalPending: number;
}

export interface ReconciliationTransaction {
    paymentId: string;
    studentId: string;
    feeId: string;
    amountEGP: number;
    date: string;
    reconciliationStatus: ReconciliationStatus;
}

export interface ReconciliationTransactionsResponse {
    data: ReconciliationTransaction[];
    page: number;
    pageSize: number;
    total: number;
    totalPages: number;
}