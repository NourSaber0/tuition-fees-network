// Mirrors com.tuitionnetwork.identity.dto.Institution* and com.tuitionnetwork.ingestion.dto.FeeSubmission*
// exactly - see InstitutionManagementController.java and InstitutionFeeSubmissionsController.java.

export type InstitutionType = "SCHOOL" | "UNIVERSITY";
export type RegistrationStatus = "PENDING" | "UNDER_REVIEW" | "APPROVED" | "REJECTED";
export type AccountStatus = "ACTIVE" | "INACTIVE" | "SUSPENDED";
export type IntegrationStatus = "NOT_INTEGRATED" | "PENDING" | "INTEGRATED" | "FAILED";

export interface InstitutionSummaryDto {
  id: string;
  name: string;
  code: string;
  city: string;
  institutionType: InstitutionType;
  subType: string;
  registrationNumber: string;
  studentCount: number;
  registrationStatus: RegistrationStatus;
  accountStatus: AccountStatus;
  integrationStatus: IntegrationStatus;
  registeredAt: string;
}

export interface InstitutionDetailDto {
  id: string;
  name: string;
  code: string;
  institutionType: InstitutionType;
  subType: string;
  city: string;
  principalName: string;
  phone: string;
  email: string;
  registrationNumber: string;
  studentCount: number;
  registrationStatus: RegistrationStatus;
  accountStatus: AccountStatus;
  integrationStatus: IntegrationStatus;
  feeAbsorptionPolicy: string | null;
  rejectionReason: string | null;
  registeredAt: string;
}

export interface RegisterInstitutionRequest {
  name: string;
  institutionType: InstitutionType;
  subType: string;
  city: string;
  principalName: string;
  phone: string;
  email: string;
  registrationNumber: string;
  studentCount: number;
  code?: string;
  feeAbsorptionPolicy?: string;
}

/** status: "Paid" | "Partial" | "Unpaid" */
export interface InstitutionStudentDto {
  studentId: string;
  fullName: string;
  dateOfBirth: string;
  totalFeesEGP: number;
  paidEGP: number;
  outstandingEGP: number;
  status: string;
}

export interface InstitutionApplicationDto {
  id: string;
  name: string;
  registrationNumber: string;
  institutionType: InstitutionType;
  subType: string;
  city: string;
  principalName: string;
  phone: string;
  email: string;
  studentCount: number;
  registeredAt: string;
  registrationStatus: RegistrationStatus;
  rejectionReason: string | null;
  requiredDocuments: string[];
  documentsTracked: boolean;
}

export interface InstitutionIntegrationDto {
  institutionId: string;
  status: IntegrationStatus;
  message: string;
  configured: boolean;
}

/** status: "PROCESSED" (no failed rows) | "PARTIAL" | "REJECTED" (all rows failed) */
export interface FeeSubmissionSummaryDto {
  submissionId: string;
  fileName: string;
  totalRows: number;
  successfulRows: number;
  failedRows: number;
  status: string;
  uploadedAt: string;
}

export interface FeeSubmissionRowErrorDto {
  rowNumber: number;
  errorMessage: string;
  rawRow: string;
}

export interface FeeSubmissionDetailDto {
  submissionId: string;
  institutionId: string;
  fileName: string;
  totalRows: number;
  successfulRows: number;
  failedRows: number;
  status: string;
  uploadedAt: string;
  errors: FeeSubmissionRowErrorDto[];
}

export interface InstitutionSettlementDto {
  id: string;
  date: string;
  grossEGP: number;
  cibFeeEGP: number;
  netEGP: number;
  status: string;
  txRef: string;
  reconRef: string;
}

export interface InstitutionSettlementSummaryDto {
  totalSettledEGP: number;
  recordCount: number;
  lastSettlementDate: string;
}

export interface InstitutionSettlementsResponse {
  summary: InstitutionSettlementSummaryDto;
  data: InstitutionSettlementDto[];
}
