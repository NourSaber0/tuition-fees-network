package com.tuitionnetwork.dashboard;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
class SchoolDashboardIntegrationTest {

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
    private JwtTokenProvider jwtTokenProvider;

    private MockMvc mockMvc;

    private Institution schoolA;
    private Institution schoolB;

    private InstitutionAdmin adminA;
    private InstitutionAdmin financeA;
    private InstitutionAdmin adminB;

    private String tokenAdminA;
    private String tokenFinanceA;
    private String tokenAdminB;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        paymentAllocationRepository.deleteAll();
        paymentRepository.deleteAll();
        feeLineRepository.deleteAll();
        studentRepository.deleteAll();
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
    }

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        paymentAllocationRepository.deleteAll();
        paymentRepository.deleteAll();
        feeLineRepository.deleteAll();
        studentRepository.deleteAll();
        institutionAdminRepository.deleteAll();
        institutionRepository.deleteAll();
    }

    private FeeLine createFeeLine(UUID institutionId, UUID studentId, BigDecimal total, BigDecimal remaining, LocalDate dueDate, FeeStatus status) {
        FeeLine fl = new FeeLine();
        fl.setInstitutionId(institutionId);
        fl.setStudentId(studentId);
        fl.setFeeType(FeeType.TUITION);
        fl.setTotalAmount(total);
        fl.setPaidAmount(total.subtract(remaining));
        fl.setRemainingAmount(remaining);
        fl.setStatus(status);
        fl.setCollectionPeriod("Term 1 - 2026");
        fl.setDueDate(dueDate);
        return feeLineRepository.save(fl);
    }

    @Test
    void testSchoolDashboard_summary_computesAccurateKpis() throws Exception {
        Student s1 = studentRepository.save(new Student(UUID.randomUUID(), schoolA.getId(), "hash1", "enc1", "Yousef Adel", LocalDate.of(2010, 1, 1)));

        // 1. Fully paid fee: total 5000, remaining 0, paid 5000
        createFeeLine(schoolA.getId(), s1.getId(), new BigDecimal("5000.00"), BigDecimal.ZERO, LocalDate.now().plusMonths(1), FeeStatus.PAID);

        // 2. Partially paid fee: total 10000, remaining 6000, paid 4000
        createFeeLine(schoolA.getId(), s1.getId(), new BigDecimal("10000.00"), new BigDecimal("6000.00"), LocalDate.now().plusDays(20), FeeStatus.PARTIALLY_PAID);

        // 3. Overdue fee: total 8000, remaining 8000, paid 0, dueDate 5 days ago
        createFeeLine(schoolA.getId(), s1.getId(), new BigDecimal("8000.00"), new BigDecimal("8000.00"), LocalDate.now().minusDays(5), FeeStatus.OUTSTANDING);

        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.asOf").isNotEmpty())
                .andExpect(jsonPath("$.kpis.totalCollectedEGP.value").value(9000.0))
                .andExpect(jsonPath("$.kpis.outstandingEGP.value").value(14000.0))
                .andExpect(jsonPath("$.kpis.overdueEGP.value").value(8000.0))
                .andExpect(jsonPath("$.kpis.overdueEGP.overdueFeeCount").value(1))
                .andExpect(jsonPath("$.kpis.feeUploadStatus.lastUploadStatus").value("No Uploads"));
    }

    @Test
    void testSchoolDashboard_summary_isolatedPerSchool() throws Exception {
        Student s1 = studentRepository.save(new Student(UUID.randomUUID(), schoolA.getId(), "hash1", "enc1", "Yousef Adel", LocalDate.of(2010, 1, 1)));

        // School A has 5000 paid and 8000 overdue
        createFeeLine(schoolA.getId(), s1.getId(), new BigDecimal("5000.00"), BigDecimal.ZERO, LocalDate.now().plusMonths(1), FeeStatus.PAID);
        createFeeLine(schoolA.getId(), s1.getId(), new BigDecimal("8000.00"), new BigDecimal("8000.00"), LocalDate.now().minusDays(2), FeeStatus.OUTSTANDING);

        // School B has no fee lines seeded
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + tokenAdminB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.totalCollectedEGP.value").value(0))
                .andExpect(jsonPath("$.kpis.outstandingEGP.value").value(0))
                .andExpect(jsonPath("$.kpis.overdueEGP.value").value(0))
                .andExpect(jsonPath("$.kpis.overdueEGP.overdueFeeCount").value(0));
    }

    @Test
    void testSchoolDashboard_recentPayments_returnsAllocatedDues() throws Exception {
        Student s1 = studentRepository.save(new Student(UUID.randomUUID(), schoolA.getId(), "hash1", "enc1", "Kareem Tarek", LocalDate.of(2011, 5, 15)));
        FeeLine fl = createFeeLine(schoolA.getId(), s1.getId(), new BigDecimal("5000.00"), BigDecimal.ZERO, LocalDate.now().plusDays(10), FeeStatus.PAID);

        Payment payment = new Payment(UUID.randomUUID(), new BigDecimal("5000.00"), PaymentMethod.CREDIT_CARD, "IDEMP-" + UUID.randomUUID());
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setTransactionReference("TX-20260906-0041");
        payment.setCreatedAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);

        PaymentAllocation allocation = new PaymentAllocation(payment, fl, new BigDecimal("5000.00"));
        paymentAllocationRepository.save(allocation);

        // School A sees the payment
        mockMvc.perform(get("/api/v1/dashboard/recent-payments")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value("TX-20260906-0041"))
                .andExpect(jsonPath("$.data[0].studentName").value("Kareem Tarek"))
                .andExpect(jsonPath("$.data[0].amountEGP").value(5000.0))
                .andExpect(jsonPath("$.data[0].status").value("Successful"))
                .andExpect(jsonPath("$.data[0].isPartial").value(false));

        // School B does NOT see the payment (zero cross-school bleed)
        mockMvc.perform(get("/api/v1/dashboard/recent-payments")
                        .header("Authorization", "Bearer " + tokenAdminB)
                        .param("limit", "5"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(0)));
    }

    @Test
    void testSchoolDashboard_quickLinks_returnsActionTargets() throws Exception {
        studentRepository.save(new Student(UUID.randomUUID(), schoolA.getId(), "hash1", "enc1", "Student 1", LocalDate.of(2010, 1, 1)));
        studentRepository.save(new Student(UUID.randomUUID(), schoolA.getId(), "hash2", "enc2", "Student 2", LocalDate.of(2011, 2, 2)));

        mockMvc.perform(get("/api/v1/dashboard/quick-links")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(6)))
                .andExpect(jsonPath("$[0].target").value("/students"))
                .andExpect(jsonPath("$[0].badgeCount").value(2))
                .andExpect(jsonPath("$[1].target").value("/fees"))
                .andExpect(jsonPath("$[2].target").value("/fee-upload"))
                .andExpect(jsonPath("$[3].target").value("/payments"))
                .andExpect(jsonPath("$[4].target").value("/reports"))
                .andExpect(jsonPath("$[5].target").value("/notifications"));
    }

    @Test
    void testSchoolDashboard_weeklyCollections_returnsSevenDaySeries() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/collections/weekly")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .param("weekOf", "2026-09-07"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.currency").value("EGP"))
                .andExpect(jsonPath("$.series", hasSize(7)))
                .andExpect(jsonPath("$.series[0].label").value("MON"))
                .andExpect(jsonPath("$.series[6].label").value("SUN"));
    }

    @Test
    void testSchoolDashboard_bankOnlyEndpoints_forbiddenForSchoolUsers() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/institution-status")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/dashboard/deadline-summary")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());
    }

    @Test
    void testSchoolDashboard_accessibleToSchoolFinance() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.totalCollectedEGP.value").exists());
    }
}
