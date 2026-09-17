/**
 * Unit tests for the Payment Deadline / Priority / Late Penalty business logic.
 *
 * Reference date in the utility module: 7 Sep 2026.
 * All "daysFromToday" values below are relative to that fixed prototype date.
 *
 * Tests map to spec section 16 requirements 1–12.
 */

import { describe, it, expect } from 'vitest'
import {
  calcPriority,
  calcPenalty,
  calcTotalDue,
  graceEnded,
  dueDateLabel,
} from './deadline'

// ── helpers ─────────────────────────────────────────────────────────────────
// Derive a dueDate string that is N days from the prototype's TODAY (7 Sep 2026)
function daysAhead(n: number): string {
  const d = new Date('2026-09-07T00:00:00')
  d.setDate(d.getDate() + n)
  return d.toISOString().slice(0, 10) // YYYY-MM-DD
}

const OUTSTANDING = 10_000   // generic non-zero balance for most tests

// ── Section 16 tests ─────────────────────────────────────────────────────────

describe('calcPriority', () => {
  // Spec test 1: 30+ days before deadline → LOW
  it('1 — payment 31 days before deadline → LOW', () => {
    expect(calcPriority(daysAhead(31), OUTSTANDING)).toBe('LOW')
  })

  // Spec test 2: 20 days before deadline → MEDIUM
  it('2 — payment 20 days before deadline → MEDIUM', () => {
    expect(calcPriority(daysAhead(20), OUTSTANDING)).toBe('MEDIUM')
  })

  // Boundary: 15 days exactly is MEDIUM
  it('boundary — 15 days → MEDIUM', () => {
    expect(calcPriority(daysAhead(15), OUTSTANDING)).toBe('MEDIUM')
  })

  // Spec test 3: 10 days before deadline → HIGH
  it('3 — payment 10 days before deadline → HIGH', () => {
    expect(calcPriority(daysAhead(10), OUTSTANDING)).toBe('HIGH')
  })

  // Boundary: 7 days exactly is HIGH
  it('boundary — 7 days → HIGH', () => {
    expect(calcPriority(daysAhead(7), OUTSTANDING)).toBe('HIGH')
  })

  // Spec test 4: 3 days before deadline → URGENT
  it('4 — payment 3 days before deadline → URGENT', () => {
    expect(calcPriority(daysAhead(3), OUTSTANDING)).toBe('URGENT')
  })

  // Spec test 5: payment due today → URGENT
  it('5 — payment due today (0 days) → URGENT', () => {
    expect(calcPriority(daysAhead(0), OUTSTANDING)).toBe('URGENT')
  })

  // 1 day remaining → URGENT (boundary below HIGH)
  it('boundary — 1 day → URGENT', () => {
    expect(calcPriority(daysAhead(1), OUTSTANDING)).toBe('URGENT')
  })

  // 6 days remaining → URGENT (top of URGENT range)
  it('boundary — 6 days → URGENT', () => {
    expect(calcPriority(daysAhead(6), OUTSTANDING)).toBe('URGENT')
  })

  // Spec test 6: 1 day overdue → OVERDUE
  it('6 — payment 1 day overdue → OVERDUE', () => {
    expect(calcPriority(daysAhead(-1), OUTSTANDING)).toBe('OVERDUE')
  })

  // Spec test 7: 7 days overdue → OVERDUE
  it('7 — payment 7 days overdue → OVERDUE', () => {
    expect(calcPriority(daysAhead(-7), OUTSTANDING)).toBe('OVERDUE')
  })

  // Spec test 8: 8 days overdue → still OVERDUE (grace period ended, no escalation)
  it('8 — payment 8 days overdue → OVERDUE (grace period ended, same priority)', () => {
    expect(calcPriority(daysAhead(-8), OUTSTANDING)).toBe('OVERDUE')
  })

  // Spec test 9: fully paid → no penalty applicable; priority = PAID
  it('9 — fully paid fee (outstanding = 0) → PAID', () => {
    expect(calcPriority(daysAhead(-5), 0)).toBe('PAID')
  })

  // Edge: zero outstanding on a not-yet-due fee → PAID
  it('edge — zero outstanding, future due date → PAID', () => {
    expect(calcPriority(daysAhead(30), 0)).toBe('PAID')
  })
})

