import type { AccountStatus, IntegrationStatus, InstitutionType, RegistrationStatus } from "./types";

export const REG_STATUS_LABEL: Record<RegistrationStatus, string> = {
  PENDING: "Pending",
  UNDER_REVIEW: "Under Review",
  APPROVED: "Approved",
  REJECTED: "Rejected",
};
export const REG_STATUS_STYLE: Record<RegistrationStatus, string> = {
  PENDING: "bg-amber-50 text-amber-700 border-amber-200",
  UNDER_REVIEW: "bg-blue-50 text-blue-700 border-blue-200",
  APPROVED: "bg-green-50 text-green-700 border-green-200",
  REJECTED: "bg-red-50 text-red-700 border-red-200",
};

export const ACCOUNT_STATUS_LABEL: Record<AccountStatus, string> = {
  ACTIVE: "Active",
  INACTIVE: "Inactive",
  SUSPENDED: "Suspended",
};
export const ACCOUNT_STATUS_STYLE: Record<AccountStatus, string> = {
  ACTIVE: "bg-green-50 text-green-700 border-green-200",
  INACTIVE: "bg-gray-100 text-gray-500 border-gray-200",
  SUSPENDED: "bg-red-50 text-red-700 border-red-200",
};

export const INTEGRATION_STATUS_LABEL: Record<IntegrationStatus, string> = {
  NOT_INTEGRATED: "Not Integrated",
  PENDING: "Pending",
  INTEGRATED: "Integrated",
  FAILED: "Failed",
};
export const INTEGRATION_STATUS_STYLE: Record<IntegrationStatus, string> = {
  NOT_INTEGRATED: "bg-gray-100 text-gray-500 border-gray-200",
  PENDING: "bg-amber-50 text-amber-700 border-amber-200",
  INTEGRATED: "bg-green-50 text-green-700 border-green-200",
  FAILED: "bg-red-50 text-red-700 border-red-200",
};

export const INSTITUTION_TYPE_LABEL: Record<InstitutionType, string> = {
  SCHOOL: "School",
  UNIVERSITY: "University",
};
export const INSTITUTION_TYPE_PILL: Record<InstitutionType, string> = {
  SCHOOL: "bg-[var(--cib-blue-light)] text-[var(--cib-blue)]",
  UNIVERSITY: "bg-[var(--cib-orange-light)] text-[var(--cib-orange-dark)]",
};

export function money(n: number): string {
  return "EGP " + Math.round(n).toLocaleString();
}

export function formatDate(iso: string | null): string {
  if (!iso) return "-";
  const [y, m, d] = iso.slice(0, 10).split("-").map(Number);
  const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  return `${d} ${months[(m ?? 1) - 1]} ${y}`;
}
