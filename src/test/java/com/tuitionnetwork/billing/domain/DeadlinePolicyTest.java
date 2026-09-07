package com.tuitionnetwork.billing.domain;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Ported from the frontend's deadline.test.ts (Phase 12 spec &sect;11-19) - same business
 * rules, same boundary cases, re-verified server-side since DeadlinePolicy is a 1:1 port
 * of deadline.ts rather than a shared library.
 */
class DeadlinePolicyTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 9, 7);

    @Test
    void priority_isPaid_whenOutstandingIsZeroOrLess_evenIfDueDateIsPast() {
        assertEquals(FeePriority.PAID, DeadlinePolicy.priority(TODAY.minusDays(30), BigDecimal.ZERO, TODAY));
        assertEquals(FeePriority.PAID, DeadlinePolicy.priority(TODAY.minusDays(30), new BigDecimal("-5.00"), TODAY));
    }

    @Test
    void priority_isLow_whenMoreThanThirtyDaysOut() {
        assertEquals(FeePriority.LOW, DeadlinePolicy.priority(TODAY.plusDays(31), new BigDecimal("1000"), TODAY));
    }

    @Test
    void priority_isMedium_atFifteenToThirtyDaysBoundaries() {
        assertEquals(FeePriority.MEDIUM, DeadlinePolicy.priority(TODAY.plusDays(15), new BigDecimal("1000"), TODAY));
        assertEquals(FeePriority.MEDIUM, DeadlinePolicy.priority(TODAY.plusDays(30), new BigDecimal("1000"), TODAY));
    }

    @Test
    void priority_isHigh_atSevenToFourteenDaysBoundaries() {
        assertEquals(FeePriority.HIGH, DeadlinePolicy.priority(TODAY.plusDays(7), new BigDecimal("1000"), TODAY));
        assertEquals(FeePriority.HIGH, DeadlinePolicy.priority(TODAY.plusDays(14), new BigDecimal("1000"), TODAY));
    }

    @Test
    void priority_isUrgent_atZeroToSixDaysBoundariesIncludingToday() {
        assertEquals(FeePriority.URGENT, DeadlinePolicy.priority(TODAY, new BigDecimal("1000"), TODAY));
        assertEquals(FeePriority.URGENT, DeadlinePolicy.priority(TODAY.plusDays(6), new BigDecimal("1000"), TODAY));
    }

    @Test
    void priority_isOverdue_whenDueDateHasPassed() {
        assertEquals(FeePriority.OVERDUE, DeadlinePolicy.priority(TODAY.minusDays(1), new BigDecimal("1000"), TODAY));
    }

    @Test
    void penalty_isZero_whenOutstandingIsZeroOrLess() {
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(
                DeadlinePolicy.computePenalty(BigDecimal.ZERO, TODAY.minusDays(10), TODAY)));
    }

    @Test
    void penalty_isZero_whenNotYetOverdue() {
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(
                DeadlinePolicy.computePenalty(new BigDecimal("1000"), TODAY, TODAY)));
        assertEquals(0, BigDecimal.ZERO.setScale(2).compareTo(
                DeadlinePolicy.computePenalty(new BigDecimal("1000"), TODAY.plusDays(5), TODAY)));
    }

    @Test
    void penalty_isFivePercentOfOutstanding_whenOverdue() {
        BigDecimal penalty = DeadlinePolicy.computePenalty(new BigDecimal("8000"), TODAY.minusDays(1), TODAY);
        assertEquals(0, new BigDecimal("400.00").compareTo(penalty));
    }

    @Test
    void penalty_isBasedOnRemainingBalance_notOriginalFeeAmount() {
        // A 10,000 EGP fee already partially paid down to 2,000 EGP outstanding -> 5% of 2,000, not 10,000
        BigDecimal penalty = DeadlinePolicy.computePenalty(new BigDecimal("2000"), TODAY.minusDays(3), TODAY);
        assertEquals(0, new BigDecimal("100.00").compareTo(penalty));
    }

    @Test
    void gracePeriodEndsAt_isSevenDaysAfterDueDate() {
        assertEquals(TODAY.plusDays(7), DeadlinePolicy.gracePeriodEndsAt(TODAY));
    }

    @Test
    void graceEnded_isFalseAtExactlySevenDaysOverdue_trueAfter() {
        assertFalse(DeadlinePolicy.graceEnded(TODAY.minusDays(7), TODAY));
        assertTrue(DeadlinePolicy.graceEnded(TODAY.minusDays(8), TODAY));
    }
}
