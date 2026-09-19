package com.tuitionnetwork.e2e;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.fees.dto.CreateFeeRequest;
import com.tuitionnetwork.fees.dto.UpdateFeeRequest;
import com.tuitionnetwork.fees.service.FeeAutomatedRulesEngine;
import com.tuitionnetwork.identity.domain.AccountStatus;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.InstitutionAdmin;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.dto.auth.LoginRequest;
import com.tuitionnetwork.identity.dto.auth.MfaVerifyRequest;
import com.tuitionnetwork.identity.dto.users.CreateSchoolUserRequest;
import com.tuitionnetwork.identity.dto.users.UpdateSchoolUserRequest;
import com.tuitionnetwork.identity.repository.GuardianRepository;
import com.tuitionnetwork.identity.repository.InstitutionAdminRepository;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.identity.security.JwtTokenProvider;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.identity.security.UserRole;
import com.tuitionnetwork.ingestion.repository.CsvUploadRepository;
import com.tuitionnetwork.ingestion.repository.UploadErrorRepository;
import com.tuitionnetwork.ingestion.repository.UploadRowRepository;
import com.tuitionnetwork.ingestion.util.XlsxParser;
import com.tuitionnetwork.notifications.domain.SchoolNotification;
import com.tuitionnetwork.notifications.dto.SchoolNotificationPreferencesDto;
import com.tuitionnetwork.notifications.repository.NotificationRepository;
import com.tuitionnetwork.notifications.repository.SchoolNotificationRepository;
import com.tuitionnetwork.notifications.service.SchoolNotificationService;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.domain.Receipt;
import com.tuitionnetwork.payments.repository.PaymentAllocationRepository;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.settings.dto.ChangePasswordRequest;
import com.tuitionnetwork.settings.dto.SchoolNotificationSettingsRequest;
import com.tuitionnetwork.students.dto.DeactivateStudentRequest;
import com.tuitionnetwork.students.dto.EnrollStudentRequest;
import com.tuitionnetwork.students.dto.LinkGuardianRequest;
import com.tuitionnetwork.students.dto.UpdateStudentRequest;
import com.tuitionnetwork.reconciliation.domain.ReconciliationException;
import com.tuitionnetwork.reconciliation.repository.ReconciliationExceptionRepository;
import com.tuitionnetwork.reconciliation.repository.ReconciliationRunRepository;
import com.tuitionnetwork.reporting.repository.GeneratedReportRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/**
 * End-to-End master verification of all School Portal APIs across Milestones 1 to 5
 * based on Master Spec (1_MASTER_SPEC.md), Developer Onboarding Guide,
 * and School Portal API Contract (docs/School-Portal-API-Contract.md).
 */
@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
public class SchoolPortalMasterE2EIntegrationTest {

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
    private CsvUploadRepository csvUploadRepository;

    @Autowired
    private UploadRowRepository uploadRowRepository;

    @Autowired
    private UploadErrorRepository uploadErrorRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private com.tuitionnetwork.payments.repository.EppInstallmentRepository eppInstallmentRepository;

    @Autowired
    private com.tuitionnetwork.payments.repository.EPPScheduleRepository eppScheduleRepository;

    @Autowired
    private PaymentAllocationRepository paymentAllocationRepository;

    @Autowired
    private com.tuitionnetwork.payments.repository.ReceiptRepository receiptRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private ReconciliationRunRepository runRepository;

    @Autowired
    private ReconciliationExceptionRepository exceptionRepository;

    @Autowired
    private GeneratedReportRepository generatedReportRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FeeAutomatedRulesEngine feeAutomatedRulesEngine;

    @Autowired
    private SchoolNotificationRepository schoolNotificationRepository;

    @Autowired
    private SchoolNotificationService schoolNotificationService;

    private MockMvc mockMvc;

    private Institution schoolA;
    private Institution schoolB;

    private InstitutionAdmin adminA;
    private InstitutionAdmin adminB;

    private String tokenAdminA;
    private String tokenAdminB;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        objectMapper.findAndRegisterModules();

        schoolNotificationRepository.deleteAll();
        uploadRowRepository.deleteAll();
        uploadErrorRepository.deleteAll();
        csvUploadRepository.deleteAll();
        generatedReportRepository.deleteAll();
        exceptionRepository.deleteAll();
        runRepository.deleteAll();
        paymentAllocationRepository.deleteAll();
        receiptRepository.deleteAll();
        eppInstallmentRepository.deleteAll();
        eppScheduleRepository.deleteAll();
        paymentRepository.deleteAll();
        notificationRepository.deleteAll();
        feeLineRepository.deleteAll();
        studentRepository.deleteAll();
        guardianRepository.deleteAll();
        institutionAdminRepository.deleteAll();
        institutionRepository.deleteAll();

        // 1. Setup School A (Cairo International School)
        schoolA = new Institution("Cairo International School", "SCH-001", "SCHOOL_ABSORBS");
        schoolA.setAccountStatus(AccountStatus.ACTIVE);
        schoolA = institutionRepository.save(schoolA);

        // 2. Setup School B (Alexandria National School)
        schoolB = new Institution("Alexandria National School", "SCH-002", "SCHOOL_ABSORBS");
        schoolB.setAccountStatus(AccountStatus.ACTIVE);
        schoolB = institutionRepository.save(schoolB);

        // 3. Setup School Admin for School A
        adminA = new InstitutionAdmin(schoolA.getId(), "Amr Hassan", "amr.hassan@cis.edu.eg",
                passwordEncoder.encode("Password123!"), "School Admin");
        adminA.setStatus("Active");
        adminA.setCreatedAt(LocalDateTime.now());
        adminA = institutionAdminRepository.save(adminA);

