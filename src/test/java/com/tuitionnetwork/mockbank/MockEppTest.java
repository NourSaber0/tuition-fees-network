package com.tuitionnetwork.mockbank;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.mockbank.dto.AmountDto;
import com.tuitionnetwork.mockbank.dto.CardDto;
import com.tuitionnetwork.mockbank.dto.CardPaymentRequest;
import com.tuitionnetwork.mockbank.dto.EppCreateRequest;
import com.tuitionnetwork.mockbank.store.MockBankStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;

import static org.hamcrest.Matchers.hasSize;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
class MockEppTest {

    private static final String API_KEY = "wit-intern-2026";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MockBankStore mockBankStore;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        mockBankStore.reset();
    }

    @Test
    void quotes_24000Amount_returnsAccurateQuotes() throws Exception {
        mockMvc.perform(get("/api/v1/epp/quotes?amount=24000")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.amount", is(24000)))
                .andExpect(jsonPath("$.currency", is("EGP")))
                .andExpect(jsonPath("$.quotes", hasSize(5)))
                // 3 months (0% rate, 0 admin fee, 8000 monthly)
                .andExpect(jsonPath("$.quotes[0].tenor_months", is(3)))
                .andExpect(jsonPath("$.quotes[0].annual_rate").value(0))
                .andExpect(jsonPath("$.quotes[0].admin_fee").value(0))
                .andExpect(jsonPath("$.quotes[0].total_payable", is(24000.0)))
                .andExpect(jsonPath("$.quotes[0].monthly_installment", is(8000.0)))
                // 12 months (14% rate, 240 admin fee, 3360 interest, 27600 total, 2300 monthly)
                .andExpect(jsonPath("$.quotes[2].tenor_months", is(12)))
                .andExpect(jsonPath("$.quotes[2].annual_rate", is(0.14)))
                .andExpect(jsonPath("$.quotes[2].interest_amount", is(3360.0)))
                .andExpect(jsonPath("$.quotes[2].admin_fee", is(240.0)))
                .andExpect(jsonPath("$.quotes[2].total_payable", is(27600.0)))
                .andExpect(jsonPath("$.quotes[2].monthly_installment", is(2300.0)));
    }

    @Test
    void createEppPlan_fromCapturedPayment_returnsActivePlanWithSchedule() throws Exception {
        // Step 1: Capture card payment
        CardPaymentRequest cardReq = new CardPaymentRequest(
                new CardDto("4111 1111 1111 1111", "Mona Samir", 12, 2030, "123"),
                new AmountDto(new BigDecimal("24000.00"), "EGP"),
                "ORD-EPP-1",
                null,
                "Tuition",
                true
        );

        MvcResult payResult = mockMvc.perform(post("/api/v1/payments/cards")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cardReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String paymentId = objectMapper.readTree(payResult.getResponse().getContentAsString()).get("payment_id").asText();

        // Step 2: Create EPP Plan
        EppCreateRequest eppReq = new EppCreateRequest(paymentId, null, null, 12, "Tuition Plan");

        MvcResult eppResult = mockMvc.perform(post("/api/v1/epp")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eppReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.plan_id", notNullValue()))
                .andExpect(jsonPath("$.status", is("ACTIVE")))
                .andExpect(jsonPath("$.payment_id", is(paymentId)))
                .andExpect(jsonPath("$.principal", is(24000.0)))
                .andExpect(jsonPath("$.tenor_months", is(12)))
                .andExpect(jsonPath("$.annual_rate", is(0.14)))
                .andExpect(jsonPath("$.interest_amount", is(3360.0)))
                .andExpect(jsonPath("$.admin_fee", is(240.0)))
                .andExpect(jsonPath("$.total_payable", is(27600.0)))
                .andExpect(jsonPath("$.monthly_installment", is(2300.0)))
                .andExpect(jsonPath("$.schedule", hasSize(12)))
                .andExpect(jsonPath("$.schedule[0].amount", is(2300.0)))
                .andExpect(jsonPath("$.schedule[0].status", is("DUE")))
                .andReturn();

        String planId = objectMapper.readTree(eppResult.getResponse().getContentAsString()).get("plan_id").asText();

        // Step 3: Get EPP Plan by ID
        mockMvc.perform(get("/api/v1/epp/" + planId)
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.plan_id", is(planId)));

        // Step 4: Cancel Plan
        mockMvc.perform(post("/api/v1/epp/" + planId + "/cancel")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CANCELLED")));
    }

    @Test
    void createEppPlan_debitCardRejected_returns422() throws Exception {
        // Debit card captured
        CardPaymentRequest cardReq = new CardPaymentRequest(
                new CardDto("4000 0566 5566 5556", "Debit User", 12, 2030, "123"),
                new AmountDto(new BigDecimal("10000.00"), "EGP"),
                "ORD-DEBIT",
                null,
                "Fees",
                true
        );

        MvcResult payResult = mockMvc.perform(post("/api/v1/payments/cards")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cardReq)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.card.type", is("DEBIT")))
                .andReturn();

        String paymentId = objectMapper.readTree(payResult.getResponse().getContentAsString()).get("payment_id").asText();

        // Attempt EPP creation on Debit card
        EppCreateRequest eppReq = new EppCreateRequest(paymentId, null, null, 12, "Tuition Plan");

        mockMvc.perform(post("/api/v1/epp")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eppReq)))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error.code", is("CARD_NOT_ELIGIBLE")));
    }

    @Test
    void createEppPlan_alreadyConverted_returns409() throws Exception {
        CardPaymentRequest cardReq = new CardPaymentRequest(
                new CardDto("4111 1111 1111 1111", "Mona Samir", 12, 2030, "123"),
                new AmountDto(new BigDecimal("5000.00"), "EGP"),
                "ORD-DBL",
                null,
                "Fees",
                true
        );

        MvcResult payResult = mockMvc.perform(post("/api/v1/payments/cards")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(cardReq)))
                .andExpect(status().isCreated())
                .andReturn();

        String paymentId = objectMapper.readTree(payResult.getResponse().getContentAsString()).get("payment_id").asText();

        EppCreateRequest eppReq = new EppCreateRequest(paymentId, null, null, 6, "Fees");

        mockMvc.perform(post("/api/v1/epp")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eppReq)))
                .andExpect(status().isCreated());

        // Second conversion attempt
        mockMvc.perform(post("/api/v1/epp")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(eppReq)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code", is("ALREADY_CONVERTED")));
    }
}
