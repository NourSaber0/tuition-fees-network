package com.tuitionnetwork.reporting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.common.util.CsvWriter;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.InstitutionType;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.payments.domain.EPPSchedule;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.repository.EPPScheduleRepository;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.reporting.catalogue.ReportCatalogue;
import com.tuitionnetwork.reporting.domain.GeneratedReport;
import com.tuitionnetwork.reporting.domain.ReportStatus;
import com.tuitionnetwork.reporting.dto.GenerateReportRequest;
import com.tuitionnetwork.reporting.dto.ReportCatalogueEntry;
import com.tuitionnetwork.reporting.dto.ReportColumn;
import com.tuitionnetwork.reporting.dto.ReportFilters;
import com.tuitionnetwork.reporting.dto.ReportHistoryEntry;
import com.tuitionnetwork.reporting.dto.ReportJobResponse;
import com.tuitionnetwork.reporting.dto.ReportPreview;
import com.tuitionnetwork.reporting.repository.GeneratedReportRepository;
import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
public class ReportsServiceImpl implements ReportsService {

    private static final int PREVIEW_LIMIT = 50;
    private static final List<PaymentStatus> CAPTURED_ONLY = List.of(PaymentStatus.CAPTURED);

    private final GeneratedReportRepository generatedReportRepository;
    private final PaymentRepository paymentRepository;
    private final FeeLineRepository feeLineRepository;
    private final EPPScheduleRepository eppScheduleRepository;
    private final InstitutionRepository institutionRepository;
    private final StudentRepository studentRepository;
    private final ObjectMapper objectMapper;
    private final AuditLogRepository auditLogRepository;

    @Autowired
    public ReportsServiceImpl(GeneratedReportRepository generatedReportRepository,
                              PaymentRepository paymentRepository,
                              FeeLineRepository feeLineRepository,
                              EPPScheduleRepository eppScheduleRepository,
                              InstitutionRepository institutionRepository,
                              StudentRepository studentRepository,
                              ObjectMapper objectMapper,
                              @Autowired(required = false) AuditLogRepository auditLogRepository) {
        this.generatedReportRepository = generatedReportRepository;
        this.paymentRepository = paymentRepository;
        this.feeLineRepository = feeLineRepository;
        this.eppScheduleRepository = eppScheduleRepository;
        this.institutionRepository = institutionRepository;
        this.studentRepository = studentRepository;
        this.objectMapper = objectMapper;
        this.auditLogRepository = auditLogRepository;
    }

    public ReportsServiceImpl(GeneratedReportRepository generatedReportRepository,
                              PaymentRepository paymentRepository,
                              FeeLineRepository feeLineRepository,
                              EPPScheduleRepository eppScheduleRepository,
                              InstitutionRepository institutionRepository,
                              StudentRepository studentRepository,
                              ObjectMapper objectMapper) {
        this(generatedReportRepository, paymentRepository, feeLineRepository, eppScheduleRepository,
                institutionRepository, studentRepository, objectMapper, null);
    }

    @Override
    public List<ReportCatalogueEntry> catalogue() {
        return ReportCatalogue.all().stream()
                .map(e -> {
                    LocalDateTime lastGen = generatedReportRepository
                            .findFirstByReportIdOrderByCreatedAtDesc(e.id())
                            .map(GeneratedReport::getCreatedAt)
                            .orElse(null);
                    return new ReportCatalogueEntry(
                            e.id(), e.title(), e.description(), e.category(),
                            e.formats(), e.singleDate(), e.contextFilters(),
                            e.available(), e.unavailableReason(), lastGen
                    );
                })
                .toList();
    }

