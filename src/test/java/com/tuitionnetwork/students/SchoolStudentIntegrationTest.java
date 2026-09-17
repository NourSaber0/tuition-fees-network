package com.tuitionnetwork.students;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
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
import com.tuitionnetwork.students.dto.DeactivateStudentRequest;
import com.tuitionnetwork.students.dto.EnrollStudentRequest;
import com.tuitionnetwork.students.dto.LinkGuardianRequest;
import com.tuitionnetwork.students.dto.UpdateStudentRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
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
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
class SchoolStudentIntegrationTest {

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
    private AuditLogRepository auditLogRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

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

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        try {
            jdbcTemplate.execute("DELETE FROM epp_installment");
            jdbcTemplate.execute("DELETE FROM epp_schedule");
            jdbcTemplate.execute("DELETE FROM receipt");
            jdbcTemplate.execute("DELETE FROM payment_state_log");
        } catch (Exception ignored) {}

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
                List.of(UserRole.ROLE_SCHOOL_FINANCE),
                schoolA.getId()
        ));

        tokenAdminB = jwtTokenProvider.generateToken(new SecurityUserPrincipal(
                adminB.getId(), adminB.getEmail(), adminB.getName(),
                UserRole.ROLE_SCHOOL_ADMIN,
                List.of(UserRole.ROLE_SCHOOL_ADMIN, UserRole.ROLE_INSTITUTION_ADMIN),
                schoolB.getId()
        ));
    }

    @Test
    void enrollStudent_Success_CreatedAndAudited() throws Exception {
        EnrollStudentRequest request = new EnrollStudentRequest(
                "2026-0512",
                "Laila Samir",
                "Grade 3",
                "A",
                "30105011234567",
                "Samir Fathy",
                "+20 10 9988 7766",
                "s.fathy@example.com"
        );

        mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentRef").value("2026-0512"))
                .andExpect(jsonPath("$.name").value("Laila Samir"))
                .andExpect(jsonPath("$.grade").value("Grade 3"))
                .andExpect(jsonPath("$.section").value("A"))
                .andExpect(jsonPath("$.status").value("Active"))
                .andExpect(jsonPath("$.nationalIdMasked").value("301*******4567"))
                .andExpect(jsonPath("$.parentName").value("Samir Fathy"))
                .andExpect(jsonPath("$.totals.totalFeesEGP").value(0))
                .andExpect(jsonPath("$.totals.totalOutstandingEGP").value(0));

        Student saved = studentRepository.findByInstitutionIdAndStudentRef(schoolA.getId(), "2026-0512").orElse(null);
        assertNotNull(saved);
        assertEquals("Laila Samir", saved.getFullName());
        assertEquals("Active", saved.getStatus());

        boolean auditFound = auditLogRepository.findAll().stream()
                .anyMatch(a -> "ENROLL_STUDENT".equals(a.getAction()));
        assertTrue(auditFound, "Expected ENROLL_STUDENT audit record");
    }

    @Test
    void enrollStudent_DuplicateStudentRef_Returns409Conflict() throws Exception {
        Student existing = new Student(
                null, schoolA.getId(), "hash_123", "enc_123", "Existing Student",
                LocalDate.of(2015, 1, 1), "2026-DUPLICATE", "Grade 1", "A",
                "Parent", "+20 10 1111 2222", "parent@example.com"
        );
        studentRepository.save(existing);

        EnrollStudentRequest request = new EnrollStudentRequest(
                "2026-DUPLICATE",
                "Another Student",
                "Grade 2",
                "B",
                "30105011234567",
                "Parent 2",
                "+20 10 3333 4444",
                "parent2@example.com"
        );

        mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void enrollStudent_InvalidNationalId_Returns400BadRequest() throws Exception {
        EnrollStudentRequest request = new EnrollStudentRequest(
                "2026-9999",
                "Invalid NID Student",
                "Grade 4",
                "A",
                "12345", // Not 14 digits
                "Parent",
                "+20 10 1111 2222",
                "parent@example.com"
        );

        mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest());
    }

    @Test
    void enrollStudent_ForbiddenForSchoolFinance() throws Exception {
        EnrollStudentRequest request = new EnrollStudentRequest(
                "2026-0513",
                "Student Name",
                "Grade 5",
                "A",
                "30105011234567",
                "Parent",
                "+20 10 1111 2222",
                "parent@example.com"
        );

        mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", "Bearer " + tokenFinanceA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void getActiveStudents_ReturnsPaginatedAndFeeRollups_OmitsNationalId() throws Exception {
        Student s1 = new Student(
                null, schoolA.getId(), "hash_s1", "enc_30105011234567", "Yousef Adel",
                LocalDate.of(2010, 5, 12), "2026-0231", "Grade 10", "B",
                "Adel Mostafa", "+20 10 1234 5678", "a.mostafa@example.com"
        );
        s1 = studentRepository.save(s1);

        Student s2 = new Student(
                null, schoolA.getId(), "hash_s2", "enc_30206011234568", "Salma Ahmed",
                LocalDate.of(2011, 8, 20), "2026-0232", "Grade 9", "A",
                "Ahmed Kamal", "+20 10 9876 5432", "ahmed@example.com"
        );
        studentRepository.save(s2);

        // Add fee lines to s1: Tuition 32000, paid 20000, remaining 12000
        FeeLine fee1 = new FeeLine(schoolA.getId(), s1.getId(), FeeType.TUITION,
                new BigDecimal("32000.00"), new BigDecimal("12000.00"), "Term 1 2026/27", LocalDate.now().plusDays(10));
        fee1.setPaidAmount(new BigDecimal("20000.00"));
        feeLineRepository.save(fee1);

        mockMvc.perform(get("/api/v1/students?search=Yousef&grade=Grade 10&page=1&pageSize=25")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].studentRef").value("2026-0231"))
                .andExpect(jsonPath("$.data[0].name").value("Yousef Adel"))
                .andExpect(jsonPath("$.data[0].grade").value("Grade 10"))
                .andExpect(jsonPath("$.data[0].section").value("B"))
                .andExpect(jsonPath("$.data[0].totalFeesEGP").value(32000.00))
                .andExpect(jsonPath("$.data[0].paidEGP").value(20000.00))
                .andExpect(jsonPath("$.data[0].outstandingEGP").value(12000.00))
                .andExpect(jsonPath("$.data[0].status").value("Active"))
                .andExpect(jsonPath("$.data[0].nationalId").doesNotExist())
                .andExpect(jsonPath("$.data[0].nationalIdMasked").doesNotExist())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.page").value(1))
                .andExpect(jsonPath("$.pageSize").value(25));
    }

    @Test
    void getStudentDetail_ReturnsMaskedNationalId_AndFullTotals() throws Exception {
        Student s = new Student(
                null, schoolA.getId(), "hash_s", "enc_29901011234567", "Yousef Adel",
                LocalDate.of(2010, 5, 12), "2026-0231", "Grade 10", "B",
                "Adel Mostafa", "+20 10 1234 5678", "a.mostafa@example.com"
        );
        s = studentRepository.save(s);

        FeeLine fee = new FeeLine(schoolA.getId(), s.getId(), FeeType.TUITION,
                new BigDecimal("18000.00"), new BigDecimal("13000.00"), "Term 1", LocalDate.now().plusDays(5));
        fee.setPaidAmount(new BigDecimal("5000.00"));
        feeLineRepository.save(fee);

        mockMvc.perform(get("/api/v1/students/" + s.getId())
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(s.getId().toString()))
                .andExpect(jsonPath("$.studentRef").value("2026-0231"))
                .andExpect(jsonPath("$.name").value("Yousef Adel"))
                .andExpect(jsonPath("$.grade").value("Grade 10"))
                .andExpect(jsonPath("$.status").value("Active"))
                .andExpect(jsonPath("$.nationalIdMasked").value("299*******4567"))
                .andExpect(jsonPath("$.parentName").value("Adel Mostafa"))
                .andExpect(jsonPath("$.parentPhone").value("+20 10 1234 5678"))
                .andExpect(jsonPath("$.totals.totalFeesEGP").value(18000.00))
                .andExpect(jsonPath("$.totals.totalPaidEGP").value(5000.00))
                .andExpect(jsonPath("$.totals.totalOutstandingEGP").value(13000.00));
    }

    @Test
    void getStudentDetail_CrossSchoolAccess_Returns403Forbidden() throws Exception {
        Student studentOfSchoolA = new Student(
                null, schoolA.getId(), "hash_sa", "enc_29901011234567", "Alice",
                LocalDate.of(2010, 1, 1), "2026-A1", "Grade 1", "A",
                "Parent A", "+20 10 1111 2222", "pa@example.com"
        );
        studentOfSchoolA = studentRepository.save(studentOfSchoolA);

        // School B admin attempts access to School A student
        mockMvc.perform(get("/api/v1/students/" + studentOfSchoolA.getId())
                        .header("Authorization", "Bearer " + tokenAdminB))
                .andExpect(status().isForbidden());
    }

    @Test
    void updateStudent_Success_AndConflictOnDuplicateRef() throws Exception {
        Student s = new Student(
                null, schoolA.getId(), "hash_s", "enc_29901011234567", "Kareem",
                LocalDate.of(2010, 5, 12), "2026-0301", "Grade 8", "A",
                "Parent", "+20 10 1111 2222", "p@example.com"
        );
        s = studentRepository.save(s);

        Student sOther = new Student(
                null, schoolA.getId(), "hash_so", "enc_29901011234568", "Omar",
                LocalDate.of(2010, 5, 12), "2026-0302", "Grade 8", "A",
                "Parent", "+20 10 1111 2222", "p2@example.com"
        );
        studentRepository.save(sOther);

        // Update name and grade
        UpdateStudentRequest updateReq = new UpdateStudentRequest(
                null, "Kareem Mahmoud", "Grade 9", "C", "New Parent", "+20 10 9999 8888", "np@example.com"
        );

        mockMvc.perform(patch("/api/v1/students/" + s.getId())
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Kareem Mahmoud"))
                .andExpect(jsonPath("$.grade").value("Grade 9"))
                .andExpect(jsonPath("$.section").value("C"))
                .andExpect(jsonPath("$.parentName").value("New Parent"));

        // Attempting to rename studentRef to existing one -> 409 Conflict
        UpdateStudentRequest collisionReq = new UpdateStudentRequest(
                "2026-0302", null, null, null, null, null, null
        );

        mockMvc.perform(patch("/api/v1/students/" + s.getId())
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(collisionReq)))
                .andExpect(status().isConflict());
    }

    @Test
    void deactivateAndReactivateStudent_Success() throws Exception {
        Student s = new Student(
                null, schoolA.getId(), "hash_s", "enc_29901011234567", "Nour Kamal",
                LocalDate.of(2012, 7, 14), "2026-0198", "Grade 8", "A",
                "Parent", "+20 10 1111 2222", "p@example.com"
        );
        s = studentRepository.save(s);

        // Deactivate student with reason
        DeactivateStudentRequest deactReq = new DeactivateStudentRequest("Withdrawn");
        mockMvc.perform(post("/api/v1/students/" + s.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Inactive"))
                .andExpect(jsonPath("$.deactivatedDate").value(LocalDate.now().toString()));

        // Trying to deactivate again -> 409 Conflict
        mockMvc.perform(post("/api/v1/students/" + s.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactReq)))
                .andExpect(status().isConflict());

        // Reactivate student -> status Active
        mockMvc.perform(post("/api/v1/students/" + s.getId() + "/reactivate")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Active"))
                .andExpect(jsonPath("$.deactivatedDate").doesNotExist());
    }

    @Test
    void getDeactivatedStudents_DateRangeFilter() throws Exception {
        Student inactiveStudent = new Student(
                null, schoolA.getId(), "hash_in", "enc_111", "Tarek Inactive",
                LocalDate.of(2011, 1, 1), "2026-INACT", "Grade 6", "A",
                "Parent", "+20 10 1111 2222", "p@example.com"
        );
        inactiveStudent.setStatus("Inactive");
        inactiveStudent.setDeactivatedDate(LocalDate.now().minusDays(5));
        inactiveStudent.setDeactivationReason("Withdrawn");
        studentRepository.save(inactiveStudent);

        mockMvc.perform(get("/api/v1/students/deactivated?deactivatedFrom=" + LocalDate.now().minusDays(10) + "&deactivatedTo=" + LocalDate.now())
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].name").value("Tarek Inactive"))
                .andExpect(jsonPath("$.data[0].status").value("Inactive"));
    }

    @Test
    void getStudentFees_ReturnsFeeLinesAndSummaryTotals() throws Exception {
        Student s = new Student(
                null, schoolA.getId(), "hash_s", "enc_29901011234567", "Yousef Adel",
                LocalDate.of(2010, 5, 12), "2026-0231", "Grade 10", "B",
                "Adel Mostafa", "+20 10 1234 5678", "a.mostafa@example.com"
        );
        s = studentRepository.save(s);

        FeeLine fee1 = new FeeLine(schoolA.getId(), s.getId(), FeeType.TUITION,
                new BigDecimal("18000.00"), new BigDecimal("13000.00"), "Term 1 2026/27", LocalDate.now().plusDays(15));
        fee1.setPaidAmount(new BigDecimal("5000.00"));
        feeLineRepository.save(fee1);

        FeeLine fee2 = new FeeLine(schoolA.getId(), s.getId(), FeeType.BUS,
                new BigDecimal("4000.00"), BigDecimal.ZERO, "Term 1 2026/27", LocalDate.now().minusDays(10));
        fee2.setPaidAmount(new BigDecimal("4000.00"));
        fee2.setStatus(FeeStatus.PAID);
        feeLineRepository.save(fee2);

        mockMvc.perform(get("/api/v1/students/" + s.getId() + "/fees")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.totals.totalFeesEGP").value(22000.00))
                .andExpect(jsonPath("$.totals.totalPaidEGP").value(9000.00))
                .andExpect(jsonPath("$.totals.totalOutstandingEGP").value(13000.00));
    }

    @Test
    void getStudentPayments_ReturnsPaymentAllocations() throws Exception {
        Student s = new Student(
                null, schoolA.getId(), "hash_s", "enc_29901011234567", "Yousef Adel",
                LocalDate.of(2010, 5, 12), "2026-0231", "Grade 10", "B",
                "Adel Mostafa", "+20 10 1234 5678", "a.mostafa@example.com"
        );
        s = studentRepository.save(s);

        FeeLine fee = new FeeLine(schoolA.getId(), s.getId(), FeeType.TUITION,
                new BigDecimal("18000.00"), new BigDecimal("13000.00"), "Term 1 2026/27", LocalDate.now().plusDays(15));
        fee.setPaidAmount(new BigDecimal("5000.00"));
        fee = feeLineRepository.save(fee);

        Payment payment = new Payment(UUID.randomUUID(), new BigDecimal("5000.00"),
                PaymentMethod.CREDIT_CARD, "idemp_123");
        payment.setTransactionReference("TX-20260906-0041");
        payment.setAuthCode("auth_123");
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCreatedAt(LocalDateTime.now());
        payment = paymentRepository.save(payment);

        PaymentAllocation allocation = new PaymentAllocation(payment, fee, new BigDecimal("5000.00"));
        paymentAllocationRepository.save(allocation);

        mockMvc.perform(get("/api/v1/students/" + s.getId() + "/payments")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value("TX-20260906-0041"))
                .andExpect(jsonPath("$.data[0].studentName").value("Yousef Adel"))
                .andExpect(jsonPath("$.data[0].amountEGP").value(5000.00))
                .andExpect(jsonPath("$.data[0].isPartial").value(true));
    }

    @Test
    void searchActiveStudents_ExcludesDeactivated() throws Exception {
        Student active = new Student(
                null, schoolA.getId(), "hash_act", "enc_1", "Hany Shaker",
                LocalDate.of(2012, 1, 1), "2026-HANY", "Grade 7", "A",
                "Parent", "+20 10 1111 2222", "p@example.com"
        );
        studentRepository.save(active);

        Student inactive = new Student(
                null, schoolA.getId(), "hash_inact", "enc_2", "Hany Samir",
                LocalDate.of(2012, 1, 1), "2026-SAMIR", "Grade 7", "A",
                "Parent", "+20 10 1111 2222", "p@example.com"
        );
        inactive.setStatus("Inactive");
        studentRepository.save(inactive);

        mockMvc.perform(get("/api/v1/students/search?q=Hany")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Hany Shaker"))
                .andExpect(jsonPath("$[0].status").value("Active"));
    }

    @Test
    void guardianManagement_LinkAndUnlink() throws Exception {
        Student s = new Student(
                null, schoolA.getId(), "hash_s", "enc_29901011234567", "Laila",
                LocalDate.of(2013, 5, 12), "2026-LAILA", "Grade 5", "B",
                null, null, null
        );
        s = studentRepository.save(s);

        LinkGuardianRequest linkReq = new LinkGuardianRequest(
                "Nader Mostafa",
                "nader@example.com",
                "+20 10 4444 5555",
                "Father",
                true
        );

        mockMvc.perform(post("/api/v1/students/" + s.getId() + "/guardians")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(linkReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Nader Mostafa"))
                .andExpect(jsonPath("$.email").value("nader@example.com"))
                .andExpect(jsonPath("$.relationship").value("Father"));

        mockMvc.perform(get("/api/v1/students/" + s.getId() + "/guardians")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].name").value("Nader Mostafa"));

        // Unlink guardian
        Student updated = studentRepository.findById(s.getId()).orElseThrow();
        mockMvc.perform(delete("/api/v1/students/" + s.getId() + "/guardians/" + updated.getGuardianId())
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isNoContent());

        Student afterUnlink = studentRepository.findById(s.getId()).orElseThrow();
        assertEquals(null, afterUnlink.getParentName());
    }

    @Test
    void getStudentStatement_CalculatesRunningBalanceChronologically() throws Exception {
        Student s = new Student(
                null, schoolA.getId(), "hash_s", "enc_29901011234567", "Omar Ahmed",
                LocalDate.of(2011, 2, 20), "2026-OMAR", "Grade 9", "A",
                "Ahmed Parent", "+20 10 1111 2222", "ahmed@example.com"
        );
        s = studentRepository.save(s);

        FeeLine fee1 = new FeeLine(schoolA.getId(), s.getId(), FeeType.TUITION,
                new BigDecimal("20000.00"), new BigDecimal("10000.00"), "Term 1", LocalDate.of(2026, 9, 1));
        fee1.setPaidAmount(new BigDecimal("10000.00"));
        fee1 = feeLineRepository.save(fee1);

        Payment payment1 = new Payment(UUID.randomUUID(), new BigDecimal("10000.00"),
                PaymentMethod.CREDIT_CARD, "idemp_001");
        payment1.setTransactionReference("TX-001");
        payment1.setAuthCode("auth_001");
        payment1.setStatus(PaymentStatus.CAPTURED);
        payment1.setCreatedAt(LocalDateTime.of(2026, 9, 5, 10, 0));
        payment1 = paymentRepository.save(payment1);
        paymentAllocationRepository.save(new PaymentAllocation(payment1, fee1, new BigDecimal("10000.00")));

        FeeLine fee2 = new FeeLine(schoolA.getId(), s.getId(), FeeType.BOOKS,
                new BigDecimal("3000.00"), new BigDecimal("3000.00"), "Term 1", LocalDate.of(2026, 9, 10));
        feeLineRepository.save(fee2);

        mockMvc.perform(get("/api/v1/students/" + s.getId() + "/statement")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.studentName").value("Omar Ahmed"))
                .andExpect(jsonPath("$.schoolName").value("Cairo International School"))
                .andExpect(jsonPath("$.totalInvoicedEGP").value(23000.00))
                .andExpect(jsonPath("$.totalPaidEGP").value(10000.00))
                .andExpect(jsonPath("$.currentBalanceEGP").value(13000.00))
                .andExpect(jsonPath("$.ledger", hasSize(3)))
                .andExpect(jsonPath("$.ledger[0].type").value("INVOICE"))
                .andExpect(jsonPath("$.ledger[0].debitEGP").value(20000.00))
                .andExpect(jsonPath("$.ledger[0].runningBalanceEGP").value(20000.00))
                .andExpect(jsonPath("$.ledger[1].type").value("PAYMENT"))
                .andExpect(jsonPath("$.ledger[1].creditEGP").value(10000.00))
                .andExpect(jsonPath("$.ledger[1].runningBalanceEGP").value(10000.00))
                .andExpect(jsonPath("$.ledger[2].type").value("INVOICE"))
                .andExpect(jsonPath("$.ledger[2].debitEGP").value(3000.00))
                .andExpect(jsonPath("$.ledger[2].runningBalanceEGP").value(13000.00));
    }
}
