package com.tuitionnetwork.audit;

import com.example.demo.DemoApplication;
import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.identity.domain.Guardian;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.GuardianRepository;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.identity.service.IdentityResolverService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
class PrivacyAuditIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private AuditLogRepository auditLogRepository;

    @Autowired
    private GuardianRepository guardianRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private IdentityResolverService identityResolverService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        auditLogRepository.deleteAll();
        guardianRepository.deleteAll();
        studentRepository.deleteAll();
    }

    @Test
    @WithMockUser(username = "emp-101@cibeg.com", roles = {"BACK_OFFICE"})
    void testNationalIdSearch_notFound_stillLogsAudit() throws Exception {
        String nonExistentNationalId = "99999999999999";
        String expectedHmac = identityResolverService.computeHmacSha256(nonExistentNationalId);

        // Perform search for non-existent national ID -> returns 404
        mockMvc.perform(get("/api/v1/guardian/dues")
                        .header("X-Guardian-National-Id", nonExistentNationalId))
                .andExpect(status().isNotFound());

        // Assert AUDIT_LOG record was saved
        List<AuditLog> auditLogs = auditLogRepository.findAll();
        assertFalse(auditLogs.isEmpty(), "An AUDIT_LOG record must be saved even if guardian not found");

        AuditLog log = auditLogs.get(auditLogs.size() - 1);
        assertEquals("BANK_EMPLOYEE", log.getActorType());
        assertEquals("SEARCH_NATIONAL_ID", log.getAction());
        assertEquals(expectedHmac, log.getTargetResource());
        assertFalse(log.getTargetResource().contains(nonExistentNationalId), "Plaintext National ID must never be logged");
    }

    @Test
    @WithMockUser(username = "emp-202@cibeg.com", roles = {"BACK_OFFICE"})
    void testNationalIdSearch_neverLogsPlaintextId() throws Exception {
        String rawNationalId = "29001011234567";
        String expectedHmac = identityResolverService.computeHmacSha256(rawNationalId);

        // Pre-seed guardian in DB
        Guardian guardian = new Guardian(expectedHmac, "enc-data", "Ahmed Saber", "ahmed@example.com", "+201012345678", "hash", true);
        Guardian savedGuardian = guardianRepository.save(guardian);

        Institution institution = institutionRepository.save(new Institution("Nile International School", "NILE-99", "PAID"));
        studentRepository.save(new Student(savedGuardian.getId(), institution.getId(), "std-hmac", "std-enc", "Sara Ahmed", LocalDate.now()));

        mockMvc.perform(get("/api/v1/guardian/dues")
                        .header("X-Guardian-National-Id", rawNationalId))
                .andExpect(status().isOk());

        // Assert AUDIT_LOG strictly contains HMAC hash and not the plaintext 14-digit National ID
        List<AuditLog> logs = auditLogRepository.findAll();
        assertFalse(logs.isEmpty());

        boolean foundMatchingAudit = false;
        for (AuditLog audit : logs) {
            if ("SEARCH_NATIONAL_ID".equals(audit.getAction())) {
                assertEquals(expectedHmac, audit.getTargetResource());
                assertFalse(audit.getTargetResource().contains(rawNationalId), "Must strictly NOT contain plaintext 14-digit National ID");
                foundMatchingAudit = true;
            }
        }
        assertTrue(foundMatchingAudit, "Must find matching SEARCH_NATIONAL_ID audit record");
    }
}
