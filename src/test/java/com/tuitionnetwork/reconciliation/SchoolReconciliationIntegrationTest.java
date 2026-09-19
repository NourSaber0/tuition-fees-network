package com.tuitionnetwork.reconciliation;

import com.example.demo.DemoApplication;
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
import com.tuitionnetwork.reconciliation.domain.ReconciliationException;
import com.tuitionnetwork.reconciliation.domain.ReconciliationRun;
import com.tuitionnetwork.reconciliation.repository.ReconciliationExceptionRepository;
import com.tuitionnetwork.reconciliation.repository.ReconciliationRunRepository;
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
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
public class SchoolReconciliationIntegrationTest {

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
    private ReconciliationRunRepository runRepository;

    @Autowired
    private ReconciliationExceptionRepository exceptionRepository;

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
    private Student studentA3;
    private Student studentB1;

    private FeeLine feeA1;
    private FeeLine feeA2;
    private FeeLine feeA3;
    private FeeLine feeB1;

    private Payment paymentA1;
    private Payment paymentA2;
    private Payment paymentA3;
    private Payment paymentB1;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        cleanDatabase();

        // 1. Schools
        schoolA = new Institution("Cairo Modern Academy", "CMA-01", "SCHOOL_ABSORBS");
        schoolA.setAccountStatus(AccountStatus.ACTIVE);
        schoolA = institutionRepository.save(schoolA);

        schoolB = new Institution("Alexandria British Academy", "ABA-02", "SCHOOL_ABSORBS");
        schoolB.setAccountStatus(AccountStatus.ACTIVE);
        schoolB = institutionRepository.save(schoolB);

        // 2. School Admins & Finance
        adminA = new InstitutionAdmin(schoolA.getId(), "Hassan Farouk", "hassan.farouk@cma.edu.eg", "Password123!", "School Admin");
        adminA.setStatus("Active");
        adminA = institutionAdminRepository.save(adminA);

        financeA = new InstitutionAdmin(schoolA.getId(), "Laila Mostafa", "laila.mostafa@cma.edu.eg", "Finance@2026", "School Finance");
        financeA.setStatus("Active");
        financeA = institutionAdminRepository.save(financeA);

        adminB = new InstitutionAdmin(schoolB.getId(), "Karim Samy", "karim.samy@aba.edu.eg", "Password123!", "School Admin");
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
        studentA1 = new Student(null, schoolA.getId(), "hash_s1", "enc_s1", "Youssef Nabil",
                LocalDate.of(2010, 3, 15), "STU-0231", "Grade 10", "A",
                "Nabil Omar", "+20 10 1111 2222", "nabil@example.com");
        studentA1 = studentRepository.save(studentA1);

        studentA2 = new Student(null, schoolA.getId(), "hash_s2", "enc_s2", "Mona Gamal",
                LocalDate.of(2011, 7, 20), "STU-0232", "Grade 9", "B",
                "Gamal Fahmy", "+20 10 3333 4444", "gamal@example.com");
        studentA2 = studentRepository.save(studentA2);

        studentA3 = new Student(null, schoolA.getId(), "hash_s3", "enc_s3", "Tarek Zaki",
                LocalDate.of(2012, 1, 10), "STU-0233", "Grade 8", "A",
                "Zaki Tawfik", "+20 10 5555 6666", "zaki@example.com");
        studentA3 = studentRepository.save(studentA3);

        studentB1 = new Student(null, schoolB.getId(), "hash_sb1", "enc_sb1", "Farida Walid",
                LocalDate.of(2010, 9, 5), "STU-0991", "Grade 10", "C",
                "Walid Saad", "+20 10 7777 8888", "walid@example.com");
        studentB1 = studentRepository.save(studentB1);

        // 5. Fee Lines
        feeA1 = new FeeLine(schoolA.getId(), studentA1.getId(), FeeType.TUITION, new BigDecimal("10000.00"), BigDecimal.ZERO, "2026/2027", LocalDate.of(2026, 9, 1));
        feeA1 = feeLineRepository.save(feeA1);

