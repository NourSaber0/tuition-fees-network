package com.tuitionnetwork.dashboard.service;

import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeePriority;
import com.tuitionnetwork.billing.dto.FeeDeadlineSnapshot;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.billing.service.FeeDeadlineService;
import com.tuitionnetwork.dashboard.dto.ActiveInstitutionsKpi;
import com.tuitionnetwork.dashboard.dto.CollectionKpi;
import com.tuitionnetwork.dashboard.dto.DashboardKpis;
import com.tuitionnetwork.dashboard.dto.DashboardSummaryResponse;
import com.tuitionnetwork.dashboard.dto.DeadlineQueueItemDto;
import com.tuitionnetwork.dashboard.dto.DeadlineSummaryResponse;
import com.tuitionnetwork.dashboard.dto.EppPlansKpi;
import com.tuitionnetwork.dashboard.dto.InstitutionStatusBreakdown;
import com.tuitionnetwork.dashboard.dto.InstitutionStatusResponse;
import com.tuitionnetwork.dashboard.dto.KpiValue;
import com.tuitionnetwork.dashboard.dto.RateKpi;
import com.tuitionnetwork.dashboard.dto.RecentTransactionsResponse;
import com.tuitionnetwork.dashboard.dto.TransactionSummaryDto;
import com.tuitionnetwork.dashboard.dto.WeeklyCollectionPoint;
import com.tuitionnetwork.dashboard.dto.WeeklyCollectionsResponse;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.repository.EPPScheduleRepository;
import com.tuitionnetwork.payments.repository.EppInstallmentRepository;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.reconciliation.repository.ReconciliationExceptionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.TextStyle;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class DashboardServiceImpl implements DashboardService {

    private static final List<PaymentStatus> PENDING_STATUSES = List.of(PaymentStatus.PENDING, PaymentStatus.AUTHORIZED);
    private static final int DEADLINE_QUEUE_LIMIT = 20;

    private final PaymentRepository paymentRepository;
    private final InstitutionRepository institutionRepository;
    private final StudentRepository studentRepository;
    private final EPPScheduleRepository eppScheduleRepository;
    private final EppInstallmentRepository eppInstallmentRepository;
    private final FeeLineRepository feeLineRepository;
    private final FeeDeadlineService feeDeadlineService;
    private final ReconciliationExceptionRepository reconciliationExceptionRepository;

    public DashboardServiceImpl(PaymentRepository paymentRepository,
                                 InstitutionRepository institutionRepository,
                                 StudentRepository studentRepository,
                                 EPPScheduleRepository eppScheduleRepository,
                                 EppInstallmentRepository eppInstallmentRepository,
                                 FeeLineRepository feeLineRepository,
                                 FeeDeadlineService feeDeadlineService,
                                 ReconciliationExceptionRepository reconciliationExceptionRepository) {
        this.paymentRepository = paymentRepository;
        this.institutionRepository = institutionRepository;
        this.studentRepository = studentRepository;
        this.eppScheduleRepository = eppScheduleRepository;
        this.eppInstallmentRepository = eppInstallmentRepository;
        this.feeLineRepository = feeLineRepository;
        this.feeDeadlineService = feeDeadlineService;
        this.reconciliationExceptionRepository = reconciliationExceptionRepository;
    }

    @Override
    public DashboardSummaryResponse getSummary() {
        LocalDateTime now = LocalDateTime.now();
        LocalDateTime startOfToday = now.toLocalDate().atStartOfDay();
        LocalDateTime startOfYesterday = startOfToday.minusDays(1);

        long todayTransactions = paymentRepository.countByCreatedAtBetween(startOfToday, now);
        long yesterdayTransactions = paymentRepository.countByCreatedAtBetween(startOfYesterday, startOfToday);

        BigDecimal todayCollectionEGP = paymentRepository.sumAmountByStatusAndCreatedAtBetween(
                PaymentStatus.CAPTURED, startOfToday, now);
        BigDecimal prevDayCollectionEGP = paymentRepository.sumAmountByStatusAndCreatedAtBetween(
                PaymentStatus.CAPTURED, startOfYesterday, startOfToday);

        long successfulToday = paymentRepository.countByStatusAndCreatedAtBetween(
                PaymentStatus.CAPTURED, startOfToday, now);
        long successfulYesterday = paymentRepository.countByStatusAndCreatedAtBetween(
                PaymentStatus.CAPTURED, startOfYesterday, startOfToday);

        long failedToday = paymentRepository.countByStatusAndCreatedAtBetween(
                PaymentStatus.FAILED, startOfToday, now);
        long failedYesterday = paymentRepository.countByStatusAndCreatedAtBetween(
                PaymentStatus.FAILED, startOfYesterday, startOfToday);

        long pendingToday = paymentRepository.countByStatusInAndCreatedAtBetween(
                PENDING_STATUSES, startOfToday, now);
        long pendingYesterday = paymentRepository.countByStatusInAndCreatedAtBetween(
                PENDING_STATUSES, startOfYesterday, startOfToday);

        long institutionsCount = institutionRepository.count();
        long studentsCount = studentRepository.count();
        long eppPlansCount = eppScheduleRepository.count();
        long pendingReconCount = reconciliationExceptionRepository.countByStatusNot("Resolved");
        BigDecimal eppOutstandingEGP = eppInstallmentRepository.sumOutstandingAmount();

        DashboardKpis kpis = new DashboardKpis(
                new ActiveInstitutionsKpi(institutionsCount, institutionsCount, 0, 0.0),
                new KpiValue(studentsCount, 0.0),
                new KpiValue(todayTransactions, trendPct(todayTransactions, yesterdayTransactions)),
                new CollectionKpi(todayCollectionEGP, prevDayCollectionEGP,
                        trendPct(todayCollectionEGP, prevDayCollectionEGP)),
                new RateKpi(successfulToday, ratePct(successfulToday, todayTransactions),
                        trendPct(successfulToday, successfulYesterday)),
                new RateKpi(failedToday, ratePct(failedToday, todayTransactions),
                        trendPct(failedToday, failedYesterday)),
                new KpiValue(pendingToday, trendPct(pendingToday, pendingYesterday)),
                new KpiValue(pendingReconCount, 0.0),
                new EppPlansKpi(eppPlansCount, eppOutstandingEGP, 0.0)
        );

        return new DashboardSummaryResponse(now, kpis);
    }

    @Override
    public WeeklyCollectionsResponse getWeeklyCollections(LocalDate weekOf) {
        LocalDate anchor = weekOf != null ? weekOf : LocalDate.now();
        LocalDate monday = anchor.with(DayOfWeek.MONDAY);
        LocalDate sunday = monday.plusDays(6);

        List<WeeklyCollectionPoint> series = monday.datesUntil(sunday.plusDays(1))
                .map(day -> {
                    LocalDateTime dayStart = day.atStartOfDay();
                    LocalDateTime dayEnd = dayStart.plusDays(1);
                    BigDecimal dayTotal = paymentRepository.sumAmountByStatusAndCreatedAtBetween(
                            PaymentStatus.CAPTURED, dayStart, dayEnd);
                    String label = day.getDayOfWeek().getDisplayName(TextStyle.SHORT, Locale.ENGLISH);
                    return new WeeklyCollectionPoint(day, label, dayTotal);
                })
                .collect(Collectors.toList());

        BigDecimal weekTotalEGP = series.stream()
                .map(WeeklyCollectionPoint::amountEGP)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
        BigDecimal dailyAvgEGP = weekTotalEGP.divide(BigDecimal.valueOf(series.size()), 2, RoundingMode.HALF_UP);

        return new WeeklyCollectionsResponse("EGP", monday, sunday, series, weekTotalEGP, dailyAvgEGP);
    }

    @Override
    public InstitutionStatusResponse getInstitutionStatus() {
        long total = institutionRepository.count();
        List<InstitutionStatusBreakdown> breakdown = List.of(
                new InstitutionStatusBreakdown("Active", total, total > 0 ? 100.0 : 0.0),
                new InstitutionStatusBreakdown("Pending Approval", 0, 0.0),
                new InstitutionStatusBreakdown("Under Review", 0, 0.0),
                new InstitutionStatusBreakdown("Suspended", 0, 0.0)
        );
        return new InstitutionStatusResponse(total, 0, breakdown);
    }

    @Override
    public RecentTransactionsResponse getRecentTransactions(int limit) {
        Page<Payment> page = paymentRepository.findAllByOrderByCreatedAtDesc(
                PageRequest.of(0, limit, Sort.by(Sort.Direction.DESC, "createdAt")));

        List<TransactionSummaryDto> data = page.getContent().stream()
                .map(this::toSummary)
                .collect(Collectors.toList());

        return new RecentTransactionsResponse(data);
    }

    private TransactionSummaryDto toSummary(Payment payment) {
        Optional<PaymentAllocation> firstAllocation = payment.getAllocations().stream().findFirst();

        String institution = firstAllocation
                .map(a -> a.getFeeLine().getInstitutionId())
                .flatMap(institutionRepository::findById)
                .map(Institution::getName)
                .orElse(null);

        String student = firstAllocation
                .map(a -> a.getFeeLine().getStudentId())
                .flatMap(studentRepository::findById)
                .map(Student::getFullName)
                .orElse(null);

        String feeType = firstAllocation
                .map(a -> a.getFeeLine().getFeeType().getDisplayName())
                .orElse(null);

        return new TransactionSummaryDto(
                payment.getId(),
                institution,
                student,
                feeType,
                payment.getTotalAmount(),
                payment.getPaymentMethod() != null ? payment.getPaymentMethod().name() : null,
                displayStatus(payment.getStatus()),
                payment.getCreatedAt()
        );
    }

    private String displayStatus(PaymentStatus status) {
        if (status == null) {
            return null;
        }
        return switch (status) {
            case CAPTURED -> "Successful";
            case FAILED -> "Failed";
            case REFUNDED -> "Refunded";
            case PENDING, AUTHORIZED -> "Pending";
        };
    }

    private double trendPct(long current, long previous) {
        if (previous == 0) {
            return current == 0 ? 0.0 : 100.0;
        }
        return BigDecimal.valueOf(current - previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(previous), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double trendPct(BigDecimal current, BigDecimal previous) {
        if (previous == null || previous.compareTo(BigDecimal.ZERO) == 0) {
            return (current == null || current.compareTo(BigDecimal.ZERO) == 0) ? 0.0 : 100.0;
        }
        return current.subtract(previous)
                .multiply(BigDecimal.valueOf(100))
                .divide(previous, 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    private double ratePct(long part, long total) {
        if (total == 0) {
            return 0.0;
        }
        return BigDecimal.valueOf(part)
                .multiply(BigDecimal.valueOf(100))
                .divide(BigDecimal.valueOf(total), 1, RoundingMode.HALF_UP)
                .doubleValue();
    }

    @Override
    public DeadlineSummaryResponse getDeadlineSummary() {
        List<FeeLine> outstanding = feeLineRepository.findAllOutstanding();

        long dueToday = 0;
        long dueThisWeek = 0;
        long urgent = 0;
        long overdue = 0;
        BigDecimal penaltiesAppliedEGP = feeLineRepository.sumTotalPenaltiesApplied();

        record Scored(FeeLine feeLine, FeeDeadlineSnapshot snapshot, int sortRank) {
        }

        List<Scored> scored = new java.util.ArrayList<>();
        for (FeeLine feeLine : outstanding) {
            FeeDeadlineSnapshot snapshot = feeDeadlineService.computeSnapshot(feeLine);
            if (snapshot.daysToDue() == 0) {
                dueToday++;
            }
            if (snapshot.daysToDue() >= 0 && snapshot.daysToDue() <= 6) {
                dueThisWeek++;
            }
            if (snapshot.priority() == FeePriority.URGENT) {
                urgent++;
            }
            if (snapshot.priority() == FeePriority.OVERDUE) {
                overdue++;
            }

            scored.add(new Scored(feeLine, snapshot, priorityRank(snapshot)));
        }

        List<DeadlineQueueItemDto> priorityQueue = scored.stream()
                .sorted(Comparator.<Scored>comparingInt(s -> s.sortRank())
                        .thenComparing(s -> s.snapshot().dueDate()))
                .limit(DEADLINE_QUEUE_LIMIT)
                .map(s -> toQueueItem(s.feeLine(), s.snapshot()))
                .toList();

        return new DeadlineSummaryResponse(dueToday, dueThisWeek, urgent, overdue,
                penaltiesAppliedEGP.setScale(2, RoundingMode.HALF_UP), priorityQueue);
    }

    /**
     * Sort key for the priority queue: OVERDUE, then "due today" (a sub-bucket of URGENT called
     * out separately by the spec), then the rest of URGENT, then HIGH/MEDIUM/LOW. PAID fee lines
     * never appear here since we only scan outstanding balances.
     */
    private int priorityRank(FeeDeadlineSnapshot snapshot) {
        if (snapshot.priority() == FeePriority.OVERDUE) {
            return 0;
        }
        if (snapshot.daysToDue() == 0) {
            return 1;
        }
        return switch (snapshot.priority()) {
            case URGENT -> 2;
            case HIGH -> 3;
            case MEDIUM -> 4;
            case LOW -> 5;
            default -> 6;
        };
    }

    private DeadlineQueueItemDto toQueueItem(FeeLine feeLine, FeeDeadlineSnapshot snapshot) {
        String institution = institutionRepository.findById(feeLine.getInstitutionId())
                .map(Institution::getName)
                .orElse(null);
        String student = studentRepository.findById(feeLine.getStudentId())
                .map(Student::getFullName)
                .orElse(null);

        return new DeadlineQueueItemDto(
                feeLine.getId(),
                institution,
                student,
                feeLine.getFeeType() != null ? feeLine.getFeeType().getDisplayName() : null,
                snapshot.dueDate(),
                snapshot.priority().name(),
                snapshot.daysToDue(),
                snapshot.outstandingEGP(),
                snapshot.penaltyEGP(),
                snapshot.totalDueEGP()
        );
    }
}
