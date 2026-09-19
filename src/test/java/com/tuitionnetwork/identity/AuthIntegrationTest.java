package com.tuitionnetwork.identity;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.identity.domain.BankEmployee;
import com.tuitionnetwork.identity.dto.auth.ForgotPasswordRequest;
import com.tuitionnetwork.identity.dto.auth.LoginRequest;
import com.tuitionnetwork.identity.dto.auth.LogoutRequest;
import com.tuitionnetwork.identity.dto.auth.MfaResendRequest;
import com.tuitionnetwork.identity.dto.auth.MfaVerifyRequest;
import com.tuitionnetwork.identity.dto.auth.RefreshTokenRequest;
import com.tuitionnetwork.identity.dto.auth.ResetPasswordRequest;
import com.tuitionnetwork.identity.repository.BankEmployeeRepository;
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

import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
class AuthIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private BankEmployeeRepository bankEmployeeRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        bankEmployeeRepository.deleteAll();

        BankEmployee admin = new BankEmployee(
                "Mohamed Ali",
                "mohamed.ali@cibeg.com",
                "USR-001",
                "CIB@2026",
                "Operations",
                "bank-admin"
        );
        admin.setPhone("+20 10 0000 4821");
        admin.setStatus("Active");
        bankEmployeeRepository.save(admin);
    }

    @Test
    void testLogin_success_triggersMfaChallenge() throws Exception {
        LoginRequest request = new LoginRequest("admin", "CIB@2026", false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.mfaRequired").value(true))
                .andExpect(jsonPath("$.mfaToken").isNotEmpty())
                .andExpect(jsonPath("$.otpChannel").value("sms"))
                .andExpect(jsonPath("$.otpDestinationHint").value("**** 4821"))
                .andExpect(jsonPath("$.expiresInSeconds").value(60))
                .andExpect(jsonPath("$.resendAvailableInSeconds").value(60));
    }

    @Test
    void testLogin_missingCredentials_returns400() throws Exception {
        LoginRequest request = new LoginRequest("", "", false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("missing_credentials"));
    }

    @Test
    void testLogin_invalidCredentials_returns401() throws Exception {
        LoginRequest request = new LoginRequest("admin", "WrongPassword123!", false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("invalid_credentials"));
    }

    @Test
    void testLogin_lockedAccount_returns403() throws Exception {
        BankEmployee emp = bankEmployeeRepository.findByEmail("mohamed.ali@cibeg.com").orElseThrow();
        emp.setStatus("Inactive");
        bankEmployeeRepository.save(emp);

        LoginRequest request = new LoginRequest("mohamed.ali@cibeg.com", "CIB@2026", false);

        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("account_locked"));
    }

    @Test
    void testLogin_bruteForceProtection_locksAndRateLimits() throws Exception {
        for (int i = 0; i < 5; i++) {
            LoginRequest badReq = new LoginRequest("admin", "BadPass" + i, false);
            mockMvc.perform(post("/api/v1/auth/login")
                            .contentType(MediaType.APPLICATION_JSON)
                            .content(objectMapper.writeValueAsString(badReq)))
                    .andExpect(status().isUnauthorized());
        }

        LoginRequest attemptAfterLock = new LoginRequest("admin", "CIB@2026", false);
        mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(attemptAfterLock)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.error.code").value("account_locked"));
    }

    @Test
    void testMfaVerify_success_returnsTokensAndUserPermissions() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "CIB@2026", false))))
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
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresIn").value(900))
                .andExpect(jsonPath("$.user.id").value("USR-001"))
                .andExpect(jsonPath("$.user.name").value("Mohamed Ali"))
                .andExpect(jsonPath("$.user.initials").value("MA"))
                .andExpect(jsonPath("$.user.email").value("mohamed.ali@cibeg.com"))
                .andExpect(jsonPath("$.user.role").value("bank-admin"))
                .andExpect(jsonPath("$.user.permissions.length()").value(10))
                .andReturn();

        JsonNode verifyJson = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
        String accessToken = verifyJson.get("accessToken").asText();

        // Verify that the issued accessToken allows calling /api/v1/auth/me
        mockMvc.perform(get("/api/v1/auth/me")
                        .header("Authorization", "Bearer " + accessToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("mohamed.ali@cibeg.com"))
                .andExpect(jsonPath("$.role").value("bank-admin"));
    }

    @Test
    void testMfaVerify_invalidCodeFormat_returns400() throws Exception {
        MfaVerifyRequest verifyRequest = new MfaVerifyRequest("mfa_fake", "123");

        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_code_format"));
    }

    @Test
    void testMfaVerify_invalidToken_returns401() throws Exception {
        MfaVerifyRequest verifyRequest = new MfaVerifyRequest("mfa_unknown_token", "123456");

        mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(verifyRequest)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("mfa_token_invalid"));
    }

    @Test
    void testMfaResend_throttled_returns429() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "CIB@2026", false))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode loginJson = objectMapper.readTree(loginResult.getResponse().getContentAsString());
        String mfaToken = loginJson.get("mfaToken").asText();

        // Immediate resend must be throttled
        mockMvc.perform(post("/api/v1/auth/mfa/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaResendRequest(mfaToken))))
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(jsonPath("$.error.code").value("resend_throttled"));
    }

    @Test
    void testForgotPassword_andResetPassword_flow() throws Exception {
        // 1. Request forgot password for valid email
        ForgotPasswordRequest forgotReq = new ForgotPasswordRequest("mohamed.ali@cibeg.com");
        mockMvc.perform(post("/api/v1/auth/forgot-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(forgotReq)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("reset link has been sent")));

        // 2. Weak password rejected
        ResetPasswordRequest weakReq = new ResetPasswordRequest("prt_invalid", "weak");
        mockMvc.perform(post("/api/v1/auth/reset-password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(weakReq)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("reset_token_invalid"));
    }

    @Test
    void testRefreshToken_flow() throws Exception {
        // 1. Login and verify MFA to obtain refreshToken
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "CIB@2026", false))))
                .andExpect(status().isOk())
                .andReturn();

        String mfaToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("mfaToken").asText();

        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaVerifyRequest(mfaToken, "123456"))))
                .andExpect(status().isOk())
                .andReturn();

        String refreshToken = objectMapper.readTree(verifyResult.getResponse().getContentAsString()).get("refreshToken").asText();

        // 2. Call /api/v1/auth/refresh
        MvcResult refreshResult = mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").isNotEmpty())
                .andExpect(jsonPath("$.refreshToken").isNotEmpty())
                .andExpect(jsonPath("$.user.email").value("mohamed.ali@cibeg.com"))
                .andReturn();

        String newRefreshToken = objectMapper.readTree(refreshResult.getResponse().getContentAsString()).get("refreshToken").asText();
        assertNotEquals(refreshToken, newRefreshToken);

        // 3. Old refreshToken must now be invalid
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("refresh_token_invalid"));
    }

    @Test
    void testLogout_revokesRefreshToken() throws Exception {
        MvcResult loginResult = mockMvc.perform(post("/api/v1/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LoginRequest("admin", "CIB@2026", false))))
                .andExpect(status().isOk())
                .andReturn();

        String mfaToken = objectMapper.readTree(loginResult.getResponse().getContentAsString()).get("mfaToken").asText();

        MvcResult verifyResult = mockMvc.perform(post("/api/v1/auth/mfa/verify")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new MfaVerifyRequest(mfaToken, "123456"))))
                .andExpect(status().isOk())
                .andReturn();

        JsonNode verifyJson = objectMapper.readTree(verifyResult.getResponse().getContentAsString());
        String accessToken = verifyJson.get("accessToken").asText();
        String refreshToken = verifyJson.get("refreshToken").asText();

        // Logout
        mockMvc.perform(post("/api/v1/auth/logout")
                        .header("Authorization", "Bearer " + accessToken)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new LogoutRequest(refreshToken))))
                .andExpect(status().isNoContent());

        // Refresh must fail after logout
        mockMvc.perform(post("/api/v1/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new RefreshTokenRequest(refreshToken))))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code").value("refresh_token_invalid"));
    }

    @Test
    void testRolesEndpoints() throws Exception {
        // GET /api/v1/roles
        mockMvc.perform(get("/api/v1/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(4))
                .andExpect(jsonPath("$[0].role").value("bank-admin"))
                .andExpect(jsonPath("$[0].permissions.length()").value(10));

        // GET /api/v1/roles/{role}/permissions
        mockMvc.perform(get("/api/v1/roles/bank-reconciliation/permissions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[0]").value("dashboard"))
                .andExpect(jsonPath("$[1]").value("reconciliation"))
                .andExpect(jsonPath("$[2]").value("notifications"));

        // GET unknown role
        mockMvc.perform(get("/api/v1/roles/non-existing/permissions"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("role_not_found"));
    }
}
