package com.tuitionnetwork.fees;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.fees.dto.CancelFeeRequest;
import com.tuitionnetwork.fees.dto.CreateFeeRequest;
import com.tuitionnetwork.fees.dto.UpdateFeeRequest;
import com.tuitionnetwork.fees.service.FeeAutomatedRulesEngine;
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
import com.tuitionnetwork.notifications.domain.Notification;
import com.tuitionnetwork.notifications.repository.NotificationRepository;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.repository.PaymentAllocationRepository;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
class SchoolFeeIntegrationTest {

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
    private GuardianRepository guardianRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FeeAutomatedRulesEngine feeAutomatedRulesEngine;

    private MockMvc mockMvc;

    private Institution schoolA;
    private Institution schoolB;

    private InstitutionAdmin adminA;
    private InstitutionAdmin financeA;
    private InstitutionAdmin adminB;

    private String tokenAdminA;
    private String tokenFinanceA;
    private String tokenAdminB;

    private Student student1;
    private Student student2;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        objectMapper.findAndRegisterModules();

        notificationRepository.deleteAll();
        paymentAllocationRepository.deleteAll();
        paymentRepository.deleteAll();
        feeLineRepository.deleteAll();
        studentRepository.deleteAll();
        guardianRepository.deleteAll();
        institutionAdminRepository.deleteAll();
        institutionRepository.deleteAll();

        schoolA = new Institution("Cairo International School", "SCH-001", "SCHOOL_ABSORBS");
        schoolA.setAccountStatus(AccountStatus.ACTIVE);
        schoolA = institutionRepository.save(schoolA);

        schoolB = new Institution("Alexandria National School", "SCH-002", "SCHOOL_ABSORBS");
        schoolB.setAccountStatus(AccountStatus.ACTIVE);
        schoolB = institutionRepository.save(schoolB);

        adminA = new InstitutionAdmin(schoolA.getId(), "Amr Hassan", "amr.hassan@cis.edu.eg", "Password123!", "School Admin");
        adminA.setStatus("Active");
        adminA.setCreatedAt(LocalDateTime.now());
        adminA = institutionAdminRepository.save(adminA);

        financeA = new InstitutionAdmin(schoolA.getId(), "Dina Fouad", "dina.fouad@cis.edu.eg", "Finance@2026", "School Finance");
        financeA.setStatus("Active");
        financeA.setCreatedAt(LocalDateTime.now());
        financeA = institutionAdminRepository.save(financeA);

        adminB = new InstitutionAdmin(schoolB.getId(), "Tarek Samy", "tarek@ans.edu.eg", "Password123!", "School Admin");
        adminB.setStatus("Active");
        adminB.setCreatedAt(LocalDateTime.now());
        adminB = institutionAdminRepository.save(adminB);

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

        student1 = new Student(
                null, schoolA.getId(), "hash_s1", "enc_s1", "Yousef Adel",
                LocalDate.of(2010, 5, 12), "2026-0231", "Grade 10", "B",
                "Adel Mostafa", "+20 10 1234 5678", "a.mostafa@example.com"
        );
        student1.setStatus("Active");
        student1 = studentRepository.save(student1);

