package com.tuitionnetwork.ingestion;

import com.example.demo.DemoApplication;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.ingestion.domain.UploadError;
import com.tuitionnetwork.ingestion.repository.CsvUploadRepository;
import com.tuitionnetwork.ingestion.repository.UploadErrorRepository;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.payments.repository.ReceiptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
class CsvIngestionIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private FeeLineRepository feeLineRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private ReceiptRepository receiptRepository;

    @Autowired
    private UploadErrorRepository uploadErrorRepository;

    @Autowired
    private CsvUploadRepository csvUploadRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private com.tuitionnetwork.identity.repository.InstitutionAdminRepository institutionAdminRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private Institution institution;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        uploadErrorRepository.deleteAll();
        csvUploadRepository.deleteAll();
        receiptRepository.deleteAll();
        jdbcTemplate.execute("DELETE FROM payment_allocation");
        jdbcTemplate.execute("DELETE FROM payment_state_log");
        jdbcTemplate.execute("DELETE FROM epp_installment");
        jdbcTemplate.execute("DELETE FROM epp_schedule");
        paymentRepository.deleteAll();
        feeLineRepository.deleteAll();

        institution = institutionRepository.save(new Institution("Integration School", "INT-" + UUID.randomUUID(), "POLICY_INT"));
    }

    @Test
    @WithMockUser(username = "admin@school.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void testCsvUpload_isolatesBadRows() throws Exception {
        // 4 rows: 1 valid, 1 negative amount, 1 invalid fee type, 1 past collection period
        String csv = """
                National_ID,Fee_Type,Amount,Currency,Collection_Period
                29801011234567,Tuition,15000.00,EGP,Term 2 · 2026
                29802021234568,Tuition,-500.00,EGP,Term 2 · 2026
                29803031234569,InvalidFeeType,5000.00,EGP,Term 2 · 2026
                29804041234570,Activities,2000.00,EGP,Term 1 · 2023
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "mixed_dues.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/institutions/{id}/dues/upload", institution.getId())
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(4))
                .andExpect(jsonPath("$.successfulRows").value(1))
                .andExpect(jsonPath("$.failedRows").value(3));

        // Assert exactly 1 FEE_LINE is saved in the database
        List<FeeLine> feeLines = feeLineRepository.findAll();
        assertEquals(1, feeLines.size());

        // Assert exactly 3 UPLOAD_ERROR records are saved in the database with row numbers and messages
        List<UploadError> uploadErrors = uploadErrorRepository.findAll();
        assertEquals(3, uploadErrors.size());

        assertEquals(3, uploadErrors.get(0).getRowNumber());
        assertTrue(uploadErrors.get(0).getErrorMessage().contains("Amount must be greater than 0"));

        assertEquals(4, uploadErrors.get(1).getRowNumber());
        assertTrue(uploadErrors.get(1).getErrorMessage().contains("Invalid Fee Type"));

        assertEquals(5, uploadErrors.get(2).getRowNumber());
        assertTrue(uploadErrors.get(2).getErrorMessage().contains("Collection period is in the past"));
    }

    @Test
    @WithMockUser(username = "admin@school.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void testCsvUpload_isIdempotent() throws Exception {
        String csv = """
                National_ID,Fee_Type,Amount,Currency,Collection_Period
                29801011234567,Tuition,15000.00,EGP,Term 2 · 2026
                29802021234568,Bus subscription,4000.00,EGP,Term 2 · 2026
                """;

        MockMultipartFile file1 = new MockMultipartFile(
                "file", "valid_dues.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        // Upload first time
        mockMvc.perform(multipart("/api/v1/institutions/{id}/dues/upload", institution.getId())
                        .file(file1))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.successfulRows").value(2));

        assertEquals(2, feeLineRepository.count());

        // Upload exact same file second time
        MockMultipartFile file2 = new MockMultipartFile(
                "file", "valid_dues.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/institutions/{id}/dues/upload", institution.getId())
                        .file(file2))
                .andExpect(status().isOk());

        // Assert exactly 2 fee lines exist (0 new FEE_LINE records created)
        assertEquals(2, feeLineRepository.count());
    }

    @Test
    @WithMockUser(username = "admin@school.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void testCsvUpload_rejectsDuplicateNationalIds() throws Exception {
        String csv = """
                National_ID,Fee_Type,Amount,Currency,Collection_Period
                29801011234567,Tuition,15000.00,EGP,Term 2 · 2026
                29801011234567,Tuition,15000.00,EGP,Term 2 · 2026
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "duplicate_dues.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/institutions/{id}/dues/upload", institution.getId())
                        .file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalRows").value(2))
                .andExpect(jsonPath("$.successfulRows").value(1))
                .andExpect(jsonPath("$.failedRows").value(1));

        // Assert exactly 1 FEE_LINE is created
        assertEquals(1, feeLineRepository.count());

        // Assert the duplicate row was rejected into UPLOAD_ERROR
        List<UploadError> uploadErrors = uploadErrorRepository.findAll();
        assertEquals(1, uploadErrors.size());
        assertEquals(3, uploadErrors.get(0).getRowNumber());
        assertTrue(uploadErrors.get(0).getErrorMessage().contains("Duplicate National ID"));
    }

    @Test
    @WithMockUser(username = "admin@other-school.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void testCsvUpload_crossInstitutionAccess_rejectedWith422() throws Exception {
        UUID otherSchoolId = UUID.randomUUID();
        com.tuitionnetwork.identity.domain.InstitutionAdmin admin = new com.tuitionnetwork.identity.domain.InstitutionAdmin(
                otherSchoolId, "Admin Other", "admin@other-school.edu.eg", "hash123", "ADMIN"
        );
        institutionAdminRepository.save(admin);

        String csv = """
                National_ID,Fee_Type,Amount,Currency,Collection_Period
                29801011234567,Tuition,15000.00,EGP,Term 2 · 2026
                """;

        MockMultipartFile file = new MockMultipartFile(
                "file", "dues.csv", "text/csv", csv.getBytes(StandardCharsets.UTF_8)
        );

        // Attempt upload to institution.getId() which does not match otherSchoolId
        mockMvc.perform(multipart("/api/v1/institutions/{id}/dues/upload", institution.getId())
                        .file(file))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PENDING_BUSINESS_RULE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cross-Institution Data Bleed is strictly prohibited")));
    }
}