        // 4. Setup School Admin for School B
        adminB = new InstitutionAdmin(schoolB.getId(), "Tarek Samy", "tarek.samy@ans.edu.eg",
                passwordEncoder.encode("Password123!"), "School Admin");
        adminB.setStatus("Active");
        adminB.setCreatedAt(LocalDateTime.now());
        adminB = institutionAdminRepository.save(adminB);

        tokenAdminA = jwtTokenProvider.generateToken(new SecurityUserPrincipal(
                adminA.getId(), adminA.getEmail(), adminA.getName(),
                UserRole.ROLE_SCHOOL_ADMIN,
                List.of(UserRole.ROLE_SCHOOL_ADMIN, UserRole.ROLE_INSTITUTION_ADMIN),
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
    @DisplayName("Stage 1: Complete Authentication, MFA, Session & School User Management Lifecycle (Phases 1 & 10)")
    void testStage1_AuthenticationAndUserLifecycle() throws Exception {
        // 1.1 Login Request -> Triggers SMS MFA challenge
        LoginRequest loginReq = new LoginRequest("amr.hassan@cis.edu.eg", "Password123!", true);
        MvcResult loginRes = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(loginReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.mfaToken").isNotEmpty())
                .andExpect(jsonPath("$.expiresInSeconds").value(60))
                .andReturn();

        String mfaToken = objectMapper.readTree(loginRes.getResponse().getContentAsString()).get("mfaToken").asText();

        // 1.2 MFA Verify -> Exchanges OTP for JWT
        MfaVerifyRequest verifyReq = new MfaVerifyRequest(mfaToken, "123456");
        MvcResult verifyRes = mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("amr.hassan@cis.edu.eg"))
                .andExpect(jsonPath("$.user.schoolId").value(schoolA.getCode()))
                .andReturn();

        String accessToken = objectMapper.readTree(verifyRes.getResponse().getContentAsString()).get("accessToken").asText();

        // 1.3 Session & Identity verification
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("amr.hassan@cis.edu.eg"))
                .andExpect(jsonPath("$.role").value("school-admin"))
                .andExpect(jsonPath("$.permissions", hasItem("fee-management")));

        // 1.4 Role Permissions verification
        mockMvc.perform(get("/api/v1/roles/school-admin/permissions")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("fee-management")));

        // 1.5 Roles Catalogue
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].role").value("School Admin"))
                .andExpect(jsonPath("$[1].role").value("School Finance"));

        // 1.6 User Management: School Admin creates Finance User
        CreateSchoolUserRequest newUserReq = new CreateSchoolUserRequest(
                "Dina Fouad", "dina.fouad@cis.edu.eg", "School Finance", "Password123!"
        );
        MvcResult createRes = mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(newUserReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value("dina.fouad@cis.edu.eg"))
                .andExpect(jsonPath("$.role").value("School Finance"))
                .andExpect(jsonPath("$.status").value("Active"))
                .andReturn();

        UUID createdUserId = UUID.fromString(objectMapper.readTree(createRes.getResponse().getContentAsString()).get("id").asText());

        // 1.7 List users with role filter
        mockMvc.perform(get("/api/v1/users?role=School Finance")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].email").value("dina.fouad@cis.edu.eg"));

