export type FeeCategory =
    | "Tuition"
    | "Books"
    | "Activity"
    | "Bus";

export type FeeStatus =
    | "Active"
    | "Paid"
    | "Partial"
    | "Outstanding"
    | "Overdue"
    | "Draft";

export interface FeeCategoryOption {
    code: FeeCategory;
    displayName: string;
    priority: number;
}

export interface Fee {
    id: string;
    studentId: string;
    studentName: string;
    name: string;
    category: FeeCategory | string;
    dueDate: string;
    term?: string;
    originalAmountEGP: number;
    paidEGP: number;
    remainingEGP: number;
    status: FeeStatus | string;
    penaltyApplied?: boolean;
    totalDueEGP?: number;
}

export interface FeeListResponse {
    data: Fee[];
    page: number;
    pageSize: number;
    total: number;
    totalPages: number;
}

export interface PaymentHistoryItem {
    paymentId: string;
    dateEGP: string;
    amountEGP: number;
    status: string;
}

export interface FeeOverdue {
    isOverdue: boolean;
    daysOverdue: number;
}

export interface FeePenalty {
    applied: boolean;
    penaltyAmountEGP: number;
    penaltyAppliedAt: string | null;
    graceEnded: boolean;
    totalDueEGP: number;
}

export interface FeeDetail extends Fee {
    term?: string;
    paymentHistory: PaymentHistoryItem[];
    overdue?: FeeOverdue;
    penalty?: FeePenalty;
}

export interface CreateFeeRequest {
    studentId: string;
    name: string;
    category: FeeCategory;
    amountEGP: number;
    term: string;
    dueDate: string;
}

export interface UpdateFeeRequest {
    name?: string;
    category?: FeeCategory;
    amountEGP?: number;
    term?: string;
    dueDate?: string;
}

export interface StudentSearchResult {
    id: string;
    studentRef?: string;
    name: string;
    grade?: string;
    section?: string;
    status?: string;
}

export interface StudentSearchResponse {
    data: StudentSearchResult[];
}

export interface PenaltyInfo {
    dueDate: string;
    priority: string;
    daysToDue: number;
    outstandingEGP: number;
    penaltyAmountEGP: number;
    penaltyAppliedAt: string | null;
    graceEnded: boolean;
    totalDueEGP: number;
}