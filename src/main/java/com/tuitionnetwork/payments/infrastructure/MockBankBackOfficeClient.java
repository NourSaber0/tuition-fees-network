package com.tuitionnetwork.payments.infrastructure;

import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.spi.GatewayResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.Map;

@Component
public class MockBankBackOfficeClient {

    private static final Logger log = LoggerFactory.getLogger(MockBankBackOfficeClient.class);

    private final RestTemplate restTemplate;

    @Value("${mockbank.api.url:http://localhost:8080/api/v1}")
    private String mockBankApiUrl;

    @Value("${mockbank.api.key:wit-intern-2026}")
    private String mockBankApiKey;

    public MockBankBackOfficeClient() {
        this.restTemplate = new RestTemplate();
    }

    public GatewayResponse processBackOfficePayment(String sourceId, BigDecimal amount, String idempotencyKey) {
        String endpoint = mockBankApiUrl + "/backoffice/payments";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", mockBankApiKey);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set("Idempotency-Key", idempotencyKey);
        }

        Map<String, Object> requestBody = Map.of(
                "source_id", sourceId,
                "amount", Map.of(
                        "value", amount.toString(),
                        "currency", "EGP"
                ),
                "reference", "BOP-" + System.currentTimeMillis(),
                "capture", true
        );

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, requestEntity, Map.class);
            Map<String, Object> body = response.getBody();

            if (body != null && ("CAPTURED".equals(body.get("status")) || "POSTED".equals(body.get("status")))) {
                return new GatewayResponse(
                        PaymentStatus.CAPTURED,
                        body.get("response_code") != null ? body.get("response_code").toString() : "00",
                        body.get("payment_id") != null ? body.get("payment_id").toString() : "TXN-" + System.currentTimeMillis(),
                        body.get("response_code") != null ? body.get("response_code").toString() : "00",
                        body.get("response_message") != null ? body.get("response_message").toString() : "Approved"
                );
            }
            
            log.warn("Mock bank back-office payment failed: {}", body);
            return new GatewayResponse(
                    PaymentStatus.FAILED,
                    null,
                    null,
                    body != null && body.get("response_code") != null ? body.get("response_code").toString() : "99",
                    body != null && body.get("response_message") != null ? body.get("response_message").toString() : "Declined"
            );

        } catch (Exception e) {
            log.error("Error calling mock bank back-office API", e);
            return new GatewayResponse(PaymentStatus.FAILED, null, null, "99", "Gateway error: " + e.getMessage());
        }
    }
}
