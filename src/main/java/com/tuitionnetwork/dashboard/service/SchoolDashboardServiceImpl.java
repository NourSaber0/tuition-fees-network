package com.tuitionnetwork.dashboard.service;

import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.dashboard.dto.SchoolDashboardKpis;
import com.tuitionnetwork.dashboard.dto.SchoolDashboardSummaryResponse;
import com.tuitionnetwork.dashboard.dto.SchoolFeeUploadStatus;
import com.tuitionnetwork.dashboard.dto.SchoolKpiValue;
import com.tuitionnetwork.dashboard.dto.SchoolOverdueKpi;
import com.tuitionnetwork.dashboard.dto.SchoolQuickLinkDto;
import com.tuitionnetwork.dashboard.dto.SchoolRecentPaymentDto;
import com.tuitionnetwork.dashboard.dto.SchoolRecentPaymentsResponse;
import com.tuitionnetwork.dashboard.dto.WeeklyCollectionPoint;
import com.tuitionnetwork.dashboard.dto.WeeklyCollectionsResponse;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.ingestion.domain.CsvUpload;
import com.tuitionnetwork.ingestion.repository.CsvUploadRepository;
import com.tuitionnetwork.notifications.repository.BackOfficeNotificationRepository;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.repository.PaymentAllocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.DayOfWeek;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class SchoolDashboardServiceImpl implements SchoolDashboardService {

    private final FeeLineRepository feeLineRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final CsvUploadRepository csvUploadRepository;
    private final StudentRepository studentRepository;
    private final BackOfficeNotificationRepository notificationRepository;

    @Autowired
    public SchoolDashboardServiceImpl(FeeLineRepository feeLineRepository,
                                      PaymentAllocationRepository paymentAllocationRepository,
                                      CsvUploadRepository csvUploadRepository,
                                      StudentRepository studentRepository,
                                      @Autowired(required = false) BackOfficeNotificationRepository notificationRepository) {
        this.feeLineRepository = feeLineRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.csvUploadRepository = csvUploadRepository;
        this.studentRepository = studentRepository;
        this.notificationRepository = notificationRepository;
    }

    @Override
    public SchoolDashboardSummaryResponse getSchoolSummary(UUID schoolId) {
        if (schoolId == null) {
            return new SchoolDashboardSummaryResponse(
                    Instant.now().toString(),
                    new SchoolDashboardKpis(
                            new SchoolKpiValue(BigDecimal.ZERO, 0.0),
                            new SchoolKpiValue(BigDecimal.ZERO, 0.0),
                            new SchoolOverdueKpi(BigDecimal.ZERO, 0, 0.0),
                            new SchoolFeeUploadStatus("No Uploads", null, false)
                    )
            );
        }

        List<FeeLine> fees = feeLineRepository.findByInstitutionId(schoolId);
        LocalDate today = LocalDate.now();

        BigDecimal totalCollected = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;
        BigDecimal overdue = BigDecimal.ZERO;
        long overdueCount = 0;

        for (FeeLine f : fees) {
            if (f.getStatus() != FeeStatus.CANCELLED) {
                if (f.getPaidAmount() != null) {
                    totalCollected = totalCollected.add(f.getPaidAmount());
                }
                if (f.getRemainingAmount() != null && f.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
                    outstanding = outstanding.add(f.getRemainingAmount());
                    if (f.getDueDate() != null && f.getDueDate().isBefore(today)) {
                        overdue = overdue.add(f.getRemainingAmount());
                        overdueCount++;
                    }
                }
            }
        }

        // Fee upload status
        List<CsvUpload> uploads = csvUploadRepository.findByInstitutionId(schoolId);
        SchoolFeeUploadStatus uploadStatus;
        if (uploads == null || uploads.isEmpty()) {
            uploadStatus = new SchoolFeeUploadStatus("No Uploads", null, false);
        } else {
            CsvUpload latest = uploads.stream()
                    .max(Comparator.comparing(u -> u.getUploadedAt() != null ? u.getUploadedAt() : java.time.LocalDateTime.MIN))
                    .orElse(uploads.get(0));
            String statusStr = latest.getFailedRows() > 0 ? "Completed with Errors" : "Completed";
            String isoDate = latest.getUploadedAt() != null
                    ? latest.getUploadedAt().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
                    : null;
            uploadStatus = new SchoolFeeUploadStatus(statusStr, isoDate, latest.getFailedRows() > 0);
        }

        SchoolDashboardKpis kpis = new SchoolDashboardKpis(
                new SchoolKpiValue(totalCollected, 5.4),
                new SchoolKpiValue(outstanding, -2.1),
                new SchoolOverdueKpi(overdue, overdueCount, 8.9),
                uploadStatus
        );

        return new SchoolDashboardSummaryResponse(Instant.now().toString(), kpis);
    }

    @Override
    public SchoolRecentPaymentsResponse getRecentPayments(UUID schoolId, int limit) {
        if (schoolId == null) {
            return new SchoolRecentPaymentsResponse(List.of());
        }
        int effectiveLimit = limit > 0 ? limit : 6;
        List<PaymentAllocation> allocations = paymentAllocationRepository.findByInstitutionId(schoolId);

        List<SchoolRecentPaymentDto> dtos = new ArrayList<>();
        int count = 0;
        for (PaymentAllocation a : allocations) {
            if (count >= effectiveLimit) break;
            Payment p = a.getPayment();
            FeeLine f = a.getFeeLine();
            if (p == null || f == null) continue;

            Optional<Student> studentOpt = studentRepository.findById(f.getStudentId());
            String studentName = studentOpt.map(Student::getFullName).orElse("Student " + f.getStudentId().toString().substring(0, 8));
            String studentId = "STU-" + f.getStudentId().toString().substring(0, 8).toUpperCase();
            String feeName = (f.getFeeType() != null ? f.getFeeType().name() : "Tuition")
                    + (f.getCollectionPeriod() != null ? " - " + f.getCollectionPeriod() : "");
            String txId = p.getTransactionReference() != null
                    ? p.getTransactionReference()
                    : "TX-" + p.getId().toString().substring(0, 8).toUpperCase();
            String date = p.getCreatedAt() != null
                    ? p.getCreatedAt().atZone(ZoneOffset.UTC).format(DateTimeFormatter.ISO_INSTANT)
                    : Instant.now().toString();
            String status = p.getStatus() == PaymentStatus.CAPTURED ? "Successful" : p.getStatus().name();
            boolean isPartial = f.getRemainingAmount() != null && f.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0;

            dtos.add(new SchoolRecentPaymentDto(
                    txId,
                    studentId,
                    studentName,
                    f.getId(),
                    feeName,
                    a.getAmountApplied() != null ? a.getAmountApplied() : p.getTotalAmount(),
                    date,
                    status,
                    isPartial
            ));
            count++;
        }

        return new SchoolRecentPaymentsResponse(dtos);
    }

    @Override
    public List<SchoolQuickLinkDto> getQuickLinks(UUID schoolId, UUID userId) {
        long studentCount = 0;
        long overdueCount = 0;
        boolean pendingUpload = false;
        long unreadNotifications = 0;

        if (schoolId != null) {
            studentCount = studentRepository.findByInstitutionId(schoolId).size();
            LocalDate today = LocalDate.now();
            List<FeeLine> fees = feeLineRepository.findByInstitutionId(schoolId);
            for (FeeLine f : fees) {
                if (f.getStatus() != FeeStatus.CANCELLED
                        && f.getDueDate() != null
                        && f.getDueDate().isBefore(today)
                        && f.getRemainingAmount() != null
                        && f.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0) {
                    overdueCount++;
                }
            }

            List<CsvUpload> uploads = csvUploadRepository.findByInstitutionId(schoolId);
            if (uploads != null && !uploads.isEmpty()) {
                CsvUpload latest = uploads.stream()
                        .max(Comparator.comparing(u -> u.getUploadedAt() != null ? u.getUploadedAt() : java.time.LocalDateTime.MIN))
                        .orElse(uploads.get(0));
                pendingUpload = latest.getFailedRows() > 0;
            }
        }

        return List.of(
                new SchoolQuickLinkDto("students", "Students", "/students", (int) studentCount),
                new SchoolQuickLinkDto("fees", "Fees", "/fees", (int) overdueCount),
                new SchoolQuickLinkDto("fee-upload", "Upload Fees", "/fee-upload", pendingUpload ? 1 : 0),
                new SchoolQuickLinkDto("payments", "Payments", "/payments", null),
                new SchoolQuickLinkDto("reports", "Reports", "/reports", null),
                new SchoolQuickLinkDto("notifications", "Notifications", "/notifications", (int) unreadNotifications)
        );
    }

    @Override
    public WeeklyCollectionsResponse getSchoolWeeklyCollections(UUID schoolId, LocalDate weekOf) {
        LocalDate base = weekOf != null ? weekOf : LocalDate.now();
        LocalDate monday = base.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY));
        LocalDate sunday = monday.plusDays(6);

        List<PaymentAllocation> allocations = schoolId != null
                ? paymentAllocationRepository.findByInstitutionId(schoolId)
                : List.of();

        List<WeeklyCollectionPoint> series = new ArrayList<>();
        BigDecimal weekTotal = BigDecimal.ZERO;

        for (int i = 0; i < 7; i++) {
            LocalDate day = monday.plusDays(i);
            BigDecimal dayTotal = BigDecimal.ZERO;
            for (PaymentAllocation a : allocations) {
                Payment p = a.getPayment();
                if (p != null && p.getStatus() == PaymentStatus.CAPTURED && p.getCreatedAt() != null) {
                    if (p.getCreatedAt().toLocalDate().equals(day)) {
                        dayTotal = dayTotal.add(a.getAmountApplied() != null ? a.getAmountApplied() : p.getTotalAmount());
                    }
                }
            }
            series.add(new WeeklyCollectionPoint(day, day.getDayOfWeek().name().substring(0, 3), dayTotal));
            weekTotal = weekTotal.add(dayTotal);
        }

        BigDecimal dailyAvg = weekTotal.divide(BigDecimal.valueOf(7), 2, RoundingMode.HALF_UP);
        return new WeeklyCollectionsResponse("EGP", monday, sunday, series, weekTotal, dailyAvg);
    }
}
