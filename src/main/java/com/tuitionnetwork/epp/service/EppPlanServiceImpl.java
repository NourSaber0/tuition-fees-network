package com.tuitionnetwork.epp.service;

import com.tuitionnetwork.epp.dto.CardValidationResponse;
import com.tuitionnetwork.epp.dto.CreateEppPlanRequest;
import com.tuitionnetwork.epp.dto.EppPlanDetailDto;
import com.tuitionnetwork.epp.dto.EppPlanListResponse;
import com.tuitionnetwork.epp.dto.EppPlanSummaryDto;
import com.tuitionnetwork.epp.dto.EppProgressDto;
import com.tuitionnetwork.epp.dto.EppQuoteRequest;
import com.tuitionnetwork.epp.dto.EppQuoteResponse;
import com.tuitionnetwork.epp.dto.EppScheduleInstallmentDto;
import com.tuitionnetwork.epp.dto.EppSummaryResponse;
import com.tuitionnetwork.epp.dto.UpdateEppPlanStatusRequest;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.epp.infrastructure.MockBankEppClient;
import com.tuitionnetwork.epp.infrastructure.MockBankEppClient.EppPlanResponse;
import com.tuitionnetwork.epp.infrastructure.MockBankEppClient.EppQuoteDto;
import com.tuitionnetwork.payments.domain.CardBinClassifier;
import com.tuitionnetwork.payments.domain.EPPSchedule;
import com.tuitionnetwork.payments.domain.EppInstallment;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.repository.EPPScheduleRepository;
import com.tuitionnetwork.payments.repository.EppInstallmentRepository;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import com.tuitionnetwork.settings.service.SettingsService;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class EppPlanServiceImpl implements EppPlanService {

    private static final Set<String> TERMINAL_STATUSES = Set.of("CANCELLED", "DEFAULTED");
    private static final BigDecimal MIN_PRINCIPAL_EGP = BigDecimal.valueOf(5000);
    private static final BigDecimal MAX_PRINCIPAL_EGP = BigDecimal.valueOf(100000);
    private static final int MAX_PLANS_PER_STUDENT = 10;

    private final EPPScheduleRepository eppScheduleRepository;
    private final EppInstallmentRepository eppInstallmentRepository;
    private final PaymentRepository paymentRepository;
    private final InstitutionRepository institutionRepository;
    private final StudentRepository studentRepository;
    private final AuditLogRepository auditLogRepository;
    private final SettingsService settingsService;
    private final MockBankEppClient mockBankEppClient;

    @Autowired
    public EppPlanServiceImpl(EPPScheduleRepository eppScheduleRepository,
                               EppInstallmentRepository eppInstallmentRepository,
                               PaymentRepository paymentRepository,
                               InstitutionRepository institutionRepository,
                               StudentRepository studentRepository,
                               @Autowired(required = false) AuditLogRepository auditLogRepository,
                               SettingsService settingsService,
                               MockBankEppClient mockBankEppClient) {
        this.eppScheduleRepository = eppScheduleRepository;
        this.eppInstallmentRepository = eppInstallmentRepository;
        this.paymentRepository = paymentRepository;
        this.institutionRepository = institutionRepository;
        this.studentRepository = studentRepository;
        this.auditLogRepository = auditLogRepository;
        this.settingsService = settingsService;
        this.mockBankEppClient = mockBankEppClient;
    }

    public EppPlanServiceImpl(EPPScheduleRepository eppScheduleRepository,
                               EppInstallmentRepository eppInstallmentRepository,
                               PaymentRepository paymentRepository,
                               InstitutionRepository institutionRepository,
                               StudentRepository studentRepository,
                               SettingsService settingsService,
                               MockBankEppClient mockBankEppClient) {
        this(eppScheduleRepository, eppInstallmentRepository, paymentRepository, institutionRepository, studentRepository, null, settingsService, mockBankEppClient);
    }

    @Override
    public EppPlanListResponse listPlans(String search, String status, Integer tenor, int page, int pageSize) {
        List<EppPlanSummaryDto> all = eppScheduleRepository.findAll().stream()
                .sorted(Comparator.comparing((EPPSchedule s) -> s.getPayment().getCreatedAt()).reversed())
                .map(this::toSummary)
                .filter(dto -> status == null || status.isBlank() || status.equalsIgnoreCase(dto.status()))
                .filter(dto -> tenor == null || tenor == dto.tenor())
                .filter(dto -> matchesSearch(dto, search))
                .toList();

        int safePage = Math.max(page, 1);
        int safePageSize = Math.max(pageSize, 1);
        int fromIndex = Math.min((safePage - 1) * safePageSize, all.size());
        int toIndex = Math.min(fromIndex + safePageSize, all.size());
        List<EppPlanSummaryDto> pageContent = all.subList(fromIndex, toIndex);
        int totalPages = (int) Math.ceil(all.size() / (double) safePageSize);

        return new EppPlanListResponse(pageContent, safePage, safePageSize, all.size(), totalPages);
    }

    private boolean matchesSearch(EppPlanSummaryDto dto, String search) {
        if (search == null || search.isBlank()) {
            return true;
        }
        String needle = search.toLowerCase();
        return dto.id().toString().toLowerCase().contains(needle)
                || (dto.payRef() != null && dto.payRef().toLowerCase().contains(needle))
                || (dto.student() != null && dto.student().toLowerCase().contains(needle));
    }

    @Override
    public EppSummaryResponse getSummary() {
        List<EPPSchedule> schedules = eppScheduleRepository.findAll();

        long active = 0;
        long completed = 0;
        long defaulted = 0;
        BigDecimal totalOutstanding = BigDecimal.ZERO;

        for (EPPSchedule schedule : schedules) {
            String derivedStatus = deriveStatus(schedule);
            switch (derivedStatus) {
                case "Completed" -> completed++;
                case "Defaulted" -> defaulted++;
                case "Cancelled" -> { /* not counted as active or outstanding */ }
                default -> active++;
            }
            if (!derivedStatus.equals("Completed") && !derivedStatus.equals("Cancelled")) {
                BigDecimal paid = sumPaidInstallments(schedule);
                totalOutstanding = totalOutstanding.add(schedule.getTotalPayable().subtract(paid));
            }
        }

        return new EppSummaryResponse(active, completed, defaulted, totalOutstanding.setScale(2, RoundingMode.HALF_UP));
    }

    @Override
    public EppPlanDetailDto getPlan(UUID planId) {
        EPPSchedule schedule = findScheduleOrThrow(planId);
        return toDetail(schedule);
    }

    @Override
    public List<EppScheduleInstallmentDto> getSchedule(UUID planId) {
        EPPSchedule schedule = findScheduleOrThrow(planId);
        List<EppInstallment> recorded = eppInstallmentRepository.findByEppPlan(schedule);

        if (!recorded.isEmpty()) {
            return recorded.stream()
                    .sorted(Comparator.comparingInt(EppInstallment::getInstallmentNumber))
                    .map(i -> new EppScheduleInstallmentDto(
                            i.getInstallmentNumber(),
                            i.getDueDate().toLocalDate(),
                            null,
                            null,
                            i.getAmount(),
                            "PAID".equalsIgnoreCase(i.getStatus()) ? i.getAmount() : BigDecimal.ZERO,
                            displayInstallmentStatus(i.getStatus())
                    ))
                    .toList();
        }

        // No installment rows have been synced from the bank gateway yet (EppInstallment is a
        // read-only projection populated externally). Derive a projected schedule from the plan's
        // own terms so the UI still has something to render, all shown as Upcoming.
        LocalDate startDate = schedule.getPayment().getCreatedAt().toLocalDate();
        int tenor = schedule.getTenorMonths();
        BigDecimal principalPerInstalment = schedule.getPrincipalAmount()
                .divide(BigDecimal.valueOf(tenor), 2, RoundingMode.HALF_UP);
        BigDecimal interestPerInstalment = schedule.getInterestAmount()
                .divide(BigDecimal.valueOf(tenor), 2, RoundingMode.HALF_UP);

        return java.util.stream.IntStream.rangeClosed(1, tenor)
                .mapToObj(n -> new EppScheduleInstallmentDto(
                        n,
                        startDate.plusMonths(n),
                        principalPerInstalment,
                        interestPerInstalment,
                        schedule.getMonthlyInstalment(),
                        BigDecimal.ZERO,
                        "Upcoming"
                ))
                .toList();
    }

    @Override
    public EppQuoteResponse quote(EppQuoteRequest request) {
        if (request.principalEGP() == null || request.tenor() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "principalEGP and tenor are required");
        }
        
        List<EppQuoteDto> quotes = mockBankEppClient.getQuotes(request.principalEGP());
        EppQuoteDto match = quotes.stream()
                .filter(q -> q.tenor_months() == request.tenor())
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST, "No quote found for requested tenor"));

        BigDecimal totalRepayment = new BigDecimal(match.total_repayment());
        BigDecimal principal = request.principalEGP();
        BigDecimal interestAmount = totalRepayment.subtract(principal);
        BigDecimal annualInterestRate = match.annual_rate() != null ? BigDecimal.valueOf(match.annual_rate()) : BigDecimal.ZERO;
        BigDecimal adminFee = match.admin_fee() != null ? new BigDecimal(match.admin_fee()) : BigDecimal.ZERO;
        BigDecimal monthlyInstalment = new BigDecimal(match.monthly_installment());

        return new EppQuoteResponse(
                principal,
                match.tenor_months(),
                annualInterestRate,
                interestAmount,
                adminFee,
                totalRepayment,
                monthlyInstalment
        );
    }

    @Override
    public CardValidationResponse validateCard(String cardNumber) {
        if (cardNumber == null || cardNumber.isBlank()) {
            return CardValidationResponse.ineligible("Card number is required");
        }
        return CardBinClassifier.isDebitCard(cardNumber)
                ? CardValidationResponse.ineligible("Debit cards not eligible for EPP")
                : CardValidationResponse.eligible("CIB", "Credit", 18);
    }

    @Override
    public EppPlanDetailDto createPlan(CreateEppPlanRequest request) {
        if (request.sourcePaymentId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "sourcePaymentId is required");
        }
        Payment sourcePayment = paymentRepository.findById(request.sourcePaymentId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Source payment not found"));

        if (sourcePayment.getStatus() != PaymentStatus.CAPTURED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "source_payment_not_successful");
        }
        if (sourcePayment.getPaymentMethod() != PaymentMethod.CREDIT_CARD) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "card_not_eligible");
        }
        if (request.cardToken() != null && CardBinClassifier.isDebitCard(request.cardToken())) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, "card_not_eligible");
        }
        if (eppScheduleRepository.findByPaymentId(sourcePayment.getId()).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "epp_plan_already_exists_for_payment");
        }

        BigDecimal principal = request.principalEGP() != null ? request.principalEGP() : sourcePayment.getTotalAmount();
        if (principal.compareTo(MIN_PRINCIPAL_EGP) < 0 || principal.compareTo(MAX_PRINCIPAL_EGP) > 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "principal_out_of_range");
        }

        int tenor = request.tenor() != null ? request.tenor() : 12;

        UUID studentId = firstAllocation(sourcePayment).map(a -> a.getFeeLine().getStudentId()).orElse(null);
        if (studentId != null && countPlansForStudent(studentId) >= MAX_PLANS_PER_STUDENT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "max_plans_per_student_exceeded");
        }

        EppPlanResponse planResponse = mockBankEppClient.createPlan(sourcePayment.getTransactionReference(), tenor);

        BigDecimal totalRepayment = new BigDecimal(planResponse.total_repayment());
        BigDecimal interestAmount = totalRepayment.subtract(principal);
        BigDecimal monthlyInstalment = new BigDecimal(planResponse.monthly_installment());
        BigDecimal annualInterestRate = planResponse.annual_rate() != null ? BigDecimal.valueOf(planResponse.annual_rate()) : BigDecimal.ZERO;
        BigDecimal adminFee = planResponse.admin_fee() != null ? new BigDecimal(planResponse.admin_fee()) : BigDecimal.ZERO;

        EPPSchedule schedule = new EPPSchedule(
                sourcePayment,
                planResponse.tenor_months(),
                principal,
                annualInterestRate,
                interestAmount,
                adminFee,
                totalRepayment,
                monthlyInstalment
        );
        schedule = eppScheduleRepository.save(schedule);

        if (planResponse.schedule() != null) {
            for (MockBankEppClient.EppInstallmentDto inst : planResponse.schedule()) {
                EppInstallment eppInst = new EppInstallment(
                        schedule,
                        inst.installment_number(),
                        new BigDecimal(inst.amount()),
                        LocalDate.parse(inst.due_date()).atStartOfDay(),
                        "Upcoming"
                );
                eppInstallmentRepository.save(eppInst);
            }
        }

        if (auditLogRepository != null) {
            auditLogRepository.save(new AuditLog(
                    null,
                    "BACK_OFFICE",
                    "CREATE_EPP_PLAN",
                    "EPP Plan created for payment " + sourcePayment.getId() + ", tenor " + planResponse.tenor_months() + ", principal " + principal
            ));
        }

        return toDetail(schedule);
    }

    @Override
    public EppPlanDetailDto updateStatus(UUID planId, UpdateEppPlanStatusRequest request) {
        EPPSchedule schedule = findScheduleOrThrow(planId);
        if (request.status() == null || request.status().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status is required");
        }

        String normalized = request.status().trim().toUpperCase();
        if (!Set.of("ACTIVE", "COMPLETED", "DEFAULTED", "CANCELLED").contains(normalized)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "invalid status");
        }

        schedule.setStatus(normalized);
        schedule = eppScheduleRepository.save(schedule);

        if (auditLogRepository != null) {
            auditLogRepository.save(new AuditLog(
                    null,
                    "BACK_OFFICE",
                    "UPDATE_EPP_PLAN_STATUS",
                    "EPP Plan " + planId + " status updated to " + normalized + (request.reason() != null ? ", reason: " + request.reason() : "")
            ));
        }

        return toDetail(schedule);
    }

    private long countPlansForStudent(UUID studentId) {
        return eppScheduleRepository.findAll().stream()
                .filter(s -> firstAllocation(s.getPayment()).map(a -> a.getFeeLine().getStudentId()).map(studentId::equals).orElse(false))
                .count();
    }

    private EPPSchedule findScheduleOrThrow(UUID planId) {
        return eppScheduleRepository.findById(planId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "EPP plan not found"));
    }

    private Optional<PaymentAllocation> firstAllocation(Payment payment) {
        return payment.getAllocations().stream().findFirst();
    }

    private EppPlanSummaryDto toSummary(EPPSchedule schedule) {
        Payment payment = schedule.getPayment();
        Optional<PaymentAllocation> allocation = firstAllocation(payment);

        Institution inst = allocation
                .map(a -> a.getFeeLine().getInstitutionId())
                .flatMap(institutionRepository::findById)
                .orElse(null);

        String institution = inst != null ? inst.getName() : null;
        String institutionType = inst != null && inst.getInstitutionType() != null
                ? inst.getInstitutionType().name()
                : "SCHOOL";

        String student = allocation
                .map(a -> a.getFeeLine().getStudentId())
                .flatMap(studentRepository::findById)
                .map(Student::getFullName)
                .orElse(null);

        int paidInstallments = countPaidInstallments(schedule);

        return new EppPlanSummaryDto(
                schedule.getId(),
                payReference(payment),
                institution,
                institutionType,
                student,
                schedule.getPrincipalAmount(),
                schedule.getTenorMonths(),
                schedule.getAnnualInterestRate().multiply(BigDecimal.valueOf(100)),
                schedule.getInterestAmount(),
                schedule.getAdminFee(),
                schedule.getTotalPayable(),
                schedule.getMonthlyInstalment(),
                paidInstallments,
                deriveStatus(schedule),
                payment.getCreatedAt().toLocalDate()
        );
    }

    private EppPlanDetailDto toDetail(EPPSchedule schedule) {
        EppPlanSummaryDto summary = toSummary(schedule);
        EppProgressDto progress = new EppProgressDto(
                summary.paidInstallments(),
                schedule.getTenorMonths(),
                schedule.getTenorMonths() == 0 ? 0.0
                        : (summary.paidInstallments() * 100.0) / schedule.getTenorMonths()
        );

        return new EppPlanDetailDto(
                summary.id(),
                summary.payRef(),
                summary.institution(),
                summary.institutionType(),
                summary.student(),
                summary.principalEGP(),
                summary.tenor(),
                summary.interestRatePct(),
                summary.interestEGP(),
                summary.adminFeeEGP(),
                summary.totalEGP(),
                summary.monthlyEGP(),
                summary.paidInstallments(),
                summary.status(),
                summary.startDate(),
                summary.startDate() != null ? summary.startDate().plusMonths(1) : null,
                progress
        );
    }

    private String payReference(Payment payment) {
        return payment.getTransactionReference() != null
                ? payment.getTransactionReference()
                : payment.getId().toString();
    }

    private int countPaidInstallments(EPPSchedule schedule) {
        return (int) eppInstallmentRepository.findByEppPlan(schedule).stream()
                .filter(i -> "PAID".equalsIgnoreCase(i.getStatus()))
                .count();
    }

    private BigDecimal sumPaidInstallments(EPPSchedule schedule) {
        return eppInstallmentRepository.findByEppPlan(schedule).stream()
                .filter(i -> "PAID".equalsIgnoreCase(i.getStatus()))
                .map(EppInstallment::getAmount)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private String deriveStatus(EPPSchedule schedule) {
        String explicit = schedule.getStatus();
        if (explicit != null && TERMINAL_STATUSES.contains(explicit.toUpperCase())) {
            return capitalize(explicit);
        }
        int paid = countPaidInstallments(schedule);
        if (paid >= schedule.getTenorMonths()) {
            return "Completed";
        }
        return "Active";
    }

    private String displayInstallmentStatus(String rawStatus) {
        if (rawStatus == null) {
            return "Upcoming";
        }
        return switch (rawStatus.toUpperCase()) {
            case "PAID" -> "Paid";
            case "DUE" -> "Due";
            default -> "Upcoming";
        };
    }

    private String capitalize(String value) {
        String lower = value.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }
}
