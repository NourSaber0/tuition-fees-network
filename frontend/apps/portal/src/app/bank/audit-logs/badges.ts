export const SEVERITY_STYLE: Record<string, string> = {
  info: "bg-blue-50 text-blue-700 border-blue-200",
  warning: "bg-amber-50 text-amber-700 border-amber-200",
  critical: "bg-red-50 text-red-700 border-red-200",
};

export const SEVERITY_DOT: Record<string, string> = {
  info: "bg-blue-400",
  warning: "bg-amber-400",
  critical: "bg-red-500",
};

export function severityClass(severity: string): string {
  return SEVERITY_STYLE[severity.toLowerCase()] ?? "bg-gray-100 text-gray-500 border-gray-200";
}

export function severityDot(severity: string): string {
  return SEVERITY_DOT[severity.toLowerCase()] ?? "bg-gray-400";
}

/** Formats a real actor_type string (e.g. "BACK_OFFICE", "SYSTEM") into title case for display. */
export function roleLabel(role: string): string {
  if (!role) return "—";
  return role
    .split("_")
    .map((w) => w.charAt(0).toUpperCase() + w.slice(1).toLowerCase())
    .join(" ");
}

export function formatTimestamp(iso: string): string {
  const d = new Date(iso);
  if (isNaN(d.getTime())) return iso;
  return (
    d.toLocaleDateString("en-GB", { day: "numeric", month: "short", year: "numeric" }) +
    " " +
    d.toLocaleTimeString("en-GB", { hour: "2-digit", minute: "2-digit", second: "2-digit" })
  );
}

/** Local-date formatting - toISOString() converts to UTC and can roll the date back a day in positive-offset timezones. */
export function toLocalIso(d: Date): string {
  const y = d.getFullYear();
  const m = String(d.getMonth() + 1).padStart(2, "0");
  const day = String(d.getDate()).padStart(2, "0");
  return `${y}-${m}-${day}`;
}
