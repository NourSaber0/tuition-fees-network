import { ApiClient } from "../client";

export interface FeePenaltySnapshotDto {
  applied: boolean;
  penaltyAmountEGP?: number;
  penaltyAppliedAt?: string;
  graceEnded: boolean;
  totalDueEGP: number;
}

export interface FeePaymentHistoryDto {
  paymentId: string;
  dateEGP?: string;
  timestamp?: string; // Sometimes the backend uses timestamp
  amountEGP: number;
  status: string;
}

export interface FeeDto {
  id: string;
  studentId: string;
  studentName?: string;
  name: string;
  category: string;
  dueDate: string;
  originalAmountEGP: number;
  paidEGP: number;
  remainingEGP: number;
  status: string;
  penaltyApplied?: boolean;
  totalDueEGP?: number;
}

export interface FeeDetailDto extends FeeDto {
  term?: string;
  overdue?: {
    isOverdue: boolean;
    daysOverdue: number;
  };
  penalty?: FeePenaltySnapshotDto;
  paymentHistory: FeePaymentHistoryDto[];
}

export interface FeeCategoryDto {
  code: string;
  displayName: string;
  priority: number;
}

export interface FeePenaltyInfoDto {
  dueDate: string;
  priority: string;
  daysToDue: number;
  outstandingEGP: number;
  penaltyRate: number;
  projectedPenaltyEGP: number;
  willApplyAt: string;
}

export interface CreateFeeRequest {
  studentId: string;
  name: string;
  category: string;
  amountEGP: number;
  term?: string;
  dueDate: string;
}

export interface UpdateFeeRequest {
  amountEGP?: number;
  dueDate?: string;
}

export async function getFees(
  client: ApiClient,
  params?: {
    search?: string;
    category?: string;
    studentId?: string;
    dueDateFrom?: string;
    dueDateTo?: string;
    status?: string;
    page?: number;
    pageSize?: number;
  }
): Promise<{ data: FeeDto[]; total?: number; page?: number; totalPages?: number }> {
  const query = new URLSearchParams();
  if (params?.search) query.set("search", params.search);
  if (params?.category) query.set("category", params.category);
  if (params?.studentId) query.set("studentId", params.studentId);
  if (params?.dueDateFrom) query.set("dueDateFrom", params.dueDateFrom);
  if (params?.dueDateTo) query.set("dueDateTo", params.dueDateTo);
  if (params?.status) query.set("status", params.status);
  if (params?.page !== undefined) query.set("page", String(params.page));
  if (params?.pageSize !== undefined) query.set("pageSize", String(params.pageSize));

  return client.get(`/fees?${query.toString()}`);
}

export async function getFeeDetail(client: ApiClient, id: string): Promise<FeeDetailDto> {
  return client.get(`/fees/${id}`);
}

export async function createFee(client: ApiClient, data: CreateFeeRequest): Promise<FeeDto> {
  return client.post("/fees", data);
}

export async function updateFee(client: ApiClient, id: string, data: UpdateFeeRequest): Promise<FeeDto> {
  return client.patch(`/fees/${id}`, data);
}

export async function getFeeCategories(client: ApiClient): Promise<FeeCategoryDto[]> {
  return client.get("/fee-categories");
}

export async function getFeePenaltyInfo(client: ApiClient, id: string): Promise<FeePenaltyInfoDto> {
  return client.get(`/fees/${id}/penalty-info`);
}
