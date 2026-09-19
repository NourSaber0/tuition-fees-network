package com.tuitionnetwork.payments.service;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeePriority;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.dto.FeeDeadlineSnapshot;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.billing.service.FeeDeadlineService;
import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.identity.domain.Guardian;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.dto.ResolvedGuardianDto;
import com.tuitionnetwork.identity.repository.GuardianRepository;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.identity.service.IdentityResolverService;
import com.tuitionnetwork.t24.dto.T24BillingDto.RetrieveBillingResponse;
import com.tuitionnetwork.t24.dto.T24BillingDto.BillingItem;
import com.tuitionnetwork.t24.service.T24CustomerBillingService;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.domain.Receipt;
import com.tuitionnetwork.payments.dto.AllocatedDueDetailDto;
import com.tuitionnetwork.payments.dto.CustomerDto;
import com.tuitionnetwork.payments.dto.CustomerFeeItemDto;
import com.tuitionnetwork.payments.dto.CustomerFeesResponse;
import com.tuitionnetwork.payments.dto.PartialPaymentDto;
import com.tuitionnetwork.payments.dto.ReceiptDetailDto;
import com.tuitionnetwork.payments.dto.TimelineEventDto;
import com.tuitionnetwork.payments.dto.TransactionDetailDto;
import com.tuitionnetwork.payments.dto.TransactionDto;
import com.tuitionnetwork.payments.dto.TransactionTabCountsDto;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.payments.repository.ReceiptRepository;
import jakarta.persistence.criteria.Predicate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.data.jpa.domain.Specification;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@Transactional(readOnly = true)
public class TransactionQueryServiceImpl implements TransactionQueryService {

    private final PaymentRepository paymentRepository;
    private final FeeLineRepository feeLineRepository;
    private final StudentRepository studentRepository;
    private final InstitutionRepository institutionRepository;
    private final GuardianRepository guardianRepository;
    private final IdentityResolverService identityResolverService;
    private final AuditLogRepository auditLogRepository;
    private final ReceiptRepository receiptRepository;
    private final FeeDeadlineService feeDeadlineService;

    @Autowired(required = false)
    private T24CustomerBillingService t24CustomerBillingService;
    @Autowired(required = false)
    private com.tuitionnetwork.identity.infrastructure.MockBankCustomerClient mockBankCustomerClient;

    @Autowired
    public TransactionQueryServiceImpl(PaymentRepository paymentRepository,
                                       FeeLineRepository feeLineRepository,
                                       StudentRepository studentRepository,
                                       InstitutionRepository institutionRepository,
                                       GuardianRepository guardianRepository,
                                       IdentityResolverService identityResolverService,
                                       @Autowired(required = false) AuditLogRepository auditLogRepository,
                                       ReceiptRepository receiptRepository,
                                       FeeDeadlineService feeDeadlineService,
                                       @Autowired(required = false) T24CustomerBillingService t24CustomerBillingService, com.tuitionnetwork.identity.infrastructure.MockBankCustomerClient mockBankCustomerClient) {
        this.paymentRepository = paymentRepository;
        this.feeLineRepository = feeLineRepository;
        this.studentRepository = studentRepository;
        this.institutionRepository = institutionRepository;
        this.guardianRepository = guardianRepository;
        this.identityResolverService = identityResolverService;
        this.auditLogRepository = auditLogRepository;
        this.receiptRepository = receiptRepository;
        this.feeDeadlineService = feeDeadlineService;
        this.t24CustomerBillingService = t24CustomerBillingService;
        this.mockBankCustomerClient = mockBankCustomerClient;
    }