        feeA2 = new FeeLine(schoolA.getId(), studentA2.getId(), FeeType.BOOKS, new BigDecimal("5000.00"), new BigDecimal("5000.00"), "2026/2027", LocalDate.of(2026, 9, 10));
        feeA2 = feeLineRepository.save(feeA2);

        feeA3 = new FeeLine(schoolA.getId(), studentA3.getId(), FeeType.TUITION, new BigDecimal("4000.00"), BigDecimal.ZERO, "2026/2027", LocalDate.of(2026, 9, 15));
        feeA3 = feeLineRepository.save(feeA3);

        feeB1 = new FeeLine(schoolB.getId(), studentB1.getId(), FeeType.TUITION, new BigDecimal("20000.00"), BigDecimal.ZERO, "2026/2027", LocalDate.of(2026, 9, 1));
        feeB1 = feeLineRepository.save(feeB1);

        // 6. Payments
        // Payment A1: CAPTURED -> Reconciled
        paymentA1 = new Payment(UUID.randomUUID(), new BigDecimal("10000.00"), PaymentMethod.CIB_ACCOUNT, "IDEMP-A1-" + UUID.randomUUID());
        paymentA1.setStatus(PaymentStatus.CAPTURED);
        paymentA1.setTransactionReference("TX-20260906-0041");
        paymentA1 = paymentRepository.save(paymentA1);

        PaymentAllocation allocA1 = new PaymentAllocation(paymentA1, feeA1, new BigDecimal("10000.00"));
        paymentAllocationRepository.save(allocA1);

        // Payment A2: PENDING -> Pending
        paymentA2 = new Payment(UUID.randomUUID(), new BigDecimal("5000.00"), PaymentMethod.CREDIT_CARD, "IDEMP-A2-" + UUID.randomUUID());
        paymentA2.setStatus(PaymentStatus.PENDING);
        paymentA2.setTransactionReference("TX-20260906-0042");
        paymentA2 = paymentRepository.save(paymentA2);

        PaymentAllocation allocA2 = new PaymentAllocation(paymentA2, feeA2, new BigDecimal("5000.00"));
        paymentAllocationRepository.save(allocA2);

        // Payment A3: CAPTURED with Exception -> Unreconciled
        paymentA3 = new Payment(UUID.randomUUID(), new BigDecimal("4000.00"), PaymentMethod.CREDIT_CARD, "IDEMP-A3-" + UUID.randomUUID());
        paymentA3.setStatus(PaymentStatus.CAPTURED);
        paymentA3.setTransactionReference("TX-20260906-0043");
        paymentA3 = paymentRepository.save(paymentA3);

        PaymentAllocation allocA3 = new PaymentAllocation(paymentA3, feeA3, new BigDecimal("4000.00"));
        paymentAllocationRepository.save(allocA3);

        ReconciliationException ex = new ReconciliationException();
        ex.setPaymentId(paymentA3.getId());
        ex.setTxRef("TX-20260906-0043");
        ex.setInstitution(schoolA.getName());
        ex.setInstitutionType("School");
        ex.setStatus("Open");
        ex.setType("Fee Mismatch");
        ex.setBankAmountEGP(3500L);
        ex.setSchoolAmountEGP(4000L);
        ex.setDifferenceEGP(500L);
        exceptionRepository.save(ex);

        // Payment B1: School B
        paymentB1 = new Payment(UUID.randomUUID(), new BigDecimal("20000.00"), PaymentMethod.CIB_ACCOUNT, "IDEMP-B1-" + UUID.randomUUID());
        paymentB1.setStatus(PaymentStatus.CAPTURED);
        paymentB1.setTransactionReference("TX-20260906-0099");
        paymentB1 = paymentRepository.save(paymentB1);

