export const TYPE_LABEL: Record<string, string> = {
  FAILED_PAYMENT: "Failed Payment",
  RECON_EXCEPTION: "Reconciliation",
  INSTITUTION_ISSUE: "Institution Issue",
  NEW_INSTITUTION: "New Institution",
  SYSTEM_ALERT: "System",
};

export const TYPE_ICON_STYLE: Record<string, { bg: string; iconColor: string }> = {
  FAILED_PAYMENT: { bg: "bg-red-50", iconColor: "text-red-500" },
  RECON_EXCEPTION: { bg: "bg-orange-50", iconColor: "text-orange-500" },
  INSTITUTION_ISSUE: { bg: "bg-amber-50", iconColor: "text-amber-600" },
  NEW_INSTITUTION: { bg: "bg-blue-50", iconColor: "text-blue-600" },
  SYSTEM_ALERT: { bg: "bg-purple-50", iconColor: "text-purple-600" },
};

export const TYPE_LABEL_STYLE: Record<string, string> = {
  FAILED_PAYMENT: "bg-red-50 text-red-600",
  RECON_EXCEPTION: "bg-orange-50 text-orange-600",
  INSTITUTION_ISSUE: "bg-amber-50 text-amber-600",
  NEW_INSTITUTION: "bg-blue-50 text-blue-600",
  SYSTEM_ALERT: "bg-gray-100 text-gray-500",
};

export const SEVERITY_STYLE: Record<string, string> = {
  HIGH: "bg-red-50 text-red-700 border-red-200",
  MEDIUM: "bg-amber-50 text-amber-700 border-amber-200",
  LOW: "bg-gray-100 text-gray-500 border-gray-200",
};

export function typeLabel(type: string): string {
  return TYPE_LABEL[type] ?? type;
}

export function typeIconStyle(type: string): { bg: string; iconColor: string } {
  return TYPE_ICON_STYLE[type] ?? { bg: "bg-gray-100", iconColor: "text-gray-500" };
}

export function typeLabelClass(type: string): string {
  return TYPE_LABEL_STYLE[type] ?? "bg-gray-100 text-gray-500";
}

export function severityClass(severity: string): string {
  return SEVERITY_STYLE[severity.toUpperCase()] ?? "bg-gray-100 text-gray-500 border-gray-200";
}

export function formatTimestamp(iso: string): string {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  const now = new Date();
  const isToday = d.toDateString() === now.toDateString();
  const yesterday = new Date(now);
  yesterday.setDate(now.getDate() - 1);
  const isYesterday = d.toDateString() === yesterday.toDateString();
  const time = d.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" });
  if (isToday) return `${time} today`;
  if (isYesterday) return `Yesterday ${time}`;
  return d.toLocaleDateString("en-GB", { day: "numeric", month: "short" }) + " " + time;
}
