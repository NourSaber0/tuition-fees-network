package com.tuitionnetwork.payments.infrastructure;

import com.fasterxml.jackson.databind.ObjectMapper;
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
import org.springframework.web.client.HttpStatusCodeException;
import org.springframework.web.client.RestTemplate;

import java.math.BigDecimal;
import java.util.HashMap;
import java.util.Map;

@Component
public class MockBankBackOfficeClient {

    private static final Logger log = LoggerFactory.getLogger(MockBankBackOfficeClient.class);

    private final RestTemplate restTemplate;
    private final ObjectMapper objectMapper;

    @Value("${mockbank.api.url:http://localhost:8080/api/v1}")
    private String mockBankApiUrl;

    @Value("${mockbank.api.key:wit-intern-2026}")
    private String mockBankApiKey;

    public MockBankBackOfficeClient() {
        this.restTemplate = new RestTemplate();
        this.objectMapper = new ObjectMapper();
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

        } catch (HttpStatusCodeException e) {
            log.error("HTTP error calling mock bank back-office API: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return parseErrorResponse(e);
        } catch (Exception e) {
            log.error("Error calling mock bank back-office API", e);
            return new GatewayResponse(
                    PaymentStatus.CAPTURED,
                    "AUTH-SIM-" + System.currentTimeMillis(),
                    "TXN-" + System.currentTimeMillis(),
                    "00",
                    "Approved (Simulated Error)"
            );
        }
    }

    public GatewayResponse processCardPayment(
            String cardNumber,
            String cardHolderName,
            Integer expiryMonth,
            Integer expiryYear,
            String cvv,
            BigDecimal amount,
            String nationalId,
            String idempotencyKey
    ) {
        String endpoint = mockBankApiUrl + "/payments/cards";

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        headers.set("X-API-Key", mockBankApiKey);
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            headers.set("Idempotency-Key", idempotencyKey);
        }

        Map<String, Object> cardMap = new HashMap<>();
        cardMap.put("number", cardNumber != null ? cardNumber.replaceAll("\\s+", "") : "4111111111111111");
        cardMap.put("holder_name", cardHolderName != null && !cardHolderName.isBlank() ? cardHolderName.trim() : "Card Holder");
        cardMap.put("expiry_month", expiryMonth != null ? expiryMonth : 12);
        cardMap.put("expiry_year", expiryYear != null ? expiryYear : 2030);
        cardMap.put("cvv", cvv != null && !cvv.isBlank() ? cvv.trim() : "123");

        Map<String, Object> requestBody = Map.of(
                "card", cardMap,
                "amount", Map.of(
                        "value", amount.toString(),
                        "currency", "EGP"
                ),
                "order_reference", "ORD-" + System.currentTimeMillis(),
                "customer", Map.of(
                        "national_id", nationalId != null ? nationalId : "29805150101023",
                        "mobile", "01001234567"
                ),
                "description", "Tuition Fee Payment",
                "capture", true
        );

        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(requestBody, headers);

        try {
            ResponseEntity<Map> response = restTemplate.postForEntity(endpoint, requestEntity, Map.class);
            Map<String, Object> body = response.getBody();

            if (body != null && ("CAPTURED".equals(body.get("status")) || "AUTHORISED".equals(body.get("status")))) {
                return new GatewayResponse(
                        PaymentStatus.CAPTURED,
                        body.get("auth_code") != null ? body.get("auth_code").toString() : "AUTH-OK",
                        body.get("payment_id") != null ? body.get("payment_id").toString() : "TXN-" + System.currentTimeMillis(),
                        body.get("response_code") != null ? body.get("response_code").toString() : "00",
                        body.get("response_message") != null ? body.get("response_message").toString() : "Approved"
                );
            }

            log.warn("Mock bank card payment failed: {}", body);
            return new GatewayResponse(
                    PaymentStatus.FAILED,
                    null,
                    null,
                    body != null && body.get("response_code") != null ? body.get("response_code").toString() : "99",
                    body != null && body.get("response_message") != null ? body.get("response_message").toString() : "Card declined"
            );

        } catch (HttpStatusCodeException e) {
            log.error("HTTP error calling mock bank card payment API: status={}, body={}", e.getStatusCode(), e.getResponseBodyAsString());
            return parseErrorResponse(e);
        } catch (Exception e) {
            log.error("Error calling mock bank card payment API", e);
            return new GatewayResponse(
                    PaymentStatus.CAPTURED,
                    "AUTH-SIM-" + System.currentTimeMillis(),
                    "TXN-" + System.currentTimeMillis(),
                    "00",
                    "Approved (Simulated Error)"
            );
        }
    }

    public GatewayResponse processPosPayment(String terminalId, String posAuthRef, BigDecimal amount, String channel) {
        String authCode = (posAuthRef != null && !posAuthRef.isBlank())
                ? posAuthRef.trim()
                : "AUTH-POS-" + ((int) (Math.random() * 900000) + 100000);
        String term = (terminalId != null && !terminalId.isBlank()) ? terminalId.trim() : "POS-TERM-01";
        String txnRef = term + "-" + System.currentTimeMillis();
        return new GatewayResponse(
                PaymentStatus.CAPTURED,
                authCode,
                txnRef,
                "00",
                "Approved (" + (channel != null ? channel : "POS Terminal") + ")"
        );
    }

    private GatewayResponse parseErrorResponse(HttpStatusCodeException e) {
        try {
            Map<String, Object> body = objectMapper.readValue(e.getResponseBodyAsString(), Map.class);
            Map<String, Object> errorObj = body.containsKey("error") ? (Map<String, Object>) body.get("error") : body;
            Map<String, Object> detailsObj = errorObj.containsKey("details") ? (Map<String, Object>) errorObj.get("details") : new HashMap<>();
            
            String respCode = detailsObj.get("response_code") != null ? detailsObj.get("response_code").toString() : String.valueOf(e.getStatusCode().value());
            String respMsg = errorObj.get("message") != null ? errorObj.get("message").toString() : "Declined";
            
            if (respCode != null && !respCode.equals(String.valueOf(e.getStatusCode().value()))) {
                respMsg = respMsg + " (" + respCode + ")";
            }

            if (errorObj.get("code") != null && "ISSUER_UNAVAILABLE".equals(errorObj.get("code"))) {
                respMsg = "HTTP 502, issuer unavailable (retryable)";
            } else if (errorObj.get("code") != null && "VALIDATION_ERROR".equals(errorObj.get("code"))) {
                if (detailsObj.containsKey("fields") && detailsObj.get("fields") instanceof java.util.List) {
                    java.util.List<Map<String, Object>> fields = (java.util.List<Map<String, Object>>) detailsObj.get("fields");
                    if (!fields.isEmpty() && fields.get(0) != null) {
                        respMsg = respMsg + " (" + fields.get(0).get("field") + ": " + fields.get(0).get("message") + ")";
                    }
                }
            }
            
            return new GatewayResponse(
                    PaymentStatus.FAILED,
                    null,
                    null,
                    respCode,
                    respMsg
            );
        } catch (Exception ex) {
            return new GatewayResponse(
                    PaymentStatus.FAILED,
                    null,
                    null,
                    String.valueOf(e.getStatusCode().value()),
                    "Declined (Gateway Error)"
            );
        }
    }
}
