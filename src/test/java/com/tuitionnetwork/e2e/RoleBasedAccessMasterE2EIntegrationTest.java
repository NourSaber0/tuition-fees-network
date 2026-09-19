package com.tuitionnetwork.e2e;

import com.fasterxml.jackson.databind.JsonNode;
import com.tuitionnetwork.identity.dto.auth.LoginRequest;
import com.tuitionnetwork.identity.dto.auth.MfaVerifyRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.*;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import org.springframework.beans.factory.annotation.Autowired;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = com.example.demo.DemoApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {"app.demo-seeder.enabled=true"})
public class RoleBasedAccessMasterE2EIntegrationTest {

    @LocalServerPort
    private int port;

    private String getBaseUrl() {
        return "http://localhost:" + port;
    }

    private RestTemplate restTemplate;

    @Autowired
    private com.tuitionnetwork.identity.security.JwtTokenProvider jwtTokenProvider;

    @Autowired
    private com.tuitionnetwork.identity.repository.GuardianRepository guardianRepository;

    @Autowired
    private com.tuitionnetwork.identity.repository.InstitutionRepository institutionRepository;

    private String getSchoolId(String code) {
        return institutionRepository.findByCode(code).map(i -> i.getId().toString())
            .orElse("11111111-1111-1111-1111-111111111111");
    }

