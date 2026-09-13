package com.tuitionnetwork.payments;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.demo.DemoApplication;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.identity.domain.AccountStatus;
import com.tuitionnetwork.identity.domain.Guardian;
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
import com.tuitionnetwork.payments.domain.Receipt;
import com.tuitionnetwork.payments.repository.PaymentAllocationRepository;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.payments.repository.ReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = DemoApplication.class)
public class SchoolPaymentIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private InstitutionAdminRepository institutionAdminRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private FeeLineRepository feeLineRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private GuardianRepository guardianRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

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

    private FeeLine feeTuitionA1;
    private FeeLine feeBooksA1;
    private FeeLine feeBusA2;
    private FeeLine feeTuitionB1;

    private Payment paymentA1;
    private Payment paymentA2;
    private Payment paymentB1;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        receiptRepository.deleteAll();
        paymentAllocationRepository.deleteAll();
        paymentRepository.deleteAll();
        feeLineRepository.deleteAll();
        studentRepository.deleteAll();
        guardianRepository.deleteAll();
        institutionAdminRepository.deleteAll();
        institutionRepository.deleteAll();

        // 1. Schools
        schoolA = new Institution("Cairo International School", "SCH-001", "SCHOOL_ABSORBS");
        schoolA.setAccountStatus(AccountStatus.ACTIVE);
        schoolA = institutionRepository.save(schoolA);

        schoolB = new Institution("Alexandria National School", "SCH-002", "SCHOOL_ABSORBS");
        schoolB.setAccountStatus(AccountStatus.ACTIVE);
        schoolB = institutionRepository.save(schoolB);

        // 2. School Admins & Finance
        adminA = new InstitutionAdmin(schoolA.getId(), "Amr Hassan", "amr.hassan@cis.edu.eg", "Password123!", "School Admin");
        adminA.setStatus("Active");
        adminA = institutionAdminRepository.save(adminA);

        financeA = new InstitutionAdmin(schoolA.getId(), "Dina Fouad", "dina.fouad@cis.edu.eg", "Finance@2026", "School Finance");
        financeA.setStatus("Active");
        financeA = institutionAdminRepository.save(financeA);

        adminB = new InstitutionAdmin(schoolB.getId(), "Tarek Samy", "tarek@ans.edu.eg", "Password123!", "School Admin");
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
        studentA1 = new Student(
                null, schoolA.getId(), "hash_s1", "enc_s1", "Yousef Adel",
                LocalDate.of(2010, 5, 12), "STU-0231", "Grade 10", "A",
                "Adel Mostafa", "+20 10 1234 5678", "a.mostafa@example.com"
        );
        studentA1.setStatus("Active");
        studentA1 = studentRepository.save(studentA1);

        studentA2 = new Student(
                null, schoolA.getId(), "hash_s2", "enc_s2", "Farida Nour",
                LocalDate.of(2011, 8, 20), "STU-0232", "Grade 9", "B",
                "Nour Ahmed", "+20 10 9876 5432", "n.ahmed@example.com"
        );
        studentA2.setStatus("Active");
        studentA2 = studentRepository.save(studentA2);

        studentB1 = new Student(
                null, schoolB.getId(), "hash_sb1", "enc_sb1", "Omar Tarek",
                LocalDate.of(2012, 1, 15), "STU-0999", "Grade 8", "A",
                "Tarek Samy", "+20 12 3456 7890", "t.samy@example.com"
        );
        studentB1.setStatus("Active");
        studentB1 = studentRepository.save(studentB1);

        // 5. Fee Lines
        feeTuitionA1 = new FeeLine(schoolA.getId(), studentA1.getId(), FeeType.TUITION,
                new BigDecimal("18000.00"), new BigDecimal("13000.00"), "Term 1 2026/27", LocalDate.now().plusMonths(1));
        feeTuitionA1.setPaidAmount(new BigDecimal("5000.00"));
        feeTuitionA1.setStatus(FeeStatus.PARTIALLY_PAID);
        feeTuitionA1 = feeLineRepository.save(feeTuitionA1);

        feeBooksA1 = new FeeLine(schoolA.getId(), studentA1.getId(), FeeType.BOOKS,
                new BigDecimal("3000.00"), BigDecimal.ZERO, "Term 1 2026/27", LocalDate.now().minusDays(5));
        feeBooksA1.setPaidAmount(new BigDecimal("3000.00"));
        feeBooksA1.setStatus(FeeStatus.PAID);
        feeBooksA1 = feeLineRepository.save(feeBooksA1);

        feeBusA2 = new FeeLine(schoolA.getId(), studentA2.getId(), FeeType.BUS,
                new BigDecimal("7000.00"), BigDecimal.ZERO, "Annual 2026/27", LocalDate.now().plusMonths(3));
        feeBusA2.setPaidAmount(new BigDecimal("7000.00"));
        feeBusA2.setStatus(FeeStatus.PAID);
        feeBusA2 = feeLineRepository.save(feeBusA2);

        feeTuitionB1 = new FeeLine(schoolB.getId(), studentB1.getId(), FeeType.TUITION,
                new BigDecimal("22000.00"), BigDecimal.ZERO, "Term 1 2026/27", LocalDate.now().plusMonths(1));
        feeTuitionB1.setPaidAmount(new BigDecimal("22000.00"));
        feeTuitionB1.setStatus(FeeStatus.PAID);
        feeTuitionB1 = feeLineRepository.save(feeTuitionB1);

        // 6. Payments
        paymentA1 = new Payment(UUID.randomUUID(), new BigDecimal("8000.00"), PaymentMethod.CREDIT_CARD, "idemp-pay-a1");
        paymentA1.setTransactionReference("TX-20260906-0041");
        paymentA1.setStatus(PaymentStatus.CAPTURED);
        paymentA1.setCreatedAt(LocalDateTime.now().minusDays(2));
        paymentA1 = paymentRepository.save(paymentA1);

        PaymentAllocation alloc1 = new PaymentAllocation(paymentA1, feeTuitionA1, new BigDecimal("5000.00"));
        paymentAllocationRepository.save(alloc1);
        PaymentAllocation alloc2 = new PaymentAllocation(paymentA1, feeBooksA1, new BigDecimal("3000.00"));
        paymentAllocationRepository.save(alloc2);

        Receipt r1 = new Receipt(paymentA1, "SIG-SHA256-AAAA111122223333", "https://cdn.tuitionnetwork.eg/receipts/receipt-" + paymentA1.getId() + ".pdf");
        r1.setIssuedAt(LocalDateTime.now().minusDays(2));
        receiptRepository.save(r1);

        paymentA2 = new Payment(UUID.randomUUID(), new BigDecimal("7000.00"), PaymentMethod.CIB_ACCOUNT, "idemp-pay-a2");
        paymentA2.setTransactionReference("TX-20260907-0052");
        paymentA2.setStatus(PaymentStatus.CAPTURED);
        paymentA2.setCreatedAt(LocalDateTime.now().minusDays(1));
        paymentA2 = paymentRepository.save(paymentA2);

        PaymentAllocation alloc3 = new PaymentAllocation(paymentA2, feeBusA2, new BigDecimal("7000.00"));
        paymentAllocationRepository.save(alloc3);

        paymentB1 = new Payment(UUID.randomUUID(), new BigDecimal("22000.00"), PaymentMethod.EPP_INSTALMENTS, "idemp-pay-b1");
        paymentB1.setTransactionReference("TX-20260908-0099");
        paymentB1.setStatus(PaymentStatus.CAPTURED);
        paymentB1.setCreatedAt(LocalDateTime.now());
        paymentB1 = paymentRepository.save(paymentB1);

        PaymentAllocation allocB = new PaymentAllocation(paymentB1, feeTuitionB1, new BigDecimal("22000.00"));
        paymentAllocationRepository.save(allocB);
    }

    @Test
    @DisplayName("6.1 List Payments: Scoped to School A, returns paginated data matching Phase 6.1 contract")
    void testListPayments_schoolScoped_success() throws Exception {
        mockMvc.perform(get("/api/v1/payments")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3))) // 3 allocations in School A (2 for paymentA1, 1 for paymentA2)
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.page").value(0))
                .andExpect(jsonPath("$.pageSize").value(25))
                .andExpect(jsonPath("$.totalPages").value(1))
                .andExpect(jsonPath("$.data[0].id").exists())
                .andExpect(jsonPath("$.data[0].studentId").exists())
                .andExpect(jsonPath("$.data[0].studentName").exists())
                .andExpect(jsonPath("$.data[0].feeName").exists())
                .andExpect(jsonPath("$.data[0].amountEGP").exists())
                .andExpect(jsonPath("$.data[0].method").exists())
                .andExpect(jsonPath("$.data[0].date").exists())
                .andExpect(jsonPath("$.data[0].status").value("Successful"))
                .andExpect(jsonPath("$.data[0].reconciliation").value("Reconciled"));

        // Finance role also can list payments
        mockMvc.perform(get("/api/v1/payments")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));

        // Unaliased path /payments also works
        mockMvc.perform(get("/payments")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));
    }

    @Test
    @DisplayName("6.1 List Payments Filter: Search by Student Name or Transaction ID")
    void testListPayments_filterBySearch() throws Exception {
        // Search by student name "Yousef"
        mockMvc.perform(get("/api/v1/payments?search=Yousef")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].studentName").value("Yousef Adel"));

        // Search by TX ref "TX-20260907-0052"
        mockMvc.perform(get("/api/v1/payments?search=TX-20260907-0052")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].studentName").value("Farida Nour"));
    }

    @Test
    @DisplayName("6.1 List Payments Filter: Filter by Date Range")
    void testListPayments_filterByDateRange() throws Exception {
        LocalDate today = LocalDate.now();
        LocalDate threeDaysAgo = today.minusDays(3);
        LocalDate yesterday = today.minusDays(1);

        mockMvc.perform(get("/api/v1/payments?dateFrom=" + threeDaysAgo + "&dateTo=" + yesterday)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)));

        // Future date range yields empty
        mockMvc.perform(get("/api/v1/payments?dateFrom=" + today.plusDays(1) + "&dateTo=" + today.plusDays(5))
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    @DisplayName("6.1 List Payments Filter: Filter by Fee Category")
    void testListPayments_filterByFeeCategory() throws Exception {
        mockMvc.perform(get("/api/v1/payments?feeCategory=Tuition")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].feeName").value(containsString("Tuition")));

        mockMvc.perform(get("/api/v1/payments?feeCategory=Bus subscription")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].feeName").value(containsString("Bus")));
    }

    @Test
    @DisplayName("6.1 List Payments Filter: Filter by Payment Method")
    void testListPayments_filterByMethod() throws Exception {
        mockMvc.perform(get("/api/v1/payments?method=Card")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)));

        mockMvc.perform(get("/api/v1/payments?method=Bank Transfer")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].studentName").value("Farida Nour"));
    }

    @Test
    @DisplayName("6.1 List Payments Filter: Filter by Student ID / Student Ref")
    void testListPayments_filterByStudentId() throws Exception {
        mockMvc.perform(get("/api/v1/payments?studentId=STU-0231")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.data[0].studentId").value("STU-0231"));
    }

    @Test
    @DisplayName("6.2 Payment Detail: Scoped detail view with allocated dues breakdown matching Phase 6.2")
    void testGetPaymentDetail_withAllocations_success() throws Exception {
        mockMvc.perform(get("/api/v1/payments/" + paymentA1.getId())
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("TX-20260906-0041"))
                .andExpect(jsonPath("$.studentId").value("STU-0231"))
                .andExpect(jsonPath("$.studentName").value("Yousef Adel"))
                .andExpect(jsonPath("$.amountEGP").value(8000.00))
                .andExpect(jsonPath("$.currency").value("EGP"))
                .andExpect(jsonPath("$.method").value("Card"))
                .andExpect(jsonPath("$.status").value("Successful"))
                .andExpect(jsonPath("$.reconciliation").value("Pending"))
                .andExpect(jsonPath("$.allocation", hasSize(2)))
                .andExpect(jsonPath("$.allocation[0].feeName").value(containsString("Tuition")))
                .andExpect(jsonPath("$.allocation[0].feeCategory").value("Tuition"))
                .andExpect(jsonPath("$.allocation[0].originalAmountEGP").value(18000.00))
                .andExpect(jsonPath("$.allocation[0].allocatedEGP").value(5000.00))
                .andExpect(jsonPath("$.allocation[0].remainingAfterEGP").value(13000.00))
                .andExpect(jsonPath("$.allocation[0].priority").value(1))
                .andExpect(jsonPath("$.allocation[1].feeName").value(containsString("Books")))
                .andExpect(jsonPath("$.allocation[1].allocatedEGP").value(3000.00))
                .andExpect(jsonPath("$.allocation[1].remainingAfterEGP").value(0.00))
                .andExpect(jsonPath("$.allocation[1].feeStatus").value("PAID"));
    }

    @Test
    @DisplayName("6.2 Payment Detail: Query by Transaction Reference string (TX-...)")
    void testGetPaymentDetail_byTransactionReference() throws Exception {
        mockMvc.perform(get("/api/v1/payments/TX-20260906-0041")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("TX-20260906-0041"))
                .andExpect(jsonPath("$.amountEGP").value(8000.00));
    }

    @Test
    @DisplayName("6.2 Payment Detail: Cross-School Isolation guardrail returns 403 Forbidden")
    void testGetPaymentDetail_crossSchoolAccess_forbidden() throws Exception {
        // School B admin tries to fetch School A payment
        mockMvc.perform(get("/api/v1/payments/" + paymentA1.getId())
                        .header("Authorization", "Bearer " + tokenAdminB))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(containsString("Access denied")));

        // School A admin tries to fetch School B payment
        mockMvc.perform(get("/api/v1/payments/" + paymentB1.getId())
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error").value(containsString("Access denied")));
    }

    @Test
    @DisplayName("6.2 Receipt: Fetch JSON receipt metadata with crypto signature")
    void testGetReceipt_jsonFormat_success() throws Exception {
        mockMvc.perform(get("/api/v1/payments/" + paymentA1.getId() + "/receipt")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(paymentA1.getId().toString()))
                .andExpect(jsonPath("$.receiptReference").value(startsWith("RCP-")))
                .andExpect(jsonPath("$.amount").value(8000.00))
                .andExpect(jsonPath("$.currency").value("EGP"))
                .andExpect(jsonPath("$.cryptoSignature").value("SIG-SHA256-AAAA111122223333"))
                .andExpect(jsonPath("$.fileUrl").value(containsString(".pdf")));
    }

    @Test
    @DisplayName("6.2 Receipt: Download official crypto-signed PDF receipt")
    void testGetReceipt_pdfFormat_download_success() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/payments/" + paymentA1.getId() + "/receipt?format=pdf")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("receipt-")))
                .andReturn();

        byte[] pdfBytes = res.getResponse().getContentAsByteArray();
        assertTrue(pdfBytes.length > 50, "PDF should not be empty");

        String pdfHeader = new String(pdfBytes, 0, Math.min(10, pdfBytes.length), StandardCharsets.ISO_8859_1);
        assertTrue(pdfHeader.startsWith("%PDF-1.4"), "Must be a valid PDF-1.4 binary document");

        String pdfContent = new String(pdfBytes, StandardCharsets.ISO_8859_1);
        assertTrue(pdfContent.contains("COMMERCIAL INTERNATIONAL BANK"), "Must contain CIB header");
        assertTrue(pdfContent.contains("Cairo International School"), "Must contain school name");
        assertTrue(pdfContent.contains("Yousef Adel"), "Must contain student name");
    }

    @Test
    @DisplayName("6.2 Receipt: Cross-School receipt access returns 403 Forbidden")
    void testGetReceipt_crossSchoolAccess_forbidden() throws Exception {
        mockMvc.perform(get("/api/v1/payments/" + paymentA1.getId() + "/receipt")
                        .header("Authorization", "Bearer " + tokenAdminB))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("6.3 Export Payments: Streams filtered CSV export for current view")
    void testExportPaymentsCsv_success() throws Exception {
        MvcResult res = mockMvc.perform(get("/api/v1/payments/export?feeCategory=Tuition")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"payments-export.csv\""))
                .andReturn();

        String csv = res.getResponse().getContentAsString(StandardCharsets.UTF_8);
        assertTrue(csv.contains("Transaction ID,Student ID,Student Name,Fee Name"), "CSV must have header row");
        assertTrue(csv.contains("TX-20260906-0041"), "CSV must include payment A1");
        assertTrue(csv.contains("Yousef Adel"), "CSV must include student name");
        assertTrue(csv.contains("Tuition"), "CSV must include filtered category");
    }

    @Test
    @DisplayName("Bank-Only Guardrails: School user cannot process payments (POST /payments returns 403)")
    void testSchoolUserCannotProcessPayment_returns403() throws Exception {
        String reqJson = """
                {
                    "nationalId": "29501011234567",
                    "feeIds": ["%s"],
                    "amountEGP": 1000.00,
                    "method": "Card"
                }
                """.formatted(feeTuitionA1.getId());

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + tokenFinanceA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(reqJson))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Bank-Only Guardrails: School user cannot retry payments (POST /payments/{id}/retry returns 403)")
    void testSchoolUserCannotRetryPayment_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/payments/" + paymentA1.getId() + "/retry")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Security Guardrail: Unauthenticated access returns 401 Unauthorized")
    void testUnauthenticatedAccess_returns401() throws Exception {
        mockMvc.perform(get("/api/v1/payments"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/payments/" + paymentA1.getId()))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/payments/" + paymentA1.getId() + "/receipt"))
                .andExpect(status().isUnauthorized());
    }
}