describe('calcPenalty', () => {
  // Spec test 6: 1 day overdue with outstanding → 5% penalty
  it('6 — 1 day overdue → 5% penalty on outstanding', () => {
    expect(calcPenalty(8_000, daysAhead(-1))).toBe(400)
  })

  // Spec test 7: 7 days overdue → same 5% penalty (no compounding)
  it('7 — 7 days overdue → still 5%', () => {
    expect(calcPenalty(8_000, daysAhead(-7))).toBe(400)
  })

  // Spec test 8: 8 days overdue → penalty remains 5% (grace ended, no escalation)
  it('8 — 8 days overdue → penalty still 5%', () => {
    expect(calcPenalty(8_000, daysAhead(-8))).toBe(400)
  })

  // Spec test 9: fully paid → no penalty
  it('9 — fully paid (outstanding = 0) → no penalty', () => {
    expect(calcPenalty(0, daysAhead(-3))).toBe(0)
  })

  // Spec test 10: partial payment → penalty from remaining amount only
  it('10 — partial payment: penalty calculated on remaining, not original', () => {
    const originalFee    = 20_000
    const alreadyPaid    = 5_000
    const remaining      = originalFee - alreadyPaid  // 15,000
    const expectedPenalty = Math.round(remaining * 0.05)  // 750
    expect(calcPenalty(remaining, daysAhead(-1))).toBe(expectedPenalty)
  })

  // Spec test 11: re-running penalty calculation → idempotent (same result)
  it('11 — re-running penalty calculation returns the same value (no duplication)', () => {
    const p1 = calcPenalty(8_000, daysAhead(-3))
    const p2 = calcPenalty(8_000, daysAhead(-3))
    expect(p1).toBe(p2)
    expect(p1).toBe(400)
  })

  // No penalty if not yet overdue
  it('no penalty for future due dates', () => {
    expect(calcPenalty(10_000, daysAhead(1))).toBe(0)
    expect(calcPenalty(10_000, daysAhead(0))).toBe(0)
  })

  // Example from spec section 3: fee=10000, paid=2000, outstanding=8000
  it('spec example — outstanding 8,000 EGP → penalty 400 EGP', () => {
    expect(calcPenalty(8_000, daysAhead(-1))).toBe(400)
  })

  // Example from spec section 5: fee=20000, paid=5000, outstanding=15000
  it('spec example — partial: outstanding 15,000 EGP → penalty 750 EGP', () => {
    expect(calcPenalty(15_000, daysAhead(-1))).toBe(750)
  })
})

describe('calcTotalDue', () => {
  // Spec test 12: payment made after penalty applied → correct balances
  it('12 — total due = outstanding + penalty when overdue', () => {
    const outstanding = 8_000
    const expected    = outstanding + Math.round(outstanding * 0.05)  // 8400
    expect(calcTotalDue(outstanding, daysAhead(-1))).toBe(expected)
  })

  it('total due = outstanding only when not overdue', () => {
    expect(calcTotalDue(8_000, daysAhead(5))).toBe(8_000)
  })

  it('total due = 0 when fully paid', () => {
    expect(calcTotalDue(0, daysAhead(-5))).toBe(0)
  })
})

describe('graceEnded', () => {
  it('7 days overdue → grace still active', () => {
    expect(graceEnded(daysAhead(-7))).toBe(false)
  })

  it('8 days overdue → grace period ended', () => {
    expect(graceEnded(daysAhead(-8))).toBe(true)
  })

  it('not overdue → grace has not started', () => {
    expect(graceEnded(daysAhead(1))).toBe(false)
  })
})

describe('dueDateLabel', () => {
  it('returns "Due Today" for today', () => {
    expect(dueDateLabel(daysAhead(0))).toBe('Due Today')
  })

  it('returns "Due in 1 day" for 1 day ahead', () => {
    expect(dueDateLabel(daysAhead(1))).toBe('Due in 1 day')
  })

  it('returns "Due in 5 days" for 5 days ahead', () => {
    expect(dueDateLabel(daysAhead(5))).toBe('Due in 5 days')
  })

  it('returns "1 day overdue" for 1 day past', () => {
    expect(dueDateLabel(daysAhead(-1))).toBe('1 day overdue')
  })

  it('returns "7 days overdue" for 7 days past', () => {
    expect(dueDateLabel(daysAhead(-7))).toBe('7 days overdue')
  })
})