    @Override
    @Transactional
    public ReportJobResponse generate(GenerateReportRequest request) {
        ReportCatalogueEntry entry = ReportCatalogue.find(request.reportId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unknown report type: " + request.reportId()));

        if (!entry.available()) {
            throw new ResponseStatusException(HttpStatus.UNPROCESSABLE_ENTITY, entry.unavailableReason());
        }

        String format = request.formatOrDefault();
        if (!"CSV".equals(format)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "unsupported_format_for_report");
        }

        LocalDate from;
        LocalDate to;
        if (entry.singleDate()) {
            from = request.date() != null ? request.date() : LocalDate.now();
            to = from;
        } else {
            from = request.dateFrom();
            to = request.dateTo();
            if (from == null || to == null) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "dateFrom and dateTo are required for this report.");
            }
            if (from.isAfter(to)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date_from_after_date_to");
            }
        }

        ReportData data = buildData(entry.id(), from, to, request.filtersOrEmpty());
        String csv = CsvWriter.toCsv(data.columns(), data.rows());

        List<Map<String, Object>> previewRows = data.rows().size() > PREVIEW_LIMIT
                ? data.rows().subList(0, PREVIEW_LIMIT)
                : data.rows();
        ReportPreview preview = new ReportPreview(data.columns(), previewRows, data.total(), data.note());

        String range = entry.singleDate() ? from.toString() : from + "_" + to;
        String filename = "report_" + entry.id() + "_" + range + "." + format.toLowerCase();

        GeneratedReport report = new GeneratedReport();
        report.setReportId(entry.id());
        report.setFormat(format);
        report.setParamsJson(describeParams(request, from, to));
        report.setFilename(filename);
        report.setCsvContent(csv);
        report.setPreviewJson(writeJson(preview));
        report.setRowCount(data.rows().size());
        report.setTotalLabel(data.total());
        report.setNoteLabel(data.note());
        report.setStatus(ReportStatus.READY);

        GeneratedReport saved = generatedReportRepository.save(report);

        if (auditLogRepository != null) {
            auditLogRepository.save(new AuditLog(
                    null,
                    "BACK_OFFICE",
                    "GENERATE_REPORT",
                    "Report " + entry.id() + " generated (" + format + ") with " + data.rows().size() + " rows"
            ));
        }

        return toJobResponse(saved, preview);
    }

    @Override
    @Transactional(readOnly = true)
    public ReportJobResponse getJob(UUID jobId) {
        GeneratedReport report = requireReport(jobId);
        ReportPreview preview = readPreview(report);
        return toJobResponse(report, preview);
    }

    @Override
    @Transactional(readOnly = true)
    public DownloadPayload download(UUID jobId) {
        GeneratedReport report = requireReport(jobId);
        byte[] bytes = (report.getCsvContent() == null ? "" : report.getCsvContent())
                .getBytes(StandardCharsets.UTF_8);
        return new DownloadPayload(report.getFilename(), bytes, "text/csv");
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportHistoryEntry> history(String reportId, int page, int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pr = PageRequest.of(safePage, safeSize);

        Page<GeneratedReport> result = (reportId == null || reportId.isBlank())
                ? generatedReportRepository.findAllByOrderByCreatedAtDesc(pr)
                : generatedReportRepository.findByReportIdOrderByCreatedAtDesc(reportId.trim(), pr);

        return PageResponse.from(result, r -> new ReportHistoryEntry(
                r.getId(), r.getReportId(), r.getFormat(), r.getFilename(), r.getRowCount(), r.getCreatedAt()));
    }

    // ── Aggregation ───────────────────────────────────────────────────────────

    private ReportData buildData(String reportId, LocalDate from, LocalDate to, ReportFilters filters) {
        return switch (reportId) {
            case "network-collections", "collections-by-institution" -> collectionsByInstitution(from, to, filters);
            case "collections-by-type" -> collectionsByType(from, to);
            case "daily-collections" -> collectionsByInstitution(from, to, filters);
            case "payments" -> paymentRegister(from, to, filters);
            case "failed-transactions" -> failedTransactions(from, to, filters);
            case "epp-report" -> eppPortfolio(filters);
            case "outstanding-balances" -> outstandingBalances(filters);
            default -> throw new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED,
                    "No aggregation wired for report: " + reportId);
        };
    }

    private ReportData collectionsByInstitution(LocalDate from, LocalDate to, ReportFilters filters) {
        Map<UUID, Institution> institutions = institutionIndex();
        List<Payment> payments = capturedPayments(from, to, filters);

        Map<String, long[]> counts = new LinkedHashMap<>();   // key -> [txCount]
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        Map<String, String> types = new LinkedHashMap<>();

        for (Payment p : payments) {
            Institution inst = resolveInstitution(p, institutions);
            String name = inst != null ? inst.getName() : "Unknown institution";
            counts.computeIfAbsent(name, k -> new long[1])[0]++;
            amounts.merge(name, nz(p.getTotalAmount()), BigDecimal::add);
            types.putIfAbsent(name, inst != null && inst.getInstitutionType() != null
                    ? capitalise(inst.getInstitutionType().name()) : "—");
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;
        for (String name : counts.keySet()) {
            rows.add(row(
                    "institution", name,
                    "type", types.get(name),
                    "txCount", counts.get(name)[0],
                    "amountEGP", amounts.get(name)));
            grandTotal = grandTotal.add(amounts.get(name));
        }

        return new ReportData(
                List.of(ReportColumn.of("institution", "Institution"),
                        ReportColumn.center("type", "Type"),
                        ReportColumn.right("txCount", "Transactions"),
                        ReportColumn.right("amountEGP", "Collections (EGP)")),
                rows,
                "EGP " + grandTotal.toPlainString() + " across " + payments.size() + " transactions",
                "Collections by institution · " + from + " to " + to);
    }

    private ReportData collectionsByType(LocalDate from, LocalDate to) {
        Map<UUID, Institution> institutions = institutionIndex();
        List<Payment> payments = capturedPayments(from, to, ReportFilters.empty());

        Map<String, long[]> counts = new LinkedHashMap<>();
        Map<String, BigDecimal> amounts = new LinkedHashMap<>();
        for (InstitutionType t : InstitutionType.values()) {
            counts.put(capitalise(t.name()), new long[1]);
            amounts.put(capitalise(t.name()), BigDecimal.ZERO);
        }
        counts.put("Unknown", new long[1]);
        amounts.put("Unknown", BigDecimal.ZERO);

        for (Payment p : payments) {
            Institution inst = resolveInstitution(p, institutions);
            String key = (inst != null && inst.getInstitutionType() != null)
                    ? capitalise(inst.getInstitutionType().name()) : "Unknown";
            counts.get(key)[0]++;
            amounts.merge(key, nz(p.getTotalAmount()), BigDecimal::add);
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        for (String key : counts.keySet()) {
            if (counts.get(key)[0] == 0) {
                continue;
            }
            rows.add(row("type", key, "txCount", counts.get(key)[0], "amountEGP", amounts.get(key)));
        }

        return new ReportData(
                List.of(ReportColumn.of("type", "Institution Type"),
                        ReportColumn.right("txCount", "Transactions"),
                        ReportColumn.right("amountEGP", "Collections (EGP)")),
                rows,
                payments.size() + " captured transactions",
                "Collections by institution type · " + from + " to " + to);
    }

    private ReportData paymentRegister(LocalDate from, LocalDate to, ReportFilters filters) {
        Map<UUID, Institution> institutions = institutionIndex();
        List<Payment> payments = paymentRepository
                .findByCreatedAtBetweenOrderByCreatedAtDesc(startOf(from), endOf(to)).stream()
                .filter(p -> matchesInstitution(p, institutions, filters))
                .filter(p -> matchesMethod(p, filters))
                .filter(p -> matchesStatus(p, filters))
                .filter(p -> matchesFeeType(p, filters))
                .toList();

        List<Map<String, Object>> rows = payments.stream().map(p -> row(
                "paymentId", p.getId(),
                "institution", nameOf(resolveInstitution(p, institutions)),
                "feeType", firstFeeType(p),
                "amountEGP", nz(p.getTotalAmount()),
                "method", methodLabel(p.getPaymentMethod()),
                "status", statusLabel(p.getStatus()))).collect(Collectors.toList());

        return new ReportData(
                List.of(ReportColumn.of("paymentId", "Payment ID"),
                        ReportColumn.of("institution", "Institution"),
                        ReportColumn.of("feeType", "Fee Type"),
                        ReportColumn.right("amountEGP", "Amount (EGP)"),
                        ReportColumn.center("method", "Method"),
                        ReportColumn.center("status", "Status")),
                rows,
                rows.size() + " payments in range",
                "Payments · " + from + " to " + to);
    }

    private ReportData failedTransactions(LocalDate from, LocalDate to, ReportFilters filters) {
        Map<UUID, Institution> institutions = institutionIndex();
        List<Payment> payments = paymentRepository.findByStatusInAndCreatedAtBetweenOrderByCreatedAtDesc(
                        List.of(PaymentStatus.FAILED, PaymentStatus.REFUNDED), startOf(from), endOf(to)).stream()
                .filter(p -> matchesInstitution(p, institutions, filters))
                .filter(p -> matchesMethod(p, filters))
                .toList();

        List<Map<String, Object>> rows = payments.stream().map(p -> row(
                "paymentId", p.getId(),
                "institution", nameOf(resolveInstitution(p, institutions)),
                "amountEGP", nz(p.getTotalAmount()),
                "method", methodLabel(p.getPaymentMethod()),
                "status", statusLabel(p.getStatus()))).collect(Collectors.toList());

        return new ReportData(
                List.of(ReportColumn.of("paymentId", "Payment ID"),
                        ReportColumn.of("institution", "Institution"),
                        ReportColumn.right("amountEGP", "Amount (EGP)"),
                        ReportColumn.center("method", "Method"),
                        ReportColumn.center("status", "Status")),
                rows,
                rows.size() + " failed / refunded transactions in range",
                "Failed transactions · " + from + " to " + to);
    }

    private ReportData eppPortfolio(ReportFilters filters) {
        Map<UUID, Institution> institutions = institutionIndex();

        List<Map<String, Object>> rows = eppScheduleRepository.findAll().stream()
                .filter(s -> filters.eppTenor() == null || filters.eppTenor().equals(s.getTenorMonths()))
                .map(s -> {
                    Institution inst = s.getPayment() != null
                            ? resolveInstitution(s.getPayment(), institutions) : null;
                    return row(
                            "planId", s.getId(),
                            "institution", nameOf(inst),
                            "principalEGP", nz(s.getPrincipalAmount()),
                            "tenorMonths", s.getTenorMonths(),
                            "monthlyEGP", nz(s.getMonthlyInstalment()),
                            "totalPayableEGP", nz(s.getTotalPayable()));
                })
                .collect(Collectors.toList());

        BigDecimal totalPrincipal = rows.stream()
                .map(r -> (BigDecimal) r.get("principalEGP"))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ReportData(
                List.of(ReportColumn.of("planId", "Plan ID"),
                        ReportColumn.of("institution", "Institution"),
                        ReportColumn.right("principalEGP", "Principal (EGP)"),
                        ReportColumn.center("tenorMonths", "Tenor (months)"),
                        ReportColumn.right("monthlyEGP", "Monthly (EGP)"),
                        ReportColumn.right("totalPayableEGP", "Total Payable (EGP)")),
                rows,
                rows.size() + " EPP plans · EGP " + totalPrincipal.toPlainString() + " total principal",
                "EPP portfolio");
    }

    private ReportData outstandingBalances(ReportFilters filters) {
        Map<UUID, Institution> institutions = institutionIndex();
        Map<UUID, Student> students = studentIndex();

        List<FeeLine> feeLines = feeLineRepository
                .findByRemainingAmountGreaterThanAndStatusNot(BigDecimal.ZERO, FeeStatus.CANCELLED).stream()
                .filter(f -> filters.institutionId() == null || filters.institutionId().equals(f.getInstitutionId()))
                .filter(f -> filters.feeType() == null
                        || f.getFeeType().getDisplayName().equalsIgnoreCase(filters.feeType().trim()))
                .toList();

        List<Map<String, Object>> rows = feeLines.stream().map(f -> row(
                "institution", nameOf(institutions.get(f.getInstitutionId())),
                "student", students.containsKey(f.getStudentId())
                        ? students.get(f.getStudentId()).getFullName() : "—",
                "feeType", f.getFeeType().getDisplayName(),
                "outstandingEGP", nz(f.getRemainingAmount()),
                "dueDate", f.getDueDate() != null ? f.getDueDate().toString() : "")).collect(Collectors.toList());

        BigDecimal total = feeLines.stream()
                .map(f -> nz(f.getRemainingAmount())).reduce(BigDecimal.ZERO, BigDecimal::add);

        return new ReportData(
                List.of(ReportColumn.of("institution", "Institution"),
                        ReportColumn.of("student", "Student"),
                        ReportColumn.of("feeType", "Fee Type"),
                        ReportColumn.right("outstandingEGP", "Outstanding (EGP)"),
                        ReportColumn.center("dueDate", "Due Date")),
                rows,
                "EGP " + total.toPlainString() + " outstanding across " + rows.size() + " fee lines",
                "Outstanding balances");
    }

    // ── Shared helpers ───────────────────────────────────────────────────────

    private List<Payment> capturedPayments(LocalDate from, LocalDate to, ReportFilters filters) {
        Map<UUID, Institution> institutions = institutionIndex();
        return paymentRepository.findByStatusInAndCreatedAtBetweenOrderByCreatedAtDesc(
                        CAPTURED_ONLY, startOf(from), endOf(to)).stream()
                .filter(p -> matchesInstitution(p, institutions, filters))
                .filter(p -> matchesMethod(p, filters))
                .filter(p -> matchesFeeType(p, filters))
                .toList();
    }

    private Map<UUID, Institution> institutionIndex() {
        return institutionRepository.findAll().stream()
                .collect(Collectors.toMap(Institution::getId, Function.identity(), (a, b) -> a));
    }

    private Map<UUID, Student> studentIndex() {
        return studentRepository.findAll().stream()
                .collect(Collectors.toMap(Student::getId, Function.identity(), (a, b) -> a));
    }

    private Institution resolveInstitution(Payment payment, Map<UUID, Institution> index) {
        return payment.getAllocations().stream().findFirst()
                .map(PaymentAllocation::getFeeLine)
                .map(FeeLine::getInstitutionId)
                .map(index::get)
                .orElse(null);
    }

    private String firstFeeType(Payment payment) {
        return payment.getAllocations().stream().findFirst()
                .map(PaymentAllocation::getFeeLine)
                .map(f -> f.getFeeType().getDisplayName())
                .orElse("—");
    }

    private boolean matchesInstitution(Payment p, Map<UUID, Institution> index, ReportFilters filters) {
        if (filters.institutionId() == null) {
            return true;
        }
        Institution inst = resolveInstitution(p, index);
        return inst != null && filters.institutionId().equals(inst.getId());
    }

    private boolean matchesMethod(Payment p, ReportFilters filters) {
        if (filters.paymentMethod() == null || filters.paymentMethod().isBlank()) {
            return true;
        }
        String want = filters.paymentMethod().trim().toLowerCase();
        String have = methodLabel(p.getPaymentMethod()).toLowerCase();
        return have.contains(want) || (p.getPaymentMethod() != null
                && p.getPaymentMethod().name().toLowerCase().contains(want.replace(" ", "_")));
    }

    private boolean matchesStatus(Payment p, ReportFilters filters) {
        if (filters.paymentStatus() == null || filters.paymentStatus().isBlank()) {
            return true;
        }
        return statusLabel(p.getStatus()).equalsIgnoreCase(filters.paymentStatus().trim());
    }

    private boolean matchesFeeType(Payment p, ReportFilters filters) {
        if (filters.feeType() == null || filters.feeType().isBlank()) {
            return true;
        }
        return firstFeeType(p).equalsIgnoreCase(filters.feeType().trim());
    }

    private GeneratedReport requireReport(UUID jobId) {
        return generatedReportRepository.findById(jobId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Report job not found: " + jobId));
    }

    private ReportJobResponse toJobResponse(GeneratedReport report, ReportPreview preview) {
        return new ReportJobResponse(
                report.getId(),
                report.getReportId(),
                report.getStatus().name(),
                report.getFilename(),
                "/api/v1/reports/jobs/" + report.getId() + "/download",
                preview,
                report.getCreatedAt());
    }

    private String writeJson(Object value) {
        try {
            return objectMapper.writeValueAsString(value);
        } catch (Exception e) {
            return null;
        }
    }

    private ReportPreview readPreview(GeneratedReport report) {
        if (report.getPreviewJson() == null) {
            return new ReportPreview(List.of(), List.of(), report.getTotalLabel(), report.getNoteLabel());
        }
        try {
            return objectMapper.readValue(report.getPreviewJson(), ReportPreview.class);
        } catch (Exception e) {
            return new ReportPreview(List.of(), List.of(), report.getTotalLabel(), report.getNoteLabel());
        }
    }

    private static String describeParams(GenerateReportRequest req, LocalDate from, LocalDate to) {
        ReportFilters f = req.filtersOrEmpty();
        return "reportId=" + req.reportId()
                + "; from=" + from + "; to=" + to
                + "; format=" + req.formatOrDefault()
                + "; institutionId=" + f.institutionId()
                + "; feeType=" + f.feeType()
                + "; paymentStatus=" + f.paymentStatus()
                + "; paymentMethod=" + f.paymentMethod()
                + "; eppTenor=" + f.eppTenor();
    }

    private static LocalDateTime startOf(LocalDate d) {
        return d.atStartOfDay();
    }

    private static LocalDateTime endOf(LocalDate d) {
        return d.atTime(LocalTime.MAX);
    }

    private static BigDecimal nz(BigDecimal v) {
        return v != null ? v : BigDecimal.ZERO;
    }

    private static String nameOf(Institution i) {
        return i != null ? i.getName() : "Unknown institution";
    }

    private static String capitalise(String enumName) {
        String lower = enumName.toLowerCase();
        return Character.toUpperCase(lower.charAt(0)) + lower.substring(1);
    }

    private static String methodLabel(PaymentMethod method) {
        if (method == null) {
            return "—";
        }
        return switch (method) {
            case CIB_ACCOUNT -> "CIB Account";
            case CREDIT_CARD -> "Credit Card";
            case EPP_INSTALMENTS -> "EPP";
        };
    }

    private static String statusLabel(PaymentStatus status) {
        if (status == null) {
            return "—";
        }
        return switch (status) {
            case CAPTURED -> "Successful";
            case FAILED -> "Failed";
            case REFUNDED -> "Refunded";
            case PENDING, AUTHORIZED -> "Pending";
        };
    }

    private static Map<String, Object> row(Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put((String) kv[i], kv[i + 1]);
        }
        return m;
    }

    @SuppressWarnings("unused")
    private static <T> Optional<T> firstOf(List<T> list) {
        return list.isEmpty() ? Optional.empty() : Optional.of(list.get(0));
    }
}
