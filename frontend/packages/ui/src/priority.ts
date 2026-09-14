/**
 * Presentational helpers only - the actual priority/penalty/daysToDue values
 * always come from the backend (FeeDeadlineService via DeadlineQueueItemDto
 * and friends). Never recompute those business rules client-side; this file
 * just turns already-computed values into badge classes and labels.
 */
export type Priority = "PAID" | "LOW" | "MEDIUM" | "HIGH" | "URGENT" | "OVERDUE";

export const PRIORITY_BADGE_CLASSES: Record<string, string> = {
  PAID: "bg-green-50 text-green-700 border-green-200",
  LOW: "bg-gray-100 text-gray-500 border-gray-200",
  MEDIUM: "bg-sky-50 text-sky-700 border-sky-200",
  HIGH: "bg-amber-50 text-amber-700 border-amber-200",
  URGENT: "bg-orange-50 text-orange-700 border-orange-200",
  OVERDUE: "bg-red-50 text-red-700 border-red-200",
};

/** daysToDue: negative = overdue, 0 = due today, positive = days remaining. */
export function dueDateLabel(daysToDue: number): string {
  if (daysToDue === 0) return "Due Today";
  if (daysToDue > 0) return `Due in ${daysToDue} day${daysToDue !== 1 ? "s" : ""}`;
  const over = Math.abs(daysToDue);
  return `${over} day${over !== 1 ? "s" : ""} overdue`;
}

export function formatIsoDate(isoDate: string): string {
  const [y, m, d] = isoDate.split("-").map(Number);
  const months = ["Jan", "Feb", "Mar", "Apr", "May", "Jun", "Jul", "Aug", "Sep", "Oct", "Nov", "Dec"];
  return `${d} ${months[(m ?? 1) - 1]} ${y}`;
}
