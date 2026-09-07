package com.tuitionnetwork.reporting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.InstitutionType;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.repository.EPPScheduleRepository;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.reporting.domain.GeneratedReport;
import com.tuitionnetwork.reporting.dto.GenerateReportRequest;
import com.tuitionnetwork.reporting.dto.ReportHistoryEntry;
import com.tuitionnetwork.reporting.dto.ReportJobResponse;
import com.tuitionnetwork.reporting.repository.GeneratedReportRepository;
import com.tuitionnetwork.reporting.service.ReportsService;
import com.tuitionnetwork.reporting.service.ReportsServiceImpl;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ReportsServiceImplTest {

    private GeneratedReportRepository generatedReportRepository;
    private PaymentRepository paymentRepository;
    private FeeLineRepository feeLineRepository;
    private EPPScheduleRepository eppScheduleRepository;
    private InstitutionRepository institutionRepository;
    private StudentRepository studentRepository;
    private com.tuitionnetwork.audit.repository.AuditLogRepository auditLogRepository;
    private ReportsServiceImpl service;

    private final GeneratedReport[] lastSaved = new GeneratedReport[1];

    @BeforeEach
    void setUp() {
        generatedReportRepository = mock(GeneratedReportRepository.class);
        paymentRepository = mock(PaymentRepository.class);
        feeLineRepository = mock(FeeLineRepository.class);
        eppScheduleRepository = mock(EPPScheduleRepository.class);
        institutionRepository = mock(InstitutionRepository.class);
        studentRepository = mock(StudentRepository.class);
        auditLogRepository = mock(com.tuitionnetwork.audit.repository.AuditLogRepository.class);

        service = new ReportsServiceImpl(generatedReportRepository, paymentRepository, feeLineRepository,
                eppScheduleRepository, institutionRepository, studentRepository, new ObjectMapper(), auditLogRepository);

        when(generatedReportRepository.save(any(GeneratedReport.class))).thenAnswer(inv -> {
            GeneratedReport r = inv.getArgument(0);
            r.setId(UUID.randomUUID());
            r.setCreatedAt(LocalDateTime.now());
            lastSaved[0] = r;
            return r;
        });
    }

    private Institution school(String name) {
        Institution i = new Institution(name, "SCH-" + name.hashCode(), null);
        i.setId(UUID.randomUUID());
        i.setInstitutionType(InstitutionType.SCHOOL);
        return i;
    }

    private Payment captured(Institution inst, String amount) {
        Payment p = new Payment(UUID.randomUUID(), new BigDecimal(amount),
                PaymentMethod.CREDIT_CARD, "IDEMP-" + UUID.randomUUID());
        p.setStatus(PaymentStatus.CAPTURED);
        FeeLine fl = new FeeLine();
        fl.setInstitutionId(inst.getId());
        fl.setStudentId(UUID.randomUUID());
        fl.setFeeType(FeeType.TUITION);
        p.addAllocation(new PaymentAllocation(p, fl, new BigDecimal(amount)));
        return p;
    }

    private GenerateReportRequest req(String reportId, LocalDate from, LocalDate to) {
        return new GenerateReportRequest(reportId, from, to, null, "CSV", null);
    }

    // ── catalogue ──────────────────────────────────────────────────────────

    @Test
    void catalogue_hasTenEntries_withReconciliationUnavailable() {
        LocalDateTime sampleTime = LocalDateTime.of(2026, 8, 31, 8, 0, 0);
        GeneratedReport sampleReport = new GeneratedReport();
        sampleReport.setCreatedAt(sampleTime);
        when(generatedReportRepository.findFirstByReportIdOrderByCreatedAtDesc("daily-collections"))
                .thenReturn(Optional.of(sampleReport));

        List<com.tuitionnetwork.reporting.dto.ReportCatalogueEntry> cat = service.catalogue();
        assertEquals(10, cat.size());
        assertTrue(cat.stream().anyMatch(e -> e.id().equals("reconciliation") && !e.available()));
        assertTrue(cat.stream().anyMatch(e -> e.id().equals("collections-by-type") && e.available()));

        com.tuitionnetwork.reporting.dto.ReportCatalogueEntry daily = cat.stream()
                .filter(e -> e.id().equals("daily-collections")).findFirst().orElseThrow();
        assertEquals(sampleTime, daily.lastGeneratedAt());
    }

    // ── generate ───────────────────────────────────────────────────────────

    @Test
    void generate_paymentsReport_buildsRowsCsvAndPersists() {
        Institution inst = school("Cairo International School");
        when(institutionRepository.findAll()).thenReturn(List.of(inst));
        when(paymentRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of(captured(inst, "18000")));

        ReportJobResponse resp = service.generate(
                req("payments", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        assertEquals("READY", resp.status());
        assertEquals("payments", resp.reportId());
        assertTrue(resp.filename().contains("payments"));
        assertTrue(resp.filename().endsWith(".csv"));
        assertEquals(6, resp.preview().columns().size());
        assertEquals(1, resp.preview().rows().size());
        assertEquals("Cairo International School", resp.preview().rows().get(0).get("institution"));
        assertTrue(resp.downloadUrl().contains(resp.jobId().toString()));
        verify(generatedReportRepository).save(any(GeneratedReport.class));
        verify(auditLogRepository).save(any(com.tuitionnetwork.audit.domain.AuditLog.class));
        assertNotNull(lastSaved[0].getCsvContent());
        assertTrue(lastSaved[0].getCsvContent().contains("Cairo International School"));
    }

    @Test
    void generate_networkCollections_groupsByInstitutionAndSumsAmounts() {
        Institution inst = school("Nasr City Academy");
        when(institutionRepository.findAll()).thenReturn(List.of(inst));
        when(paymentRepository.findByStatusInAndCreatedAtBetweenOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(List.of(captured(inst, "10000"), captured(inst, "5000")));

        ReportJobResponse resp = service.generate(
                req("network-collections", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        assertEquals(1, resp.preview().rows().size());
        assertEquals(2L, resp.preview().rows().get(0).get("txCount"));
        assertEquals(new BigDecimal("15000"), resp.preview().rows().get(0).get("amountEGP"));
    }

    @Test
    void generate_unknownReportType_notFound() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generate(req("does-not-exist", LocalDate.now(), LocalDate.now())));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void generate_unavailableReport_unprocessableEntity() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generate(req("reconciliation", LocalDate.now(), LocalDate.now())));
        assertEquals(422, ex.getStatusCode().value());
    }

    @Test
    void generate_nonCsvFormat_badRequest() {
        GenerateReportRequest r = new GenerateReportRequest(
                "payments", LocalDate.now().minusDays(1), LocalDate.now(), null, "PDF", null);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.generate(r));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void generate_dateFromAfterDateTo_badRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generate(req("payments", LocalDate.of(2026, 9, 1), LocalDate.of(2026, 8, 1))));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void generate_missingDateRange_badRequest() {
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> service.generate(req("payments", null, null)));
        assertEquals(400, ex.getStatusCode().value());
    }

    @Test
    void generate_singleDateReport_acceptsDateInsteadOfRange() {
        Institution inst = school("Heliopolis Academy");
        when(institutionRepository.findAll()).thenReturn(List.of(inst));
        when(paymentRepository.findByStatusInAndCreatedAtBetweenOrderByCreatedAtDesc(any(), any(), any()))
                .thenReturn(List.of(captured(inst, "3200")));

        GenerateReportRequest r = new GenerateReportRequest(
                "daily-collections", null, null, LocalDate.of(2026, 8, 31), "CSV", null);

        ReportJobResponse resp = service.generate(r);
        assertEquals("READY", resp.status());
        assertTrue(resp.filename().contains("2026-08-31"));
    }

    // ── jobs / download / history ──────────────────────────────────────────

    @Test
    void getJob_unknown_notFound() {
        UUID id = UUID.randomUUID();
        when(generatedReportRepository.findById(id)).thenReturn(Optional.empty());
        ResponseStatusException ex = assertThrows(ResponseStatusException.class, () -> service.getJob(id));
        assertEquals(404, ex.getStatusCode().value());
    }

    @Test
    void getJob_roundTripsStoredPreview() {
        Institution inst = school("Ain Shams University");
        when(institutionRepository.findAll()).thenReturn(List.of(inst));
        when(paymentRepository.findByCreatedAtBetweenOrderByCreatedAtDesc(any(), any()))
                .thenReturn(List.of(captured(inst, "8000")));

        ReportJobResponse created = service.generate(
                req("payments", LocalDate.of(2026, 8, 1), LocalDate.of(2026, 8, 31)));

        when(generatedReportRepository.findById(created.jobId())).thenReturn(Optional.of(lastSaved[0]));

        ReportJobResponse fetched = service.getJob(created.jobId());
        assertEquals(created.jobId(), fetched.jobId());
        assertEquals(1, fetched.preview().rows().size());
        assertEquals(6, fetched.preview().columns().size());
    }

    @Test
    void download_returnsCsvBytes() {
        GeneratedReport report = new GeneratedReport();
        report.setId(UUID.randomUUID());
        report.setFilename("report_payments_x.csv");
        report.setCsvContent("Payment ID,Amount (EGP)\r\nP1,100\r\n");
        when(generatedReportRepository.findById(report.getId())).thenReturn(Optional.of(report));

        ReportsService.DownloadPayload payload = service.download(report.getId());
        assertEquals("report_payments_x.csv", payload.filename());
        assertEquals("text/csv", payload.contentType());
        assertEquals("Payment ID,Amount (EGP)\r\nP1,100\r\n",
                new String(payload.content(), StandardCharsets.UTF_8));
    }

    @Test
    void history_mapsPageToHistoryEntries() {
        GeneratedReport report = new GeneratedReport();
        report.setId(UUID.randomUUID());
        report.setReportId("payments");
        report.setFormat("CSV");
        report.setFilename("report_payments_x.csv");
        report.setRowCount(42);
        report.setCreatedAt(LocalDateTime.now());
        when(generatedReportRepository.findAllByOrderByCreatedAtDesc(any()))
                .thenReturn(new PageImpl<>(List.of(report), PageRequest.of(0, 25), 1));

        PageResponse<ReportHistoryEntry> page = service.history(null, 0, 25);
        assertEquals(1, page.total());
        assertEquals("payments", page.data().get(0).reportId());
        assertEquals(42, page.data().get(0).rowCount());
    }
}
