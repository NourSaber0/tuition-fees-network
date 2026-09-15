export const CATEGORY_COLORS: Record<string, string> = {
  Collections: "bg-[#003087]/10 text-[#003087]",
  Payments: "bg-green-50 text-green-700",
  EPP: "bg-purple-50 text-purple-700",
  Reconciliation: "bg-amber-50 text-amber-700",
  Daily: "bg-gray-100 text-gray-600",
  Fees: "bg-sky-50 text-sky-700",
};

export function categoryClass(category: string): string {
  return CATEGORY_COLORS[category] ?? "bg-gray-100 text-gray-600";
}

/** Preview status cells are real, free-text backend values (e.g. reconciliation run statuses) - classify by keyword. */
export function previewStatusClass(value: string): string {
  const v = value.toLowerCase();
  if (v.includes("except") || v === "failed" || v === "defaulted") return "text-red-600 font-semibold";
  if (v === "pending" || v === "refunded") return "text-amber-600 font-semibold";
  if (v.includes("match") || v === "successful" || v === "settled" || v === "active" || v === "completed") {
    return "text-green-700 font-semibold";
  }
  return "text-gray-600";
}

const GENERATE_ERROR_MESSAGES: Record<string, string> = {
  unsupported_format_for_report: "Only CSV export is currently supported for this report.",
  date_from_after_date_to: "The start date must be before the end date.",
};

export function friendlyGenerateError(message: string): string {
  return GENERATE_ERROR_MESSAGES[message.trim()] ?? message;
}
