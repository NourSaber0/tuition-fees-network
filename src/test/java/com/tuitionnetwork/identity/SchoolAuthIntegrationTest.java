package com.tuitionnetwork.identity;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.identity.domain.AccountStatus;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.InstitutionAdmin;
import com.tuitionnetwork.identity.dto.auth.LoginRequest;
import com.tuitionnetwork.identity.dto.auth.LogoutRequest;
import com.tuitionnetwork.identity.dto.auth.MfaVerifyRequest;
import com.tuitionnetwork.identity.dto.auth.RefreshTokenRequest;
import com.tuitionnetwork.identity.dto.auth.TrustDeviceRequest;
import com.tuitionnetwork.identity.repository.InstitutionAdminRepository;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
class SchoolAuthIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private InstitutionAdminRepository institutionAdminRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Institution testSchool;
    private InstitutionAdmin schoolAdmin;
    private InstitutionAdmin schoolFinance;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        institutionAdminRepository.deleteAll();
        institutionRepository.deleteAll();

        testSchool = new Institution(
                "Cairo International School",
                "SCH-001",
                "SCHOOL_ABSORBS"
        );
        testSchool.setEmail("info@cis.edu.eg");
        testSchool.setAccountStatus(AccountStatus.ACTIVE);
        testSchool = institutionRepository.save(testSchool);

        schoolAdmin = new InstitutionAdmin(
                testSchool.getId(),
                "Amr Hassan",
                "amr.hassan@cis.edu.eg",
                "Password123!",
                "School Admin"
        );
        schoolAdmin.setPhone("+20 10 9999 8888");
        schoolAdmin.setStatus("Active");
        schoolAdmin.setCreatedAt(LocalDateTime.now());
        schoolAdmin = institutionAdminRepository.save(schoolAdmin);

        schoolFinance = new InstitutionAdmin(
                testSchool.getId(),
                "Dina Fouad",
                "dina.fouad@cis.edu.eg",
                "Finance@2026",
                "School Finance"
        );
        schoolFinance.setPhone("+20 11 2222 3333");
        schoolFinance.setStatus("Active");
        schoolFinance.setCreatedAt(LocalDateTime.now());
        schoolFinance = institutionAdminRepository.save(schoolFinance);
    }

    @Test
    void testSchoolAdmin_login_triggersMfaChallenge() throws Exception {
        LoginRequest request = new LoginRequest("amr.hassan@cis.edu.eg", "Password123!", false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.mfaToken").isNotEmpty())
                .andExpect(jsonPath("$.otpChannel").value("sms"))
                .andExpect(jsonPath("$.otpDestinationHint").value("**** 8888"))
                .andExpect(jsonPath("$.expiresInSeconds").value(60));
    }

    @Test
    void testSchoolFinance_login_triggersMfaChallenge() throws Exception {
        LoginRequest request = new LoginRequest("dina.fouad@cis.edu.eg", "Finance@2026", false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.mfaToken").isNotEmpty())
                .andExpect(jsonPath("$.otpChannel").value("sms"))
                .andExpect(jsonPath("$.otpDestinationHint").value("**** 3333"));
    }

    @Test
    void testSchoolUser_verifyMfa_returnsSchoolTokensAndUserDtoWithSchoolId() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("amr.hassan@cis.edu.eg", "Password123!", false))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String mfaToken = loginJson.get("mfaToken").asText();

        MfaVerifyRequest verifyRequest = new MfaVerifyRequest(mfaToken, "123456");

        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.name").value("Amr Hassan"))
                .andExpect(jsonPath("$.user.email").value("amr.hassan@cis.edu.eg"))
                .andExpect(jsonPath("$.user.role").value("school-admin"))
                .andExpect(jsonPath("$.user.schoolId").value(testSchool.getCode()))
                .andExpect(jsonPath("$.user.schoolName").value("Cairo International School"))
                .andReturn();

        JsonNode verifyJson = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
        String accessToken = verifyJson.get("accessToken").asText();

        // Check /auth/me with the issued school token
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("amr.hassan@cis.edu.eg"))
                .andExpect(jsonPath("$.schoolId").value(testSchool.getCode()))
                .andExpect(jsonPath("$.schoolName").value("Cairo International School"))
                .andExpect(jsonPath("$.role").value("school-admin"));
    }

    @Test
    void testSchoolUser_trustDevice_returnsSuccess() throws Exception {
        TrustDeviceRequest request = new TrustDeviceRequest("mfa_fake_token_123");

        mockMvc.perform(post("/api/v1/auth/mfa/trust-device")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Device trusted successfully"));
    }

    @Test
    void testSchoolUser_inactiveAccount_isBlocked() throws Exception {
        schoolAdmin.setStatus("Inactive");
        institutionAdminRepository.save(schoolAdmin);

        LoginRequest request = new LoginRequest("amr.hassan@cis.edu.eg", "Password123!", false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("account_locked"));
    }

    @Test
    void testSchoolUser_suspendedSchool_isBlocked() throws Exception {
        testSchool.setAccountStatus(AccountStatus.SUSPENDED);
        institutionRepository.save(testSchool);

        LoginRequest request = new LoginRequest("amr.hassan@cis.edu.eg", "Password123!", false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("account_deactivated"));
    }

    @Test
    void testSchoolUser_invalidPassword_incrementsFailedAttemptsAndLocksAtThreshold() throws Exception {
        LoginRequest wrongPw = new LoginRequest("amr.hassan@cis.edu.eg", "WrongPassword!", false);

        for (int i = 1; i <= 4; i++) {
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(wrongPw)))
                    .andExpect(status().isUnauthorized())
                    .andExpect(jsonPath("$.error.code").value("invalid_credentials"));

            InstitutionAdmin updated = institutionAdminRepository.findById(schoolAdmin.getId()).orElseThrow();
            assertEquals(i, updated.getFailedLoginAttempts());
        }

        // 5th attempt locks the account
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPw)))
                .andExpect(status().isUnauthorized());

        InstitutionAdmin lockedAdmin = institutionAdminRepository.findById(schoolAdmin.getId()).orElseThrow();
        assertEquals(5, lockedAdmin.getFailedLoginAttempts());
        assertTrue(lockedAdmin.isAccountLocked());

        // Subsequent attempt returns 429 rate_limited or 403 account_locked
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(wrongPw)))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testSchoolUser_refreshTokenAndLogout() throws Exception {
        // 1. Login
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("amr.hassan@cis.edu.eg", "Password123!", false))))
                .andExpect(status().isOk())
                .andReturn();

        String mfaToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("mfaToken").asText();

        // 2. Verify MFA
        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaVerifyRequest(mfaToken, "123456"))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode verifyJson = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
        String accessToken = verifyJson.get("accessToken").asText();
        String refreshToken = verifyJson.get("refreshToken").asText();

        // 3. Refresh Token
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.schoolId").value(testSchool.getCode()))
                .andReturn();

        String newRefreshToken = objectMapper.readTree(refreshResult.getResponse().getContentAsString()).get("refreshToken").asText();

        // 4. Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest(newRefreshToken))))
                .andExpect(status().isNoContent());

        // 5. Trying to refresh again with revoked token fails with 401
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(newRefreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("refresh_token_invalid"));
    }
}
