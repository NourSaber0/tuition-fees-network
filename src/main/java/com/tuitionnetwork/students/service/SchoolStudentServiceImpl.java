package com.tuitionnetwork.students.service;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.identity.domain.Guardian;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.GuardianRepository;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.identity.service.IdentityResolverService;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.repository.PaymentAllocationRepository;
import com.tuitionnetwork.students.dto.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@Transactional
public class SchoolStudentServiceImpl implements SchoolStudentService {

    private final StudentRepository studentRepository;
    private final FeeLineRepository feeLineRepository;
    private final PaymentAllocationRepository paymentAllocationRepository;
    private final InstitutionRepository institutionRepository;
    private final GuardianRepository guardianRepository;
    private final AuditLogRepository auditLogRepository;
    private final IdentityResolverService identityResolverService;

    @Autowired
    public SchoolStudentServiceImpl(StudentRepository studentRepository,
                                    FeeLineRepository feeLineRepository,
                                    PaymentAllocationRepository paymentAllocationRepository,
                                    InstitutionRepository institutionRepository,
                                    GuardianRepository guardianRepository,
                                    @Autowired(required = false) AuditLogRepository auditLogRepository,
                                    @Autowired(required = false) IdentityResolverService identityResolverService) {
        this.studentRepository = studentRepository;
        this.feeLineRepository = feeLineRepository;
        this.paymentAllocationRepository = paymentAllocationRepository;
        this.institutionRepository = institutionRepository;
        this.guardianRepository = guardianRepository;
        this.auditLogRepository = auditLogRepository;
        this.identityResolverService = identityResolverService;
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentSummaryDto> getStudents(UUID institutionId, String search, String grade, int page, int pageSize) {
        validateInstitution(institutionId);
        List<Student> all = studentRepository.findByInstitutionId(institutionId);

        List<Student> filtered = all.stream()
                .filter(s -> "Active".equalsIgnoreCase(s.getStatus()))
                .filter(s -> matchesSearch(s, search))
                .filter(s -> matchesGrade(s, grade))
                .sorted(Comparator.comparing(Student::getFullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        return paginateStudents(filtered, institutionId, page, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<StudentSummaryDto> getDeactivatedStudents(UUID institutionId, String search,
                                                                 LocalDate deactivatedFrom, LocalDate deactivatedTo,
                                                                 int page, int pageSize) {
        validateInstitution(institutionId);
        List<Student> all = studentRepository.findByInstitutionId(institutionId);

        List<Student> filtered = all.stream()
                .filter(s -> "Inactive".equalsIgnoreCase(s.getStatus()))
                .filter(s -> matchesSearch(s, search))
                .filter(s -> matchesDateRange(s.getDeactivatedDate(), deactivatedFrom, deactivatedTo))
                .sorted(Comparator.comparing(Student::getDeactivatedDate, Comparator.nullsLast(Comparator.reverseOrder()))
                        .thenComparing(Student::getFullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .toList();

        return paginateStudents(filtered, institutionId, page, pageSize);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentDetailDto getStudentById(UUID institutionId, UUID studentId) {
        Student student = requireStudent(institutionId, studentId);
        StudentTotalsDto totals = calculateTotals(institutionId, studentId);
        String maskedNid = maskNationalId(student.getNationalIdEncrypted());

        return new StudentDetailDto(
                student.getId().toString(),
                student.getStudentRef() != null ? student.getStudentRef() : student.getId().toString(),
                student.getFullName(),
                student.getGrade(),
                student.getSection(),
                student.getStatus(),
                maskedNid,
                student.getParentName(),
                student.getParentPhone(),
                student.getParentEmail(),
                totals,
                student.getDeactivatedDate(),
                student.getDeactivationReason()
        );
    }

    @Override
    public StudentDetailDto enrollStudent(UUID institutionId, EnrollStudentRequest request, UUID actorId) {
        validateInstitution(institutionId);

        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required");
        }

        String studentRef;
        if (request.studentRef() == null || request.studentRef().isBlank() ||
                studentRepository.findByInstitutionIdAndStudentRef(institutionId, request.studentRef().trim()).isPresent()) {
            studentRef = generateUniqueStudentRef(institutionId);
        } else {
            studentRef = request.studentRef().trim();
        }

        String nationalId = request.nationalId() != null ? request.nationalId().trim() : null;
        String nidHash;
        String nidEncrypted;
        if (nationalId != null && !nationalId.isBlank()) {
            if (nationalId.length() != 14 || !nationalId.chars().allMatch(Character::isDigit)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "validation_failed: National ID must be exactly 14 numeric digits");
            }
            nidHash = computeHmac(nationalId);
            nidEncrypted = "enc_" + nationalId;
        } else {
            nidHash = computeHmac(UUID.randomUUID().toString());
            nidEncrypted = "enc_unspecified";
        }

        // Handle Guardian association if parent info is supplied
        UUID guardianId = null;
        if (request.parentEmail() != null && !request.parentEmail().isBlank()) {
            Optional<Guardian> existingGuardian = guardianRepository.findByEmail(request.parentEmail().trim().toLowerCase());
            if (existingGuardian.isPresent()) {
                guardianId = existingGuardian.get().getId();
            } else if (request.parentName() != null && !request.parentName().isBlank()) {
                Guardian newGuardian = new Guardian(
                        computeHmac(request.parentEmail()),
                        "enc_" + request.parentEmail(),
                        request.parentName().trim(),
                        request.parentEmail().trim().toLowerCase(),
                        request.parentPhone() != null ? request.parentPhone().trim() : "Unspecified",
                        null,
                        false
                );
                newGuardian = guardianRepository.save(newGuardian);
                guardianId = newGuardian.getId();
            }
        }

        Student student = new Student(
                guardianId,
                institutionId,
                nidHash,
                nidEncrypted,
                request.name().trim(),
                null,
                studentRef,
                request.grade() != null ? request.grade().trim() : null,
                request.section() != null ? request.section().trim() : null,
                request.parentName() != null ? request.parentName().trim() : null,
                request.parentPhone() != null ? request.parentPhone().trim() : null,
                request.parentEmail() != null ? request.parentEmail().trim() : null
        );
        student.setStatus("Active");
        student = studentRepository.save(student);

        logAudit(actorId, institutionId, "ENROLL_STUDENT", "Enrolled student: " + student.getFullName() + " (Ref: " + studentRef + ")");

        StudentTotalsDto totals = new StudentTotalsDto(BigDecimal.ZERO, BigDecimal.ZERO, BigDecimal.ZERO);
        return new StudentDetailDto(
                student.getId().toString(),
                student.getStudentRef(),
                student.getFullName(),
                student.getGrade(),
                student.getSection(),
                student.getStatus(),
                maskNationalId(nidEncrypted),
                student.getParentName(),
                student.getParentPhone(),
                student.getParentEmail(),
                totals,
                null,
                null
        );
    }

    @Override
    public StudentDetailDto updateStudent(UUID institutionId, UUID studentId, UpdateStudentRequest request, UUID actorId) {
        Student student = requireStudent(institutionId, studentId);

        if (request.studentRef() != null && !request.studentRef().isBlank()) {
            String newRef = request.studentRef().trim();
            if (!newRef.equalsIgnoreCase(student.getStudentRef())) {
                if (studentRepository.findByInstitutionIdAndStudentRef(institutionId, newRef).isPresent()) {
                    throw new ResponseStatusException(HttpStatus.CONFLICT, "duplicate_student_ref: " + newRef);
                }
                student.setStudentRef(newRef);
            }
        }

        if (request.name() != null && !request.name().isBlank()) {
            student.setFullName(request.name().trim());
        }
        if (request.grade() != null) {
            student.setGrade(request.grade().trim());
        }
        if (request.section() != null) {
            student.setSection(request.section().trim());
        }
        if (request.parentName() != null) {
            student.setParentName(request.parentName().trim());
        }
        if (request.parentPhone() != null) {
            student.setParentPhone(request.parentPhone().trim());
        }
        if (request.parentEmail() != null) {
            student.setParentEmail(request.parentEmail().trim());
        }

        student = studentRepository.save(student);
        logAudit(actorId, institutionId, "UPDATE_STUDENT", "Updated student: " + student.getId());

        StudentTotalsDto totals = calculateTotals(institutionId, studentId);
        return new StudentDetailDto(
                student.getId().toString(),
                student.getStudentRef() != null ? student.getStudentRef() : student.getId().toString(),
                student.getFullName(),
                student.getGrade(),
                student.getSection(),
                student.getStatus(),
                maskNationalId(student.getNationalIdEncrypted()),
                student.getParentName(),
                student.getParentPhone(),
                student.getParentEmail(),
                totals,
                student.getDeactivatedDate(),
                student.getDeactivationReason()
        );
    }

    @Override
    public StudentSummaryDto deactivateStudent(UUID institutionId, UUID studentId, String reason, UUID actorId) {
        Student student = requireStudent(institutionId, studentId);

        if ("Inactive".equalsIgnoreCase(student.getStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "already_inactive");
        }

        student.setStatus("Inactive");
        student.setDeactivatedDate(LocalDate.now());
        student.setDeactivationReason(reason != null && !reason.isBlank() ? reason.trim() : "Withdrawn");
        student = studentRepository.save(student);

        logAudit(actorId, institutionId, "DEACTIVATE_STUDENT", "Deactivated student: " + student.getId() + " Reason: " + student.getDeactivationReason());

        StudentTotalsDto totals = calculateTotals(institutionId, studentId);
        return new StudentSummaryDto(
                student.getId().toString(),
                student.getStudentRef(),
                student.getFullName(),
                student.getGrade(),
                student.getSection(),
                totals.totalFeesEGP(),
                totals.totalPaidEGP(),
                totals.totalOutstandingEGP(),
                student.getStatus(),
                student.getDeactivatedDate(),
                student.getDeactivationReason()
        );
    }

    @Override
    public StudentSummaryDto reactivateStudent(UUID institutionId, UUID studentId, UUID actorId) {
        Student student = requireStudent(institutionId, studentId);

        student.setStatus("Active");
        student.setDeactivatedDate(null);
        student.setDeactivationReason(null);
        student = studentRepository.save(student);

        logAudit(actorId, institutionId, "REACTIVATE_STUDENT", "Reactivated student: " + student.getId());

        StudentTotalsDto totals = calculateTotals(institutionId, studentId);
        return new StudentSummaryDto(
                student.getId().toString(),
                student.getStudentRef(),
                student.getFullName(),
                student.getGrade(),
                student.getSection(),
                totals.totalFeesEGP(),
                totals.totalPaidEGP(),
                totals.totalOutstandingEGP(),
                student.getStatus(),
                null,
                null
        );
    }

    @Override
    @Transactional(readOnly = true)
    public StudentFeesResponse getStudentFees(UUID institutionId, UUID studentId) {
        requireStudent(institutionId, studentId);
        List<FeeLine> feeLines = feeLineRepository.findByInstitutionIdAndStudentId(institutionId, studentId);
        LocalDate today = LocalDate.now();

        List<StudentFeeItemDto> data = new ArrayList<>();
        BigDecimal totalFees = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;
        BigDecimal totalOutstanding = BigDecimal.ZERO;

        for (FeeLine fee : feeLines) {
            if (fee.getStatus() == FeeStatus.CANCELLED) {
                continue;
            }

            BigDecimal orig = nz(fee.getTotalAmount());
            BigDecimal paid = nz(fee.getPaidAmount());
            BigDecimal remaining = nz(fee.getRemainingAmount());

            totalFees = totalFees.add(orig);
            totalPaid = totalPaid.add(paid);
            totalOutstanding = totalOutstanding.add(remaining);

            String statusStr;
            if (remaining.compareTo(BigDecimal.ZERO) <= 0) {
                statusStr = "Paid";
            } else if (fee.getDueDate() != null && fee.getDueDate().isBefore(today)) {
                statusStr = "Overdue";
            } else if (paid.compareTo(BigDecimal.ZERO) > 0) {
                statusStr = "Partial";
            } else {
                statusStr = "Outstanding";
            }

            String feeName = fee.getFeeType().getDisplayName() + (fee.getCollectionPeriod() != null ? " - " + fee.getCollectionPeriod() : "");

            data.add(new StudentFeeItemDto(
                    fee.getId().toString(),
                    feeName,
                    fee.getFeeType().name(),
                    fee.getCollectionPeriod(),
                    fee.getDueDate(),
                    orig,
                    paid,
                    remaining,
                    statusStr
            ));
        }

        return new StudentFeesResponse(data, new StudentTotalsDto(totalFees, totalPaid, totalOutstanding));
    }

    @Override
    @Transactional(readOnly = true)
    public StudentPaymentsResponse getStudentPayments(UUID institutionId, UUID studentId, LocalDate dateFrom, LocalDate dateTo) {
        Student student = requireStudent(institutionId, studentId);
        List<PaymentAllocation> allocations = paymentAllocationRepository.findByInstitutionId(institutionId);

        List<StudentPaymentItemDto> list = new ArrayList<>();
        for (PaymentAllocation pa : allocations) {
            if (pa.getFeeLine() != null && studentId.equals(pa.getFeeLine().getStudentId())) {
                LocalDateTime createdAt = pa.getPayment().getCreatedAt();
                LocalDate txDate = createdAt != null ? createdAt.toLocalDate() : LocalDate.now();

                if (dateFrom != null && txDate.isBefore(dateFrom)) {
                    continue;
                }
                if (dateTo != null && txDate.isAfter(dateTo)) {
                    continue;
                }

                FeeLine fee = pa.getFeeLine();
                String feeName = fee.getFeeType().getDisplayName() + (fee.getCollectionPeriod() != null ? " - " + fee.getCollectionPeriod() : "");
                boolean isPartial = fee.getRemainingAmount() != null && fee.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0;

                list.add(new StudentPaymentItemDto(
                        pa.getPayment().getTransactionReference() != null ? pa.getPayment().getTransactionReference() : pa.getPayment().getId().toString(),
                        student.getId().toString(),
                        student.getFullName(),
                        fee.getId().toString(),
                        feeName,
                        fee.getDueDate(),
                        1,
                        nz(pa.getAmountApplied()),
                        pa.getPayment().getPaymentMethod() != null ? pa.getPayment().getPaymentMethod().name() : "Card",
                        txDate.toString(),
                        createdAt != null ? createdAt.toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm")) : "12:00",
                        pa.getPayment().getStatus() != null ? pa.getPayment().getStatus().name() : "Successful",
                        "Reconciled",
                        isPartial,
                        nz(fee.getTotalAmount()),
                        nz(fee.getRemainingAmount())
                ));
            }
        }

        list.sort(Comparator.comparing(StudentPaymentItemDto::date, Comparator.reverseOrder())
                .thenComparing(StudentPaymentItemDto::time, Comparator.reverseOrder()));

        return new StudentPaymentsResponse(list);
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentSearchDto> searchActiveStudents(UUID institutionId, String query) {
        validateInstitution(institutionId);
        List<Student> all = studentRepository.findByInstitutionId(institutionId);

        return all.stream()
                .filter(s -> "Active".equalsIgnoreCase(s.getStatus()))
                .filter(s -> matchesSearch(s, query))
                .sorted(Comparator.comparing(Student::getFullName, Comparator.nullsLast(String.CASE_INSENSITIVE_ORDER)))
                .map(s -> new StudentSearchDto(
                        s.getId().toString(),
                        s.getStudentRef() != null ? s.getStudentRef() : s.getId().toString(),
                        s.getFullName(),
                        s.getGrade(),
                        s.getSection(),
                        s.getStatus()
                ))
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public List<StudentGuardianDto> getStudentGuardians(UUID institutionId, UUID studentId) {
        Student student = requireStudent(institutionId, studentId);
        List<StudentGuardianDto> guardians = new ArrayList<>();

        if (student.getGuardianId() != null) {
            guardianRepository.findById(student.getGuardianId()).ifPresent(g -> {
                guardians.add(new StudentGuardianDto(
                        g.getId().toString(),
                        g.getName(),
                        g.getEmail(),
                        g.getPhone(),
                        "Guardian",
                        true
                ));
            });
        }

        if (guardians.isEmpty() && student.getParentName() != null && !student.getParentName().isBlank()) {
            guardians.add(new StudentGuardianDto(
                    "contact-" + student.getId(),
                    student.getParentName(),
                    student.getParentEmail(),
                    student.getParentPhone(),
                    "Parent",
                    true
            ));
        }

        return guardians;
    }

    @Override
    public StudentGuardianDto linkGuardian(UUID institutionId, UUID studentId, LinkGuardianRequest request, UUID actorId) {
        Student student = requireStudent(institutionId, studentId);

        if (request.name() == null || request.name().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Guardian name is required");
        }

        student.setParentName(request.name().trim());
        if (request.phone() != null) {
            student.setParentPhone(request.phone().trim());
        }
        if (request.email() != null) {
            student.setParentEmail(request.email().trim());
        }

        UUID guardianId = null;
        if (request.email() != null && !request.email().isBlank()) {
            Optional<Guardian> existing = guardianRepository.findByEmail(request.email().trim().toLowerCase());
            if (existing.isPresent()) {
                guardianId = existing.get().getId();
            } else {
                Guardian created = new Guardian(
                        computeHmac(request.email()),
                        "enc_" + request.email(),
                        request.name().trim(),
                        request.email().trim().toLowerCase(),
                        request.phone() != null ? request.phone().trim() : "Unspecified",
                        null,
                        false
                );
                created = guardianRepository.save(created);
                guardianId = created.getId();
            }
            student.setGuardianId(guardianId);
        }

        student = studentRepository.save(student);
        logAudit(actorId, institutionId, "LINK_GUARDIAN", "Linked guardian: " + request.name() + " to student: " + studentId);

        return new StudentGuardianDto(
                guardianId != null ? guardianId.toString() : "contact-" + student.getId(),
                student.getParentName(),
                student.getParentEmail(),
                student.getParentPhone(),
                request.relationship() != null ? request.relationship() : "Guardian",
                Boolean.TRUE.equals(request.primaryGuardian())
        );
    }

    @Override
    public void unlinkGuardian(UUID institutionId, UUID studentId, UUID guardianId, UUID actorId) {
        Student student = requireStudent(institutionId, studentId);

        if (student.getGuardianId() != null && student.getGuardianId().equals(guardianId)) {
            student.setGuardianId(null);
        }
        student.setParentName(null);
        student.setParentPhone(null);
        student.setParentEmail(null);
        studentRepository.save(student);

        logAudit(actorId, institutionId, "UNLINK_GUARDIAN", "Unlinked guardian: " + guardianId + " from student: " + studentId);
    }

    @Override
    @Transactional(readOnly = true)
    public StudentStatementResponse getStudentStatement(UUID institutionId, UUID studentId) {
        Student student = requireStudent(institutionId, studentId);
        Institution institution = institutionRepository.findById(institutionId).orElse(null);
        String schoolName = institution != null ? institution.getName() : "Institution";

        List<StatementTransactionDto> ledger = new ArrayList<>();
        BigDecimal totalInvoiced = BigDecimal.ZERO;
        BigDecimal totalPaid = BigDecimal.ZERO;

        // Add fee lines as debits
        List<FeeLine> feeLines = feeLineRepository.findByInstitutionIdAndStudentId(institutionId, studentId);
        for (FeeLine fee : feeLines) {
            if (fee.getStatus() == FeeStatus.CANCELLED) {
                continue;
            }
            BigDecimal amt = nz(fee.getTotalAmount());
            totalInvoiced = totalInvoiced.add(amt);
            ledger.add(new StatementTransactionDto(
                    fee.getDueDate() != null ? fee.getDueDate().toString() : LocalDate.now().toString(),
                    "INVOICE",
                    fee.getId().toString(),
                    fee.getFeeType().getDisplayName() + (fee.getCollectionPeriod() != null ? " - " + fee.getCollectionPeriod() : ""),
                    amt,
                    BigDecimal.ZERO,
                    BigDecimal.ZERO // calculated in running balance
            ));
        }

        // Add payments as credits
        List<PaymentAllocation> allocations = paymentAllocationRepository.findByInstitutionId(institutionId);
        for (PaymentAllocation pa : allocations) {
            if (pa.getFeeLine() != null && studentId.equals(pa.getFeeLine().getStudentId())) {
                BigDecimal amt = nz(pa.getAmountApplied());
                totalPaid = totalPaid.add(amt);
                LocalDateTime createdAt = pa.getPayment().getCreatedAt();
                String dateStr = createdAt != null ? createdAt.toLocalDate().toString() : LocalDate.now().toString();

                ledger.add(new StatementTransactionDto(
                        dateStr,
                        "PAYMENT",
                        pa.getPayment().getTransactionReference() != null ? pa.getPayment().getTransactionReference() : pa.getPayment().getId().toString(),
                        "Payment received for " + pa.getFeeLine().getFeeType().getDisplayName(),
                        BigDecimal.ZERO,
                        amt,
                        BigDecimal.ZERO // calculated in running balance
                ));
            }
        }

        // Sort chronologically by date
        ledger.sort(Comparator.comparing(StatementTransactionDto::date));

        // Compute running balance step-by-step
        List<StatementTransactionDto> calculatedLedger = new ArrayList<>();
        BigDecimal runningBalance = BigDecimal.ZERO;
        for (StatementTransactionDto tx : ledger) {
            runningBalance = runningBalance.add(tx.debitEGP()).subtract(tx.creditEGP());
            calculatedLedger.add(new StatementTransactionDto(
                    tx.date(),
                    tx.type(),
                    tx.reference(),
                    tx.description(),
                    tx.debitEGP(),
                    tx.creditEGP(),
                    runningBalance
            ));
        }

        return new StudentStatementResponse(
                student.getId().toString(),
                student.getStudentRef() != null ? student.getStudentRef() : student.getId().toString(),
                student.getFullName(),
                schoolName,
                student.getGrade(),
                Instant.now().toString(),
                totalInvoiced,
                totalPaid,
                runningBalance,
                calculatedLedger
        );
    }

    private Student requireStudent(UUID institutionId, UUID studentId) {
        validateInstitution(institutionId);
        Student student = studentRepository.findById(studentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Student not found: " + studentId));

        if (!institutionId.equals(student.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "cross_school_access: Student does not belong to the requested institution");
        }
        return student;
    }

    private StudentTotalsDto calculateTotals(UUID institutionId, UUID studentId) {
        List<FeeLine> fees = feeLineRepository.findByInstitutionIdAndStudentId(institutionId, studentId);
        BigDecimal total = BigDecimal.ZERO;
        BigDecimal paid = BigDecimal.ZERO;
        BigDecimal outstanding = BigDecimal.ZERO;

        for (FeeLine f : fees) {
            if (f.getStatus() != FeeStatus.CANCELLED) {
                total = total.add(nz(f.getTotalAmount()));
                paid = paid.add(nz(f.getPaidAmount()));
                outstanding = outstanding.add(nz(f.getRemainingAmount()));
            }
        }
        return new StudentTotalsDto(total, paid, outstanding);
    }

    private PageResponse<StudentSummaryDto> paginateStudents(List<Student> filtered, UUID institutionId, int page, int pageSize) {
        int safePage = page > 0 ? page : 1;
        int safeSize = pageSize > 0 ? pageSize : 25;
        int total = filtered.size();
        int totalPages = total == 0 ? 0 : (int) Math.ceil((double) total / safeSize);

        int start = Math.min((safePage - 1) * safeSize, total);
        int end = Math.min(start + safeSize, total);
        List<Student> sublist = filtered.subList(start, end);

        List<StudentSummaryDto> data = sublist.stream().map(s -> {
            StudentTotalsDto totals = calculateTotals(institutionId, s.getId());
            return new StudentSummaryDto(
                    s.getId().toString(),
                    s.getStudentRef() != null ? s.getStudentRef() : s.getId().toString(),
                    s.getFullName(),
                    s.getGrade(),
                    s.getSection(),
                    totals.totalFeesEGP(),
                    totals.totalPaidEGP(),
                    totals.totalOutstandingEGP(),
                    s.getStatus(),
                    s.getDeactivatedDate(),
                    s.getDeactivationReason()
            );
        }).toList();

        return new PageResponse<>(data, safePage, safeSize, (long) total, totalPages);
    }

    private boolean matchesSearch(Student s, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String q = search.trim().toLowerCase();
        boolean nameMatch = s.getFullName() != null && s.getFullName().toLowerCase().contains(q);
        boolean refMatch = s.getStudentRef() != null && s.getStudentRef().toLowerCase().contains(q);
        return nameMatch || refMatch;
    }

    private boolean matchesGrade(Student s, String grade) {
        if (grade == null || grade.isBlank() || "All Grades".equalsIgnoreCase(grade.trim())) {
            return true;
        }
        if (s.getGrade() == null || s.getGrade().isBlank()) {
            return false;
        }
        String sGrade = s.getGrade().trim();
        String qGrade = grade.trim();
        if (sGrade.equalsIgnoreCase(qGrade)) {
            return true;
        }

        // Normalize by removing case-insensitive "grade" / "gr" and extra non-alphanumeric characters
        String sNorm = sGrade.replaceAll("(?i)\\bgrade\\b|\\bgr\\b", "").replaceAll("[^a-zA-Z0-9]", "").trim();
        String qNorm = qGrade.replaceAll("(?i)\\bgrade\\b|\\bgr\\b", "").replaceAll("[^a-zA-Z0-9]", "").trim();

        if (!sNorm.isEmpty() && !qNorm.isEmpty()) {
            if (sNorm.matches("\\d+") && qNorm.matches("\\d+")) {
                try {
                    return Integer.parseInt(sNorm) == Integer.parseInt(qNorm);
                } catch (NumberFormatException ignored) {
                }
            }
            return sNorm.equalsIgnoreCase(qNorm);
        }
        return sGrade.equalsIgnoreCase(qGrade);
    }

    private String generateUniqueStudentRef(UUID institutionId) {
        List<Student> all = studentRepository.findByInstitutionId(institutionId);
        Set<String> existing = all.stream()
                .map(Student::getStudentRef)
                .filter(Objects::nonNull)
                .map(String::trim)
                .collect(Collectors.toSet());

        for (int i = 1; i <= 9999; i++) {
            String candidate = String.format("STU-%03d", i);
            if (!existing.contains(candidate)) {
                return candidate;
            }
        }
        return "STU-" + UUID.randomUUID().toString().substring(0, 6).toUpperCase();
    }

    private boolean matchesDateRange(LocalDate target, LocalDate from, LocalDate to) {
        if (target == null) {
            return false;
        }
        if (from != null && target.isBefore(from)) {
            return false;
        }
        if (to != null && target.isAfter(to)) {
            return false;
        }
        return true;
    }

    private String maskNationalId(String rawEncrypted) {
        if (rawEncrypted == null || rawEncrypted.isBlank()) {
            return null;
        }
        String val = rawEncrypted.startsWith("enc_") ? rawEncrypted.substring(4) : rawEncrypted;
        if (val.length() == 14 && val.chars().allMatch(Character::isDigit)) {
            return val.substring(0, 3) + "*******" + val.substring(10);
        }
        if (val.length() >= 7) {
            return val.substring(0, 3) + "*******" + val.substring(val.length() - 4);
        }
        return "***";
    }

    private String computeHmac(String raw) {
        if (identityResolverService != null) {
            return identityResolverService.computeHmacSha256(raw);
        }
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder hexString = new StringBuilder();
            for (byte b : hash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) hexString.append('0');
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            return raw;
        }
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