        // 1.8 Update User Profile
        UpdateSchoolUserRequest updateReq = new UpdateSchoolUserRequest("Dina F. Fouad", "dina.fouad@cis.edu.eg", "School Finance");
        mockMvc.perform(patch("/api/v1/users/" + createdUserId)
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Dina F. Fouad"));

        // 1.9 Deactivate User
        mockMvc.perform(post("/api/v1/users/" + createdUserId + "/deactivate")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Inactive"));

        // 1.10 Reactivate User
        mockMvc.perform(post("/api/v1/users/" + createdUserId + "/activate")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Active"));

        // 1.11 Change Password self-service
        ChangePasswordRequest pwReq = new ChangePasswordRequest("Password123!", "NewSecret2026@!");
        mockMvc.perform(post("/api/v1/settings/change-password")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(pwReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(containsString("Password updated")));
    }

    @Test
    @DisplayName("Stage 2: School Settings Profile & Notification Configuration (Phase 11)")
    void testStage2_SettingsAndNotifications() throws Exception {
        // 2.1 Read school profile
        mockMvc.perform(get("/api/v1/settings/profile")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cairo International School"))
                .andExpect(jsonPath("$.code").value("SCH-001"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

        // 2.2 Read notification delivery channels
        mockMvc.perform(get("/api/v1/settings/notifications")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels").isMap());

        // 2.3 Update notification delivery channels
        SchoolNotificationSettingsRequest updateReq = new SchoolNotificationSettingsRequest(
                Map.of("inApp", true, "email", false)
        );
        mockMvc.perform(put("/api/v1/settings/notifications")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.inApp").value(true))
                .andExpect(jsonPath("$.channels.email").value(false));
    }

    @Test
    @DisplayName("Stage 3: Student Roster, Guardian Linking & Deactivation Rules (Phase 3)")
    void testStage3_StudentRosterAndGuardians() throws Exception {
        // 3.1 Enroll student 1
        EnrollStudentRequest enroll1 = new EnrollStudentRequest(
                "STU-001", "Yousef Adel", "Grade 10", "A", "29801011234567",
                "Adel Mahmoud", "+201001234567", "adel@example.com"
        );
        MvcResult res1 = mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enroll1)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.studentRef").value("STU-001"))
                .andExpect(jsonPath("$.status").value("Active"))
                .andReturn();

        UUID studentId1 = UUID.fromString(objectMapper.readTree(res1.getResponse().getContentAsString()).get("id").asText());

        // 3.2 Enroll student 2
        EnrollStudentRequest enroll2 = new EnrollStudentRequest(
                "STU-002", "Mariam Tarek", "Grade 9", "B", "29802021234568",
                "Tarek Kamal", "+201009876543", "tarek@example.com"
        );
        mockMvc.perform(post("/api/v1/students")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(enroll2)))
                .andExpect(status().isCreated());

        // 3.3 Query roster with filters
        mockMvc.perform(get("/api/v1/students?search=Yousef&grade=Grade 10")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].studentRef").value("STU-001"));

        // 3.4 Query student detail
        mockMvc.perform(get("/api/v1/students/" + studentId1)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Yousef Adel"))
                .andExpect(jsonPath("$.parentName").value("Adel Mahmoud"));

        // 3.5 Update student info
        UpdateStudentRequest updateStudent = new UpdateStudentRequest(
                "STU-001", "Yousef A. Adel", "Grade 10", "A1",
                "Adel Mahmoud", "+201001234567", "adel@example.com"
        );
        mockMvc.perform(patch("/api/v1/students/" + studentId1)
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateStudent)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Yousef A. Adel"))
                .andExpect(jsonPath("$.section").value("A1"));

        // 3.6 Update guardian link
        LinkGuardianRequest linkGuardian = new LinkGuardianRequest(
                "Noha Adel", "noha@example.com", "+201009998888", "Mother", true
        );
        mockMvc.perform(post("/api/v1/students/" + studentId1 + "/guardians")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(linkGuardian)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Noha Adel"))
                .andExpect(jsonPath("$.relationship").value("Mother"));

        // 3.7 Student picker search
        mockMvc.perform(get("/api/v1/students/search?activeOnly=true&q=Yousef")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].studentRef").value("STU-001"));

        // 3.8 Deactivate student
        DeactivateStudentRequest deactReq = new DeactivateStudentRequest("Transferred to another school");
        mockMvc.perform(post("/api/v1/students/" + studentId1 + "/deactivate")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(deactReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Inactive"));

        // 3.9 Confirm deactivated student excluded from active search picker
        mockMvc.perform(get("/api/v1/students/search?activeOnly=true&q=Yousef")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(0)));

        // 3.10 Reactivate student
        mockMvc.perform(post("/api/v1/students/" + studentId1 + "/reactivate")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Active"));
    }

    @Test
    @DisplayName("Stage 4: Bulk Fee Upload, Dual Format Ingestion, Isolation & Resubmission (Phase 5)")
    void testStage4_BulkFeeIngestionPipeline() throws Exception {
        // Pre-seed active student STU-001
        Student student = new Student();
        student.setInstitutionId(schoolA.getId());
        student.setFullName("Karim Amr");
        student.setStudentRef("STU-001");
        student.setGrade("Grade 10");
        student.setNationalIdHash("hash_karim");
        student.setNationalIdEncrypted("enc_karim");
        student.setStatus("Active");
        studentRepository.save(student);

        // 4.1 Download CSV Template
        mockMvc.perform(get("/api/v1/fee-uploads/template")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("studentRef,feeName,category,amountEGP,term,dueDate")));

        // 4.2 Upload Mixed CSV (1 valid row, 1 negative amount row, 1 bad category row)
        String mixedCsv = """
                studentRef,feeName,category,amountEGP,term,dueDate
                STU-001,Tuition - Term 1,Tuition,20000.00,Term 1 2026/27,2026-10-15
                STU-001,Books - Term 1,Books,-500.00,Term 1 2026/27,2026-10-15
                STU-001,Activity - Term 1,UnknownCat,1500.00,Term 1 2026/27,2026-10-15
                """;

        MockMultipartFile csvFile = new MockMultipartFile(
                "file", "batch_fees.csv", "text/csv", mixedCsv.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult uploadRes = mockMvc.perform(multipart("/api/v1/fee-uploads")
                        .file(csvFile)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("Completed with Errors"))
                .andReturn();

        String uploadId = objectMapper.readTree(uploadRes.getResponse().getContentAsString()).get("uploadId").asText();

        // 4.3 Poll upload job detail
        mockMvc.perform(get("/api/v1/fee-uploads/" + uploadId)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(3))
                .andExpect(jsonPath("$.acceptedRows").value(1))
                .andExpect(jsonPath("$.rejectedRows").value(2));

        // 4.4 Inspect rows with filter
        mockMvc.perform(get("/api/v1/fee-uploads/" + uploadId + "/rows?status=Accepted")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("Accepted"));

        // 4.5 Inspect error drawer
        mockMvc.perform(get("/api/v1/fee-uploads/" + uploadId + "/errors")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // 4.6 Export error CSV
        mockMvc.perform(get("/api/v1/fee-uploads/" + uploadId + "/errors/export?format=csv")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("fee_upload_errors_" + uploadId + ".csv")))
                .andExpect(content().string(containsString("Amount must be greater than 0")));

        // 4.7 Resubmit corrected rows (US-36: no duplicates for already accepted row 1)
        String correctedCsv = """
                studentRef,feeName,category,amountEGP,term,dueDate
                STU-001,Tuition - Term 1,Tuition,20000.00,Term 1 2026/27,2026-10-15
                STU-001,Books - Term 1,Books,2500.00,Term 1 2026/27,2026-10-15
                STU-001,Activity - Term 1,Activity,1500.00,Term 1 2026/27,2026-10-15
                """;

        MockMultipartFile correctedFile = new MockMultipartFile(
                "file", "batch_fees_corrected.csv", "text/csv", correctedCsv.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/fee-uploads/" + uploadId + "/resubmit")
                        .file(correctedFile)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Completed"))
                .andExpect(jsonPath("$.acceptedRows").value(3))
                .andExpect(jsonPath("$.rejectedRows").value(0));

        // Verify exact fee line count is 3 (STU-001 Tuition was not duplicated)
        List<FeeLine> feeLines = feeLineRepository.findByInstitutionId(schoolA.getId());
        assertEquals(3, feeLines.size());

        // 4.8 Duplicate upload rejected with 409 (US-37)
        mockMvc.perform(multipart("/api/v1/fee-uploads")
                        .file(csvFile)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("duplicate_upload")));

        // 4.9 Unsupported file type rejected with 400
        MockMultipartFile invalidExtFile = new MockMultipartFile(
                "file", "fees.exe", "application/octet-stream", "bad binary".getBytes(StandardCharsets.UTF_8)
        );
        mockMvc.perform(multipart("/api/v1/fee-uploads")
                        .file(invalidExtFile)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("unsupported_file_type")));

        // 4.10 Excel XLSX Ingestion
        List<List<String>> xlsxRows = List.of(
                List.of("studentRef", "feeName", "category", "amountEGP", "term", "dueDate"),
                List.of("STU-001", "Bus - Annual", "Bus", "5000.00", "Term 1 2026/27", "2026-10-15")
        );
        byte[] xlsxBytes = XlsxParser.createMinimalXlsx(xlsxRows);
        MockMultipartFile xlsxFile = new MockMultipartFile(
                "file", "bus_fees.xlsx", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", xlsxBytes
        );

        mockMvc.perform(multipart("/api/v1/fee-uploads")
                        .file(xlsxFile)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("Completed"));

        // 4.11 Upload history
        mockMvc.perform(get("/api/v1/fee-uploads")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));
    }

    @Test
    @DisplayName("Stage 5: Fee Structure, Dynamic Overdue, Automated Engines & Penalty Logic (Phase 4)")
    void testStage5_FeeStructureAndAutomation() throws Exception {
        // Pre-seed student
        Student student = new Student();
        student.setInstitutionId(schoolA.getId());
        student.setFullName("Laila Nour");
        student.setStudentRef("STU-010");
        student.setGrade("Grade 12");
        student.setNationalIdHash("hash_laila");
        student.setNationalIdEncrypted("enc_laila");
        student.setStatus("Active");
        student = studentRepository.save(student);

        // Pre-seed inactive student
        Student inactStudent = new Student();
        inactStudent.setInstitutionId(schoolA.getId());
        inactStudent.setFullName("Ziad Deactivated");
        inactStudent.setStudentRef("STU-011");
        inactStudent.setGrade("Grade 12");
        inactStudent.setNationalIdHash("hash_ziad");
        inactStudent.setNationalIdEncrypted("enc_ziad");
        inactStudent.setStatus("Inactive");
        inactStudent = studentRepository.save(inactStudent);

        // 5.1 Fee Categories Catalogue
        mockMvc.perform(get("/api/v1/fee-categories")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].code").value("Tuition"))
                .andExpect(jsonPath("$[0].priority").value(1));

        // 5.2 Create Fee Line: Due date is required
        CreateFeeRequest noDueDateReq = new CreateFeeRequest(
                student.getId().toString(), "Tuition", "Tuition", new BigDecimal("15000.00"), "Term 1", null
        );
        mockMvc.perform(post("/api/v1/fees")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(noDueDateReq)))
                .andExpect(status().isBadRequest());

        // 5.3 Create Fee Line: Blocked for deactivated student
        CreateFeeRequest inactFeeReq = new CreateFeeRequest(
                inactStudent.getId().toString(), "Tuition", "Tuition", new BigDecimal("15000.00"), "Term 1", LocalDate.now().plusMonths(1)
        );
        mockMvc.perform(post("/api/v1/fees")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(inactFeeReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("student_inactive")));

        // 5.4 Create Valid Fee Line
        CreateFeeRequest validFeeReq = new CreateFeeRequest(
                student.getId().toString(), "Tuition - Term 1", "Tuition",
                new BigDecimal("18000.00"), "Term 1 2026/27", LocalDate.now().plusMonths(2)
        );
        MvcResult feeRes = mockMvc.perform(post("/api/v1/fees")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(validFeeReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.name").value("Tuition - Term 1 2026/27"))
                .andExpect(jsonPath("$.originalAmountEGP").value(18000.00))
                .andExpect(jsonPath("$.status").value("Active"))
                .andReturn();

        UUID feeId = UUID.fromString(objectMapper.readTree(feeRes.getResponse().getContentAsString()).get("id").asText());

        // 5.5 Query Fee List with filter
        mockMvc.perform(get("/api/v1/fees?category=Tuition")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].studentName").value("Laila Nour"));

        // 5.6 Query Fee Detail
        mockMvc.perform(get("/api/v1/fees/" + feeId)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(feeId.toString()))
                .andExpect(jsonPath("$.overdue.isOverdue").value(false));

        // 5.7 Update Fee Line
        UpdateFeeRequest updateFeeReq = new UpdateFeeRequest(
                new BigDecimal("19000.00"), LocalDate.now().plusMonths(2), "Tuition - Term 1 (Updated)", "Term 1 2026/27", "Amount adjustment"
        );
        mockMvc.perform(patch("/api/v1/fees/" + feeId)
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateFeeReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.originalAmountEGP").value(19000.00));

        // 5.8 Overdue & Late Penalty Engine Verification:
        // Set dueDate to 10 days ago (grace ended > 7 days)
        FeeLine feeLine = feeLineRepository.findById(feeId).orElseThrow();
        feeLine.setDueDate(LocalDate.now().minusDays(10));
        feeLineRepository.save(feeLine);

        // Verify status dynamically evaluates to Overdue
        mockMvc.perform(get("/api/v1/fees/" + feeId)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Overdue"))
                .andExpect(jsonPath("$.overdue.isOverdue").value(true))
                .andExpect(jsonPath("$.overdue.daysOverdue").value(10));

        // Trigger Automated Penalty Rules Engine
        feeAutomatedRulesEngine.runTuitionPenaltyEngine();

        // Verify flat 5% penalty applied (5% of 19000 = 950 EGP)
        mockMvc.perform(get("/api/v1/fees/" + feeId)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penalty.applied").value(true))
                .andExpect(jsonPath("$.penalty.penaltyAmountEGP").value(950.00))
                .andExpect(jsonPath("$.penalty.totalDueEGP").value(19950.00));

        // Verify Penalty Info endpoint
        mockMvc.perform(get("/api/v1/fees/" + feeId + "/penalty-info")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.penaltyAmountEGP").value(950.00))
                .andExpect(jsonPath("$.graceEnded").value(true));

        // 5.9 Cancel Fee Line
        mockMvc.perform(post("/api/v1/fees/" + feeId + "/cancel")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Cancelled"));

        // 5.10 Fee Stats
        mockMvc.perform(get("/api/v1/fees/stats")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalInvoicedEGP").exists())
                .andExpect(jsonPath("$.activeFeeLinesCount").exists());
    }

    @Test
    @DisplayName("Stage 6: Dashboard Summary, Roll-Ups & Analytics (Phase 2)")
    void testStage6_DashboardRollups() throws Exception {
        mockMvc.perform(get("/api/v1/dashboard/summary")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis").exists())
                .andExpect(jsonPath("$.kpis.totalCollectedEGP").exists())
                .andExpect(jsonPath("$.kpis.outstandingEGP").exists());

        mockMvc.perform(get("/api/v1/dashboard/recent-payments")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/v1/dashboard/quick-links")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk());
    }

    @Test
    @DisplayName("Stage 7: Multi-Tenant Data Isolation & Paranoia Guardrails")
    void testStage7_CrossSchoolIsolationGuardrails() throws Exception {
        // Seed Student in School A
        Student studentA = new Student();
        studentA.setInstitutionId(schoolA.getId());
        studentA.setFullName("Ahmad CIS");
        studentA.setStudentRef("STU-A");
        studentA.setStatus("Active");
        studentA.setNationalIdHash("hashA");
        studentA.setNationalIdEncrypted("encA");
        studentA = studentRepository.save(studentA);

        // Seed Fee Line in School A
        FeeLine feeA = new FeeLine(schoolA.getId(), studentA.getId(), FeeType.TUITION,
                new BigDecimal("10000.00"), new BigDecimal("10000.00"), "Term 1", LocalDate.now().plusMonths(1));
        feeA = feeLineRepository.save(feeA);

        // 7.1 Cross-school student access blocked (School B admin accessing School A student)
        mockMvc.perform(get("/api/v1/students/" + studentA.getId())
                        .header("Authorization", "Bearer " + tokenAdminB))
                .andExpect(status().isForbidden());

        // 7.2 Cross-school fee access blocked
        mockMvc.perform(get("/api/v1/fees/" + feeA.getId())
                        .header("Authorization", "Bearer " + tokenAdminB))
                .andExpect(status().isForbidden());

        // 7.3 Unauthenticated access blocked on all protected endpoints
        mockMvc.perform(get("/api/v1/students"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/fees"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/fee-uploads"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Stage 8: Payments View, Allocated Dues Breakdown & Crypto-Signed Receipts (Phase 6 / Milestone 6)")
    void testStage8_PaymentsViewAndCryptoSignedReceipts() throws Exception {
        // 8.1 Seed Student, FeeLine, Payment and Allocation in School A
        Student student = new Student(
                null, schoolA.getId(), "hash_p8", "enc_p8", "Tarek El-Sayed",
                LocalDate.of(2010, 4, 10), "STU-0888", "Grade 11", "A",
                "El-Sayed Tarek", "+20 10 9999 8888", "elsayed@example.com"
        );
        student.setStatus("Active");
        student = studentRepository.save(student);

        FeeLine fee = new FeeLine(schoolA.getId(), student.getId(), FeeType.TUITION,
                new BigDecimal("20000.00"), new BigDecimal("10000.00"), "Term 1 2026/27", LocalDate.now().plusMonths(1));
        fee.setPaidAmount(new BigDecimal("10000.00"));
        fee.setStatus(FeeStatus.PARTIALLY_PAID);
        fee = feeLineRepository.save(fee);

        Payment payment = new Payment(UUID.randomUUID(), new BigDecimal("10000.00"), PaymentMethod.CREDIT_CARD, "idemp-m6-stage8");
        payment.setTransactionReference("TX-20260913-0888");
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setCreatedAt(LocalDateTime.now().minusHours(2));
        payment = paymentRepository.save(payment);

        PaymentAllocation alloc = new PaymentAllocation(payment, fee, new BigDecimal("10000.00"));
        paymentAllocationRepository.save(alloc);

        Receipt receipt = new Receipt(payment, "SIG-SHA256-STAGE8TESTSIG9999", "https://cdn.tuitionnetwork.eg/receipts/receipt-" + payment.getId() + ".pdf");
        receipt.setIssuedAt(LocalDateTime.now().minusHours(2));
        receiptRepository.save(receipt);

        // 8.2 Query School Payments list (Phase 6.1)
        mockMvc.perform(get("/api/v1/payments")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].id").value("TX-20260913-0888"))
                .andExpect(jsonPath("$.data[0].studentName").value("Tarek El-Sayed"))
                .andExpect(jsonPath("$.data[0].amountEGP").value(10000.00))
                .andExpect(jsonPath("$.data[0].status").value("Successful"))
                .andExpect(jsonPath("$.data[0].isPartial").value(true));

        // 8.3 Query Payment Detail with Allocated Dues Breakdown (Phase 6.2)
        mockMvc.perform(get("/api/v1/payments/" + payment.getId())
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("TX-20260913-0888"))
                .andExpect(jsonPath("$.amountEGP").value(10000.00))
                .andExpect(jsonPath("$.allocation", hasSize(1)))
                .andExpect(jsonPath("$.allocation[0].feeCategory").value("Tuition"))
                .andExpect(jsonPath("$.allocation[0].allocatedEGP").value(10000.00))
                .andExpect(jsonPath("$.allocation[0].remainingAfterEGP").value(10000.00));

        // 8.4 Query Crypto-Signed Receipt JSON
        mockMvc.perform(get("/api/v1/payments/" + payment.getId() + "/receipt")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.cryptoSignature").value("SIG-SHA256-STAGE8TESTSIG9999"))
                .andExpect(jsonPath("$.amount").value(10000.00));

        // 8.5 Download Crypto-Signed PDF Receipt
        mockMvc.perform(get("/api/v1/payments/" + payment.getId() + "/receipt?format=pdf")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "application/pdf"))
                .andExpect(header().string("Content-Disposition", containsString("receipt-")));

        // 8.6 Export Payments CSV (Phase 6.3)
        mockMvc.perform(get("/api/v1/payments/export")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("payments-export.csv")));

        // 8.7 Cross-School Payment Isolation (School B blocked from School A payment)
        mockMvc.perform(get("/api/v1/payments/" + payment.getId())
                        .header("Authorization", "Bearer " + tokenAdminB))
                .andExpect(status().isForbidden());

        // 8.8 Bank-Only Guardrail: School Admin cannot execute payment
        mockMvc.perform(post("/api/v1/payments")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "nationalId": "29501011234567",
                                    "feeIds": ["%s"],
                                    "amountEGP": 1000.00,
                                    "method": "CREDIT_CARD"
                                }
                                """.formatted(fee.getId())))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Stage 9: Reconciliation & Settlement Visibility (Phase 7)")
    void testStage9_ReconciliationAndSettlementVisibility() throws Exception {
        // 9.0 Setup test student, fee lines, and payments for School A
        Student student = new Student(null, schoolA.getId(), "hash_recon1", "enc_recon1", "Amira Khaled",
                LocalDate.of(2011, 4, 10), "STU-0771", "Grade 11", "A",
                "Khaled Farouk", "+20 10 9988 7766", "khaled@example.com");
        student = studentRepository.save(student);

        FeeLine fee1 = new FeeLine(schoolA.getId(), student.getId(), FeeType.TUITION, new BigDecimal("10000.00"), BigDecimal.ZERO, "2026/2027", LocalDate.of(2026, 9, 1));
        fee1 = feeLineRepository.save(fee1);

        FeeLine fee2 = new FeeLine(schoolA.getId(), student.getId(), FeeType.BUS, new BigDecimal("5000.00"), new BigDecimal("5000.00"), "2026/2027", LocalDate.of(2026, 9, 15));
        fee2 = feeLineRepository.save(fee2);

        FeeLine fee3 = new FeeLine(schoolA.getId(), student.getId(), FeeType.ACTIVITIES, new BigDecimal("3000.00"), BigDecimal.ZERO, "2026/2027", LocalDate.of(2026, 9, 20));
        fee3 = feeLineRepository.save(fee3);

        // Payment 1: CAPTURED -> Reconciled
        Payment p1 = new Payment(UUID.randomUUID(), new BigDecimal("10000.00"), PaymentMethod.CIB_ACCOUNT, "IDEMP-RECON1-" + UUID.randomUUID());
        p1.setStatus(PaymentStatus.CAPTURED);
        p1.setTransactionReference("TX-20260914-0701");
        p1 = paymentRepository.save(p1);
        paymentAllocationRepository.save(new PaymentAllocation(p1, fee1, new BigDecimal("10000.00")));

        // Payment 2: PENDING -> Pending
        Payment p2 = new Payment(UUID.randomUUID(), new BigDecimal("5000.00"), PaymentMethod.CREDIT_CARD, "IDEMP-RECON2-" + UUID.randomUUID());
        p2.setStatus(PaymentStatus.PENDING);
        p2.setTransactionReference("TX-20260914-0702");
        p2 = paymentRepository.save(p2);
        paymentAllocationRepository.save(new PaymentAllocation(p2, fee2, new BigDecimal("5000.00")));

        // Payment 3: CAPTURED with Exception -> Unreconciled
        Payment p3 = new Payment(UUID.randomUUID(), new BigDecimal("3000.00"), PaymentMethod.CREDIT_CARD, "IDEMP-RECON3-" + UUID.randomUUID());
        p3.setStatus(PaymentStatus.CAPTURED);
        p3.setTransactionReference("TX-20260914-0703");
        p3 = paymentRepository.save(p3);
        paymentAllocationRepository.save(new PaymentAllocation(p3, fee3, new BigDecimal("3000.00")));

        ReconciliationException exception = new ReconciliationException();
        exception.setPaymentId(p3.getId());
        exception.setTxRef("TX-20260914-0703");
        exception.setInstitution(schoolA.getName());
        exception.setInstitutionType("School");
        exception.setStatus("Open");
        exception.setType("Amount Mismatch");
        exception.setBankAmountEGP(2800L);
        exception.setSchoolAmountEGP(3000L);
        exception.setDifferenceEGP(200L);
        exceptionRepository.save(exception);

        // 9.1 Query GET /reconciliation/summary (Phase 7.1)
        // 10,000 gross, 2% CIB fee = 200, net settled = 9,800, pending payout = 5,000
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

        // Dual endpoint /api/v1/reconciliation/summary
        mockMvc.perform(get("/api/v1/reconciliation/summary")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReconciled", is(1)))
                .andExpect(jsonPath("$.netSettledEGP", is(9800)));

        // 9.2 Query GET /reconciliation/transactions with filters & pagination (Phase 7.2)
        mockMvc.perform(get("/reconciliation/transactions")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(3)))
                .andExpect(jsonPath("$.data", hasSize(3)));

        // Filter status=Reconciled
        mockMvc.perform(get("/reconciliation/transactions")
                        .param("status", "Reconciled")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(1)))
                .andExpect(jsonPath("$.data[0].paymentId", is("TX-20260914-0701")))
                .andExpect(jsonPath("$.data[0].reconciliationStatus", is("Reconciled")));

        // Filter status=Unreconciled
        mockMvc.perform(get("/reconciliation/transactions")
                        .param("status", "Unreconciled")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(1)))
                .andExpect(jsonPath("$.data[0].paymentId", is("TX-20260914-0703")))
                .andExpect(jsonPath("$.data[0].reconciliationStatus", is("Unreconciled")));

        // Filter by studentId
        mockMvc.perform(get("/reconciliation/transactions")
                        .param("studentId", "STU-0771")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total", is(3)))
                .andExpect(jsonPath("$.data[0].studentId", is("STU-0771")));

        // 9.3 Query GET /reconciliation/settlements
        mockMvc.perform(get("/reconciliation/settlements")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].id", startsWith("SET-SCH-001-")))
                .andExpect(jsonPath("$.data[0].grossEGP", is(10000)))
                .andExpect(jsonPath("$.data[0].cibFeeEGP", is(200)))
                .andExpect(jsonPath("$.data[0].netEGP", is(9800)))
                .andExpect(jsonPath("$.data[0].status", is("Completed")))
                .andExpect(jsonPath("$.summary.totalSettledEGP", is(9800)));

        // 9.4 Multi-tenant isolation: School B sees 0 data
        mockMvc.perform(get("/reconciliation/summary")
                        .header("Authorization", "Bearer " + tokenAdminB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalReconciled", is(0)))
                .andExpect(jsonPath("$.grossCollectedEGP", is(0)));

        // Cross-school query param rejected with 403 Forbidden
        mockMvc.perform(get("/reconciliation/summary")
                        .param("institutionId", schoolB.getId().toString())
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());

        // 9.5 Bank Guardrails: School users attempting mutating recon actions are rejected with 403 Forbidden
        mockMvc.perform(post("/reconciliation/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"date\":\"2026-09-14\"}")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());

        mockMvc.perform(post("/reconciliation/exceptions/" + UUID.randomUUID() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"assignedTo\":\"Staff\"}")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());

        mockMvc.perform(patch("/reconciliation/exceptions/" + UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"resolutionAction\":\"Manual Match\"}")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/reconciliation/exceptions")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Stage 10: Reports & Async Generation Pipeline (Phase 8)")
    void testStage10_ReportsAndAsyncGenerationPipeline() throws Exception {
        // 10.0 Setup test student, fee line, and payment for School A
        Student student = new Student(null, schoolA.getId(), "hash_rpt1", "enc_rpt1", "Nader Nabil",
                LocalDate.of(2010, 5, 20), "STU-0881", "Grade 10", "A",
                "Nabil Nader", "+20 10 3322 1100", "nabil.nader@example.com");
        student = studentRepository.save(student);

        FeeLine fee = new FeeLine(schoolA.getId(), student.getId(), FeeType.TUITION,
                new BigDecimal("15000.00"), new BigDecimal("5000.00"), "2026/2027", LocalDate.of(2026, 9, 20));
        fee.setPaidAmount(new BigDecimal("10000.00"));
        fee.setStatus(FeeStatus.PARTIALLY_PAID);
        fee = feeLineRepository.save(fee);

        Payment payment = new Payment(UUID.randomUUID(), new BigDecimal("10000.00"),
                PaymentMethod.CREDIT_CARD, "IDEMP-RPT-" + UUID.randomUUID());
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setTransactionReference("TX-20260914-0801");
        payment = paymentRepository.save(payment);
        paymentAllocationRepository.save(new PaymentAllocation(payment, fee, new BigDecimal("10000.00")));

        // 10.1 GET /reports/catalogue & /reports/templates return 4 school report templates (Phase 8.1)
        mockMvc.perform(get("/reports/catalogue")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)))
                .andExpect(jsonPath("$[0].id").value("school-collections"))
                .andExpect(jsonPath("$[1].id").value("school-payments"))
                .andExpect(jsonPath("$[2].id").value("school-outstanding-fees"))
                .andExpect(jsonPath("$[3].id").value("school-partial-payments"));

        // Dual alias /reports/templates works identically
        mockMvc.perform(get("/reports/templates")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(4)));

        // 10.2 Validation errors on POST /reports/generate (Phase 8.2)
        // 1. date_from_after_date_to
        mockMvc.perform(post("/reports/generate")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "reportId": "school-collections",
                                    "dateFrom": "2026-09-30",
                                    "dateTo": "2026-09-01",
                                    "format": "CSV"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("date_from_after_date_to"));

        // 2. unsupported_format_for_report
        mockMvc.perform(post("/reports/generate")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "reportId": "school-collections",
                                    "dateFrom": "2026-09-01",
                                    "dateTo": "2026-09-30",
                                    "format": "XML"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("unsupported_format_for_report"));

        // 10.3 Successful generation returns 202 Accepted with status processing (Phase 8.2)
        MvcResult genResult = mockMvc.perform(post("/reports/generate")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "reportId": "school-outstanding-fees",
                                    "dateFrom": "2026-08-01",
                                    "dateTo": "2026-09-30",
                                    "format": "CSV",
                                    "filters": {
                                        "feeCategory": "Tuition"
                                    }
                                }
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.jobId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("processing"))
                .andExpect(jsonPath("$.reportId").value("school-outstanding-fees"))
                .andReturn();

        String jobId = objectMapper.readTree(genResult.getResponse().getContentAsString()).get("jobId").asText();

        // 10.4 GET /reports/jobs/{jobId} returns preview and downloadUrl (Phase 8.3)
        mockMvc.perform(get("/reports/jobs/{jobId}", jobId)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.downloadUrl").value("/reports/jobs/" + jobId + "/download"))
                .andExpect(jsonPath("$.preview.columns", hasSize(greaterThanOrEqualTo(4))))
                .andExpect(jsonPath("$.preview.rows", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.preview.rows[0].student").value("Nader Nabil"));

        // 10.5 GET /reports/jobs/{jobId}/download streams CSV file (Phase 8.4)
        mockMvc.perform(get("/reports/jobs/{jobId}/download", jobId)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", containsString("text/csv")))
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=")))
                .andExpect(content().string(containsString("Nader Nabil")))
                .andExpect(content().string(containsString("5000")));

        // 10.6 GET /reports/history returns prior runs scoped to School A (Phase 8.5)
        mockMvc.perform(get("/reports/history")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].reportId").value("school-outstanding-fees"));

        // 10.7 Multi-tenant isolation: School B cannot access School A job or download
        mockMvc.perform(get("/reports/jobs/{jobId}", jobId)
                        .header("Authorization", "Bearer " + tokenAdminB))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/reports/jobs/{jobId}/download", jobId)
                        .header("Authorization", "Bearer " + tokenAdminB))
                .andExpect(status().isForbidden());

        // Cross-school query param rejected with 403 Forbidden
        mockMvc.perform(post("/reports/generate")
                        .param("institutionId", schoolB.getId().toString())
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "reportId": "school-collections",
                                    "dateFrom": "2026-08-01",
                                    "dateTo": "2026-09-30",
                                    "format": "CSV"
                                }
                                """))
                .andExpect(status().isForbidden());

        // School user attempting to generate bank-only report rejected with 403 Forbidden
        mockMvc.perform(post("/reports/generate")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "reportId": "network-collections",
                                    "dateFrom": "2026-08-01",
                                    "dateTo": "2026-09-30",
                                    "format": "CSV"
                                }
                                """))
                .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("Stage 11: School In-App Notifications, Bell Counter, Reminders Feed & Preferences (Phase 9)")
    void testStage11_NotificationsCenterAndDeliveryPreferences() throws Exception {
        // 11.1 Bell Counter returns 0 initially (Phase 9.2)
        mockMvc.perform(get("/notifications/unread-count")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        // 11.2 Pre-seed server-generated notifications for School A
        SchoolNotification nReminder = schoolNotificationService.createNotification(
                schoolA.getId(),
                "reminder",
                "Payment reminder sent",
                "Reminder: Yousef Adel's Tuition fee of 18,000 EGP is due in 1 week on 2026-09-15.",
                "Yousef Adel",
                null,
                "Tuition",
                18000L,
                LocalDate.of(2026, 9, 15),
                7,
                "Sent",
                "FEE-0231-01"
        );

        SchoolNotification nPenalty = schoolNotificationService.createNotification(
                schoolA.getId(),
                "penalty",
                "Late penalty applied",
                "Late penalty of 900 EGP applied to Karim Omar's fee line",
                "Karim Omar",
                null,
                "Tuition",
                900L,
                LocalDate.now().minusDays(10),
                0,
                "Sent",
                "FEE-0099-01"
        );

        // Pre-seed a notification for School B (to test isolation)
        SchoolNotification nSchoolB = schoolNotificationService.createNotification(
                schoolB.getId(),
                "reminder",
                "School B Reminder",
                "School B private notification",
                "Salma B",
                null,
                "Tuition",
                12000L,
                LocalDate.now().plusDays(7),
                7,
                "Sent",
                "FEE-B-01"
        );

        // 11.3 Bell Counter reflects unread items for School A (count = 2)
        mockMvc.perform(get("/notifications/unread-count")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(2));

        // 11.4 GET /notifications returns feed with unreadCount and shape matching Contract 9.1
        mockMvc.perform(get("/notifications")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.unreadCount").value(2))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.data[?(@.type == 'reminder')].studentName").value(hasItem("Yousef Adel")))
                .andExpect(jsonPath("$.data[?(@.type == 'penalty')].studentName").value(hasItem("Karim Omar")));

        // Filter by type=reminder
        mockMvc.perform(get("/notifications?type=reminder")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type").value("reminder"));

        // 11.5 POST & PATCH /notifications/{id}/read mark as read (Phase 9.3)
        mockMvc.perform(post("/notifications/" + nReminder.getNotificationRef() + "/read")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(nReminder.getNotificationRef()))
                .andExpect(jsonPath("$.read").value(true));

        // Bell counter now down to 1
        mockMvc.perform(get("/notifications/unread-count")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(1));

        // 11.6 POST /notifications/read-all marks all remaining read (Phase 9.4)
        mockMvc.perform(post("/notifications/read-all")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updated").value(1));

        mockMvc.perform(get("/notifications/unread-count")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.count").value(0));

        // 11.7 DELETE /notifications/{id} dismisses notification with 204 (Phase 9.5)
        mockMvc.perform(delete("/notifications/" + nPenalty.getNotificationRef())
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isNoContent());

        // Feed now only has 1 notification left
        mockMvc.perform(get("/notifications")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)));

        // 11.8 GET /notifications/reminders dedicated parent-reminder feed (Phase 9.6)
        mockMvc.perform(get("/notifications/reminders")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(1)))
                .andExpect(jsonPath("$.data[0].type").value("reminder"))
                .andExpect(jsonPath("$.data[0].notificationStatus").value("Sent"));

        // 11.9 GET & PUT /notifications/preferences channel toggles
        mockMvc.perform(get("/notifications/preferences")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.inApp").value(true))
                .andExpect(jsonPath("$.channels.email").value(true));

        mockMvc.perform(put("/notifications/preferences")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                    "channels": {
                                        "inApp": true,
                                        "email": false
                                    }
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.email").value(false));

        // 11.10 Multi-tenant isolation: School A cannot read or delete School B's notifications
        mockMvc.perform(post("/notifications/" + nSchoolB.getNotificationRef() + "/read")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/notifications/" + nSchoolB.getNotificationRef())
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());
    }
}
