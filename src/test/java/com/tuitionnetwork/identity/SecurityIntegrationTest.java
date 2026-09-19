package com.tuitionnetwork.identity;

import com.example.demo.DemoApplication;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
class SecurityIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Autowired
    private com.tuitionnetwork.identity.security.JwtTokenProvider jwtTokenProvider;

    @Test
    @WithMockUser(username = "admin@nile.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void testInstitutionAdmin_cannotAccessGlobalSearch() throws Exception {
        mockMvc.perform(get("/api/v1/guardian/dues")
                        .header("X-Guardian-National-Id", "29001011234567"))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "emp@cibeg.com", roles = {"BACK_OFFICE"})
    void testBankEmployee_cannotUploadInstitutionFiles() throws Exception {
        UUID institutionId = UUID.randomUUID();
        MockMultipartFile file = new MockMultipartFile(
                "file", "dues.csv", "text/csv",
                "National_ID,Fee_Type,Amount,Currency,Collection_Period\n29801011234567,Tuition,15000.00,EGP,Term 2 · 2026\n".getBytes(StandardCharsets.UTF_8)
        );

        mockMvc.perform(multipart("/api/v1/institutions/{id}/dues/upload", institutionId)
                        .file(file))
                .andExpect(status().isForbidden());
    }

    @Test
    void testUnauthenticatedUser_isRejected() throws Exception {
        mockMvc.perform(get("/api/v1/guardian/dues")
                        .header("X-Guardian-National-Id", "29001011234567"))
                .andExpect(status().isUnauthorized());
    }

    @Autowired
    private com.tuitionnetwork.identity.repository.GuardianRepository guardianRepository;

    @Autowired
    private com.tuitionnetwork.identity.service.IdentityResolverService identityResolverService;

    @org.junit.jupiter.api.AfterEach
    void tearDown() {
        if (guardianRepository != null) {
            guardianRepository.deleteAll();
        }
    }

    @Test
    void testJwtBearerToken_bankEmployee_canAccessGlobalSearch() throws Exception {
        String nationalId = "29009998887776";
        String hmac = identityResolverService.computeHmacSha256(nationalId);
        com.tuitionnetwork.identity.domain.Guardian guardian =
                new com.tuitionnetwork.identity.domain.Guardian(hmac, "enc", "Ahmed Unique", "ahmed.unique@example.com", "01000000000", null, true);
        guardianRepository.save(guardian);

        com.tuitionnetwork.identity.security.SecurityUserPrincipal principal =
                new com.tuitionnetwork.identity.security.SecurityUserPrincipal(
                        UUID.randomUUID(), "emp@cibeg.com", "emp@cibeg.com", "ROLE_BACK_OFFICE"
                );
        String token = jwtTokenProvider.generateToken(principal);

        mockMvc.perform(get("/api/v1/guardian/dues")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Guardian-National-Id", nationalId))
                .andExpect(status().isOk());
    }

    @Test
    void testJwtBearerToken_institutionAdmin_forbiddenGlobalSearch() throws Exception {
        com.tuitionnetwork.identity.security.SecurityUserPrincipal principal =
                new com.tuitionnetwork.identity.security.SecurityUserPrincipal(
                        UUID.randomUUID(), "admin@nile.edu.eg", "admin@nile.edu.eg", "ROLE_INSTITUTION_ADMIN"
                );
        String token = jwtTokenProvider.generateToken(principal);

        mockMvc.perform(get("/api/v1/guardian/dues")
                        .header("Authorization", "Bearer " + token)
                        .header("X-Guardian-National-Id", "29001011234567"))
                .andExpect(status().isForbidden());
    }

    @Test
    void testJwtBearerToken_invalidSignature_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/guardian/dues")
                        .header("Authorization", "Bearer invalid.token.signature")
                        .header("X-Guardian-National-Id", "29001011234567"))
                .andExpect(status().isUnauthorized());
    }
}