        PaymentAllocation allocB1 = new PaymentAllocation(paymentB1, feeB1, new BigDecimal("20000.00"));
        paymentAllocationRepository.save(allocB1);
    }

    @AfterEach
    void tearDown() {
        cleanDatabase();
    }

    private void cleanDatabase() {
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
    @DisplayName("7.1 GET /reconciliation/summary as School Admin returns accurate match counts and 2% CIB fee transparency")
    void summary_asSchoolAdmin_returnsSchoolScopedSummaryWithFeeTransparency() throws Exception {
        // School A has: 1 Reconciled (10,000 EGP), 1 Pending (5,000 EGP), 1 Unreconciled (4,000 EGP)
        // 2% CIB fee on 10,000 EGP = 200 EGP; Net settled = 9,800 EGP.
        mockMvc.perform(get("/reconciliation/summary")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReconciled", is(1)))
                .andExpect(jsonPath("$.totalPending", is(1)))
                .andExpect(jsonPath("$.totalUnreconciled", is(1)))
                .andExpect(jsonPath("$.grossCollectedEGP", is(10000)))
                .andExpect(jsonPath("$.cibFeeEGP", is(200)))
                .andExpect(jsonPath("$.netSettledEGP", is(9800)))
                .andExpect(jsonPath("$.pendingPayoutEGP", is(5000)));

        // Dual path /api/v1/reconciliation/summary works identically
        mockMvc.perform(get("/api/v1/reconciliation/summary")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReconciled", is(1)))
                .andExpect(jsonPath("$.netSettledEGP", is(9800)));

        // Accessible to School Finance
        mockMvc.perform(get("/reconciliation/summary")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReconciled", is(1)))
                .andExpect(jsonPath("$.cibFeeEGP", is(200)));
    }

    @Test
    @DisplayName("7.2 GET /reconciliation/transactions with query filters and pagination")
    void transactions_withFiltersAndPagination_returnsSchoolTransactions() throws Exception {
        // Query all transactions for School A
        mockMvc.perform(get("/reconciliation/transactions")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(3)))
                .andExpect(jsonPath("$.data", hasSize(3)));

        // Filter by status = Reconciled
        mockMvc.perform(get("/reconciliation/transactions")
                        .param("status", "Reconciled")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(1)))
                .andExpect(jsonPath("$.data[0].paymentId", is("TX-20260906-0041")))
                .andExpect(jsonPath("$.data[0].reconciliationStatus", is("Reconciled")))
                .andExpect(jsonPath("$.data[0].amountEGP", is(10000)))
                .andExpect(jsonPath("$.data[0].studentId", is("STU-0231")));

        // Filter by status = Pending
        mockMvc.perform(get("/reconciliation/transactions")
                        .param("status", "Pending")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(1)))
                .andExpect(jsonPath("$.data[0].paymentId", is("TX-20260906-0042")))
                .andExpect(jsonPath("$.data[0].reconciliationStatus", is("Pending")))
                .andExpect(jsonPath("$.data[0].amountEGP", is(5000)));

        // Filter by status = Unreconciled
        mockMvc.perform(get("/reconciliation/transactions")
                        .param("status", "Unreconciled")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(1)))
                .andExpect(jsonPath("$.data[0].paymentId", is("TX-20260906-0043")))
                .andExpect(jsonPath("$.data[0].reconciliationStatus", is("Unreconciled")))
                .andExpect(jsonPath("$.data[0].amountEGP", is(4000)));

        // Filter by studentId
        mockMvc.perform(get("/reconciliation/transactions")
                        .param("studentId", "STU-0231")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(1)))
                .andExpect(jsonPath("$.data[0].studentId", is("STU-0231")));

        // Filter by paymentId
        mockMvc.perform(get("/reconciliation/transactions")
                        .param("paymentId", "TX-20260906-0042")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(1)))
                .andExpect(jsonPath("$.data[0].paymentId", is("TX-20260906-0042")));

        // Pagination test
        mockMvc.perform(get("/reconciliation/transactions")
                        .param("page", "0")
                        .param("pageSize", "2")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(3)))
                .andExpect(jsonPath("$.page", is(0)))
                .andExpect(jsonPath("$.pageSize", is(2)))
                .andExpect(jsonPath("$.totalPages", is(2)))
                .andExpect(jsonPath("$.data", hasSize(2)));
    }

    @Test
    @DisplayName("7.3 GET /reconciliation/settlements returns settlement cycles with bank references and CIB fee deduction")
    void settlements_returnsDailyCyclesWithCibFeeTransparency() throws Exception {
        mockMvc.perform(get("/reconciliation/settlements")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].id", startsWith("SET-CMA-01-")))
                .andExpect(jsonPath("$.data[0].txRef", startsWith("TX-")))
                .andExpect(jsonPath("$.data[0].reconRef", startsWith("RECON-")))
                .andExpect(jsonPath("$.data[0].grossEGP", is(10000)))
                .andExpect(jsonPath("$.data[0].cibFeeEGP", is(200)))
                .andExpect(jsonPath("$.data[0].netEGP", is(9800)))
                .andExpect(jsonPath("$.data[0].status", is("Completed")))
                .andExpect(jsonPath("$.summary.totalSettledEGP", is(9800)))
                .andExpect(jsonPath("$.summary.recordCount", is(1)));
    }

    @Test
    @DisplayName("7.4 Multi-tenant isolation: School A cannot see School B's reconciliation data and cross-school access is rejected")
    void tenantIsolation_enforcesSchoolBoundary() throws Exception {
        // School B sees only School B data (1 Reconciled = 20,000 EGP, 2% fee = 400 EGP, Net = 19,600 EGP)
        mockMvc.perform(get("/reconciliation/summary")
                        .header("Authorization", "Bearer " + tokenAdminB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReconciled", is(1)))
                .andExpect(jsonPath("$.totalPending", is(0)))
                .andExpect(jsonPath("$.totalUnreconciled", is(0)))
                .andExpect(jsonPath("$.grossCollectedEGP", is(20000)))
                .andExpect(jsonPath("$.cibFeeEGP", is(400)))
                .andExpect(jsonPath("$.netSettledEGP", is(19600)));

        // Transactions for School B contains only School B transactions
        mockMvc.perform(get("/reconciliation/transactions")
                        .header("Authorization", "Bearer " + tokenAdminB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(1)))
                .andExpect(jsonPath("$.data[0].paymentId", is("TX-20260906-0099")));

        // Attempting explicit cross-school access via institutionId query param returns 403 Forbidden
        mockMvc.perform(get("/reconciliation/summary")
                        .param("institutionId", schoolB.getId().toString())
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("7.5 Mutation rejection: School users attempting bank-only mutating actions are rejected with 403 Forbidden")
    void mutatingActions_bySchoolUser_returns403Forbidden() throws Exception {
        // Trigger recon run is bank-only
        mockMvc.perform(post("/reconciliation/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-06\"}")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/api/v1/reconciliation/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-06\"}")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isForbidden());

        // Exception assignment is bank-only
        mockMvc.perform(post("/reconciliation/exceptions/" + UUID.randomUUID() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedTo\":\"Investigator 1\"}")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());

        // Exception resolution is bank-only
        mockMvc.perform(patch("/reconciliation/exceptions/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolutionAction\":\"Manual Match\"}")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());

        // Exception listing is bank-only
        mockMvc.perform(get("/reconciliation/exceptions")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("7.6 Unauthenticated requests are rejected with 401 Unauthorized")
    void unauthenticatedRequests_rejected() throws Exception {
        mockMvc.perform(get("/api/v1/reconciliation/summary"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/reconciliation/transactions"))
                .andExpect(status().isUnauthorized());
    }
}