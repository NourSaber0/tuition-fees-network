package com.tuitionnetwork.fees.service;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.billing.domain.DeadlinePolicy;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeePriority;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.fees.dto.*;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.repository.PaymentAllocationRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SchoolFeeServiceImpl implements SchoolFeeService {

    private final FeeLineRepository feeLineRepository;
    private final StudentRepository studentRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final AuditLogRepository auditLogRepository;

    @Autowired
    public SchoolFeeServiceImpl(FeeLineRepository feeLineRepository,
                                StudentRepository studentRepository,
                                PaymentAllocationRepository paymentAllocationRepository,
                                @Autowired(required = false) AuditLogRepository auditLogRepository) {
        this.feeLineRepository = feeLineRepository;
        this.studentRepository = studentRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<FeeItemSummaryDto> getFees(UUID institutionId,
                                                  String search,
                                                  String category,
                                                  UUID studentId,
                                                  String grade,
                                                  LocalDate dueDateFrom,
                                                  LocalDate dueDateTo,
                                                  String status,
                                                  int page,
                                                  int pageSize) {
        validateInstitution(institutionId);
        List<FeeLine> allFees = feeLineRepository.findByInstitutionId(institutionId);
        Map<UUID, Student> studentMap = studentRepository.findByInstitutionId(institutionId).stream()
                .collect(Collectors.toMap(Student::getId, s -> s, (s1, s2) -> s1));

        LocalDate today = LocalDate.now();

        List<FeeItemSummaryDto> mapped = allFees.stream()
                .map(fee -> toSummaryDto(fee, studentMap.get(fee.getStudentId()), today))
                .filter(dto -> matchesSearch(dto, search))
                .filter(dto -> matchesCategory(dto, category))
                .filter(dto -> studentId == null || studentId.toString().equalsIgnoreCase(dto.studentId()))
                .filter(dto -> matchesGrade(dto, grade))
                .filter(dto -> matchesDueDate(dto.dueDate(), dueDateFrom, dueDateTo))
                .filter(dto -> matchesStatus(dto.status(), status))
                .sorted(Comparator.comparing(FeeItemSummaryDto::dueDate, Comparator.nullsLast(Comparator.naturalOrder()))
                        .thenComparing(FeeItemSummaryDto::name, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        return paginate(mapped, page, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public FeeDetailDto getFeeById(UUID institutionId, UUID feeId) {
        FeeLine fee = requireFee(institutionId, feeId);
        Student student = studentRepository.findById(fee.getStudentId()).orElse(null);
        return toDetailDto(fee, student, LocalDate.now());
    }

    @Override
    public FeeDetailDto createFee(UUID institutionId, CreateFeeRequest request, UUID actorId) {
        validateInstitution(institutionId);

        if (request.dueDate() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "due_date_required: Due date is mandatory");
        }
        if (request.amountEGP() == null || request.amountEGP().compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "validation_failed: amountEGP must be positive");
        }

        FeeType feeType = parseCategory(request.category());

        UUID studentId;
        try {
            studentId = UUID.fromString(request.studentId().trim());
        } catch (Exception e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "validation_failed: Invalid student ID format");
        }

        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found: " + studentId));

        if (!institutionId.equals(student.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cross_school_access: Student belongs to another school");
        }

        if ("Inactive".equalsIgnoreCase(student.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "student_inactive: Cannot create fee for deactivated student");
        }

        String term = request.term() != null && !request.term().isBlank() ? request.term().trim() : "Term 1 2026/27";

        FeeLine feeLine = new FeeLine(
                institutionId,
                studentId,
                feeType,
                request.amountEGP(),
                request.amountEGP(),
                term,
                request.dueDate()
        );
        feeLine.setPaidAmount(BigDecimal.ZERO);
        feeLine.setStatus(FeeStatus.OUTSTANDING);
        feeLine.setCurrency("EGP");

        feeLine = feeLineRepository.save(feeLine);

        logAudit(actorId, institutionId, "CREATE_FEE_LINE",
                "Created fee line " + feeLine.getId() + " (" + feeType.getDisplayName() + ": " + request.amountEGP() + " EGP) for student " + studentId);

        return toDetailDto(feeLine, student, LocalDate.now());
    }

    @Override
    public FeeDetailDto updateFee(UUID institutionId, UUID feeId, UpdateFeeRequest request, UUID actorId) {
        FeeLine fee = requireFee(institutionId, feeId);

        if (fee.getStatus() == FeeStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "not_eligible_for_edit: Cannot modify cancelled fee line");
        }

        // Check if locked in active EPP
        assertNotLockedInEpp(feeId);

        if (request.amountEGP() != null) {
            if (request.amountEGP().compareTo(nz(fee.getPaidAmount())) < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "amount_below_paid: New amount cannot be less than collected paid amount");
            }
            fee.setTotalAmount(request.amountEGP());
            BigDecimal newRemaining = request.amountEGP().subtract(nz(fee.getPaidAmount()));
            fee.setRemainingAmount(newRemaining);

            if (newRemaining.compareTo(BigDecimal.ZERO) <= 0) {
                fee.setStatus(FeeStatus.PAID);
            } else if (nz(fee.getPaidAmount()).compareTo(BigDecimal.ZERO) > 0) {
                fee.setStatus(FeeStatus.PARTIALLY_PAID);
            } else {
                fee.setStatus(FeeStatus.OUTSTANDING);
            }
        }

        if (request.dueDate() != null) {
            fee.setDueDate(request.dueDate());
        }

        if (request.term() != null && !request.term().isBlank()) {
            fee.setCollectionPeriod(request.term().trim());
        }

        fee = feeLineRepository.save(fee);
        logAudit(actorId, institutionId, "UPDATE_FEE_LINE", "Updated fee line " + feeId);

        Student student = studentRepository.findById(fee.getStudentId()).orElse(null);
        return toDetailDto(fee, student, LocalDate.now());
    }

    @Override
    public void cancelFee(UUID institutionId, UUID feeId, CancelFeeRequest request, UUID actorId) {
        FeeLine fee = requireFee(institutionId, feeId);

        if (fee.getStatus() == FeeStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already_cancelled: Fee line is already cancelled");
        }

        if (fee.getPaidAmount() != null && fee.getPaidAmount().compareTo(BigDecimal.ZERO) > 0) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cannot_cancel_partially_paid_fee: Fee lines with existing collected payments cannot be cancelled");
        }

        // Guardrail: Mid-Year EPP Cancellation check
        assertNotLockedInEpp(feeId);

        fee.setStatus(FeeStatus.CANCELLED);
        feeLineRepository.save(fee);

        String reason = request != null && request.reason() != null ? " Reason: " + request.reason().trim() : "";
        logAudit(actorId, institutionId, "CANCEL_FEE_LINE", "Cancelled fee line " + feeId + reason);
    }

    @Override
    public FeeDetailDto applyManualPenalty(UUID institutionId, UUID feeId, UUID actorId) {
        FeeLine fee = requireFee(institutionId, feeId);

        if (fee.getStatus() == FeeStatus.CANCELLED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "cannot_penalize_cancelled_fee: Cancelled fee line cannot be penalized");
        }

        if (fee.getFeeType() != FeeType.TUITION) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "penalty_only_applies_to_tuition: Late penalties only apply to Tuition fees");
        }

        if (fee.getPenaltyAppliedAt() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "penalty_already_applied: Late penalty has already been applied to this fee line");
        }

        BigDecimal remaining = nz(fee.getRemainingAmount());
        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fee_fully_paid: Cannot penalize a fully paid fee");
        }

        LocalDate today = LocalDate.now();
        if (fee.getDueDate() != null && !fee.getDueDate().isBefore(today)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "fee_not_overdue: Cannot apply late penalty to a fee before its due date");
        }

        BigDecimal penalty = DeadlinePolicy.computePenalty(remaining, fee.getDueDate(), today);
        if (penalty.compareTo(BigDecimal.ZERO) <= 0) {
            penalty = remaining.multiply(new BigDecimal("0.05")).setScale(2, java.math.RoundingMode.HALF_UP);
        }

        fee.setPenaltyAmountEGP(penalty);
        fee.setPenaltyAppliedAt(LocalDateTime.now());
        fee = feeLineRepository.save(fee);

        logAudit(actorId, institutionId, "MANUAL_PENALTY_APPLIED",
                "Manually applied late penalty of " + penalty + " EGP to fee line " + feeId);

        Student student = studentRepository.findById(fee.getStudentId()).orElse(null);
        return toDetailDto(fee, student, today);
    }

    @Override
    @Transactional(readOnly = true)
    public FeePenaltyInfoDto getFeePenaltyInfo(UUID institutionId, UUID feeId) {
        FeeLine fee = requireFee(institutionId, feeId);
        LocalDate today = LocalDate.now();
        BigDecimal remaining = nz(fee.getRemainingAmount());

        FeePriority priority = DeadlinePolicy.priority(fee.getDueDate(), remaining, today);
        long daysToDue = DeadlinePolicy.daysToDue(fee.getDueDate(), today);
        boolean graceEnded = DeadlinePolicy.graceEnded(fee.getDueDate(), today);

        BigDecimal penalty;
        if (fee.getPenaltyAppliedAt() != null) {
            penalty = nz(fee.getPenaltyAmountEGP());
        } else if (fee.getFeeType() == FeeType.TUITION && daysToDue < 0) {
            penalty = DeadlinePolicy.computePenalty(remaining, fee.getDueDate(), today);
        } else {
            penalty = BigDecimal.ZERO;
        }

        BigDecimal totalDue = remaining.add(penalty);

        return new FeePenaltyInfoDto(
                fee.getDueDate(),
                priority.name(),
                daysToDue,
                remaining,
                penalty,
                fee.getPenaltyAppliedAt(),
                graceEnded,
                totalDue
        );
    }

    @Override
    @Transactional(readOnly = true)
    public FeeStatsDto getFeeStats(UUID institutionId) {
        validateInstitution(institutionId);
        List<FeeLine> fees = feeLineRepository.findByInstitutionId(institutionId);
        LocalDate today = LocalDate.now();

        BigDecimal totalInvoiced = BigDecimal.ZERO;
        BigDecimal totalCollected = BigDecimal.ZERO;
        BigDecimal totalOutstanding = BigDecimal.ZERO;
        BigDecimal totalOverdue = BigDecimal.ZERO;
        long overdueCount = 0;
        long activeCount = 0;
        Set<UUID> overdueStudents = new HashSet<>();

        for (FeeLine f : fees) {
            if (f.getStatus() == FeeStatus.CANCELLED) {
                continue;
            }

            BigDecimal orig = nz(f.getTotalAmount());
            BigDecimal paid = nz(f.getPaidAmount());
            BigDecimal remaining = nz(f.getRemainingAmount());

            totalInvoiced = totalInvoiced.add(orig);
            totalCollected = totalCollected.add(paid);
            totalOutstanding = totalOutstanding.add(remaining);

            boolean isOverdue = f.getDueDate() != null && f.getDueDate().isBefore(today) && remaining.compareTo(BigDecimal.ZERO) > 0;
            if (isOverdue) {
                totalOverdue = totalOverdue.add(remaining);
                overdueCount++;
                overdueStudents.add(f.getStudentId());
            }

            if (remaining.compareTo(BigDecimal.ZERO) > 0) {
                activeCount++;
            }
        }

        return new FeeStatsDto(
                totalInvoiced,
                totalCollected,
                totalOutstanding,
                totalOverdue,
                overdueCount,
                overdueStudents.size(),
                activeCount
        );
    }

    @Override
    public List<FeeCategoryDto> getFeeCategories() {
        return List.of(
                new FeeCategoryDto("Tuition", "Tuition", 1),
                new FeeCategoryDto("Books", "Books & materials", 2),
                new FeeCategoryDto("Activity", "Activities", 3),
                new FeeCategoryDto("Bus", "Bus subscription", 4)
        );
    }

    private FeeLine requireFee(UUID institutionId, UUID feeId) {
        validateInstitution(institutionId);
        FeeLine fee = feeLineRepository.findById(feeId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Fee line not found: " + feeId));

        if (!institutionId.equals(fee.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cross_school_access: Fee line belongs to another institution");
        }
        return fee;
    }

    private void assertNotLockedInEpp(UUID feeLineId) {
        if (paymentAllocationRepository != null) {
            List<PaymentAllocation> allocations = paymentAllocationRepository.findByFeeLineId(feeLineId);
            for (PaymentAllocation pa : allocations) {
                Payment payment = pa.getPayment();
                if (payment != null && payment.getEppSchedule() != null) {
                    Integer tenor = payment.getEppSchedule().getTenorMonths();
                    if (tenor != null && tenor >= 12) {
                        throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY,
                                "Pending Business Rule: Mid-Year EPP Cancellation is undefined. FeeLine is locked in an active " + tenor + "-month EPP schedule");
                    }
                }
            }
        }
    }

    private FeeType parseCategory(String rawCategory) {
        if (rawCategory == null || rawCategory.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_category: Category is required");
        }
        String clean = rawCategory.trim().toUpperCase();
        if (clean.equals("TUITION")) return FeeType.TUITION;
        if (clean.equals("BOOKS") || clean.contains("BOOK")) return FeeType.BOOKS;
        if (clean.equals("ACTIVITY") || clean.equals("ACTIVITIES") || clean.contains("ACTIV")) return FeeType.ACTIVITIES;
        if (clean.equals("BUS")) return FeeType.BUS;

        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid_category: Must be one of Tuition, Books, Activity, Bus");
    }

    private String toCategoryCode(FeeType feeType) {
        if (feeType == null) return "Tuition";
        return switch (feeType) {
            case TUITION -> "Tuition";
            case BOOKS -> "Books";
            case ACTIVITIES -> "Activity";
            case BUS -> "Bus";
        };
    }

    private String computeStatus(FeeLine fee, LocalDate today) {
        if (fee.getStatus() == FeeStatus.CANCELLED) {
            return "Cancelled";
        }
        BigDecimal remaining = nz(fee.getRemainingAmount());
        BigDecimal paid = nz(fee.getPaidAmount());

        if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
            return "Paid";
        }
        if (fee.getDueDate() != null && fee.getDueDate().isBefore(today)) {
            return "Overdue";
        }
        if (paid.compareTo(BigDecimal.ZERO) > 0) {
            return "Partial";
        }
        return "Active";
    }

    private FeeItemSummaryDto toSummaryDto(FeeLine fee, Student student, LocalDate today) {
        String studentName = student != null ? student.getFullName() : "Unknown Student";
        String studentRef = student != null && student.getStudentRef() != null ? student.getStudentRef() : fee.getStudentId().toString();
        String grade = student != null ? student.getGrade() : null;

        String category = toCategoryCode(fee.getFeeType());
        String name = fee.getFeeType().getDisplayName() + (fee.getCollectionPeriod() != null ? " - " + fee.getCollectionPeriod() : "");
        String statusStr = computeStatus(fee, today);

        boolean penaltyApplied = fee.getPenaltyAppliedAt() != null;
        BigDecimal penaltyAmount = nz(fee.getPenaltyAmountEGP());
        BigDecimal totalDue = nz(fee.getRemainingAmount()).add(penaltyAmount);

        return new FeeItemSummaryDto(
                fee.getId().toString(),
                fee.getStudentId().toString(),
                studentName,
                studentRef,
                grade,
                name,
                category,
                fee.getCollectionPeriod(),
                fee.getDueDate(),
                nz(fee.getTotalAmount()),
                nz(fee.getPaidAmount()),
                nz(fee.getRemainingAmount()),
                statusStr,
                penaltyApplied,
                penaltyAmount,
                totalDue
        );
    }

    private FeeDetailDto toDetailDto(FeeLine fee, Student student, LocalDate today) {
        String studentName = student != null ? student.getFullName() : "Unknown Student";
        String studentRef = student != null && student.getStudentRef() != null ? student.getStudentRef() : fee.getStudentId().toString();
        String grade = student != null ? student.getGrade() : null;

        String category = toCategoryCode(fee.getFeeType());
        String name = fee.getFeeType().getDisplayName() + (fee.getCollectionPeriod() != null ? " - " + fee.getCollectionPeriod() : "");
        String statusStr = computeStatus(fee, today);

        long daysOverdue = DeadlinePolicy.daysOverdue(fee.getDueDate(), today);
        boolean isOverdue = daysOverdue > 0 && nz(fee.getRemainingAmount()).compareTo(BigDecimal.ZERO) > 0;
        FeeOverdueInfoDto overdueInfo = new FeeOverdueInfoDto(isOverdue, daysOverdue);

        boolean penaltyApplied = fee.getPenaltyAppliedAt() != null;
        boolean graceEnded = DeadlinePolicy.graceEnded(fee.getDueDate(), today);
        BigDecimal penaltyAmount = nz(fee.getPenaltyAmountEGP());
        BigDecimal totalDue = nz(fee.getRemainingAmount()).add(penaltyAmount);

        FeePenaltySnapshotDto penaltySnapshot = new FeePenaltySnapshotDto(
                penaltyApplied,
                penaltyAmount,
                fee.getPenaltyAppliedAt(),
                graceEnded,
                totalDue
        );

        List<FeePaymentHistoryDto> paymentHistory = new ArrayList<>();
        if (paymentAllocationRepository != null) {
            List<PaymentAllocation> allocations = paymentAllocationRepository.findByFeeLineId(fee.getId());
            for (PaymentAllocation pa : allocations) {
                Payment p = pa.getPayment();
                if (p != null) {
                    LocalDateTime created = p.getCreatedAt() != null ? p.getCreatedAt() : LocalDateTime.now();
                    paymentHistory.add(new FeePaymentHistoryDto(
                            p.getTransactionReference() != null ? p.getTransactionReference() : p.getId().toString(),
                            created.format(DateTimeFormatter.ISO_LOCAL_DATE_TIME),
                            nz(pa.getAmountApplied()),
                            p.getStatus() != null ? p.getStatus().name() : "Captured",
                            p.getPaymentMethod() != null ? p.getPaymentMethod().name() : "Card"
                    ));
                }
            }
        }

        return new FeeDetailDto(
                fee.getId().toString(),
                fee.getStudentId().toString(),
                studentName,
                studentRef,
                grade,
                name,
                category,
                fee.getCollectionPeriod(),
                fee.getDueDate(),
                nz(fee.getTotalAmount()),
                nz(fee.getPaidAmount()),
                nz(fee.getRemainingAmount()),
                statusStr,
                overdueInfo,
                penaltySnapshot,
                paymentHistory
        );
    }

    private PageResponse<FeeItemSummaryDto> paginate(List<FeeItemSummaryDto> list, int page, int pageSize) {
        int safePage = page > 0 ? page : 1;
        int safeSize = pageSize > 0 ? pageSize : 25;
        int total = list.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);

        int start = Math.min((safePage - 1) * safeSize, total);
        int end = Math.min(start + safeSize, total);
        List<FeeItemSummaryDto> sublist = list.subList(start, end);

        return new PageResponse<>(sublist, safePage, safeSize, (long) total, totalPages);
    }

    private boolean matchesSearch(FeeItemSummaryDto dto, String search) {
        if (search == null || search.isBlank()) return true;
        String q = search.trim().toLowerCase();
        return (dto.name() != null && dto.name().toLowerCase().contains(q))
                || (dto.studentName() != null && dto.studentName().toLowerCase().contains(q))
                || (dto.studentRef() != null && dto.studentRef().toLowerCase().contains(q))
                || (dto.id() != null && dto.id().toLowerCase().contains(q));
    }

    private boolean matchesCategory(FeeItemSummaryDto dto, String category) {
        if (category == null || category.isBlank()) return true;
        return dto.category() != null && dto.category().equalsIgnoreCase(category.trim());
    }

    private boolean matchesGrade(FeeItemSummaryDto dto, String grade) {
        if (grade == null || grade.isBlank()) return true;
        return dto.grade() != null && dto.grade().equalsIgnoreCase(grade.trim());
    }

    private boolean matchesDueDate(LocalDate target, LocalDate from, LocalDate to) {
        if (target == null) return true;
        if (from != null && target.isBefore(from)) return false;
        if (to != null && target.isAfter(to)) return false;
        return true;
    }

    private boolean matchesStatus(String feeStatus, String queryStatus) {
        if (queryStatus == null || queryStatus.isBlank()) return true;
        return feeStatus != null && feeStatus.equalsIgnoreCase(queryStatus.trim());
    }

    private void validateInstitution(UUID institutionId) {
        if (institutionId == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "institutionId is required");
        }
    }

    private BigDecimal nz(BigDecimal val) {
        return val != null ? val : BigDecimal.ZERO;
    }

    private void logAudit(UUID actorId, UUID institutionId, String action, String target) {
        if (auditLogRepository != null) {
            auditLogRepository.save(new AuditLog(
                    actorId != null ? actorId : institutionId,
                    "INSTITUTION_ADMIN",
                    action,
                    target
            ));
        }
    }
}
