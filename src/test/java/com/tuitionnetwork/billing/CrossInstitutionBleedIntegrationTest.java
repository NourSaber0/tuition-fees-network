package com.tuitionnetwork.billing;

import com.example.demo.DemoApplication;
import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.payments.repository.ReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.payments.domain.EPPSchedule;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
class CrossInstitutionBleedIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private FeeLineRepository feeLineRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Institution schoolA;
    private Institution schoolB;
    private Student studentB;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        auditLogRepository.deleteAll();
        receiptRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM payment_allocation");
        jdbcTemplate.execute("DELETE FROM payment_state_log");
        jdbcTemplate.execute("DELETE FROM epp_installment");
        jdbcTemplate.execute("DELETE FROM epp_schedule");
        paymentRepository.deleteAll();
        feeLineRepository.deleteAll();

        schoolA = institutionRepository.save(new Institution("School A", "SCH-A-" + UUID.randomUUID(), "POLICY_A"));
        schoolB = institutionRepository.save(new Institution("School B", "SCH-B-" + UUID.randomUUID(), "POLICY_B"));

        studentB = studentRepository.save(new Student(
                UUID.randomUUID(),
                schoolB.getId(),
                "std-b-hmac",
                "std-b-enc",
                "Student B",
                LocalDate.of(2012, 1, 1)
        ));

        // FeeLine belonging to School B
        FeeLine feeB = new FeeLine(
                schoolB.getId(),
                studentB.getId(),
                FeeType.TUITION,
                new BigDecimal("20000.00"),
                new BigDecimal("20000.00"),
                "Term 2 · 2026",
                LocalDate.now().plusMonths(3)
        );
        feeLineRepository.save(feeB);
    }

    @Test
    @WithMockUser(username = "admin@school-a.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void testCrossInstitutionDataBleed_isBlocked() throws Exception {
        // Attempt to query Student B's dues using School A's path / institution ID
        // Because the fee belongs to School B, querying under School A must return empty (0 results), ensuring no data bleed
        mockMvc.perform(get("/api/v1/institutions/{id}/students/{studentId}/dues", schoolA.getId(), studentB.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(0));
    }

    @Test
    @WithMockUser(username = "admin@school-a.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void testInstitutionLedgerView_logsAudit() throws Exception {
        // School A admin views School A's student ledger
        mockMvc.perform(get("/api/v1/institutions/{id}/students/{studentId}/dues", schoolA.getId(), studentB.getId()))
                .andExpect(status().isOk());

        List<AuditLog> logs = auditLogRepository.findAll();
        assertFalse(logs.isEmpty());

        AuditLog log = logs.get(logs.size() - 1);
        assertEquals(schoolA.getId(), log.getActorId());
        assertEquals("INSTITUTION_ADMIN", log.getActorType());
        assertEquals("VIEW_STUDENT_DUES", log.getAction());
    }

    @Test
    @WithMockUser(username = "admin@school-a.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void testCrossInstitutionFeeCancellation_isBlockedByGuardrail4() throws Exception {
        // School B has fee line feeB
        List<FeeLine> feeLines = feeLineRepository.findAll();
        assertFalse(feeLines.isEmpty());
        FeeLine feeB = feeLines.get(0);

        // School A attempts to cancel School B's fee line
        mockMvc.perform(post("/api/v1/institutions/{id}/dues/{feeLineId}/cancel", schoolA.getId(), feeB.getId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PENDING_BUSINESS_RULE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cross-Institution Data Bleed is strictly prohibited")));
    }

    @Test
    @WithMockUser(username = "admin@school-b.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void testMidYearEppFeeCancellation_isBlockedByGuardrail5() throws Exception {
        FeeLine feeB = feeLineRepository.findAll().get(0);

        Payment payment = new Payment(UUID.randomUUID(), new BigDecimal("20000.00"), PaymentMethod.EPP_INSTALMENTS, "IDEMP-EPP-" + UUID.randomUUID());
        EPPSchedule schedule = new EPPSchedule(
                payment,
                12, // 12-month tenor
                new BigDecimal("20000.00"),
                new BigDecimal("0.18"),
                new BigDecimal("3600.00"),
                new BigDecimal("200.00"),
                new BigDecimal("23800.00"),
                new BigDecimal("1983.33")
        );
        payment.setEppSchedule(schedule);
        PaymentAllocation allocation = new PaymentAllocation(payment, feeB, new BigDecimal("20000.00"));
        payment.addAllocation(allocation);
        paymentRepository.save(payment);

        // School B admin attempts to cancel fee line locked in 12-month EPP
        mockMvc.perform(post("/api/v1/institutions/{id}/dues/{feeLineId}/cancel", schoolB.getId(), feeB.getId()))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PENDING_BUSINESS_RULE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Mid-Year EPP Cancellation is undefined")));
    }

    @Test
    @WithMockUser(username = "admin@school-b.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void testNormalFeeCancellation_succeeds() throws Exception {
        FeeLine feeB = feeLineRepository.findAll().get(0);

        mockMvc.perform(post("/api/v1/institutions/{id}/dues/{feeLineId}/cancel", schoolB.getId(), feeB.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));

        FeeLine updated = feeLineRepository.findById(feeB.getId()).orElseThrow();
        assertEquals(FeeStatus.CANCELLED, updated.getStatus());
    }
}
