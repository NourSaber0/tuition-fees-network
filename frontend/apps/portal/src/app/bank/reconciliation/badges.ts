export const EXC_STATUS_STYLE: Record<string, string> = {
  Open: "bg-red-50 text-red-700 border-red-200",
  "Under Investigation": "bg-amber-50 text-amber-700 border-amber-200",
  Resolved: "bg-green-50 text-green-700 border-green-200",
  Escalated: "bg-purple-50 text-purple-700 border-purple-200",
};

export function excStatusClass(status: string): string {
  return EXC_STATUS_STYLE[status] ?? "bg-gray-100 text-gray-600 border-gray-200";
}

export const PRIORITY_STYLE: Record<string, string> = {
  High: "bg-red-100 text-red-700",
  Medium: "bg-amber-100 text-amber-700",
  Low: "bg-gray-100 text-gray-500",
};

export function priorityClass(priority: string | null): string {
  if (!priority) return "bg-gray-100 text-gray-500";
  return PRIORITY_STYLE[priority] ?? "bg-gray-100 text-gray-500";
}

/** Run status is a free-text field from the backend (e.g. "Matched", "Exceptions Found") - classify by keyword rather than an exact enum. */
export function runStatusClass(status: string): string {
  const s = status.toLowerCase();
  if (s.includes("exception")) return "bg-red-50 text-red-700 border-red-200";
  if (s.includes("pending")) return "bg-amber-50 text-amber-700 border-amber-200";
  if (s.includes("match")) return "bg-green-50 text-green-700 border-green-200";
  return "bg-gray-100 text-gray-600 border-gray-200";
}

export function isExceptionRun(status: string): boolean {
  return status.toLowerCase().includes("exception");
}

/** institutionType is free text ("International School", "University", ...) - only "University" gets the distinct pill. */
export function institutionTypePill(institutionType: string | null): string {
  if (institutionType && institutionType.toLowerCase().includes("university")) {
    return "bg-[#FEF3E6] text-[#C96B10]";
  }
  return "bg-[#EBF1FB] text-[#003087]";
}

export function money(amount: number | null | undefined): string {
  if (amount == null || isNaN(amount)) return "0";
  return Math.round(amount).toLocaleString("en-US");
}

export function formatDateTime(iso: string | null): string {
  if (!iso) return "—";
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return (
    d.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" }) +
    " · " +
    d.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit" })
  );
}

export function runRef(id: string): string {
  return "RUN-" + id.slice(0, 8).toUpperCase();
}

export function excRef(id: string): string {
  return "EXC-" + id.slice(0, 8).toUpperCase();
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