        student2 = new Student(
                null, schoolA.getId(), "hash_s2", "enc_s2", "Salma Ahmed",
                LocalDate.of(2011, 8, 20), "2026-0232", "Grade 9", "A",
                "Ahmed Kamal", "+20 10 9876 5432", "ahmed@example.com"
        );
        student2.setStatus("Active");
        student2 = studentRepository.save(student2);
    }

    @Test
    void getFees_ReturnsPaginatedAndDynamicStatuses() throws Exception {
        // Fee 1: Partial
        FeeLine fee1 = new FeeLine(schoolA.getId(), student1.getId(), FeeType.TUITION,
                new BigDecimal("18000.00"), new BigDecimal("13000.00"), "Term 1", LocalDate.now().plusDays(10));
        fee1.setPaidAmount(new BigDecimal("5000.00"));
        feeLineRepository.save(fee1);

        // Fee 2: Paid
        FeeLine fee2 = new FeeLine(schoolA.getId(), student1.getId(), FeeType.BOOKS,
                new BigDecimal("3000.00"), BigDecimal.ZERO, "Term 1", LocalDate.now().plusDays(5));
        fee2.setPaidAmount(new BigDecimal("3000.00"));
        fee2.setStatus(FeeStatus.PAID);
        feeLineRepository.save(fee2);

        // Fee 3: Overdue
        FeeLine fee3 = new FeeLine(schoolA.getId(), student2.getId(), FeeType.BUS,
                new BigDecimal("5000.00"), new BigDecimal("5000.00"), "Term 1", LocalDate.now().minusDays(3));
        feeLineRepository.save(fee3);

        mockMvc.perform(get("/api/v1/fees?page=1&pageSize=10")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(3)))
                .andExpect(jsonPath("$.total").value(3))
                .andExpect(jsonPath("$.data[?(@.status == 'Overdue')]").exists())
                .andExpect(jsonPath("$.data[?(@.status == 'Partial')]").exists())
                .andExpect(jsonPath("$.data[?(@.status == 'Paid')]").exists());
    }

    @Test
    void getFees_FiltersByCategoryGradeAndDates() throws Exception {
        FeeLine feeTuition = new FeeLine(schoolA.getId(), student1.getId(), FeeType.TUITION,
                new BigDecimal("15000.00"), new BigDecimal("15000.00"), "Term 1", LocalDate.now().plusDays(15));
        feeLineRepository.save(feeTuition);

        FeeLine feeBooks = new FeeLine(schoolA.getId(), student2.getId(), FeeType.BOOKS,
                new BigDecimal("2000.00"), new BigDecimal("2000.00"), "Term 1", LocalDate.now().plusDays(25));
        feeLineRepository.save(feeBooks);

        // Filter by category Tuition
        mockMvc.perform(get("/api/v1/fees?category=Tuition")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].category").value("Tuition"));

        // Filter by grade
        mockMvc.perform(get("/api/v1/fees?grade=Grade 9")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].studentName").value("Salma Ahmed"));
    }

    @Test
    void createFee_Success_AndAudited() throws Exception {
        CreateFeeRequest req = new CreateFeeRequest(
                student1.getId().toString(),
                "Tuition - Term 1 2026/27",
                "Tuition",
                new BigDecimal("25000.00"),
                "Term 1 2026/27",
                LocalDate.now().plusMonths(2)
        );

        mockMvc.perform(post("/api/v1/fees")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentId").value(student1.getId().toString()))
                .andExpect(jsonPath("$.studentName").value("Yousef Adel"))
                .andExpect(jsonPath("$.category").value("Tuition"))
                .andExpect(jsonPath("$.originalAmountEGP").value(25000.00))
                .andExpect(jsonPath("$.paidEGP").value(0))
                .andExpect(jsonPath("$.remainingEGP").value(25000.00))
                .andExpect(jsonPath("$.status").value("Active"));

        boolean auditFound = auditLogRepository.findAll().stream()
                .anyMatch(a -> "CREATE_FEE_LINE".equals(a.getAction()));
        assertTrue(auditFound, "Expected CREATE_FEE_LINE audit entry");
    }

    @Test
    void createFee_DueDateRequired_Returns400() throws Exception {
        CreateFeeRequest req = new CreateFeeRequest(
                student1.getId().toString(),
                "Books Fee",
                "Books",
                new BigDecimal("1500.00"),
                "Term 1",
                null // Missing due date
        );

        mockMvc.perform(post("/api/v1/fees")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void createFee_DeactivatedStudent_Returns409() throws Exception {
        Student inactive = new Student(
                null, schoolA.getId(), "hash_in", "enc_in", "Inactive Student",
                LocalDate.of(2012, 1, 1), "2026-INACT", "Grade 4", "A",
                "Parent", "+20 10 1111 2222", "in@example.com"
        );
        inactive.setStatus("Inactive");
        inactive = studentRepository.save(inactive);

        CreateFeeRequest req = new CreateFeeRequest(
                inactive.getId().toString(),
                "Activity Fee",
                "Activity",
                new BigDecimal("1000.00"),
                "Term 1",
                LocalDate.now().plusMonths(1)
        );

        mockMvc.perform(post("/api/v1/fees")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isConflict());
    }

    @Test
    void createFee_InvalidCategory_Returns400() throws Exception {
        CreateFeeRequest req = new CreateFeeRequest(
                student1.getId().toString(),
                "Graduation Fee",
                "Graduation", // Invalid
                new BigDecimal("1000.00"),
                "Term 1",
                LocalDate.now().plusMonths(1)
        );

        mockMvc.perform(post("/api/v1/fees")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFeeDetail_IncludesPenaltyAndPaymentHistory() throws Exception {
        FeeLine fee = new FeeLine(schoolA.getId(), student1.getId(), FeeType.TUITION,
                new BigDecimal("20000.00"), new BigDecimal("10000.00"), "Term 1", LocalDate.now().minusDays(8));
        fee.setPaidAmount(new BigDecimal("10000.00"));
        fee.setPenaltyAmountEGP(new BigDecimal("500.00"));
        fee.setPenaltyAppliedAt(LocalDateTime.now().minusDays(1));
        fee = feeLineRepository.save(fee);

        Payment payment = new Payment(UUID.randomUUID(), new BigDecimal("10000.00"),
                PaymentMethod.CREDIT_CARD, "idemp_det_01");
        payment.setTransactionReference("TX-2026-DETAIL");
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCreatedAt(LocalDateTime.now().minusDays(2));
        payment = paymentRepository.save(payment);

        paymentAllocationRepository.save(new PaymentAllocation(payment, fee, new BigDecimal("10000.00")));

        mockMvc.perform(get("/api/v1/fees/" + fee.getId())
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(fee.getId().toString()))
                .andExpect(jsonPath("$.studentName").value("Yousef Adel"))
                .andExpect(jsonPath("$.category").value("Tuition"))
                .andExpect(jsonPath("$.status").value("Overdue"))
                .andExpect(jsonPath("$.overdue.isOverdue").value(true))
                .andExpect(jsonPath("$.penalty.applied").value(true))
                .andExpect(jsonPath("$.penalty.penaltyAmountEGP").value(500.00))
                .andExpect(jsonPath("$.paymentHistory", hasSize(1)))
                .andExpect(jsonPath("$.paymentHistory[0].paymentId").value("TX-2026-DETAIL"))
                .andExpect(jsonPath("$.paymentHistory[0].amountEGP").value(10000.00));
    }

    @Test
    void getFeeDetail_CrossSchool_Returns403Forbidden() throws Exception {
        FeeLine fee = new FeeLine(schoolA.getId(), student1.getId(), FeeType.TUITION,
                new BigDecimal("10000.00"), new BigDecimal("10000.00"), "Term 1", LocalDate.now().plusDays(10));
        fee = feeLineRepository.save(fee);

        // School B tries to access School A fee
        mockMvc.perform(get("/api/v1/fees/" + fee.getId())
                        .header("Authorization", "Bearer " + tokenAdminB))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateFee_Success_RecalculatesRemaining() throws Exception {
        FeeLine fee = new FeeLine(schoolA.getId(), student1.getId(), FeeType.BUS,
                new BigDecimal("4000.00"), new BigDecimal("3000.00"), "Term 1", LocalDate.now().plusDays(10));
        fee.setPaidAmount(new BigDecimal("1000.00"));
        fee = feeLineRepository.save(fee);

        UpdateFeeRequest updateReq = new UpdateFeeRequest(
                new BigDecimal("5000.00"),
                LocalDate.now().plusDays(20),
                "Bus - Term 1 Updated",
                "Term 1",
                "Adjustment"
        );

        mockMvc.perform(patch("/api/v1/fees/" + fee.getId())
                        .header("Authorization", "Bearer " + tokenFinanceA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalAmountEGP").value(5000.00))
                .andExpect(jsonPath("$.paidEGP").value(1000.00))
                .andExpect(jsonPath("$.remainingEGP").value(4000.00));
    }

    @Test
    void updateFee_AmountBelowPaid_Returns400() throws Exception {
        FeeLine fee = new FeeLine(schoolA.getId(), student1.getId(), FeeType.TUITION,
                new BigDecimal("10000.00"), new BigDecimal("4000.00"), "Term 1", LocalDate.now().plusDays(10));
        fee.setPaidAmount(new BigDecimal("6000.00"));
        fee = feeLineRepository.save(fee);

        // Attempt to lower total amount below paid amount
        UpdateFeeRequest updateReq = new UpdateFeeRequest(
                new BigDecimal("5000.00"),
                null, null, null, null
        );

        mockMvc.perform(patch("/api/v1/fees/" + fee.getId())
                        .header("Authorization", "Bearer " + tokenFinanceA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void cancelFee_Success_SetsCancelledAndAudited() throws Exception {
        FeeLine fee = new FeeLine(schoolA.getId(), student1.getId(), FeeType.ACTIVITIES,
                new BigDecimal("2000.00"), new BigDecimal("2000.00"), "Term 1", LocalDate.now().plusDays(10));
        fee = feeLineRepository.save(fee);

        CancelFeeRequest cancelReq = new CancelFeeRequest("Student changed mind");

        mockMvc.perform(post("/api/v1/fees/" + fee.getId() + "/cancel")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cancelReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Cancelled"));

        FeeLine updated = feeLineRepository.findById(fee.getId()).orElseThrow();
        assertEquals(FeeStatus.CANCELLED, updated.getStatus());

        boolean auditFound = auditLogRepository.findAll().stream()
                .anyMatch(a -> "CANCEL_FEE_LINE".equals(a.getAction()));
        assertTrue(auditFound, "Expected CANCEL_FEE_LINE audit entry");
    }

    @Test
    void cancelFee_PartiallyPaid_Returns409Conflict() throws Exception {
        FeeLine fee = new FeeLine(schoolA.getId(), student1.getId(), FeeType.TUITION,
                new BigDecimal("10000.00"), new BigDecimal("7000.00"), "Term 1", LocalDate.now().plusDays(10));
        fee.setPaidAmount(new BigDecimal("3000.00"));
        fee = feeLineRepository.save(fee);

        mockMvc.perform(post("/api/v1/fees/" + fee.getId() + "/cancel")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isConflict());
    }

    @Test
    void cancelFee_ForbiddenForSchoolFinance() throws Exception {
        FeeLine fee = new FeeLine(schoolA.getId(), student1.getId(), FeeType.BOOKS,
                new BigDecimal("1000.00"), new BigDecimal("1000.00"), "Term 1", LocalDate.now().plusDays(10));
        fee = feeLineRepository.save(fee);

        mockMvc.perform(post("/api/v1/fees/" + fee.getId() + "/cancel")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isForbidden());
    }

    @Test
    void applyManualPenalty_SuccessOnOverdueTuition() throws Exception {
        FeeLine fee = new FeeLine(schoolA.getId(), student1.getId(), FeeType.TUITION,
                new BigDecimal("20000.00"), new BigDecimal("20000.00"), "Term 1", LocalDate.now().minusDays(5));
        fee = feeLineRepository.save(fee);

        mockMvc.perform(post("/api/v1/fees/" + fee.getId() + "/apply-penalty")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penalty.applied").value(true))
                .andExpect(jsonPath("$.penalty.penaltyAmountEGP").value(1000.00))
                .andExpect(jsonPath("$.penalty.totalDueEGP").value(21000.00));

        // Attempting to penalize again -> 409 Conflict
        mockMvc.perform(post("/api/v1/fees/" + fee.getId() + "/apply-penalty")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isConflict());
    }

    @Test
    void applyManualPenalty_RejectsNonTuition() throws Exception {
        FeeLine fee = new FeeLine(schoolA.getId(), student1.getId(), FeeType.BUS,
                new BigDecimal("5000.00"), new BigDecimal("5000.00"), "Term 1", LocalDate.now().minusDays(5));
        fee = feeLineRepository.save(fee);

        mockMvc.perform(post("/api/v1/fees/" + fee.getId() + "/apply-penalty")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isBadRequest());
    }

    @Test
    void getFeePenaltyInfo_CalculatesRealtimeDeadlineSnapshot() throws Exception {
        FeeLine fee = new FeeLine(schoolA.getId(), student1.getId(), FeeType.TUITION,
                new BigDecimal("18000.00"), new BigDecimal("18000.00"), "Term 1", LocalDate.now().minusDays(10));
        fee = feeLineRepository.save(fee);

        mockMvc.perform(get("/api/v1/fees/" + fee.getId() + "/penalty-info")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.priority").value("OVERDUE"))
                .andExpect(jsonPath("$.daysToDue").value(-10))
                .andExpect(jsonPath("$.outstandingEGP").value(18000.00))
                .andExpect(jsonPath("$.penaltyAmountEGP").value(900.00))
                .andExpect(jsonPath("$.graceEnded").value(true))
                .andExpect(jsonPath("$.totalDueEGP").value(18900.00));
    }

    @Test
    void getFeeStats_ComputesAggregatesCorrectly() throws Exception {
        // Fee 1: 10000 total, 4000 paid, 6000 remaining, Overdue
        FeeLine fee1 = new FeeLine(schoolA.getId(), student1.getId(), FeeType.TUITION,
                new BigDecimal("10000.00"), new BigDecimal("6000.00"), "Term 1", LocalDate.now().minusDays(5));
        fee1.setPaidAmount(new BigDecimal("4000.00"));
        feeLineRepository.save(fee1);

        // Fee 2: 5000 total, 5000 paid, 0 remaining, Paid
        FeeLine fee2 = new FeeLine(schoolA.getId(), student2.getId(), FeeType.BOOKS,
                new BigDecimal("5000.00"), BigDecimal.ZERO, "Term 1", LocalDate.now().plusDays(10));
        fee2.setPaidAmount(new BigDecimal("5000.00"));
        feeLineRepository.save(fee2);

        mockMvc.perform(get("/api/v1/fees/stats")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvoicedEGP").value(15000.00))
                .andExpect(jsonPath("$.totalCollectedEGP").value(9000.00))
                .andExpect(jsonPath("$.totalOutstandingEGP").value(6000.00))
                .andExpect(jsonPath("$.totalOverdueEGP").value(6000.00))
                .andExpect(jsonPath("$.overdueCount").value(1))
                .andExpect(jsonPath("$.totalStudentsWithOverdue").value(1))
                .andExpect(jsonPath("$.activeFeeLinesCount").value(1));
    }

    @Test
    void getFeeCategories_ReturnsPriorityOrder() throws Exception {
        mockMvc.perform(get("/api/v1/fee-categories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].code").value("Tuition"))
                .andExpect(jsonPath("$[0].priority").value(1))
                .andExpect(jsonPath("$[1].code").value("Books"))
                .andExpect(jsonPath("$[1].priority").value(2))
                .andExpect(jsonPath("$[2].code").value("Activity"))
                .andExpect(jsonPath("$[2].priority").value(3))
                .andExpect(jsonPath("$[3].code").value("Bus"))
                .andExpect(jsonPath("$[3].priority").value(4));
    }

    @Test
    void engineA_TuitionPenaltyEngine_Applies5PercentAfter7DaysGrace() {
        // Fee 1: Tuition overdue 8 days -> grace ended, should be penalized with 5% (500 EGP)
        FeeLine fee1 = new FeeLine(schoolA.getId(), student1.getId(), FeeType.TUITION,
                new BigDecimal("10000.00"), new BigDecimal("10000.00"), "Term 1", LocalDate.now().minusDays(8));
        feeLineRepository.save(fee1);

        // Fee 2: Tuition overdue 3 days -> grace NOT ended, should NOT be penalized
        FeeLine fee2 = new FeeLine(schoolA.getId(), student1.getId(), FeeType.TUITION,
                new BigDecimal("10000.00"), new BigDecimal("10000.00"), "Term 1", LocalDate.now().minusDays(3));
        feeLineRepository.save(fee2);

        // Fee 3: Books overdue 10 days -> NOT tuition, should NOT be penalized
        FeeLine fee3 = new FeeLine(schoolA.getId(), student2.getId(), FeeType.BOOKS,
                new BigDecimal("5000.00"), new BigDecimal("5000.00"), "Term 1", LocalDate.now().minusDays(10));
        feeLineRepository.save(fee3);

        int processed = feeAutomatedRulesEngine.evaluateTuitionPenalties(LocalDate.now());
        assertEquals(1, processed);

        FeeLine updatedFee1 = feeLineRepository.findById(fee1.getId()).orElseThrow();
        assertNotNull(updatedFee1.getPenaltyAppliedAt());
        assertEquals(new BigDecimal("500.00"), updatedFee1.getPenaltyAmountEGP());

        FeeLine updatedFee2 = feeLineRepository.findById(fee2.getId()).orElseThrow();
        assertNull(updatedFee2.getPenaltyAppliedAt());

        FeeLine updatedFee3 = feeLineRepository.findById(fee3.getId()).orElseThrow();
        assertNull(updatedFee3.getPenaltyAppliedAt());
    }

    @Test
    void engineB_OneWeekReminderEngine_DispatchesNotification7DaysBeforeDueDate() {
        // Fee 1: exactly 7 days to due date -> should trigger reminder
        FeeLine fee1 = new FeeLine(schoolA.getId(), student1.getId(), FeeType.TUITION,
                new BigDecimal("12000.00"), new BigDecimal("12000.00"), "Term 1", LocalDate.now().plusDays(7));
        feeLineRepository.save(fee1);

        // Fee 2: 4 days to due date -> should NOT trigger
        FeeLine fee2 = new FeeLine(schoolA.getId(), student2.getId(), FeeType.BUS,
                new BigDecimal("4000.00"), new BigDecimal("4000.00"), "Term 1", LocalDate.now().plusDays(4));
        feeLineRepository.save(fee2);

        int sent = feeAutomatedRulesEngine.dispatchApproachingReminders(LocalDate.now());
        assertEquals(1, sent);

        List<Notification> notifs = notificationRepository.findAll();
        assertEquals(1, notifs.size());
        assertEquals("reminder", notifs.get(0).getType());
        assertTrue(notifs.get(0).getMessage().contains("is due in 7 days"));

        // Running again -> idempotency check ensures no duplicate notification
        int secondRun = feeAutomatedRulesEngine.dispatchApproachingReminders(LocalDate.now());
        assertEquals(0, secondRun);
        assertEquals(1, notificationRepository.findAll().size());
    }
}
