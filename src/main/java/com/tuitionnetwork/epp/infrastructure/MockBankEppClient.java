package com.tuitionnetwork.epp.infrastructure;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.server.ResponseStatusException;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

@Component
public class MockBankEppClient {

    private static final Logger log = LoggerFactory.getLogger(MockBankEppClient.class);

    private final RestTemplate restTemplate;

    @Value("${mockbank.api.url:http://localhost:8000/api/v1}")
    private String mockBankApiUrl;

    @Value("${mockbank.api.key:wit-intern-2026}")
    private String mockBankApiKey;

    public MockBankEppClient() {
        this.restTemplate = new RestTemplate();
    }

    public List<EppQuoteDto> getQuotes(BigDecimal amount) {
        String endpoint = mockBankApiUrl + "/epp/quotes?amount=" + amount.toString();
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", mockBankApiKey);
        
        HttpEntity<Void> requestEntity = new HttpEntity<>(headers);
        
        try {
            ResponseEntity<Map> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.GET,
                    requestEntity,
                    Map.class
            );
            
            Map<String, Object> body = response.getBody();
            if (body != null && body.containsKey("options")) {
                List<Map<String, Object>> options = (List<Map<String, Object>>) body.get("options");
                return options.stream().map(opt -> new EppQuoteDto(
                        ((Number) opt.get("tenor_months")).intValue(),
                        opt.get("monthly_installment").toString(),
                        opt.get("interest_amount").toString(),
                        opt.get("total_payable").toString()
                )).toList();
            }
            throw new RuntimeException("Missing 'options' array in response");
        } catch (Exception e) {
            log.error("Failed to fetch EPP quotes from Mock Bank, simulating locally. Error: {}", e.getMessage());
            // Fallback simulation
            return List.of(
                    new EppQuoteDto(6, "891.67", "300.00", "5350.00"),
                    new EppQuoteDto(12, "479.17", "700.00", "5750.00")
            );
        }
    }

    public EppPlanResponse createPlan(String paymentId, int tenorMonths) {
        String endpoint = mockBankApiUrl + "/epp";
        
        HttpHeaders headers = new HttpHeaders();
        headers.set("X-API-Key", mockBankApiKey);
        headers.setContentType(MediaType.APPLICATION_JSON);
        
        Map<String, Object> body = Map.of(
                "payment_id", paymentId,
                "tenor_months", tenorMonths
        );
        
        HttpEntity<Map<String, Object>> requestEntity = new HttpEntity<>(body, headers);
        
        try {
            ResponseEntity<EppPlanResponse> response = restTemplate.exchange(
                    endpoint,
                    HttpMethod.POST,
                    requestEntity,
                    EppPlanResponse.class
            );
            return response.getBody();
        } catch (Exception e) {
            log.error("Failed to create EPP plan at Mock Bank, simulating locally. Error: {}", e.getMessage());
            
            // Fallback simulation
            BigDecimal amount = new BigDecimal("10000.00"); // Just a default fallback
            BigDecimal interest = new BigDecimal("500.00");
            BigDecimal totalPayable = amount.add(interest);
            BigDecimal monthlyInstallment = totalPayable.divide(new BigDecimal(tenorMonths), 2, java.math.RoundingMode.HALF_UP);
            
            return new EppPlanResponse(
                    "EPP-" + System.currentTimeMillis(),
                    paymentId,
                    tenorMonths,
                    monthlyInstallment.toString(),
                    totalPayable.toString(),
                    List.of()
            );
        }
    }

    public record EppQuotesResponse(List<EppQuoteDto> quotes) {}

    public record EppQuoteDto(
            int tenor_months,
            String monthly_installment,
            String total_interest,
            String total_repayment
    ) {}

    public record EppPlanResponse(
            String plan_id,
            String payment_id,
            int tenor_months,
            String monthly_installment,
            @com.fasterxml.jackson.annotation.JsonProperty("total_payable") String total_repayment,
            List<EppInstallmentDto> schedule
    ) {}

    public record EppInstallmentDto(
            @com.fasterxml.jackson.annotation.JsonProperty("number") int installment_number,
            String due_date,
            String amount
    ) {}
}
