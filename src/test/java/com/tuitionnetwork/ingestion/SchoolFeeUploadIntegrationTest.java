package com.tuitionnetwork.ingestion;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.billing.domain.FeeLine;
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
import com.tuitionnetwork.ingestion.domain.CsvUpload;
import com.tuitionnetwork.ingestion.repository.CsvUploadRepository;
import com.tuitionnetwork.ingestion.repository.UploadErrorRepository;
import com.tuitionnetwork.ingestion.repository.UploadRowRepository;
import com.tuitionnetwork.ingestion.util.XlsxParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = DemoApplication.class)
class SchoolFeeUploadIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private CsvUploadRepository csvUploadRepository;

    @Autowired
    private UploadRowRepository uploadRowRepository;

    @Autowired
    private UploadErrorRepository uploadErrorRepository;

    @Autowired
    private FeeLineRepository feeLineRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private InstitutionAdminRepository institutionAdminRepository;

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

    private Student student1;
    private Student student2;
    private Student inactiveStudent;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        uploadRowRepository.deleteAll();
        uploadErrorRepository.deleteAll();
        csvUploadRepository.deleteAll();
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

        // Seed students for schoolA
        student1 = new Student();
        student1.setInstitutionId(schoolA.getId());
        student1.setFullName("Yousef Adel");
        student1.setStudentRef("STU-001");
        student1.setGrade("Grade 10");
        student1.setNationalIdHash("hash1");
        student1.setNationalIdEncrypted("enc1");
        student1.setStatus("Active");
        student1 = studentRepository.save(student1);

        student2 = new Student();
        student2.setInstitutionId(schoolA.getId());
        student2.setFullName("Mariam Tarek");
        student2.setStudentRef("STU-002");
        student2.setGrade("Grade 11");
        student2.setNationalIdHash("hash2");
        student2.setNationalIdEncrypted("enc2");
        student2.setStatus("Active");
        student2 = studentRepository.save(student2);

        inactiveStudent = new Student();
        inactiveStudent.setInstitutionId(schoolA.getId());
        inactiveStudent.setFullName("Karim Deactivated");
        inactiveStudent.setStudentRef("STU-003");
        inactiveStudent.setGrade("Grade 10");
        inactiveStudent.setNationalIdHash("hash3");
        inactiveStudent.setNationalIdEncrypted("enc3");
        inactiveStudent.setStatus("Inactive");
        inactiveStudent = studentRepository.save(inactiveStudent);
    }

    @Test
    void testDownloadTemplate_returnsCsv() throws Exception {
        mockMvc.perform(get("/api/v1/fee-uploads/template")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("fee_upload_template.csv")))
                .andExpect(content().string(containsString("studentRef,feeName,category,amountEGP,term,dueDate")));

        // Test alias path
        mockMvc.perform(get("/fee-upload/template")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(content().string(containsString("studentRef,feeName,category,amountEGP,term,dueDate")));
    }

    @Test
    void testUploadCsv_standardSisFormat_success() throws Exception {
        String csv = """
                studentRef,feeName,category,amountEGP,term,dueDate
                STU-001,Tuition - Term 1 2026/27,Tuition,18000.00,Term 1 2026/27,2026-10-15
                STU-002,Books - Term 1 2026/27,Books,2500.00,Term 1 2026/27,2026-10-15
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "term1_fees.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/fee-uploads")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.uploadId").isNotEmpty())
                .andExpect(jsonPath("$.status").value("Completed"))
                .andReturn();

        List<FeeLine> feeLines = feeLineRepository.findByInstitutionId(schoolA.getId());
        assertEquals(2, feeLines.size());

        FeeLine tuition = feeLines.stream().filter(f -> f.getFeeType() == FeeType.TUITION).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("18000.00").compareTo(tuition.getTotalAmount()));
        assertEquals(student1.getId(), tuition.getStudentId());

        FeeLine books = feeLines.stream().filter(f -> f.getFeeType() == FeeType.BOOKS).findFirst().orElseThrow();
        assertEquals(0, new BigDecimal("2500.00").compareTo(books.getTotalAmount()));
        assertEquals(student2.getId(), books.getStudentId());
    }

    @Test
    void testUploadCsv_legacyFormat_success() throws Exception {
        String csv = """
                National_ID,Fee_Type,Amount,Currency,Collection_Period
                29801011234567,Tuition,15000.00,EGP,Term 2 · 2026
                29802021234568,Bus subscription,4000.00,EGP,Term 2 · 2026
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "legacy_dues.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/fee-upload")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("Completed"));

        List<FeeLine> feeLines = feeLineRepository.findByInstitutionId(schoolA.getId());
        assertEquals(2, feeLines.size());
    }

    @Test
    void testUploadCsv_errorIsolation_badRowsDoNotAbortValidRows() throws Exception {
        // 4 rows: 2 valid, 1 bad amount (-500), 1 bad category
        String csv = """
                studentRef,feeName,category,amountEGP,term,dueDate
                STU-001,Tuition - Term 1,Tuition,18000.00,Term 1,2026-10-15
                STU-002,Books - Term 1,Books,-500.00,Term 1,2026-10-15
                STU-001,Activity - Term 1,InvalidCategory,1200.00,Term 1,2026-10-15
                STU-002,Bus - Term 1,Bus,3000.00,Term 1,2026-10-15
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "mixed_fees.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/fee-uploads")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("Completed with Errors"))
                .andReturn();

        String responseJson = result.getResponse().getContentAsString();
        String uploadId = objectMapper.readTree(responseJson).get("uploadId").asText();

        // 2 valid fee lines were created
        List<FeeLine> fees = feeLineRepository.findByInstitutionId(schoolA.getId());
        assertEquals(2, fees.size());

        // Check GET /fee-uploads/{uploadId}
        mockMvc.perform(get("/api/v1/fee-uploads/" + uploadId)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Completed with Errors"))
                .andExpect(jsonPath("$.totalRows").value(4))
                .andExpect(jsonPath("$.acceptedRows").value(2))
                .andExpect(jsonPath("$.rejectedRows").value(2));

        // Check GET /fee-uploads/{uploadId}/errors
        mockMvc.perform(get("/api/v1/fee-uploads/" + uploadId + "/errors")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].rowNumber").value(2))
                .andExpect(jsonPath("$[0].errorReason").value(containsString("Amount must be greater than 0")))
                .andExpect(jsonPath("$[1].rowNumber").value(3))
                .andExpect(jsonPath("$[1].errorReason").value(containsString("Invalid category")));
    }

    @Test
    void testUpload_unsupportedFileType_rejectsWith400() throws Exception {
        MockMultipartFile badFile = new MockMultipartFile(
                "file", "fees.pdf", "application/pdf", "dummy pdf content".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/fee-uploads")
                        .file(badFile)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value(containsString("unsupported_file_type")));
    }

    @Test
    void testUpload_duplicateUpload_rejectsWith409() throws Exception {
        String csv = """
                studentRef,feeName,category,amountEGP,term,dueDate
                STU-001,Tuition,Tuition,10000.00,Term 1,2026-10-15
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "unique_dues.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        // First upload succeeds
        mockMvc.perform(multipart("/api/v1/fee-uploads")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isAccepted());

        // Duplicate upload fails with 409
        mockMvc.perform(multipart("/api/v1/fee-uploads")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value(containsString("duplicate_upload")));
    }

    @Test
    void testUploadXlsx_success() throws Exception {
        List<List<String>> xlsxRows = List.of(
                List.of("studentRef", "feeName", "category", "amountEGP", "term", "dueDate"),
                List.of("STU-001", "Tuition - XLSX", "Tuition", "15500.00", "Term 1 2026/27", "2026-10-15"),
                List.of("STU-002", "Bus - XLSX", "Bus", "3500.00", "Term 1 2026/27", "2026-10-15")
        );

        byte[] xlsxBytes = XlsxParser.createMinimalXlsx(xlsxRows);

        MockMultipartFile file = new MockMultipartFile(
                "file", "term1_fees.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes
        );

        mockMvc.perform(multipart("/api/v1/fee-uploads")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("Completed"));

        List<FeeLine> fees = feeLineRepository.findByInstitutionId(schoolA.getId());
        assertEquals(2, fees.size());
    }

    @Test
    void testGetUploadRows_filterByStatus() throws Exception {
        String csv = """
                studentRef,feeName,category,amountEGP,term,dueDate
                STU-001,Tuition,Tuition,10000.00,Term 1,2026-10-15
                STU-002,Books,Books,-100.00,Term 1,2026-10-15
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "filter_rows.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/fee-uploads")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isAccepted())
                .andReturn();

        String uploadId = objectMapper.readTree(result.getResponse().getContentAsString()).get("uploadId").asText();

        // All rows
        mockMvc.perform(get("/api/v1/fee-uploads/" + uploadId + "/rows")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)));

        // Accepted only
        mockMvc.perform(get("/api/v1/fee-uploads/" + uploadId + "/rows?status=Accepted")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("Accepted"));

        // Rejected only
        mockMvc.perform(get("/api/v1/fee-uploads/" + uploadId + "/rows?status=Rejected")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].status").value("Rejected"));
    }

    @Test
    void testExportUploadErrorsCsv() throws Exception {
        String csv = """
                studentRef,feeName,category,amountEGP,term,dueDate
                STU-001,Tuition,Tuition,10000.00,Term 1,2026-10-15
                STU-002,Books,Books,-250.00,Term 1,2026-10-15
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "export_err.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/fee-uploads")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isAccepted())
                .andReturn();

        String uploadId = objectMapper.readTree(result.getResponse().getContentAsString()).get("uploadId").asText();

        mockMvc.perform(get("/api/v1/fee-uploads/" + uploadId + "/errors/export?format=csv")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("fee_upload_errors_" + uploadId + ".csv")))
                .andExpect(content().string(containsString("rowNumber,studentRef,feeName,category,amountEGP,dueDate,errorReason")))
                .andExpect(content().string(containsString("STU-002,Books,Books,-250.00")));
    }

    @Test
    void testResubmitUpload_onlyCreatesNewlyValidRows_noDuplicates() throws Exception {
        // Pass 1: 1 valid row (STU-001), 1 bad row (STU-002)
        String pass1Csv = """
                studentRef,feeName,category,amountEGP,term,dueDate
                STU-001,Tuition - Term 1,Tuition,18000.00,Term 1,2026-10-15
                STU-002,Books - Term 1,Books,-500.00,Term 1,2026-10-15
                """;

        MockMultipartFile file1 = new MockMultipartFile(
                "file", "pass1.csv", "text/csv", pass1Csv.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/fee-uploads")
                        .file(file1)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("Completed with Errors"))
                .andReturn();

        String uploadId = objectMapper.readTree(result.getResponse().getContentAsString()).get("uploadId").asText();
        assertEquals(1, feeLineRepository.findByInstitutionId(schoolA.getId()).size());

        // Pass 2 (Resubmit): Correct row 2 to 2500.00, row 1 remains valid
        String pass2Csv = """
                studentRef,feeName,category,amountEGP,term,dueDate
                STU-001,Tuition - Term 1,Tuition,18000.00,Term 1,2026-10-15
                STU-002,Books - Term 1,Books,2500.00,Term 1,2026-10-15
                """;

        MockMultipartFile file2 = new MockMultipartFile(
                "file", "pass2_corrected.csv", "text/csv", pass2Csv.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/fee-uploads/" + uploadId + "/resubmit")
                        .file(file2)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Completed"))
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.acceptedRows").value(2))
                .andExpect(jsonPath("$.rejectedRows").value(0));

        // US-36: Total fee lines must be exactly 2 (STU-001 was NOT duplicated)
        List<FeeLine> feeLines = feeLineRepository.findByInstitutionId(schoolA.getId());
        assertEquals(2, feeLines.size(), "US-36 violated: Resubmission created duplicate fee lines");
    }

    @Test
    void testGetUploadHistory_returnsList() throws Exception {
        String csv = """
                studentRef,feeName,category,amountEGP,term,dueDate
                STU-001,Tuition,Tuition,10000.00,Term 1,2026-10-15
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "history_test.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/fee-uploads")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isAccepted());

        mockMvc.perform(get("/api/v1/fee-uploads")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].fileName").value("history_test.csv"))
                .andExpect(jsonPath("$[0].status").value("Completed"));

        // Test history route alias
        mockMvc.perform(get("/fee-upload/history")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)));
    }

    @Test
    void testCrossSchoolAccess_forbidden() throws Exception {
        String csv = """
                studentRef,feeName,category,amountEGP,term,dueDate
                STU-001,Tuition,Tuition,10000.00,Term 1,2026-10-15
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "school_a.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/fee-uploads")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isAccepted())
                .andReturn();

        String uploadId = objectMapper.readTree(result.getResponse().getContentAsString()).get("uploadId").asText();

        // Admin B from School B tries to access School A's upload
        mockMvc.perform(get("/api/v1/fee-uploads/" + uploadId)
                        .header("Authorization", "Bearer " + tokenAdminB))
                .andExpect(status().isForbidden());
    }

    @Test
    void testInactiveStudent_rejectedInRowIsolation() throws Exception {
        // STU-003 is inactive in setup()
        String csv = """
                studentRef,feeName,category,amountEGP,term,dueDate
                STU-001,Tuition,Tuition,10000.00,Term 1,2026-10-15
                STU-003,Tuition,Tuition,10000.00,Term 1,2026-10-15
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "inactive_student.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        MvcResult result = mockMvc.perform(multipart("/api/v1/fee-uploads")
                        .file(file)
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.status").value("Completed with Errors"))
                .andReturn();

        String uploadId = objectMapper.readTree(result.getResponse().getContentAsString()).get("uploadId").asText();

        mockMvc.perform(get("/api/v1/fee-uploads/" + uploadId + "/errors")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(1)))
                .andExpect(jsonPath("$[0].studentRef").value("STU-003"))
                .andExpect(jsonPath("$[0].errorReason").value(containsString("student_inactive")));
    }
}
