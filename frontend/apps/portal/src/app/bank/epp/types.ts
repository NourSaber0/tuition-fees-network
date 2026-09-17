export interface EppPlanSummaryDto {
  id: string;
  payRef: string | null;
  institution: string | null;
  institutionType: string | null;
  student: string | null;
  principalEGP: number;
  tenor: number;
  interestRatePct: number;
  interestEGP: number;
  adminFeeEGP: number;
  totalEGP: number;
  monthlyEGP: number;
  paidInstallments: number;
  status: string;
  startDate: string | null;
}

export interface EppProgressDto {
  paidInstallments: number;
  totalInstallments: number;
  percent: number;
}

export interface EppPlanDetailDto extends EppPlanSummaryDto {
  firstPaymentDate: string | null;
  progress: EppProgressDto;
}

export interface EppPlanListResponse {
  data: EppPlanSummaryDto[];
  page: number;
  pageSize: number;
  total: number;
  totalPages: number;
}

export interface EppScheduleInstallmentDto {
  number: number;
  dueDate: string;
  principalEGP: number | null;
  interestEGP: number | null;
  amountEGP: number;
  paidAmountEGP: number;
  status: string;
  paidDate: string | null;
}

export interface EppQuoteResponse {
  principalEGP: number;
  tenor: number;
  interestRatePct: number;
  interestEGP: number;
  adminFeeEGP: number;
  totalEGP: number;
  monthlyEGP: number;
}

export interface CardValidationResponse {
  eligible: boolean;
  bank: string | null;
  cardType: string | null;
  maxTenor: number | null;
  reason: string | null;
  result: string;
}

export interface CreateEppPlanRequest {
  cardToken?: string;
  studentName?: string;
  nationalId?: string;
  institution?: string;
  feeDescription?: string;
  principalEGP: number;
  tenor: number;
  sourcePaymentId: string;
}

export interface UpdateEppPlanStatusRequest {
  status: string;
  reason?: string;
}

export interface EppSummaryResponse {
  active: number;
  completed: number;
  defaulted: number;
  totalOutstandingEGP: number;
}
