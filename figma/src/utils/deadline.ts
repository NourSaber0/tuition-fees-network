export type Priority = 'PAID' | 'LOW' | 'MEDIUM' | 'HIGH' | 'URGENT' | 'OVERDUE'

// Fixed reference date for prototype (today = 7 Sep 2026)
const TODAY = new Date('2026-09-07T00:00:00')

export function daysFromToday(dueDate: string): number {
  const due = new Date(dueDate + 'T00:00:00')
  return Math.round((due.getTime() - TODAY.getTime()) / 86400000)
}

/**
 * Priority tiers:
 *   > 30 days   → LOW
 *  15–30 days   → MEDIUM
 *   7–14 days   → HIGH
 *   0–6  days   → URGENT  (includes due-today = 0)
 *   1–7  overdue → OVERDUE (penalty applies)
 *   > 7  overdue → OVERDUE / grace ended (same penalty, no escalation)
 */
export function calcPriority(dueDate: string, outstanding: number): Priority {
  if (outstanding <= 0) return 'PAID'
  const d = daysFromToday(dueDate)
  if (d > 30)  return 'LOW'
  if (d >= 15) return 'MEDIUM'
  if (d >= 7)  return 'HIGH'
  if (d >= 0)  return 'URGENT'
  return 'OVERDUE'
}

/**
 * 5% penalty applied once when the fee is overdue.
 * Penalty does NOT compound — it is calculated once from the
 * outstanding amount at the time the deadline passed.
 * Zero if paid or not yet overdue.
 */
export function calcPenalty(outstanding: number, dueDate: string): number {
  if (outstanding <= 0) return 0
  if (daysFromToday(dueDate) >= 0) return 0
  return Math.round(outstanding * 0.05)
}

/** Total amount owed = outstanding + penalty. */
export function calcTotalDue(outstanding: number, dueDate: string): number {
  return outstanding + calcPenalty(outstanding, dueDate)
}

/**
 * After 7 days overdue the penalty does NOT increase, but the
 * status is labelled "Grace Period Ended" to indicate the
 * one-week late window has closed.
 */
export function graceEnded(dueDate: string): boolean {
  return daysFromToday(dueDate) < -7
}

export function dueDateLabel(dueDate: string): string {
  const d = daysFromToday(dueDate)
  if (d === 0) return 'Due Today'
  if (d > 0)   return `Due in ${d} day${d !== 1 ? 's' : ''}`
  const over = Math.abs(d)
  return `${over} day${over !== 1 ? 's' : ''} overdue`
}

export const priorityBadge: Record<Priority, string> = {
  PAID:    'bg-green-50  text-green-700  border-green-200',
  LOW:     'bg-gray-100  text-gray-500   border-gray-200',
  MEDIUM:  'bg-sky-50    text-sky-700    border-sky-200',
  HIGH:    'bg-amber-50  text-amber-700  border-amber-200',
  URGENT:  'bg-orange-50 text-orange-700 border-orange-200',
  OVERDUE: 'bg-red-50    text-red-700    border-red-200',
}

export function formatDueDate(dueDate: string): string {
  const [y, m, d] = dueDate.split('-').map(Number)
  const months = ['Jan','Feb','Mar','Apr','May','Jun','Jul','Aug','Sep','Oct','Nov','Dec']
  return `${d} ${months[m - 1]} ${y}`
}