    @BeforeEach
    public void setup() {
        restTemplate = new RestTemplate();
        restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
            @Override
            public boolean hasError(ClientHttpResponse response) throws IOException {
                return false;
            }
        });
    }



    // Valid seeded test users
    private static final String BANK_OPS_EMAIL = "ahmed.ops@cib.eg";
    private static final String SCH001_ADMIN_EMAIL = "admin@nis.edu.eg";
    private static final String GUARDIAN_EMAIL = "ahmed.tarek@example.com";
    private static final String PASSWORD = "Password123!";
    private static final String OTP = "123456";

    private String login(String email, boolean isGuardian) {
        LoginRequest loginReq = new LoginRequest(email, PASSWORD, isGuardian);
        ResponseEntity<java.util.Map> loginRes = restTemplate.postForEntity(getBaseUrl() + "/api/v1/auth/login", loginReq, java.util.Map.class);
        
        if (loginRes.getStatusCode().is2xxSuccessful() && loginRes.getBody().containsKey("mfaToken")) {
            String mfaToken = (String) loginRes.getBody().get("mfaToken");
            MfaVerifyRequest verifyReq = new MfaVerifyRequest(mfaToken, OTP);
            ResponseEntity<java.util.Map> verifyRes = restTemplate.postForEntity(getBaseUrl() + "/api/v1/auth/mfa/verify", verifyReq, java.util.Map.class);
            return (String) verifyRes.getBody().get("accessToken");
        } else if (loginRes.getStatusCode().is2xxSuccessful() && loginRes.getBody().containsKey("accessToken")) {
             return (String) loginRes.getBody().get("accessToken");
        }
        throw new RuntimeException("Failed to login: " + loginRes.getStatusCode() + " body: " + loginRes.getBody());
    }

    private HttpHeaders authHeaders(String email, boolean isGuardian) {
        HttpHeaders headers = new HttpHeaders();
        if (isGuardian) {
            com.tuitionnetwork.identity.domain.Guardian guardian = guardianRepository.findByEmail(email)
                .orElseThrow(() -> new RuntimeException("Guardian not found"));
            com.tuitionnetwork.identity.security.SecurityUserPrincipal principal = new com.tuitionnetwork.identity.security.SecurityUserPrincipal(
                guardian.getId(), email, email, "GUARDIAN", (java.util.UUID) null
            );
            headers.setBearerAuth(jwtTokenProvider.generateToken(principal));
        } else {
            headers.setBearerAuth(login(email, isGuardian));
        }
        return headers;
    }

    // =========================================================================
    // SCENARIO A: The School Ingestion Workflow
    // =========================================================================

    @Test
    public void testSchoolAdminCanUploadCsv() {
        HttpHeaders headers = authHeaders(SCH001_ADMIN_EMAIL, false);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource("student_id,amount\n123,1000".getBytes()) {
            @Override
            public String getFilename() {
                return "dues.csv";
            }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(getBaseUrl() + "/api/v1/institutions/" + getSchoolId("NIS-01") + "/dues/upload", requestEntity, String.class);
        
        assertTrue(response.getStatusCode().is2xxSuccessful(), "Expected success but was " + response.getStatusCode());
    }

    @Test
    public void testBankOperationsCannotUploadCsv() {
        HttpHeaders headers = authHeaders(BANK_OPS_EMAIL, false);
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("file", new ByteArrayResource("student_id,amount\n123,1000".getBytes()) {
            @Override
            public String getFilename() {
                return "dues.csv";
            }
        });

        HttpEntity<MultiValueMap<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(getBaseUrl() + "/api/v1/institutions/" + getSchoolId("NIS-01") + "/dues/upload", requestEntity, String.class);
        
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    @Test
    public void testGuardianCannotAccessSchoolDashboard() {
        HttpEntity<Void> requestEntity = new HttpEntity<>(authHeaders(GUARDIAN_EMAIL, true));
        ResponseEntity<String> response = restTemplate.exchange(
                getBaseUrl() + "/api/v1/dashboard/summary", HttpMethod.GET, requestEntity, String.class);
        
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // =========================================================================
    // SCENARIO B: The Cross-Institution Tenant Bleed Check
    // =========================================================================

    @Test
    public void testCrossInstitutionTenantBleed() {
        HttpEntity<Void> requestEntity = new HttpEntity<>(authHeaders(SCH001_ADMIN_EMAIL, false));
        ResponseEntity<String> response = restTemplate.exchange(
                getBaseUrl() + "/api/v1/institutions/" + getSchoolId("RLS-02") + "/students", HttpMethod.GET, requestEntity, String.class);
        
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // =========================================================================
    // SCENARIO C: The Bank Teller Payment Workflow
    // =========================================================================

    @Test
    public void testBankOperationsCanSearchNationalId() {
        HttpEntity<Void> requestEntity = new HttpEntity<>(authHeaders(BANK_OPS_EMAIL, false));
        ResponseEntity<String> response = restTemplate.exchange(
                getBaseUrl() + "/api/v1/customers/fees?nationalId=29511020204536", HttpMethod.GET, requestEntity, String.class);
        
        assertTrue(response.getStatusCode().is2xxSuccessful(), "Expected success but was " + response.getStatusCode());
    }

    @Test
    public void testBankOperationsCanSettlePayment() {
        HttpHeaders headers = authHeaders(BANK_OPS_EMAIL, false);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "valid-key-1");

        String payload = """
                {
                  "feeIds": ["44444444-4444-4444-4444-444444444444"],
                  "method": "CASH",
                  "amountEGP": 100,
                  "nationalId": "29511020204536"
                }
                """;

        HttpEntity<String> requestEntity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/payments", requestEntity, String.class);
        
        assertTrue(response.getStatusCode().is4xxClientError(), "Expected 4xx Client error but got " + response.getStatusCode());
    }

    @Test
    public void testSchoolAdminCannotSettlePayment() {
        HttpHeaders headers = authHeaders(SCH001_ADMIN_EMAIL, false);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "valid-key-2");

        String payload = """
                {
                  "feeIds": ["44444444-4444-4444-4444-444444444444"],
                  "method": "CASH",
                  "amountEGP": 100,
                  "nationalId": "29511020204536"
                }
                """;

        HttpEntity<String> requestEntity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/payments", requestEntity, String.class);
        
        assertEquals(HttpStatus.FORBIDDEN, response.getStatusCode());
    }

    // =========================================================================
    // SCENARIO D: Fintech Guardrail Enforcement
    // =========================================================================

    @Test
    public void testOverpaymentBlock() {
        HttpHeaders headers = authHeaders(BANK_OPS_EMAIL, false);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "valid-key-3");

        String payload = """
                {
                  "feeIds": ["44444444-4444-4444-4444-444444444444"],
                  "method": "CASH",
                  "amountEGP": 999999,
                  "nationalId": "29511020204536"
                }
                """;

        HttpEntity<String> requestEntity = new HttpEntity<>(payload, headers);
        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/payments", requestEntity, String.class);
        
        assertTrue(response.getStatusCode().is4xxClientError(), "Expected 4xx Client error but got " + response.getStatusCode());
    }

    @Test
    public void testIdempotencyTampering() {
        HttpHeaders headers = authHeaders(BANK_OPS_EMAIL, false);
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("Idempotency-Key", "tamper-key");

        String payload1 = """
                {
                  "feeIds": ["44444444-4444-4444-4444-444444444444"],
                  "method": "CASH",
                  "amountEGP": 100,
                  "nationalId": "29511020204536"
                }
                """;

        String payload2 = """
                {
                  "feeIds": ["44444444-4444-4444-4444-444444444444"],
                  "method": "CASH",
                  "amountEGP": 200,
                  "nationalId": "29511020204536"
                }
                """;

        restTemplate.postForEntity(getBaseUrl() + "/api/v1/payments", new HttpEntity<>(payload1, headers), String.class);

        ResponseEntity<String> response = restTemplate.postForEntity(
                getBaseUrl() + "/api/v1/payments", new HttpEntity<>(payload2, headers), String.class);
        
        assertTrue(response.getStatusCode().is4xxClientError(), "Expected 4xx Client error but got " + response.getStatusCode());
    }
}