    @Override
    public PageResponse<TransactionDto> listTransactions(
            String status,
            String search,
            String institution,
            String institutionType,
            String method,
            LocalDate dateFrom,
            LocalDate dateTo,
            String priority,
            String dueBucket,
            int page,
            int pageSize) {

        int resolvedPage = Math.max(0, page > 0 ? page - 1 : page);
        int resolvedSize = pageSize > 0 ? pageSize : 25;

        Specification<Payment> spec = (root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();

            if (status != null && !status.isBlank() && !status.equalsIgnoreCase("ALL")) {
                if (status.equalsIgnoreCase("SUCCESSFUL")) {
                    predicates.add(root.get("status").in(PaymentStatus.CAPTURED));
                } else if (status.equalsIgnoreCase("PENDING")) {
                    predicates.add(root.get("status").in(PaymentStatus.PENDING, PaymentStatus.AUTHORIZED));
                } else if (status.equalsIgnoreCase("FAILED")) {
                    predicates.add(root.get("status").in(PaymentStatus.FAILED));
                } else {
                    try {
                        PaymentStatus ps = PaymentStatus.valueOf(status.toUpperCase());
                        predicates.add(cb.equal(root.get("status"), ps));
                    } catch (IllegalArgumentException ignored) {
                    }
                }
            }

            if (dateFrom != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), dateFrom.atStartOfDay()));
            }
            if (dateTo != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), dateTo.atTime(23, 59, 59)));
            }

            if (search != null && !search.isBlank()) {
                String searchLower = "%" + search.trim().toLowerCase() + "%";
                Predicate bankRefLike = cb.like(cb.lower(root.get("transactionReference")), searchLower);
                Predicate authLike = cb.like(cb.lower(root.get("authCode")), searchLower);
                Predicate idempLike = cb.like(cb.lower(root.get("idempotencyKey")), searchLower);
                predicates.add(cb.or(bankRefLike, authLike, idempLike));
            }

            if (method != null && !method.isBlank()) {
                String m = method.toUpperCase();
                if (m.contains("CREDIT")) {
                    predicates.add(cb.equal(root.get("paymentMethod"), PaymentMethod.CREDIT_CARD));
                } else if (m.contains("EPP")) {
                    predicates.add(cb.equal(root.get("paymentMethod"), PaymentMethod.EPP_INSTALMENTS));
                } else if (m.contains("DEBIT") || m.contains("ACCOUNT") || m.contains("TRANSFER")) {
                    predicates.add(cb.equal(root.get("paymentMethod"), PaymentMethod.CIB_ACCOUNT));
                }
            }

            return cb.and(predicates.toArray(new Predicate[0]));
        };

        boolean needsDerivedFilter = (priority != null && !priority.isBlank()) || (dueBucket != null && !dueBucket.isBlank());
        if (!needsDerivedFilter) {
            Page<Payment> paymentPage = paymentRepository.findAll(
                    spec,
                    PageRequest.of(resolvedPage, resolvedSize, Sort.by(Sort.Direction.DESC, "createdAt"))
            );
            return PageResponse.from(paymentPage, this::mapToTransactionDto);
        }

        // priority/dueBucket are derived from the fee line's dueDate, not a Payment column, so they
        // can't be pushed into the DB predicate above. Filter in memory, then paginate the result.
        List<TransactionDto> matching = paymentRepository.findAll(spec, Sort.by(Sort.Direction.DESC, "createdAt")).stream()
                .map(this::mapToTransactionDto)
                .filter(tx -> matchesPriority(tx, priority))
                .filter(tx -> matchesDueBucket(tx, dueBucket))
                .toList();

        int fromIndex = Math.min(resolvedPage * resolvedSize, matching.size());
        int toIndex = Math.min(fromIndex + resolvedSize, matching.size());
        var pageImpl = new org.springframework.data.domain.PageImpl<>(
                matching.subList(fromIndex, toIndex),
                PageRequest.of(resolvedPage, resolvedSize),
                matching.size()
        );
        return PageResponse.from(pageImpl, java.util.function.Function.identity());
    }

    private boolean matchesPriority(TransactionDto tx, String priority) {
        return priority == null || priority.isBlank() || priority.equalsIgnoreCase(tx.priority());
    }

    private boolean matchesDueBucket(TransactionDto tx, String dueBucket) {
        if (dueBucket == null || dueBucket.isBlank() || tx.daysToDue() == null) {
            return true;
        }
        return switch (dueBucket.toLowerCase()) {
            case "today" -> tx.daysToDue() == 0;
            case "this-week" -> tx.daysToDue() >= 0 && tx.daysToDue() <= 6;
            case "overdue" -> tx.daysToDue() < 0;
            default -> true;
        };
    }

    @Override
    public TransactionTabCountsDto getTabCounts() {
        long all = paymentRepository.count();
        long successful = paymentRepository.countByStatus(PaymentStatus.CAPTURED);
        long pending = paymentRepository.countByStatusIn(List.of(PaymentStatus.PENDING, PaymentStatus.AUTHORIZED));
        long failed = paymentRepository.countByStatus(PaymentStatus.FAILED);
        return new TransactionTabCountsDto(all, successful, pending, failed);
    }

    @Override
    public TransactionDetailDto getTransactionDetail(UUID id) {
        Payment payment = paymentRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Transaction not found: " + id));

        TransactionDto base = mapToTransactionDto(payment);

        List<TimelineEventDto> timeline = new ArrayList<>();
        timeline.add(new TimelineEventDto("Initiated", payment.getCreatedAt(), true));

        boolean authDone = payment.getStatus() != PaymentStatus.PENDING;
        timeline.add(new TimelineEventDto("Bank Authorisation", payment.getCreatedAt().plusSeconds(1), authDone));

        boolean capturedDone = payment.getStatus() == PaymentStatus.CAPTURED;
        timeline.add(new TimelineEventDto("Captured", payment.getCreatedAt().plusSeconds(2), capturedDone));

        boolean settlementDone = payment.getStatus() == PaymentStatus.CAPTURED;
        timeline.add(new TimelineEventDto("Settlement", payment.getCreatedAt().plusDays(1), settlementDone));

        timeline.add(new TimelineEventDto("Reconciliation", null, false));

        List<AllocatedDueDetailDto> allocations = new ArrayList<>();
        if (payment.getAllocations() != null) {
            for (PaymentAllocation pa : payment.getAllocations()) {
                FeeLine fl = pa.getFeeLine();
                if (fl != null) {
                    allocations.add(new AllocatedDueDetailDto(
                            fl.getId(),
                            fl.getFeeType() != null ? fl.getFeeType().name() : "Tuition",
                            pa.getAmountApplied(),
                            fl.getCollectionPeriod()
                    ));
                }
            }
        }

        return new TransactionDetailDto(
                base.id(),
                base.institution(),
                base.institutionType(),
                base.student(),
                base.feeType(),
                base.amountEGP(),
                base.method(),
                base.status(),
                base.bankRef(),
                base.settlementStatus(),
                base.reconStatus(),
                base.timestamp(),
                base.partial(),
                base.idempotencyKey(),
                base.channel(),
                base.dueDate(),
                base.priority(),
                base.daysToDue(),
                base.outstandingEGP(),
                base.penaltyEGP(),
                base.penaltyAppliedAt(),
                base.graceEnded(),
                base.totalDueEGP(),
                base.penaltyPaidEGP(),
                base.penaltyRemainingEGP(),
                timeline,
                allocations
        );
    }

    @Override
    public byte[] exportTransactionsCsv(
            String status,
            String search,
            String institution,
            String institutionType,
            String method,
            LocalDate dateFrom,
            LocalDate dateTo) {

        PageResponse<TransactionDto> page = listTransactions(status, search, institution, institutionType, method, dateFrom, dateTo, null, null, 0, 5000);

        StringBuilder sb = new StringBuilder();
        sb.append("Transaction ID,Timestamp,Institution,Institution Type,Student,Fee Type,Amount (EGP),Method,Status,Bank Ref,Settlement Status,Recon Status,Channel\n");

        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
        for (TransactionDto tx : page.data()) {
            sb.append('"').append(tx.id()).append("\",")
                    .append('"').append(tx.timestamp() != null ? tx.timestamp().format(fmt) : "").append("\",")
                    .append('"').append(escapeCsv(tx.institution())).append("\",")
                    .append('"').append(escapeCsv(tx.institutionType())).append("\",")
                    .append('"').append(escapeCsv(tx.student())).append("\",")
                    .append('"').append(escapeCsv(tx.feeType())).append("\",")
                    .append(tx.amountEGP()).append(',')
                    .append('"').append(escapeCsv(tx.method())).append("\",")
                    .append('"').append(escapeCsv(tx.status())).append("\",")
                    .append('"').append(escapeCsv(tx.bankRef())).append("\",")
                    .append('"').append(escapeCsv(tx.settlementStatus())).append("\",")
                    .append('"').append(escapeCsv(tx.reconStatus())).append("\",")
                    .append('"').append(escapeCsv(tx.channel())).append("\"\n");
        }

        return sb.toString().getBytes(StandardCharsets.UTF_8);
    }

    @Override
    @Transactional
    public CustomerFeesResponse lookupCustomerFees(String rawNationalId) {
        if (rawNationalId == null || !rawNationalId.trim().matches("^\\d{14}$")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "National ID must be exactly 14 digits.");
        }

        String nationalId = rawNationalId.trim();
        String masked = nationalId.substring(0, 3) + "*******" + nationalId.substring(10);

        // 1. Check Guardian
        Optional<ResolvedGuardianDto> guardianOpt = identityResolverService.resolveGuardianByNationalId(nationalId);
        if (guardianOpt.isPresent()) {
            ResolvedGuardianDto guardian = guardianOpt.get();
            List<Student> students = studentRepository.findByGuardianId(guardian.id());
            Student primaryStudent = students.isEmpty() ? null : students.get(0);

            Institution inst = null;
            if (primaryStudent != null && primaryStudent.getInstitutionId() != null) {
                inst = institutionRepository.findById(primaryStudent.getInstitutionId()).orElse(null);
            }

            String gradeText;
            if (students.size() == 1) {
                String g = students.get(0).getGrade();
                gradeText = (g != null && !g.isBlank()) ? g : "1 Enrolled Student";
            } else if (students.size() > 1) {
                gradeText = students.size() + " Enrolled Students";
            } else {
                gradeText = "Guardian Account";
            }

            CustomerDto customer = new CustomerDto(
                    guardian.fullName(),
                    masked,
                    inst != null ? inst.getName() : "-",
                    inst != null && inst.getInstitutionType() != null ? inst.getInstitutionType().name() : "School",
                    gradeText
            );

            List<CustomerFeeItemDto> feeDtos = new ArrayList<>();
            for (Student student : students) {
                List<FeeLine> fees = feeLineRepository.findByStudentId(student.getId());
                for (FeeLine f : fees) {
                    feeDtos.add(toCustomerFeeItemDto(f, student.getFullName(), student.getGrade()));
                }
            }
            
            if (t24CustomerBillingService != null && guardian.linkedAccountId() != null) {
                try {
                    RetrieveBillingResponse t24Response = t24CustomerBillingService.retrieveCustomerDues(nationalId, guardian.linkedAccountId(), null);
                    if (t24Response != null && "SUCCESS".equals(t24Response.status()) && t24Response.items() != null) {
                        for (BillingItem item : t24Response.items()) {
                            feeDtos.add(new CustomerFeeItemDto(
                                    UUID.randomUUID().toString(),
                                    item.studentName() != null ? item.studentName() : "External",
                                    "External",
                                    item.feeType() != null ? item.feeType() : "T24 Fee",
                                    item.originalAmount() != null ? item.originalAmount() : item.remainingAmount(),
                                    item.paidAmount() != null ? item.paidAmount() : BigDecimal.ZERO,
                                    item.remainingAmount(),
                                    item.status() != null ? item.status() : "OUTSTANDING",
                                    true,
                                    item.dueDate(),
                                    "Normal",
                                    0L,
                                    BigDecimal.ZERO,
                                    item.remainingAmount()
                            ));
                        }
                    }
                } catch (Exception ignored) {
                }
            }

            auditCustomerSearch(masked);
            return new CustomerFeesResponse(customer, feeDtos);
        }

        // 2. Check Student directly
        String hmac = identityResolverService.computeHmacSha256(nationalId);
        Optional<Student> studentOpt = studentRepository.findByNationalIdHash(hmac);
        if (studentOpt.isPresent()) {
            Student student = studentOpt.get();
            Institution inst = student.getInstitutionId() != null
                    ? institutionRepository.findById(student.getInstitutionId()).orElse(null)
                    : null;

            String gradeText = (student.getGrade() != null && !student.getGrade().isBlank())
                    ? student.getGrade()
                    : "Student Account";

            CustomerDto customer = new CustomerDto(
                    student.getFullName(),
                    masked,
                    inst != null ? inst.getName() : "-",
                    inst != null && inst.getInstitutionType() != null ? inst.getInstitutionType().name() : "School",
                    gradeText
            );

            List<FeeLine> fees = feeLineRepository.findByStudentId(student.getId());
            List<CustomerFeeItemDto> feeDtos = new ArrayList<>();
            for (FeeLine f : fees) {
                feeDtos.add(toCustomerFeeItemDto(f, student.getFullName(), student.getGrade()));
            }

            auditCustomerSearch(masked);
            return new CustomerFeesResponse(customer, feeDtos);
        }

        // 3. Mock Bank fallback removed.

        throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found for National ID: " + masked);
    }

    @Override
    public ReceiptDetailDto getPaymentReceipt(UUID paymentId) {
        Payment payment = paymentRepository.findById(paymentId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Payment not found: " + paymentId));

        Optional<Receipt> receiptOpt = receiptRepository.findByPaymentId(paymentId);
        if (receiptOpt.isPresent()) {
            Receipt r = receiptOpt.get();
            return new ReceiptDetailDto(
                    r.getId(),
                    payment.getId(),
                    "RCP-" + payment.getId().toString().substring(0, 8).toUpperCase(),
                    payment.getTotalAmount(),
                    payment.getCurrency(),
                    r.getCryptoSignature(),
                    r.getFileUrl(),
                    r.getIssuedAt()
            );
        }

        return new ReceiptDetailDto(
                UUID.randomUUID(),
                payment.getId(),
                "RCP-" + payment.getId().toString().substring(0, 8).toUpperCase(),
                payment.getTotalAmount(),
                payment.getCurrency(),
                "sig_" + payment.getId(),
                "/receipts/" + payment.getId() + ".pdf",
                payment.getCreatedAt()
        );
    }

    private void auditCustomerSearch(String maskedNationalId) {
        if (auditLogRepository != null) {
            auditLogRepository.save(new AuditLog(
                    null,
                    "BACK_OFFICE",
                    "CUSTOMER_FEES_SEARCH",
                    "Customer dues lookup for National ID: " + maskedNationalId
            ));
        }
    }

    private CustomerFeeItemDto toCustomerFeeItemDto(FeeLine f, String studentName, String studentGrade) {
        FeeDeadlineSnapshot deadline = feeDeadlineService.computeSnapshot(f);
        return new CustomerFeeItemDto(
                f.getId().toString(),
                studentName,
                studentGrade,
                (f.getFeeType() != null ? f.getFeeType().name() : "Fee") + " - " + (f.getCollectionPeriod() != null ? f.getCollectionPeriod() : ""),
                f.getTotalAmount(),
                f.getPaidAmount() != null ? f.getPaidAmount() : BigDecimal.ZERO,
                f.getRemainingAmount(),
                f.getStatus() != null ? f.getStatus().name() : "OUTSTANDING",
                f.getStatus() != FeeStatus.PAID && f.getStatus() != FeeStatus.CANCELLED,
                deadline.dueDate(),
                deadline.priority().name(),
                deadline.daysToDue(),
                deadline.penaltyEGP(),
                deadline.totalDueEGP()
        );
    }

    private CustomerFeeItemDto toCustomerFeeItemDto(FeeLine f) {
        String studentName = null;
        String studentGrade = null;
        if (f.getStudentId() != null) {
            Optional<Student> st = studentRepository.findById(f.getStudentId());
            if (st.isPresent()) {
                studentName = st.get().getFullName();
                studentGrade = st.get().getGrade();
            }
        }
        return toCustomerFeeItemDto(f, studentName, studentGrade);
    }

    private TransactionDto mapToTransactionDto(Payment payment) {
        String institution = "-";
        String institutionType = "School";
        String studentName = "-";
        String feeType = "Tuition";

        BigDecimal originalAmount = BigDecimal.ZERO;
        BigDecimal previouslyPaid = BigDecimal.ZERO;
        FeeLine primaryFeeForDeadline = null;

        if (payment.getAllocations() != null && !payment.getAllocations().isEmpty()) {
            for (PaymentAllocation pa : payment.getAllocations()) {
                FeeLine fl = pa.getFeeLine();
                if (fl != null) {
                    originalAmount = originalAmount.add(fl.getTotalAmount() != null ? fl.getTotalAmount() : BigDecimal.ZERO);
                    BigDecimal prev = (fl.getPaidAmount() != null ? fl.getPaidAmount() : BigDecimal.ZERO)
                            .subtract(pa.getAmountApplied() != null ? pa.getAmountApplied() : BigDecimal.ZERO);
                    if (prev.compareTo(BigDecimal.ZERO) > 0) {
                        previouslyPaid = previouslyPaid.add(prev);
                    }
                }
            }

            FeeLine primaryFee = payment.getAllocations().get(0).getFeeLine();
            primaryFeeForDeadline = primaryFee;
            if (primaryFee != null) {
                if (primaryFee.getFeeType() != null) {
                    feeType = primaryFee.getFeeType().name();
                }
                if (primaryFee.getStudentId() != null) {
                    Student s = studentRepository.findById(primaryFee.getStudentId()).orElse(null);
                    if (s != null) {
                        studentName = s.getFullName();
                    }
                }
                if (primaryFee.getInstitutionId() != null) {
                    Institution inst = institutionRepository.findById(primaryFee.getInstitutionId()).orElse(null);
                    if (inst != null) {
                        institution = inst.getName();
                        if (inst.getInstitutionType() != null) {
                            institutionType = inst.getInstitutionType().name();
                        }
                    }
                }
            }
        }

        BigDecimal principalAllocated = BigDecimal.ZERO;
        if (payment.getAllocations() != null) {
            for (PaymentAllocation pa : payment.getAllocations()) {
                if (pa.getAmountApplied() != null) {
                    principalAllocated = principalAllocated.add(pa.getAmountApplied());
                }
            }
        }
        BigDecimal penaltyPaid = BigDecimal.ZERO;
        if (payment.getTotalAmount() != null && payment.getTotalAmount().compareTo(principalAllocated) > 0) {
            penaltyPaid = payment.getTotalAmount().subtract(principalAllocated);
        }

        FeeDeadlineSnapshot deadline = primaryFeeForDeadline != null
                ? feeDeadlineService.computeSnapshot(primaryFeeForDeadline)
                : null;

        BigDecimal totalPenaltyOnFee = deadline != null && deadline.penaltyEGP() != null ? deadline.penaltyEGP() : BigDecimal.ZERO;
        BigDecimal penaltyRemaining = totalPenaltyOnFee.subtract(penaltyPaid);
        if (penaltyRemaining.compareTo(BigDecimal.ZERO) < 0) {
            penaltyRemaining = BigDecimal.ZERO;
        }

        String method = "CIB Account";
        if (payment.getPaymentMethod() != null) {
            switch (payment.getPaymentMethod()) {
                case CREDIT_CARD -> {
                    if ((payment.getAuthCode() != null && payment.getAuthCode().contains("POS"))
                            || (payment.getTransactionReference() != null && payment.getTransactionReference().contains("POS"))) {
                        method = "POS Terminal";
                    } else {
                        method = "CIB Credit Card";
                    }
                }
                case EPP_INSTALMENTS -> method = "EPP";
                default -> method = "CIB Account";
            }
        }

        String channel = "Counter";
        if (payment.getTransactionReference() != null && payment.getTransactionReference().contains("SCH")) {
            channel = "School POS";
        } else if ((payment.getAuthCode() != null && payment.getAuthCode().contains("POS"))
                || (payment.getTransactionReference() != null && payment.getTransactionReference().contains("POS"))) {
            channel = "Branch POS";
        }

        String status = "Pending";
        if (payment.getStatus() != null) {
            switch (payment.getStatus()) {
                case CAPTURED -> status = "Successful";
                case FAILED -> status = "Failed";
                default -> status = "Pending";
            }
        }

        String bankRef = payment.getTransactionReference() != null
                ? payment.getTransactionReference()
                : (payment.getAuthCode() != null ? "BNK-" + payment.getAuthCode() : "BNK-CIB-" + payment.getId().toString().substring(0, 8).toUpperCase());

        String settlementStatus = payment.getStatus() == PaymentStatus.CAPTURED ? "Settled" : "Pending";
        String reconStatus = payment.getStatus() == PaymentStatus.CAPTURED ? "Matched" : "Pending";

        PartialPaymentDto partial = null;
        if (originalAmount.compareTo(BigDecimal.ZERO) > 0 && payment.getTotalAmount().compareTo(originalAmount) < 0) {
            partial = new PartialPaymentDto(originalAmount, previouslyPaid);
        }

        return new TransactionDto(
                payment.getId(),
                institution,
                institutionType,
                studentName,
                feeType,
                payment.getTotalAmount(),
                method,
                status,
                bankRef,
                settlementStatus,
                reconStatus,
                payment.getCreatedAt(),
                partial,
                payment.getIdempotencyKey(),
                channel,
                deadline != null ? deadline.dueDate() : null,
                deadline != null ? deadline.priority().name() : null,
                deadline != null ? deadline.daysToDue() : null,
                deadline != null ? deadline.outstandingEGP() : null,
                deadline != null ? deadline.penaltyEGP() : null,
                deadline != null ? deadline.penaltyAppliedAt() : null,
                deadline != null ? deadline.graceEnded() : null,
                deadline != null ? deadline.totalDueEGP() : null,
                penaltyPaid,
                penaltyRemaining
        );
    }

    private String escapeCsv(String val) {
        if (val == null) return "";
        return val.replace("\"", "\"\"");
    }
}
