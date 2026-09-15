export const STATUS_STYLE: Record<string, string> = {
  Active: "bg-green-50 text-green-700 border-green-200",
  Completed: "bg-blue-50 text-blue-700 border-blue-200",
  Defaulted: "bg-red-50 text-red-700 border-red-200",
  Cancelled: "bg-gray-100 text-gray-500 border-gray-200",
};

export function statusClass(status: string): string {
  return STATUS_STYLE[status] ?? "bg-gray-100 text-gray-600 border-gray-200";
}

/** institutionType is free text - only "University" gets the distinct pill. */
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

export function planRef(id: string): string {
  return "EPP-" + id.slice(0, 8).toUpperCase();
}

const CREATE_ERROR_MESSAGES: Record<string, string> = {
  source_payment_not_successful: "That payment isn't in a successful state, so it can't be converted to an EPP plan.",
  card_not_eligible: "This card isn't eligible for EPP - only CIB credit cards can be used.",
  epp_plan_already_exists_for_payment: "This payment already has an EPP plan. Choose a different payment.",
  principal_out_of_range: "Principal must be between EGP 5,000 and EGP 100,000.",
  max_plans_per_student_exceeded: "This student already has the maximum of 2 EPP plans.",
};

/** Maps the backend's raw ResponseStatusException reason codes to a readable message. */
export function friendlyCreateError(message: string): string {
  return CREATE_ERROR_MESSAGES[message.trim()] ?? message;
}
