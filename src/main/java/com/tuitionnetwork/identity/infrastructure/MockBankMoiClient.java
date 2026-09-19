package com.tuitionnetwork.identity.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

@Component
public class MockBankMoiClient {

    private static final Logger log = LoggerFactory.getLogger(MockBankMoiClient.class);
    private final RestTemplate restTemplate;

    @Value("${mockbank.api.url:http://localhost:8000/api/v1}")
    private String mockBankApiUrl;

    @Value("${mockbank.api.key:wit-intern-2026}")
    private String mockBankApiKey;

    public MockBankMoiClient() {
        this.restTemplate = new RestTemplate();
    }

    public MockBankMoiClient(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    public boolean validateNationalId(String nationalId) {
        String endpoint = mockBankApiUrl + "/moi/validate";
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", mockBankApiKey);

        Map<String, Object> body = Map.of(
                "national_id", nationalId,
                "purpose", "OTC_PAYMENT"
        );

        HttpEntity<Map<String, Object>> request = new HttpEntity<>(body, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, request, Map.class);
            Map<String, Object> responseBody = response.getBody();
            if (responseBody != null && Boolean.TRUE.equals(responseBody.get("valid"))) {
                return true;
            }
            log.warn("MOI Validation failed for National ID. Status or Valid flag was false.");
            return false;
        } catch (Exception e) {
            log.error("Failed to connect to MOI Mock Service for validation.", e);
            // Default to false for strict KYC, but if the mock server is down during dev, we might want it to pass?
            // Actually, we should return false so the Teller cannot proceed without MOI.
            return false;
        }
    }
}
