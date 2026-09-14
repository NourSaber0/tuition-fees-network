package com.tuitionnetwork.mockbank;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.mockbank.dto.AmountDto;
import com.tuitionnetwork.mockbank.dto.CardActionRequests;
import com.tuitionnetwork.mockbank.dto.CardDto;
import com.tuitionnetwork.mockbank.dto.CardPaymentRequest;
import com.tuitionnetwork.mockbank.dto.CustomerDto;
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

import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
class MockCardPaymentTest {

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
    void cardPayment_approvedVisaCredit_returns201Captured() throws Exception {
        CardPaymentRequest request = new CardPaymentRequest(
                new CardDto("4111 1111 1111 1111", "Mona Samir", 12, 2030, "123"),
                new AmountDto(new BigDecimal("24000.00"), "EGP"),
                "ORD-100234",
                new CustomerDto("29805150101023", "01001234567"),
                "Tuition Fees",
                true
        );

        mockMvc.perform(post("/api/v1/payments/cards")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("CAPTURED")))
                .andExpect(jsonPath("$.approved", is(true)))
                .andExpect(jsonPath("$.amount", is(24000.0)))
                .andExpect(jsonPath("$.captured_amount", is(24000.0)))
                .andExpect(jsonPath("$.card.scheme", is("VISA")))
                .andExpect(jsonPath("$.card.type", is("CREDIT")))
                .andExpect(jsonPath("$.card.masked_number", is("411111******1111")))
                .andExpect(jsonPath("$.auth_code", notNullValue()))
                .andExpect(jsonPath("$.rrn", notNullValue()))
                .andExpect(jsonPath("$.response_code", is("00")));
    }

    @Test
    void cardPayment_idempotencyKeyReplay_returnsCachedResponse() throws Exception {
        CardPaymentRequest request = new CardPaymentRequest(
                new CardDto("4111 1111 1111 1111", "Mona Samir", 12, 2030, "123"),
                new AmountDto(new BigDecimal("5000.00"), "EGP"),
                "ORD-999",
                new CustomerDto("29805150101023", "01001234567"),
                "Books",
                true
        );

        MvcResult firstResult = mockMvc.perform(post("/api/v1/payments/cards")
                        .header("X-API-Key", API_KEY)
                        .header("Idempotency-Key", "IDEM-ABC-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String paymentId1 = objectMapper.readTree(firstResult.getResponse().getContentAsString()).get("payment_id").asText();

        MvcResult secondResult = mockMvc.perform(post("/api/v1/payments/cards")
                        .header("X-API-Key", API_KEY)
                        .header("Idempotency-Key", "IDEM-ABC-123")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andReturn();

        String paymentId2 = objectMapper.readTree(secondResult.getResponse().getContentAsString()).get("payment_id").asText();
        assertEquals(paymentId1, paymentId2);
    }

    @Test
    void cardPayment_invalidLuhn_returns400() throws Exception {
        CardPaymentRequest request = new CardPaymentRequest(
                new CardDto("4111 1111 1111 1112", "Bad Card", 12, 2030, "123"),
                new AmountDto(new BigDecimal("1000.00"), "EGP"),
                "ORD-001",
                null,
                "Fees",
                true
        );

        mockMvc.perform(post("/api/v1/payments/cards")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_CARD_NUMBER")));
    }

    @Test
    void cardPayment_insufficientFunds_returns402() throws Exception {
        CardPaymentRequest request = new CardPaymentRequest(
                new CardDto("4000 0000 0000 0002", "Low Balance", 12, 2030, "123"),
                new AmountDto(new BigDecimal("1000.00"), "EGP"),
                "ORD-002",
                null,
                "Fees",
                true
        );

        mockMvc.perform(post("/api/v1/payments/cards")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.code", is("CARD_DECLINED")))
                .andExpect(jsonPath("$.error.details.response_code", is("51")));
    }

    @Test
    void cardPayment_expiredCard_returns402() throws Exception {
        CardPaymentRequest request = new CardPaymentRequest(
                new CardDto("4000 0000 0000 0069", "Expired Card", 12, 2030, "123"),
                new AmountDto(new BigDecimal("1000.00"), "EGP"),
                "ORD-003",
                null,
                "Fees",
                true
        );

        mockMvc.perform(post("/api/v1/payments/cards")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.code", is("CARD_EXPIRED")))
                .andExpect(jsonPath("$.error.details.response_code", is("54")));
    }

    @Test
    void cardPayment_issuerUnavailable_returns502() throws Exception {
        CardPaymentRequest request = new CardPaymentRequest(
                new CardDto("4000 0000 0000 0119", "Timeout Card", 12, 2030, "123"),
                new AmountDto(new BigDecimal("1000.00"), "EGP"),
                "ORD-004",
                null,
                "Fees",
                true
        );

        mockMvc.perform(post("/api/v1/payments/cards")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.error.code", is("ISSUER_UNAVAILABLE")));
    }

    @Test
    void cardPayment_3dsFlow_pendingAndConfirmation() throws Exception {
        CardPaymentRequest request = new CardPaymentRequest(
                new CardDto("4000 0000 0000 3220", "3DS Card", 12, 2030, "123"),
                new AmountDto(new BigDecimal("15000.00"), "EGP"),
                "ORD-3DS",
                null,
                "Tuition",
                true
        );

        MvcResult result = mockMvc.perform(post("/api/v1/payments/cards")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PENDING_3DS")))
                .andExpect(jsonPath("$.approved", is(false)))
                .andReturn();

        String paymentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("payment_id").asText();

        // Failed OTP
        mockMvc.perform(post("/api/v1/payments/cards/" + paymentId + "/3ds")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CardActionRequests.Card3dsRequest("000000"))))
                .andExpect(status().isPaymentRequired())
                .andExpect(jsonPath("$.error.code", is("CARD_DECLINED")));

        // Valid OTP 123456
        mockMvc.perform(post("/api/v1/payments/cards/" + paymentId + "/3ds")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CardActionRequests.Card3dsRequest("123456"))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CAPTURED")))
                .andExpect(jsonPath("$.approved", is(true)));
    }

    @Test
    void cardPayment_authoriseCaptureVoidRefund_lifecycle() throws Exception {
        CardPaymentRequest request = new CardPaymentRequest(
                new CardDto("5555 5555 5555 4444", "Mastercard User", 12, 2030, "123"),
                new AmountDto(new BigDecimal("10000.00"), "EGP"),
                "ORD-LIFE",
                null,
                "Services",
                false // Authorise only
        );

        MvcResult result = mockMvc.perform(post("/api/v1/payments/cards")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status", is("AUTHORISED")))
                .andReturn();

        String paymentId = objectMapper.readTree(result.getResponse().getContentAsString()).get("payment_id").asText();

        // Capture payment
        mockMvc.perform(post("/api/v1/payments/cards/" + paymentId + "/capture")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CardActionRequests.CardCaptureRequest(new BigDecimal("10000.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("CAPTURED")));

        // Partial Refund
        mockMvc.perform(post("/api/v1/payments/cards/" + paymentId + "/refund")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(new CardActionRequests.CardRefundRequest(new BigDecimal("3000.00")))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("PARTIALLY_REFUNDED")))
                .andExpect(jsonPath("$.captured_amount", is(7000.0)));
    }
}
