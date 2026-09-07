package com.tuitionnetwork.billing.domain;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

/**
 * Server-side port of the frontend's deadline.ts (spec Phase 12 &sect;11-19).
 * Pure functions only - no persistence, no idempotency guard. Callers that need the
 * "never charge the 5% twice" guarantee must consult FeeLine.penaltyAppliedAt themselves
 * (see FeeDeadlineService) rather than re-deriving from this class.
 */
public final class DeadlinePolicy {

    private static final BigDecimal PENALTY_RATE = new BigDecimal("0.05");
    private static final int GRACE_PERIOD_DAYS = 7;

    private DeadlinePolicy() {
    }

    public static long daysToDue(LocalDate dueDate, LocalDate today) {
        return ChronoUnit.DAYS.between(today, dueDate);
    }

    public static long daysOverdue(LocalDate dueDate, LocalDate today) {
        long overdue = ChronoUnit.DAYS.between(dueDate, today);
        return Math.max(overdue, 0);
    }

    public static LocalDate gracePeriodEndsAt(LocalDate dueDate) {
        return dueDate.plusDays(GRACE_PERIOD_DAYS);
    }

    public static boolean graceEnded(LocalDate dueDate, LocalDate today) {
        return daysOverdue(dueDate, today) > GRACE_PERIOD_DAYS;
    }

    public static FeePriority priority(LocalDate dueDate, BigDecimal outstanding, LocalDate today) {
        if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            return FeePriority.PAID;
        }
        long daysToDue = daysToDue(dueDate, today);
        if (daysToDue < 0) {
            return FeePriority.OVERDUE;
        }
        if (daysToDue <= 6) {
            return FeePriority.URGENT;
        }
        if (daysToDue <= 14) {
            return FeePriority.HIGH;
        }
        if (daysToDue <= 30) {
            return FeePriority.MEDIUM;
        }
        return FeePriority.LOW;
    }

    /**
     * Live preview of the 5% late penalty. This is a pure calculation and does NOT consult
     * FeeLine.penaltyAppliedAt - once a penalty has actually been applied and persisted,
     * callers must display the stored penaltyAmountEGP instead of re-calling this method,
     * otherwise a fee that becomes further overdue would look like it re-earns a fresh 5%.
     */
    public static BigDecimal computePenalty(BigDecimal outstanding, LocalDate dueDate, LocalDate today) {
        if (outstanding == null || outstanding.compareTo(BigDecimal.ZERO) <= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        if (daysToDue(dueDate, today) >= 0) {
            return BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP);
        }
        return outstanding.multiply(PENALTY_RATE).setScale(0, RoundingMode.HALF_UP).setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal totalDue(BigDecimal outstanding, BigDecimal penalty) {
        BigDecimal safeOutstanding = outstanding != null ? outstanding : BigDecimal.ZERO;
        BigDecimal safePenalty = penalty != null ? penalty : BigDecimal.ZERO;
        return safeOutstanding.add(safePenalty).setScale(2, RoundingMode.HALF_UP);
    }
}
