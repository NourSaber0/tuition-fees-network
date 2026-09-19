package com.tuitionnetwork.identity.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.RestTemplate;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@Component
public class MockBankCustomerClient {

    private static final Logger log = LoggerFactory.getLogger(MockBankCustomerClient.class);

    private final RestTemplate restTemplate;

    @Value("${mockbank.api.url:http://localhost:8000/api/v1}")
    private String mockBankApiUrl;

    @Value("${mockbank.api.key:wit-intern-2026}")
    private String mockBankApiKey;

    public MockBankCustomerClient() {
        this.restTemplate = new RestTemplate();
    }

    public CustomerResponse getCustomerByNationalId(String nationalId) {
        String endpoint = mockBankApiUrl + "/customers?national_id=" + nationalId;

        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", mockBankApiKey);
        
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<CustomerResponse> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.GET,
                    requestEntity,
                    CustomerResponse.class
            );
            return response.getBody();
        } catch (HttpClientErrorException.NotFound e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Customer not found in Mock Bank");
        } catch (Exception e) {
            log.error("Failed to fetch customer from Mock Bank", e);
            throw new ResponseStatusException(HttpStatus.INTERNAL_SERVER_ERROR, "Failed to connect to Mock Bank");
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CustomerResponse(
            @JsonProperty("customer_id") String customer_id,
            @JsonProperty("full_name_en") String full_name_en,
            @JsonProperty("national_id") String national_id,
            List<AccountDto> accounts,
            List<CardDto> cards
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record AccountDto(
            String account_id,
            String account_number,
            String type,
            String currency,
            Double available_balance,
            String status
    ) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record CardDto(
            String card_id,
            String masked_number,
            String scheme,
            String type,
            String holder_name,
            String expiry,
            String status,
            Double credit_limit,
            Double available_limit,
            String linked_account_id
    ) {}
}
