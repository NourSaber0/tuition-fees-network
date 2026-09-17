export const ROLE_COLOR: Record<string, string> = {
  "Bank Admin": "bg-[#003087]/10 text-[#003087] border-[#003087]/20",
  Operations: "bg-indigo-50 text-indigo-700 border-indigo-200",
  Finance: "bg-amber-50 text-amber-700 border-amber-200",
  Reconciliation: "bg-teal-50 text-teal-700 border-teal-200",
};

export function roleColorClass(role: string): string {
  return ROLE_COLOR[role] ?? "bg-gray-100 text-gray-600 border-gray-200";
}

export const STATUS_STYLE: Record<string, string> = {
  Active: "bg-green-50 text-green-700 border-green-200",
  Inactive: "bg-gray-100 text-gray-500 border-gray-200",
};

export function statusClass(status: string): string {
  return STATUS_STYLE[status] ?? "bg-gray-100 text-gray-500 border-gray-200";
}

/** Mirrors the backend's BankRole enum (roleId <-> displayName) - a small, fixed, real domain. */
export const ROLE_ID_TO_DISPLAY: Record<string, string> = {
  "bank-admin": "Bank Admin",
  "bank-operations": "Operations",
  "bank-finance": "Finance",
  "bank-reconciliation": "Reconciliation",
};

/** Mirrors the bank sidebar's real nav items (bank/layout.tsx) - permission segment id -> label. */
export const SEGMENT_LABEL: Record<string, string> = {
  dashboard: "Dashboard",
  schools: "Institution Management",
  transactions: "Transactions",
  reconciliation: "Reconciliation",
  epp: "EPP Plans",
  reports: "Reports",
  notifications: "Notifications",
  "audit-logs": "Audit Logs",
  users: "Users & Roles",
  settings: "System Settings",
};

export function segmentLabel(segment: string): string {
  return SEGMENT_LABEL[segment] ?? segment;
}

export function initials(name: string): string {
  return name
    .trim()
    .split(/\s+/)
    .map((n) => n[0])
    .join("")
    .slice(0, 2)
    .toUpperCase();
}

export function formatDateTime(iso: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return (
    d.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" }) +
    " " +
    d.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })
  );
}
