package com.tuitionnetwork.fees.service;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.billing.domain.DeadlinePolicy;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.notifications.domain.Notification;
import com.tuitionnetwork.notifications.repository.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class FeeAutomatedRulesEngine {

    private static final Logger log = LoggerFactory.getLogger(FeeAutomatedRulesEngine.class);

    private final FeeLineRepository feeLineRepository;
    private final StudentRepository studentRepository;
    private final NotificationRepository notificationRepository;
    private final AuditLogRepository auditLogRepository;

    @Autowired
    public FeeAutomatedRulesEngine(FeeLineRepository feeLineRepository,
                                  StudentRepository studentRepository,
                                  NotificationRepository notificationRepository,
                                  @Autowired(required = false) AuditLogRepository auditLogRepository) {
        this.feeLineRepository = feeLineRepository;
        this.studentRepository = studentRepository;
        this.notificationRepository = notificationRepository;
        this.auditLogRepository = auditLogRepository;
    }

    /**
     * Engine A: Automated Late Penalty Evaluation Engine.
     * Daily batch at 00:05 Cairo time.
     * Evaluates Tuition fees overdue for > 7 days with remaining balance, applies 5% penalty idempotently.
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "Africa/Cairo")
    @Transactional
    public int runTuitionPenaltyEngine() {
        return evaluateTuitionPenalties(LocalDate.now());
    }

    @Transactional
    public int evaluateTuitionPenalties(LocalDate today) {
        log.info("Starting Engine A: Tuition Late Penalty Evaluation for date {}", today);
        List<FeeLine> candidates = feeLineRepository.findAll();
        int processedCount = 0;
        BigDecimal totalPenalties = BigDecimal.ZERO;

        for (FeeLine fee : candidates) {
            if (fee.getStatus() == FeeStatus.CANCELLED) {
                continue;
            }
            if (fee.getFeeType() != FeeType.TUITION) {
                continue;
            }
            if (fee.getPenaltyAppliedAt() != null) {
                continue;
            }
            if (fee.getRemainingAmount() == null || fee.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (fee.getDueDate() == null) {
                continue;
            }

            if (DeadlinePolicy.graceEnded(fee.getDueDate(), today)) {
                BigDecimal penalty = fee.getRemainingAmount().multiply(new BigDecimal("0.05")).setScale(2, RoundingMode.HALF_UP);
                if (penalty.compareTo(BigDecimal.ZERO) <= 0) {
                    penalty = new BigDecimal("1.00");
                }

                fee.setPenaltyAmountEGP(penalty);
                fee.setPenaltyAppliedAt(LocalDateTime.now());
                feeLineRepository.save(fee);

                totalPenalties = totalPenalties.add(penalty);
                processedCount++;

                log.info("Engine A applied 5% penalty of {} EGP to fee line {}", penalty, fee.getId());
            }
        }

        if (processedCount > 0 && auditLogRepository != null) {
            auditLogRepository.save(new AuditLog(
                    null,
                    "SYSTEM",
                    "ENGINE_A_TUITION_PENALTIES",
                    "Applied 5% late penalties to " + processedCount + " overdue fee lines. Total: " + totalPenalties + " EGP"
            ));
        }

        log.info("Engine A completed: {} fee lines penalized", processedCount);
        return processedCount;
    }

    /**
     * Engine B: 7-Day Due Date Approaching Reminder Engine.
     * Daily batch at 08:00 Cairo time.
     * Sends payment reminders exactly 7 calendar days before dueDate for uncollected fees.
     */
    @Scheduled(cron = "0 0 8 * * *", zone = "Africa/Cairo")
    @Transactional
    public int runApproachingReminderEngine() {
        return dispatchApproachingReminders(LocalDate.now());
    }

    @Transactional
    public int dispatchApproachingReminders(LocalDate today) {
        log.info("Starting Engine B: 7-Day Approaching Reminder Engine for date {}", today);
        List<FeeLine> candidates = feeLineRepository.findAll();
        int remindersSent = 0;

        for (FeeLine fee : candidates) {
            if (fee.getStatus() == FeeStatus.CANCELLED) {
                continue;
            }
            if (fee.getRemainingAmount() == null || fee.getRemainingAmount().compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }
            if (fee.getDueDate() == null) {
                continue;
            }

            long daysToDue = DeadlinePolicy.daysToDue(fee.getDueDate(), today);
            if (daysToDue == 7) {
                Optional<Student> studentOpt = studentRepository.findById(fee.getStudentId());
                if (studentOpt.isEmpty()) {
                    continue;
                }
                Student student = studentOpt.get();
                UUID guardianId = student.getGuardianId();
                if (guardianId == null) {
                    guardianId = student.getId(); // Fallback identifier
                }

                String feeRef = fee.getId().toString();
                boolean alreadySent = notificationRepository.existsByGuardianIdAndTypeAndMessageContaining(
                        guardianId, "reminder", feeRef
                );

                if (!alreadySent) {
                    String feeName = fee.getFeeType().getDisplayName() +
                            (fee.getCollectionPeriod() != null ? " - " + fee.getCollectionPeriod() : "");
                    String message = "Payment reminder: Fee '" + feeName + "' for " + student.getFullName() +
                            " of " + fee.getRemainingAmount() + " EGP is due in 7 days on " + fee.getDueDate() +
                            " [Ref: " + feeRef + "]";

                    Notification notif = new Notification(
                            guardianId,
                            "reminder",
                            message,
                            "Unread"
                    );
                    notificationRepository.save(notif);
                    remindersSent++;

                    log.info("Engine B dispatched reminder for fee line {} to guardian {}", fee.getId(), guardianId);
                }
            }
        }

        if (remindersSent > 0 && auditLogRepository != null) {
            auditLogRepository.save(new AuditLog(
                    null,
                    "SYSTEM",
                    "ENGINE_B_APPROACHING_REMINDERS",
                    "Dispatched " + remindersSent + " 7-day payment approaching reminders"
            ));
        }

        log.info("Engine B completed: {} reminders dispatched", remindersSent);
        return remindersSent;
    }
}
