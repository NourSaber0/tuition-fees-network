package com.tuitionnetwork.reporting;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.identity.domain.AccountStatus;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.InstitutionAdmin;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.GuardianRepository;
import com.tuitionnetwork.identity.repository.InstitutionAdminRepository;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.identity.security.JwtTokenProvider;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.identity.security.UserRole;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.repository.PaymentAllocationRepository;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.payments.repository.ReceiptRepository;
import com.tuitionnetwork.reconciliation.repository.ReconciliationExceptionRepository;
import com.tuitionnetwork.reconciliation.repository.ReconciliationRunRepository;
import com.tuitionnetwork.reporting.repository.GeneratedReportRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
public class SchoolReportIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private InstitutionAdminRepository institutionAdminRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private GuardianRepository guardianRepository;

    @Autowired
    private FeeLineRepository feeLineRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private ReconciliationExceptionRepository exceptionRepository;

    @Autowired
    private ReconciliationRunRepository runRepository;

    @Autowired
    private GeneratedReportRepository generatedReportRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private MockMvc mockMvc;

    private Institution schoolA;
    private Institution schoolB;

    private InstitutionAdmin adminA;
    private InstitutionAdmin financeA;
    private InstitutionAdmin adminB;

    private String tokenAdminA;
    private String tokenFinanceA;
    private String tokenAdminB;

    private Student studentA1;
    private Student studentA2;
    private Student studentB1;

    private FeeLine feeA1;
    private FeeLine feeA2;
    private FeeLine feeB1;

    private Payment paymentA1;
    private Payment paymentB1;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        cleanDatabase();

        // 1. Schools
        schoolA = new Institution("Al-Amal Language School", "SCH-AML-01", "SCHOOL_ABSORBS");
        schoolA.setAccountStatus(AccountStatus.ACTIVE);
        schoolA = institutionRepository.save(schoolA);

        schoolB = new Institution("Cairo Modern British School", "SCH-CMB-02", "SCHOOL_ABSORBS");
        schoolB.setAccountStatus(AccountStatus.ACTIVE);
        schoolB = institutionRepository.save(schoolB);

        // 2. Admins
        adminA = new InstitutionAdmin(schoolA.getId(), "Tarek Mahmoud", "tarek@amal.edu.eg", "Password123!", "School Admin");
        adminA.setStatus("Active");
        adminA = institutionAdminRepository.save(adminA);

        financeA = new InstitutionAdmin(schoolA.getId(), "Salma Nabil", "salma@amal.edu.eg", "Finance@2026", "School Finance");
        financeA.setStatus("Active");
        financeA = institutionAdminRepository.save(financeA);

        adminB = new InstitutionAdmin(schoolB.getId(), "Yasser Galal", "yasser@cmb.edu.eg", "Password123!", "School Admin");
        adminB.setStatus("Active");
        adminB = institutionAdminRepository.save(adminB);

        // 3. JWT Tokens
        tokenAdminA = jwtTokenProvider.generateToken(new SecurityUserPrincipal(
                adminA.getId(), adminA.getEmail(), adminA.getName(),
                UserRole.ROLE_SCHOOL_ADMIN,
                List.of(UserRole.ROLE_SCHOOL_ADMIN, UserRole.ROLE_INSTITUTION_ADMIN),
                schoolA.getId()
        ));

        tokenFinanceA = jwtTokenProvider.generateToken(new SecurityUserPrincipal(
                financeA.getId(), financeA.getEmail(), financeA.getName(),
                UserRole.ROLE_SCHOOL_FINANCE,
                List.of(UserRole.ROLE_SCHOOL_FINANCE, UserRole.ROLE_INSTITUTION_ADMIN),
                schoolA.getId()
        ));

        tokenAdminB = jwtTokenProvider.generateToken(new SecurityUserPrincipal(
                adminB.getId(), adminB.getEmail(), adminB.getName(),
                UserRole.ROLE_SCHOOL_ADMIN,
                List.of(UserRole.ROLE_SCHOOL_ADMIN, UserRole.ROLE_INSTITUTION_ADMIN),
                schoolB.getId()
        ));

        // 4. Students
        studentA1 = new Student();
        studentA1.setFullName("Yousef Adel");
        studentA1.setStudentRef("STD-AML-001");
        studentA1.setGrade("Grade 10");
        studentA1.setStatus("Active");
        studentA1.setNationalIdHash("hash-a1");
        studentA1.setNationalIdEncrypted("enc-a1");
        studentA1.setInstitutionId(schoolA.getId());
        studentA1 = studentRepository.save(studentA1);

        studentA2 = new Student();
        studentA2.setFullName("Mariam Sherif");
        studentA2.setStudentRef("STD-AML-002");
        studentA2.setGrade("Grade 11");
        studentA2.setStatus("Active");
        studentA2.setNationalIdHash("hash-a2");
        studentA2.setNationalIdEncrypted("enc-a2");
        studentA2.setInstitutionId(schoolA.getId());
        studentA2 = studentRepository.save(studentA2);

        studentB1 = new Student();
        studentB1.setFullName("Omar Farouk");
        studentB1.setStudentRef("STD-CMB-001");
        studentB1.setGrade("Grade 12");
        studentB1.setStatus("Active");
        studentB1.setNationalIdHash("hash-b1");
        studentB1.setNationalIdEncrypted("enc-b1");
        studentB1.setInstitutionId(schoolB.getId());
        studentB1 = studentRepository.save(studentB1);

        // 5. Fee Lines
        // feeA1: fully paid
        feeA1 = new FeeLine(schoolA.getId(), studentA1.getId(), FeeType.TUITION,
                new BigDecimal("18000.00"), new BigDecimal("0.00"), "2026/2027", LocalDate.of(2026, 9, 15));
        feeA1.setPaidAmount(new BigDecimal("18000.00"));
        feeA1.setStatus(FeeStatus.PAID);
        feeA1 = feeLineRepository.save(feeA1);

        // feeA2: partially paid (remaining 7,000 of 15,000)
        feeA2 = new FeeLine(schoolA.getId(), studentA2.getId(), FeeType.TUITION,
                new BigDecimal("15000.00"), new BigDecimal("7000.00"), "2026/2027", LocalDate.of(2026, 9, 20));
        feeA2.setPaidAmount(new BigDecimal("8000.00"));
        feeA2.setStatus(FeeStatus.PARTIALLY_PAID);
        feeA2 = feeLineRepository.save(feeA2);

        // feeB1: School B tuition (outstanding 25,000)
        feeB1 = new FeeLine(schoolB.getId(), studentB1.getId(), FeeType.TUITION,
                new BigDecimal("25000.00"), new BigDecimal("25000.00"), "2026/2027", LocalDate.of(2026, 9, 10));
        feeB1.setStatus(FeeStatus.OUTSTANDING);
        feeB1 = feeLineRepository.save(feeB1);

        // 6. Payments
        paymentA1 = new Payment(UUID.randomUUID(), new BigDecimal("18000.00"),
                PaymentMethod.CREDIT_CARD, "IDEMP-AML-" + UUID.randomUUID());
        paymentA1.setStatus(PaymentStatus.CAPTURED);
        paymentA1.setTransactionReference("TX-AML-20260901-01");
        paymentA1 = paymentRepository.save(paymentA1);

        PaymentAllocation allocA1 = new PaymentAllocation(paymentA1, feeA1, new BigDecimal("18000.00"));
        paymentAllocationRepository.save(allocA1);

        paymentB1 = new Payment(UUID.randomUUID(), new BigDecimal("10000.00"),
                PaymentMethod.CIB_ACCOUNT, "IDEMP-CMB-" + UUID.randomUUID());
        paymentB1.setStatus(PaymentStatus.CAPTURED);
        paymentB1.setTransactionReference("TX-CMB-20260901-02");
        paymentB1 = paymentRepository.save(paymentB1);

        PaymentAllocation allocB1 = new PaymentAllocation(paymentB1, feeB1, new BigDecimal("10000.00"));
        paymentAllocationRepository.save(allocB1);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
        generatedReportRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM epp_installment");
        jdbcTemplate.execute("DELETE FROM epp_schedule");
        jdbcTemplate.execute("DELETE FROM payment_state_log");
        receiptRepository.deleteAll();
        exceptionRepository.deleteAll();
        runRepository.deleteAll();
        paymentAllocationRepository.deleteAll();
        paymentRepository.deleteAll();
        feeLineRepository.deleteAll();
        studentRepository.deleteAll();
        guardianRepository.deleteAll();
        institutionAdminRepository.deleteAll();
        institutionRepository.deleteAll();
    }

    @Test
    @DisplayName("8.1 GET /reports/catalogue and /reports/templates return 4 school templates")
    void catalogueAndTemplates_returnFourSchoolTemplates() throws Exception {
        // Both /reports/catalogue and alias /reports/templates must return the 4 templates
        for (String path : List.of("/reports/catalogue", "/reports/templates")) {
            mockMvc.perform(get(path)
                            .header("Authorization", "Bearer " + tokenAdminA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$", hasSize(4)))
                    .andExpect(jsonPath("$[0].id").value("school-collections"))
                    .andExpect(jsonPath("$[0].title").value("Collection Report"))
                    .andExpect(jsonPath("$[0].category").value("Collections"))
                    .andExpect(jsonPath("$[0].formats", hasItems("PDF", "CSV")))
                    .andExpect(jsonPath("$[1].id").value("school-payments"))
                    .andExpect(jsonPath("$[1].title").value("Payment History"))
                    .andExpect(jsonPath("$[1].category").value("Payments"))
                    .andExpect(jsonPath("$[2].id").value("school-outstanding-fees"))
                    .andExpect(jsonPath("$[2].title").value("Outstanding Fees"))
                    .andExpect(jsonPath("$[2].category").value("Fees"))
                    .andExpect(jsonPath("$[3].id").value("school-partial-payments"))
                    .andExpect(jsonPath("$[3].title").value("Partial Payments"))
                    .andExpect(jsonPath("$[3].category").value("Fees"));
        }
    }

    @Test
    @DisplayName("8.2 POST /reports/generate returns 202 Accepted with status processing")
    void generate_returns202Accepted_withProcessingStatus() throws Exception {
        String body = """
                {
                    "reportId": "school-outstanding-fees",
                    "dateFrom": "2026-08-01",
                    "dateTo": "2026-09-30",
                    "format": "CSV",
                    "filters": {
                        "feeCategory": "Tuition"
                    }
                }
                """;

        mockMvc.perform(post("/reports/generate")
                        .header("Authorization", "Bearer " + tokenFinanceA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.reportId").value("school-outstanding-fees"))
                .andExpect(jsonPath("$.status").value("processing"))
                .andExpect(jsonPath("$.downloadUrl").isNotEmpty());
    }

    @Test
    @DisplayName("8.2 POST /reports/generate validations: date_from_after_date_to and unsupported_format_for_report")
    void generate_validations_dateRangeAndUnsupportedFormat() throws Exception {
        // 1. date_from_after_date_to
        String invalidDateBody = """
                {
                    "reportId": "school-collections",
                    "dateFrom": "2026-09-30",
                    "dateTo": "2026-08-01",
                    "format": "CSV"
                }
                """;

        mockMvc.perform(post("/reports/generate")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidDateBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("date_from_after_date_to"));

        // 2. unsupported_format_for_report
        String invalidFormatBody = """
                {
                    "reportId": "school-collections",
                    "dateFrom": "2026-08-01",
                    "dateTo": "2026-09-30",
                    "format": "XML"
                }
                """;

        mockMvc.perform(post("/reports/generate")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(invalidFormatBody))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("unsupported_format_for_report"));
    }

    @Test
    @DisplayName("8.3 & 8.4 GET /reports/jobs/{jobId} and /download stream CSV file and preview")
    void pollJob_andDownload_endToEnd() throws Exception {
        // 1. Generate school-collections report
        String genBody = """
                {
                    "reportId": "school-collections",
                    "dateFrom": "2026-08-01",
                    "dateTo": "2026-09-30",
                    "format": "CSV"
                }
                """;

        MvcResult genResult = mockMvc.perform(post("/reports/generate")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(genBody))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("processing"))
                .andReturn();

        JsonNode json = objectMapper.readTree(genResult.getResponse().getContentAsString());
        String jobId = json.get("jobId").asText();
        assertNotNull(jobId);

        // 2. Poll GET /reports/jobs/{jobId} -> returns READY with preview
        mockMvc.perform(get("/reports/jobs/{jobId}", jobId)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.jobId").value(jobId))
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.reportId").value("school-collections"))
                .andExpect(jsonPath("$.preview.columns", hasSize(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$.preview.rows", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.preview.rows[0].student").value("Yousef Adel"))
                .andExpect(jsonPath("$.preview.rows[0].amountEGP").value(18000.0));

        // 3. Download GET /reports/jobs/{jobId}/download
        MvcResult dlResult = mockMvc.perform(get("/reports/jobs/{jobId}/download", jobId)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=")))
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andReturn();

        String csvContent = dlResult.getResponse().getContentAsString();
        assertTrue(csvContent.contains("Date"));
        assertTrue(csvContent.contains("Payment ID"));
        assertTrue(csvContent.contains("Yousef Adel"));
        assertTrue(csvContent.contains("18000"));
        // Must NOT contain School B's student or payment
        assertFalse(csvContent.contains("Omar Farouk"));
    }

    @Test
    @DisplayName("8.2 Generate all 4 school report templates successfully")
    void generate_allFourSchoolReportTypes() throws Exception {
        List<String> reportIds = List.of(
                "school-collections",
                "school-payments",
                "school-outstanding-fees",
                "school-partial-payments"
        );

        for (String rptId : reportIds) {
            String body = String.format("""
                    {
                        "reportId": "%s",
                        "dateFrom": "2026-08-01",
                        "dateTo": "2026-09-30",
                        "format": "CSV"
                    }
                    """, rptId);

            MvcResult result = mockMvc.perform(post("/reports/generate")
                            .header("Authorization", "Bearer " + tokenAdminA)
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(body))
                    .andExpect(status().isAccepted())
                    .andExpect(jsonPath("$.jobId").isNotEmpty())
                    .andExpect(jsonPath("$.reportId").value(rptId))
                    .andReturn();

            String jobId = objectMapper.readTree(result.getResponse().getContentAsString()).get("jobId").asText();

            // Verify job can be fetched and has preview
            mockMvc.perform(get("/reports/jobs/{jobId}", jobId)
                            .header("Authorization", "Bearer " + tokenAdminA))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("READY"))
                    .andExpect(jsonPath("$.preview.columns").isArray());
        }
    }

    @Test
    @DisplayName("8.5 GET /reports/history is strictly scoped to the authenticated school")
    void history_scopedStrictlyToSchool() throws Exception {
        // School A generates outstanding-fees
        String bodyA = """
                {
                    "reportId": "school-outstanding-fees",
                    "dateFrom": "2026-08-01",
                    "dateTo": "2026-09-30",
                    "format": "CSV"
                }
                """;
        mockMvc.perform(post("/reports/generate")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyA))
                .andExpect(status().isAccepted());

        // School B generates school-payments
        String bodyB = """
                {
                    "reportId": "school-payments",
                    "dateFrom": "2026-08-01",
                    "dateTo": "2026-09-30",
                    "format": "CSV"
                }
                """;
        mockMvc.perform(post("/reports/generate")
                        .header("Authorization", "Bearer " + tokenAdminB)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyB))
                .andExpect(status().isAccepted());

        // School A history -> should only see school-outstanding-fees
        mockMvc.perform(get("/reports/history")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].reportId").value("school-outstanding-fees"));

        // School B history -> should only see school-payments
        mockMvc.perform(get("/reports/history")
                        .header("Authorization", "Bearer " + tokenAdminB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].reportId").value("school-payments"));
    }

    @Test
    @DisplayName("Multi-Tenant Isolation: School B cannot access School A jobs, downloads, or cross-school parameters")
    void multiTenantIsolation_cannotAccessOtherSchoolReports() throws Exception {
        // School A generates a report
        String bodyA = """
                {
                    "reportId": "school-collections",
                    "dateFrom": "2026-08-01",
                    "dateTo": "2026-09-30",
                    "format": "CSV"
                }
                """;
        MvcResult resA = mockMvc.perform(post("/reports/generate")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyA))
                .andExpect(status().isAccepted())
                .andReturn();

        String jobIdA = objectMapper.readTree(resA.getResponse().getContentAsString()).get("jobId").asText();

        // 1. School B attempts to poll School A's job -> 403 Forbidden
        mockMvc.perform(get("/reports/jobs/{jobId}", jobIdA)
                        .header("Authorization", "Bearer " + tokenAdminB))
                .andExpect(status().isForbidden());

        // 2. School B attempts to download School A's report file -> 403 Forbidden
        mockMvc.perform(get("/reports/jobs/{jobId}/download", jobIdA)
                        .header("Authorization", "Bearer " + tokenAdminB))
                .andExpect(status().isForbidden());

        // 3. School A attempts to tamper with institutionId query parameter targeting School B -> 403 Forbidden
        mockMvc.perform(post("/reports/generate")
                        .param("institutionId", schoolB.getId().toString())
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bodyA))
                .andExpect(status().isForbidden());

        // 4. School user cannot generate bank-only report (e.g. network-collections) -> 403 Forbidden
        String bankReportBody = """
                {
                    "reportId": "network-collections",
                    "dateFrom": "2026-08-01",
                    "dateTo": "2026-09-30",
                    "format": "CSV"
                }
                """;
        mockMvc.perform(post("/reports/generate")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(bankReportBody))
                .andExpect(status().isForbidden());
    }
}
