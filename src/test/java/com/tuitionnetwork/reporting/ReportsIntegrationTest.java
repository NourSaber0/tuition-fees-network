package com.tuitionnetwork.reporting;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.InstitutionType;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.reporting.repository.GeneratedReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
@WithMockUser(username = "finance@cibeg.com", roles = {"BACK_OFFICE"})
class ReportsIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private FeeLineRepository feeLineRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private GeneratedReportRepository generatedReportRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void endToEnd_reportLifecycle_catalogueGenerateDownloadHistoryAndAudit() throws Exception {
        // 1. Initial catalogue check
        mockMvc.perform(get("/api/v1/reports/catalogue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)))
                .andExpect(jsonPath("$[0].id").value("network-collections"));

        // Seed an institution and payment in range
        Institution inst = new Institution("Cairo Modern College", "SCH-CMC-01", null);
        inst.setInstitutionType(InstitutionType.SCHOOL);
        inst = institutionRepository.save(inst);

        FeeLine feeLine = new FeeLine(
                inst.getId(),
                UUID.randomUUID(),
                FeeType.TUITION,
                new BigDecimal("15000.00"),
                new BigDecimal("15000.00"),
                "Term 1 · 2026",
                LocalDate.now().plusMonths(1)
        );
        feeLine = feeLineRepository.save(feeLine);

        Payment payment = new Payment(UUID.randomUUID(), new BigDecimal("15000.00"),
                PaymentMethod.CREDIT_CARD, "IDEMP-RPT-" + UUID.randomUUID());
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.addAllocation(new PaymentAllocation(payment, feeLine, new BigDecimal("15000.00")));
        paymentRepository.save(payment);

        // 2. Generate Payments Report
        String fromStr = LocalDate.now().minusDays(1).toString();
        String toStr = LocalDate.now().plusDays(1).toString();
        String generateBody = "{\"reportId\":\"payments\",\"dateFrom\":\"" + fromStr + "\",\"dateTo\":\"" + toStr + "\",\"format\":\"CSV\"}";

        MvcResult genResult = mockMvc.perform(post("/api/v1/reports/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(generateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.reportId").value("payments"))
                .andExpect(jsonPath("$.downloadUrl").isNotEmpty())
                .andExpect(jsonPath("$.preview.columns").isNotEmpty())
                .andReturn();

        String jobId = objectMapper.readTree(genResult.getResponse().getContentAsString()).get("jobId").asText();
        assertNotNull(jobId);

        // 3. Verify audit log entry was created
        List<AuditLog> auditLogs = auditLogRepository.findByAction("GENERATE_REPORT");
        assertFalse(auditLogs.isEmpty());

        // 4. GET /reports/jobs/{jobId}
        mockMvc.perform(get("/api/v1/reports/jobs/{id}", jobId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.preview.rows").isNotEmpty());

        // 5. GET /reports/jobs/{jobId}/download
        mockMvc.perform(get("/api/v1/reports/jobs/{id}/download", jobId))
                .andExpect(status().isOk())
                .andExpect(header().exists("Content-Disposition"))
                .andExpect(content().contentTypeCompatibleWith("text/csv"));

        // 6. Check that GET /reports/catalogue now has lastGeneratedAt populated for 'payments'
        mockMvc.perform(get("/api/v1/reports/catalogue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[?(@.id == 'payments')].lastGeneratedAt").isNotEmpty());

        // 7. GET /reports/history
        mockMvc.perform(get("/api/v1/reports/history")
                        .param("reportId", "payments")
                        .param("page", "0")
                        .param("pageSize", "10"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").isNumber())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[0].reportId").value("payments"));
    }

    @Test
    void generate_errorsValidation() throws Exception {
        // Date from after date to
        mockMvc.perform(post("/api/v1/reports/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportId\":\"payments\",\"dateFrom\":\"2026-09-01\",\"dateTo\":\"2026-08-01\",\"format\":\"CSV\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("date_from_after_date_to"));

        // Unsupported format
        mockMvc.perform(post("/api/v1/reports/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportId\":\"payments\",\"dateFrom\":\"2026-08-01\",\"dateTo\":\"2026-08-31\",\"format\":\"PDF\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("unsupported_format_for_report"));
    }
}
