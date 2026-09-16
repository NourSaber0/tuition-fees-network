export interface PartialPaymentDto {
  isPartial: boolean;
  totalFeeAmountEGP: number;
  previouslyPaidEGP: number;
  currentPaymentEGP: number;
  remainingAmountEGP: number;
  paymentCount: number;
}

export interface TransactionDto {
  id: string;
  institution: string;
  institutionType?: string;
  student: string;
  feeType: string;
  amountEGP: number;
  method: string;
  status: "Successful" | "Pending" | "Failed" | string;
  bankRef?: string;
  settlementStatus?: string;
  reconStatus?: string;
  timestamp: string;
  partial?: PartialPaymentDto;
  idempotencyKey?: string;
  channel?: string;
  dueDate?: string;
  priority?: "PAID" | "LOW" | "MEDIUM" | "HIGH" | "URGENT" | "OVERDUE" | string;
  daysToDue?: number;
  outstandingEGP?: number;
  penaltyEGP?: number;
  penaltyAppliedAt?: string;
  graceEnded?: boolean;
  totalDueEGP?: number;
}

export interface TimelineEventDto {
  stage: string;
  title: string;
  timestamp: string;
  status: "COMPLETED" | "PENDING" | "FAILED" | string;
  description?: string;
}

export interface AllocatedDueDetailDto {
  feeLineId: string;
  feeName: string;
  originalAmountEGP: number;
  penaltyEGP: number;
  allocatedAmountEGP: number;
  remainingAmountEGP: number;
}

export interface TransactionDetailDto {
  id: string;
  institution: string;
  institutionType?: string;
  student: string;
  feeType: string;
  amountEGP: number;
  method: string;
  status: "Successful" | "Pending" | "Failed" | string;
  bankRef?: string;
  settlementStatus?: string;
  reconStatus?: string;
  timestamp: string;
  partial?: PartialPaymentDto;
  idempotencyKey?: string;
  channel?: string;
  dueDate?: string;
  priority?: string;
  daysToDue?: number;
  outstandingEGP?: number;
  penaltyEGP?: number;
  penaltyAppliedAt?: string;
  graceEnded?: boolean;
  totalDueEGP?: number;
  timeline?: TimelineEventDto[];
  allocations?: AllocatedDueDetailDto[];
}

export interface TransactionTabCountsDto {
  all?: number;
  successful?: number;
  pending?: number;
  failed?: number;
  All?: number;
  Successful?: number;
  Pending?: number;
  Failed?: number;
}

export interface CustomerDto {
  name?: string;
  fullName?: string;
  nationalId?: string;
  nationalIdMasked?: string;
  institution?: string;
  institutionType?: string;
  grade?: string;
  mobileNumber?: string;
  cibCustomer?: boolean;
}

export interface CustomerFeeItemDto {
  id: string;
  name: string;
  originalAmountEGP: number;
  paidEGP: number;
  remainingEGP: number;
  status: string;
  eligible: boolean;
  dueDate?: string;
  priority?: string;
  daysToDue?: number;
  penaltyEGP?: number;
  totalDueEGP?: number;
}

export interface CustomerFeesResponse {
  customer: CustomerDto;
  fees: CustomerFeeItemDto[];
}

export interface EppSummaryDto {
  planId?: string;
  tenorMonths?: number;
  monthlyInstallmentEGP?: number;
  interestRatePct?: number;
  tenor?: number;
  monthlyEGP?: number;
}

export interface BankAccountDto {
  account_id: string;
  account_number: string;
  type: string;
  currency: string;
  available_balance: number;
  status: string;
}

export interface BankCardDto {
  card_id: string;
  masked_number: string;
  scheme: string;
  type: string;
  holder_name: string;
  expiry: string;
  status: string;
  credit_limit?: number;
  available_limit?: number;
  linked_account_id?: string;
}

export interface BankCustomerLookupResponse {
  customer_id: string;
  national_id: string;
  full_name_en: string;
  full_name_ar: string;
  mobile: string;
  status: string;
  accounts: BankAccountDto[];
  cards: BankCardDto[];
}

export interface BackOfficePaymentRequest {
  nationalId: string;
  feeIds: string[];
  amountEGP: number;
  method: "ACCOUNT_DEBIT" | "CARD" | string;
  sourceId?: string;
  creditPaymentType?: "full" | "epp";
  eppTenor?: number;
  cardToken?: string;
  processedBy?: string;
}

export interface BackOfficePaymentResponse {
  transactionId: string;
  status: string;
  amountPaidEGP: number;
  isPartial: boolean;
  remainingBalanceEGP: number;
  method: string;
  bankRef?: string;
  authCode?: string;
  receiptRef?: string;
  epp?: EppSummaryDto;
  originalFeeEGP?: number;
  penaltyEGP?: number;
  totalCollectedEGP?: number;
  penaltyAppliedAt?: string;
}
