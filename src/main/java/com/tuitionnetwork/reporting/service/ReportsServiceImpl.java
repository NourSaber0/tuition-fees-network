package com.tuitionnetwork.reporting.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.domain.FeeType;
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
import java.util.HashSet;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import com.tuitionnetwork.reconciliation.domain.ReconciliationRun;
import com.tuitionnetwork.reconciliation.repository.ReconciliationRunRepository;

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
    private final ReconciliationRunRepository reconciliationRunRepository;

    @Autowired
    public ReportsServiceImpl(GeneratedReportRepository generatedReportRepository,
                              PaymentRepository paymentRepository,
                              FeeLineRepository feeLineRepository,
                              EPPScheduleRepository eppScheduleRepository,
                              InstitutionRepository institutionRepository,
                              StudentRepository studentRepository,
                              ObjectMapper objectMapper,
                              @Autowired(required = false) AuditLogRepository auditLogRepository,
                              @Autowired(required = false) ReconciliationRunRepository reconciliationRunRepository) {
        this.generatedReportRepository = generatedReportRepository;
        this.paymentRepository = paymentRepository;
        this.feeLineRepository = feeLineRepository;
        this.eppScheduleRepository = eppScheduleRepository;
        this.institutionRepository = institutionRepository;
        this.studentRepository = studentRepository;
        this.objectMapper = objectMapper;
        this.auditLogRepository = auditLogRepository;
        this.reconciliationRunRepository = reconciliationRunRepository;
    }

    public ReportsServiceImpl(GeneratedReportRepository generatedReportRepository,
                              PaymentRepository paymentRepository,
                              FeeLineRepository feeLineRepository,
                              EPPScheduleRepository eppScheduleRepository,
                              InstitutionRepository institutionRepository,
                              StudentRepository studentRepository,
                              ObjectMapper objectMapper,
                              AuditLogRepository auditLogRepository) {
        this(generatedReportRepository, paymentRepository, feeLineRepository, eppScheduleRepository,
                institutionRepository, studentRepository, objectMapper, auditLogRepository, null);
    }

    public ReportsServiceImpl(GeneratedReportRepository generatedReportRepository,
                              PaymentRepository paymentRepository,
                              FeeLineRepository feeLineRepository,
                              EPPScheduleRepository eppScheduleRepository,
                              InstitutionRepository institutionRepository,
                              StudentRepository studentRepository,
                              ObjectMapper objectMapper) {
        this(generatedReportRepository, paymentRepository, feeLineRepository, eppScheduleRepository,
                institutionRepository, studentRepository, objectMapper, null, null);
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
    public List<ReportCatalogueEntry> catalogue(UUID schoolId) {
        return ReportCatalogue.schoolEntries().stream()
                .map(e -> {
                    LocalDateTime lastGen = schoolId != null
                            ? generatedReportRepository
                            .findFirstByInstitutionIdAndReportIdOrderByCreatedAtDesc(schoolId, e.id())
                            .map(GeneratedReport::getCreatedAt)
                            .orElse(null)
                            : null;
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
    @Transactional
    public ReportJobResponse generateForSchool(GenerateReportRequest request, UUID schoolId) {
        if (schoolId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "cross_school_access: No school associated with user");
        }

        ReportCatalogueEntry entry = ReportCatalogue.find(request.reportId())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Unknown report type: " + request.reportId()));

        if (!ReportCatalogue.isSchoolReport(request.reportId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "cross_school_access: School users can only generate school reports");
        }

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
            if (from != null && to != null && from.isAfter(to)) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "date_from_after_date_to");
            }
        }

        ReportFilters filters = request.filtersOrEmpty().withInstitutionId(schoolId);

        ReportData data = buildData(entry.id(), from, to, filters);
        String csv = CsvWriter.toCsv(data.columns(), data.rows());

        List<Map<String, Object>> previewRows = data.rows().size() > PREVIEW_LIMIT
                ? data.rows().subList(0, PREVIEW_LIMIT)
                : data.rows();
        ReportPreview preview = new ReportPreview(data.columns(), previewRows, data.total(), data.note());

        String range = (from != null && to != null) ? (from + "_" + to) : (from != null ? from.toString() : "all");
        String filename = "report_" + entry.id() + "_" + range + "." + format.toLowerCase();

        GeneratedReport report = new GeneratedReport();
        report.setReportId(entry.id());
        report.setFormat(format);
        report.setInstitutionId(schoolId);
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
                    "SCHOOL",
                    "GENERATE_REPORT",
                    "Report " + entry.id() + " generated (" + format + ") with " + data.rows().size() + " rows for school " + schoolId
            ));
        }

        return toSchoolJobResponse(saved, preview);
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
    public ReportJobResponse getJobForSchool(UUID jobId, UUID schoolId) {
        GeneratedReport report = requireReport(jobId);
        if (schoolId != null && report.getInstitutionId() != null && !schoolId.equals(report.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "cross_school_access: Access denied to other school's report");
        }
        if (report.getInstitutionId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "cross_school_access: Access denied to bank report");
        }
        ReportPreview preview = readPreview(report);
        return new ReportJobResponse(
                report.getId(),
                report.getReportId(),
                report.getStatus().name(),
                report.getFilename(),
                "/reports/jobs/" + report.getId() + "/download",
                preview,
                report.getCreatedAt());
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
    public DownloadPayload downloadForSchool(UUID jobId, UUID schoolId) {
        GeneratedReport report = requireReport(jobId);
        if (schoolId != null && report.getInstitutionId() != null && !schoolId.equals(report.getInstitutionId())) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "cross_school_access: Access denied to other school's report");
        }
        if (report.getInstitutionId() == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "cross_school_access: Access denied to bank report");
        }
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

    @Override
    @Transactional(readOnly = true)
    public PageResponse<ReportHistoryEntry> historyForSchool(UUID schoolId, String reportId, int page, int size) {
        if (schoolId == null) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN,
                    "cross_school_access: No school associated with user");
        }
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), 100);
        PageRequest pr = PageRequest.of(safePage, safeSize);

        Page<GeneratedReport> result = (reportId == null || reportId.isBlank())
                ? generatedReportRepository.findByInstitutionIdOrderByCreatedAtDesc(schoolId, pr)
                : generatedReportRepository.findByInstitutionIdAndReportIdOrderByCreatedAtDesc(schoolId, reportId.trim(), pr);

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
            case "reconciliation" -> reconciliationReport(from, to, filters);
            case "daily-report" -> dailySummaryReport(from != null ? from : LocalDate.now());
            case "school-collections" -> schoolCollections(from, to, filters);
            case "school-payments" -> schoolPayments(from, to, filters);
            case "school-outstanding-fees" -> schoolOutstandingFees(from, to, filters);
            case "school-partial-payments" -> schoolPartialPayments(from, to, filters);
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

    private ReportData reconciliationReport(LocalDate from, LocalDate to, ReportFilters filters) {
        List<ReconciliationRun> runs;
        if (reconciliationRunRepository != null) {
            runs = reconciliationRunRepository.findAll().stream()
                    .filter(r -> {
                        LocalDate d = r.getRunDate() != null ? r.getRunDate()
                                : (r.getCreatedAt() != null ? r.getCreatedAt().toLocalDate() : LocalDate.now());
                        if (from != null && d.isBefore(from)) return false;
                        if (to != null && d.isAfter(to)) return false;
                        if (filters.reconStatus() != null && !filters.reconStatus().isBlank()) {
                            return filters.reconStatus().equalsIgnoreCase(r.getStatus());
                        }
                        return true;
                    })
                    .sorted((a, b) -> {
                        LocalDateTime ta = a.getCreatedAt() != null ? a.getCreatedAt() : LocalDateTime.MIN;
                        LocalDateTime tb = b.getCreatedAt() != null ? b.getCreatedAt() : LocalDateTime.MIN;
                        return tb.compareTo(ta);
                    })
                    .toList();
        } else {
            runs = List.of();
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        long totalMatched = 0;
        long totalExceptions = 0;

        for (ReconciliationRun r : runs) {
            int matched = r.getMatchedCount() != null ? r.getMatchedCount() : 0;
            int exceptions = r.getExceptionCount() != null ? r.getExceptionCount() : 0;
            totalMatched += matched;
            totalExceptions += exceptions;

            LocalDate date = r.getRunDate() != null ? r.getRunDate()
                    : (r.getCreatedAt() != null ? r.getCreatedAt().toLocalDate() : LocalDate.now());

            rows.add(row(
                    "runId", r.getId(),
                    "institution", r.getInstitution() != null ? r.getInstitution() : "All Institutions",
                    "type", r.getInstitutionType() != null ? r.getInstitutionType() : "—",
                    "date", date.toString(),
                    "totalTx", r.getTotalTransactions() != null ? r.getTotalTransactions() : 0,
                    "matched", matched,
                    "exceptions", exceptions,
                    "bankAmountEGP", nz(BigDecimal.valueOf(r.getBankAmountEGP() != null ? r.getBankAmountEGP() : 0)),
                    "systemAmountEGP", nz(BigDecimal.valueOf(r.getSystemAmountEGP() != null ? r.getSystemAmountEGP() : 0)),
                    "schoolAmountEGP", nz(BigDecimal.valueOf(r.getSchoolAmountEGP() != null ? r.getSchoolAmountEGP() : 0)),
                    "status", r.getStatus() != null ? r.getStatus() : "Completed"
            ));
        }

        return new ReportData(
                List.of(
                        ReportColumn.of("runId", "Run ID"),
                        ReportColumn.of("institution", "Institution"),
                        ReportColumn.center("type", "Type"),
                        ReportColumn.center("date", "Date"),
                        ReportColumn.right("totalTx", "Total Tx"),
                        ReportColumn.right("matched", "Matched"),
                        ReportColumn.right("exceptions", "Exceptions"),
                        ReportColumn.right("bankAmountEGP", "Bank (EGP)"),
                        ReportColumn.right("systemAmountEGP", "System (EGP)"),
                        ReportColumn.right("schoolAmountEGP", "School (EGP)"),
                        ReportColumn.center("status", "Status")
                ),
                rows,
                runs.size() + " reconciliation runs (" + totalMatched + " matched, " + totalExceptions + " exceptions)",
                "Reconciliation report · " + (from != null ? from : "start") + " to " + (to != null ? to : "end")
        );
    }

    private ReportData dailySummaryReport(LocalDate date) {
        List<Payment> payments = paymentRepository
                .findByStatusInAndCreatedAtBetweenOrderByCreatedAtDesc(
                        CAPTURED_ONLY, startOf(date), endOf(date));

        List<FeeLine> allFees = feeLineRepository.findAll();

        Map<FeeType, Long> txCountByType = new LinkedHashMap<>();
        Map<FeeType, BigDecimal> collectedByType = new LinkedHashMap<>();
        Map<FeeType, BigDecimal> outstandingByType = new LinkedHashMap<>();
        Map<FeeType, Set<UUID>> studentsByType = new LinkedHashMap<>();

        for (FeeType t : FeeType.values()) {
            txCountByType.put(t, 0L);
            collectedByType.put(t, BigDecimal.ZERO);
            outstandingByType.put(t, BigDecimal.ZERO);
            studentsByType.put(t, new HashSet<>());
        }

        // Process payments collected on date
        for (Payment p : payments) {
            for (PaymentAllocation a : p.getAllocations()) {
                FeeLine fl = a.getFeeLine();
                if (fl != null && fl.getFeeType() != null) {
                    FeeType ft = fl.getFeeType();
                    txCountByType.merge(ft, 1L, Long::sum);
                    collectedByType.merge(ft, nz(a.getAmountApplied()), BigDecimal::add);
                    if (fl.getStudentId() != null) {
                        studentsByType.get(ft).add(fl.getStudentId());
                    }
                }
            }
        }

        // Process outstanding fee lines
        for (FeeLine fl : allFees) {
            if (fl.getFeeType() != null) {
                FeeType ft = fl.getFeeType();
                if (fl.getStatus() != FeeStatus.PAID && fl.getStatus() != FeeStatus.CANCELLED) {
                    BigDecimal rem = fl.getRemainingAmount() != null ? fl.getRemainingAmount() : fl.getTotalAmount();
                    outstandingByType.merge(ft, nz(rem), BigDecimal::add);
                    if (fl.getStudentId() != null) {
                        studentsByType.get(ft).add(fl.getStudentId());
                    }
                }
            }
        }

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal grandCollected = BigDecimal.ZERO;
        BigDecimal grandOutstanding = BigDecimal.ZERO;
        long grandTx = 0;

        for (FeeType t : FeeType.values()) {
            long tx = txCountByType.get(t);
            BigDecimal col = collectedByType.get(t);
            BigDecimal out = outstandingByType.get(t);
            int studentCount = studentsByType.get(t).size();

            grandTx += tx;
            grandCollected = grandCollected.add(col);
            grandOutstanding = grandOutstanding.add(out);

            rows.add(row(
                    "feeType", t.getDisplayName(),
                    "txCount", tx,
                    "collectedEGP", col,
                    "outstandingEGP", out,
                    "studentsBilled", studentCount
            ));
        }

        return new ReportData(
                List.of(
                        ReportColumn.of("feeType", "Fee Type"),
                        ReportColumn.right("txCount", "Transactions Today"),
                        ReportColumn.right("collectedEGP", "Collected Today (EGP)"),
                        ReportColumn.right("outstandingEGP", "Outstanding Balance (EGP)"),
                        ReportColumn.right("studentsBilled", "Students Billed")
                ),
                rows,
                "EGP " + grandCollected.toPlainString() + " collected across " + grandTx + " transactions on " + date,
                "Daily summary report by fee type · " + date
        );
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

    private ReportJobResponse toSchoolJobResponse(GeneratedReport report, ReportPreview preview) {
        return new ReportJobResponse(
                report.getId(),
                report.getReportId(),
                "processing",
                report.getFilename(),
                "/reports/jobs/" + report.getId() + "/download",
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

    private ReportData schoolCollections(LocalDate from, LocalDate to, ReportFilters filters) {
        UUID schoolId = filters.institutionId();
        Map<UUID, Student> students = studentIndex();
        List<Payment> payments = capturedPaymentsForSchool(schoolId, from, to, filters);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (Payment p : payments) {
            for (PaymentAllocation a : p.getAllocations()) {
                FeeLine fl = a.getFeeLine();
                if (fl != null && (schoolId == null || schoolId.equals(fl.getInstitutionId()))) {
                    Student s = fl.getStudentId() != null ? students.get(fl.getStudentId()) : null;
                    BigDecimal amount = nz(a.getAmountApplied());
                    grandTotal = grandTotal.add(amount);

                    rows.add(row(
                            "date", p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate().toString() : "",
                            "paymentId", p.getId() != null ? p.getId().toString() : "",
                            "student", s != null ? s.getFullName() : "—",
                            "studentRef", s != null && s.getStudentRef() != null ? s.getStudentRef() : "—",
                            "feeType", fl.getFeeType() != null ? fl.getFeeType().getDisplayName() : "—",
                            "amountEGP", amount,
                            "method", methodLabel(p.getPaymentMethod()),
                            "status", statusLabel(p.getStatus())
                    ));
                }
            }
        }

        return new ReportData(
                List.of(
                        ReportColumn.center("date", "Date"),
                        ReportColumn.of("paymentId", "Payment ID"),
                        ReportColumn.of("student", "Student"),
                        ReportColumn.of("studentRef", "Student Ref"),
                        ReportColumn.of("feeType", "Fee Type"),
                        ReportColumn.right("amountEGP", "Amount (EGP)"),
                        ReportColumn.center("method", "Method"),
                        ReportColumn.center("status", "Status")
                ),
                rows,
                "EGP " + grandTotal.toPlainString() + " across " + rows.size() + " collections",
                "Collection Report" + (from != null && to != null ? " · " + from + " to " + to : "")
        );
    }

    private ReportData schoolPayments(LocalDate from, LocalDate to, ReportFilters filters) {
        UUID schoolId = filters.institutionId();
        Map<UUID, Student> students = studentIndex();
        List<Payment> payments = allPaymentsForSchool(schoolId, from, to, filters);

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal grandTotal = BigDecimal.ZERO;

        for (Payment p : payments) {
            for (PaymentAllocation a : p.getAllocations()) {
                FeeLine fl = a.getFeeLine();
                if (fl != null && (schoolId == null || schoolId.equals(fl.getInstitutionId()))) {
                    Student s = fl.getStudentId() != null ? students.get(fl.getStudentId()) : null;
                    BigDecimal amount = nz(a.getAmountApplied());
                    grandTotal = grandTotal.add(amount);

                    rows.add(row(
                            "paymentId", p.getId() != null ? p.getId().toString() : "",
                            "date", p.getCreatedAt() != null ? p.getCreatedAt().toLocalDate().toString() : "",
                            "student", s != null ? s.getFullName() : "—",
                            "studentRef", s != null && s.getStudentRef() != null ? s.getStudentRef() : "—",
                            "feeType", fl.getFeeType() != null ? fl.getFeeType().getDisplayName() : "—",
                            "amountEGP", amount,
                            "method", methodLabel(p.getPaymentMethod()),
                            "status", statusLabel(p.getStatus())
                    ));
                }
            }
        }

        return new ReportData(
                List.of(
                        ReportColumn.of("paymentId", "Payment ID"),
                        ReportColumn.center("date", "Date"),
                        ReportColumn.of("student", "Student"),
                        ReportColumn.of("studentRef", "Student Ref"),
                        ReportColumn.of("feeType", "Fee Type"),
                        ReportColumn.right("amountEGP", "Amount (EGP)"),
                        ReportColumn.center("method", "Method"),
                        ReportColumn.center("status", "Status")
                ),
                rows,
                rows.size() + " payments · EGP " + grandTotal.toPlainString() + " total",
                "Payment History" + (from != null && to != null ? " · " + from + " to " + to : "")
        );
    }

    private ReportData schoolOutstandingFees(LocalDate from, LocalDate to, ReportFilters filters) {
        UUID schoolId = filters.institutionId();
        Map<UUID, Student> students = studentIndex();

        List<FeeLine> feeLines = (schoolId != null)
                ? feeLineRepository.findByInstitutionIdAndStatusNot(schoolId, FeeStatus.CANCELLED)
                : feeLineRepository.findAll();

        feeLines = feeLines.stream()
                .filter(f -> f.getRemainingAmount() != null && f.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0)
                .filter(f -> f.getStatus() != FeeStatus.PAID)
                .filter(f -> {
                    if (from != null && f.getDueDate() != null && f.getDueDate().isBefore(from)) return false;
                    if (to != null && f.getDueDate() != null && f.getDueDate().isAfter(to)) return false;
                    return true;
                })
                .filter(f -> filters.feeType() == null || filters.feeType().isBlank()
                        || (f.getFeeType() != null && f.getFeeType().getDisplayName().equalsIgnoreCase(filters.feeType().trim())))
                .toList();

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalOutstanding = BigDecimal.ZERO;

        for (FeeLine f : feeLines) {
            Student s = f.getStudentId() != null ? students.get(f.getStudentId()) : null;
            BigDecimal remaining = nz(f.getRemainingAmount());
            totalOutstanding = totalOutstanding.add(remaining);

            rows.add(row(
                    "feeId", f.getId() != null ? f.getId().toString() : "",
                    "student", s != null ? s.getFullName() : "—",
                    "studentRef", s != null && s.getStudentRef() != null ? s.getStudentRef() : "—",
                    "grade", s != null && s.getGrade() != null ? s.getGrade() : "—",
                    "feeType", f.getFeeType() != null ? f.getFeeType().getDisplayName() : "—",
                    "totalAmountEGP", nz(f.getTotalAmount()),
                    "paidAmountEGP", nz(f.getPaidAmount()),
                    "outstandingEGP", remaining,
                    "dueDate", f.getDueDate() != null ? f.getDueDate().toString() : "",
                    "status", f.getStatus() != null ? f.getStatus().name() : ""
            ));
        }

        return new ReportData(
                List.of(
                        ReportColumn.of("feeId", "Fee ID"),
                        ReportColumn.of("student", "Student"),
                        ReportColumn.of("studentRef", "Student Ref"),
                        ReportColumn.center("grade", "Grade"),
                        ReportColumn.of("feeType", "Fee Type"),
                        ReportColumn.right("totalAmountEGP", "Total (EGP)"),
                        ReportColumn.right("paidAmountEGP", "Paid (EGP)"),
                        ReportColumn.right("outstandingEGP", "Outstanding (EGP)"),
                        ReportColumn.center("dueDate", "Due Date"),
                        ReportColumn.center("status", "Status")
                ),
                rows,
                "EGP " + totalOutstanding.toPlainString() + " outstanding across " + rows.size() + " fee lines",
                "Outstanding Fees Report"
        );
    }

    private ReportData schoolPartialPayments(LocalDate from, LocalDate to, ReportFilters filters) {
        UUID schoolId = filters.institutionId();
        Map<UUID, Student> students = studentIndex();

        List<FeeLine> feeLines = (schoolId != null)
                ? feeLineRepository.findByInstitutionIdAndStatusNot(schoolId, FeeStatus.CANCELLED)
                : feeLineRepository.findAll();

        feeLines = feeLines.stream()
                .filter(f -> (f.getStatus() == FeeStatus.PARTIALLY_PAID)
                        || (f.getPaidAmount() != null && f.getPaidAmount().compareTo(BigDecimal.ZERO) > 0
                        && f.getRemainingAmount() != null && f.getRemainingAmount().compareTo(BigDecimal.ZERO) > 0))
                .filter(f -> {
                    if (from != null && f.getDueDate() != null && f.getDueDate().isBefore(from)) return false;
                    if (to != null && f.getDueDate() != null && f.getDueDate().isAfter(to)) return false;
                    return true;
                })
                .filter(f -> filters.feeType() == null || filters.feeType().isBlank()
                        || (f.getFeeType() != null && f.getFeeType().getDisplayName().equalsIgnoreCase(filters.feeType().trim())))
                .toList();

        List<Map<String, Object>> rows = new ArrayList<>();
        BigDecimal totalRemaining = BigDecimal.ZERO;

        for (FeeLine f : feeLines) {
            Student s = f.getStudentId() != null ? students.get(f.getStudentId()) : null;
            BigDecimal remaining = nz(f.getRemainingAmount());
            totalRemaining = totalRemaining.add(remaining);

            rows.add(row(
                    "feeId", f.getId() != null ? f.getId().toString() : "",
                    "student", s != null ? s.getFullName() : "—",
                    "studentRef", s != null && s.getStudentRef() != null ? s.getStudentRef() : "—",
                    "feeType", f.getFeeType() != null ? f.getFeeType().getDisplayName() : "—",
                    "totalAmountEGP", nz(f.getTotalAmount()),
                    "paidAmountEGP", nz(f.getPaidAmount()),
                    "remainingAmountEGP", remaining,
                    "dueDate", f.getDueDate() != null ? f.getDueDate().toString() : "",
                    "collectionPeriod", f.getCollectionPeriod() != null ? f.getCollectionPeriod() : "—"
            ));
        }

        return new ReportData(
                List.of(
                        ReportColumn.of("feeId", "Fee ID"),
                        ReportColumn.of("student", "Student"),
                        ReportColumn.of("studentRef", "Student Ref"),
                        ReportColumn.of("feeType", "Fee Type"),
                        ReportColumn.right("totalAmountEGP", "Total (EGP)"),
                        ReportColumn.right("paidAmountEGP", "Paid (EGP)"),
                        ReportColumn.right("remainingAmountEGP", "Remaining (EGP)"),
                        ReportColumn.center("dueDate", "Due Date"),
                        ReportColumn.center("collectionPeriod", "Period")
                ),
                rows,
                "EGP " + totalRemaining.toPlainString() + " remaining across " + rows.size() + " partial payments",
                "Partial Payments Report"
        );
    }

    private List<Payment> capturedPaymentsForSchool(UUID schoolId, LocalDate from, LocalDate to, ReportFilters filters) {
        LocalDateTime start = (from != null) ? startOf(from) : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime end = (to != null) ? endOf(to) : LocalDateTime.of(2099, 12, 31, 23, 59);

        return paymentRepository.findByStatusInAndCreatedAtBetweenOrderByCreatedAtDesc(
                        CAPTURED_ONLY, start, end).stream()
                .filter(p -> p.getAllocations().stream().anyMatch(a -> a.getFeeLine() != null
                        && (schoolId == null || schoolId.equals(a.getFeeLine().getInstitutionId()))))
                .filter(p -> matchesMethod(p, filters))
                .filter(p -> matchesFeeType(p, filters))
                .toList();
    }

    private List<Payment> allPaymentsForSchool(UUID schoolId, LocalDate from, LocalDate to, ReportFilters filters) {
        LocalDateTime start = (from != null) ? startOf(from) : LocalDateTime.of(2000, 1, 1, 0, 0);
        LocalDateTime end = (to != null) ? endOf(to) : LocalDateTime.of(2099, 12, 31, 23, 59);

        return paymentRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(start, end).stream()
                .filter(p -> p.getAllocations().stream().anyMatch(a -> a.getFeeLine() != null
                        && (schoolId == null || schoolId.equals(a.getFeeLine().getInstitutionId()))))
                .filter(p -> matchesMethod(p, filters))
                .filter(p -> matchesStatus(p, filters))
                .filter(p -> matchesFeeType(p, filters))
                .toList();
    }
}
