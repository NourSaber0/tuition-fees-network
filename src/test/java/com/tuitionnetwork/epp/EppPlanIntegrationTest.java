package com.tuitionnetwork.epp;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.dto.PaymentSettleRequest;
import com.tuitionnetwork.payments.dto.SelectedDueDto;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.payments.spi.BankGatewayAdapterInterface;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.Duration;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
@WithMockUser(username = "emp@cibeg.com", roles = {"BACK_OFFICE"})
class EppPlanIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private FeeLineRepository feeLineRepository;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private com.tuitionnetwork.audit.repository.AuditLogRepository auditLogRepository;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @org.springframework.test.context.bean.override.mockito.MockitoSpyBean
    private BankGatewayAdapterInterface bankGatewayAdapter;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        try {
            jdbcTemplate.execute("ALTER TABLE event_publication ALTER COLUMN serialized_event VARCHAR(65535)");
            Awaitility.await()
                    .atMost(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofMillis(50))
                    .until(() -> jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL",
                            Integer.class) == 0);
            jdbcTemplate.execute("DELETE FROM event_publication");
        } catch (Exception ignored) {}

        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(50))
                .ignoreExceptions()
                .until(() -> {
                    jdbcTemplate.execute("DELETE FROM receipt");
                    jdbcTemplate.execute("DELETE FROM payment_allocation");
                    jdbcTemplate.execute("DELETE FROM payment_state_log");
                    jdbcTemplate.execute("DELETE FROM epp_schedule");
                    paymentRepository.deleteAll();
                    return true;
                });
        feeLineRepository.deleteAll();
    }

    private UUID settleCreditCardPayment(BigDecimal amount) throws Exception {
        UUID institutionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        String idempotencyKey = "IDEMP-" + UUID.randomUUID();

        FeeLine feeLine = new FeeLine(
                institutionId,
                studentId,
                FeeType.TUITION,
                amount,
                amount,
                "Term 2 · 2026",
                LocalDate.now().plusMonths(2)
        );
        feeLine = feeLineRepository.save(feeLine);

        SelectedDueDto due = new SelectedDueDto(feeLine.getId(), amount);
        PaymentSettleRequest request = new PaymentSettleRequest(
                idempotencyKey,
                guardianId,
                PaymentMethod.CREDIT_CARD,
                List.of(due),
                amount,
                null,
                "4111222233334444",
                null
        );

        MvcResult result = mockMvc.perform(post("/api/v1/payments/settle")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String json = result.getResponse().getContentAsString();
        String paymentId = objectMapper.readTree(json).get("paymentId").asText();
        return UUID.fromString(paymentId);
    }

    @Test
    void quote_returnsCalculatedPricingForTwelveMonthTenor() throws Exception {
        mockMvc.perform(post("/api/v1/epp/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principalEGP\":24000,\"tenor\":12}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.principalEGP").value(24000.00))
                .andExpect(jsonPath("$.tenor").value(12))
                .andExpect(jsonPath("$.interestRatePct").value(14.0000))
                .andExpect(jsonPath("$.totalEGP").value(27600.00))
                .andExpect(jsonPath("$.monthlyEGP").value(2300.00));
    }

    @Test
    void quote_threeMonthTenorIsInterestFree() throws Exception {
        mockMvc.perform(post("/api/v1/epp/quote")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"principalEGP\":6000,\"tenor\":3}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.interestEGP").value(0.00))
                .andExpect(jsonPath("$.totalEGP").value(6000.00));
    }

    @Test
    void validateCard_rejectsKnownDebitBin() throws Exception {
        mockMvc.perform(post("/api/v1/epp/cards/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardNumber\":\"5078123412341234\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("rejected-debit"));
    }

    @Test
    void validateCard_acceptsCreditCard() throws Exception {
        mockMvc.perform(post("/api/v1/epp/cards/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"cardNumber\":\"4111222233334444\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.result").value("valid-credit"));
    }

    @Test
    void createPlan_fromCapturedCreditCardPayment_thenListDetailScheduleAndCancel() throws Exception {
        UUID paymentId = settleCreditCardPayment(new BigDecimal("24000.00"));

        String createBody = objectMapper.writeValueAsString(
                new com.tuitionnetwork.epp.dto.CreateEppPlanRequest(
                        "tok_abc", "Ahmed Hassan", "29901011234567", "Cairo International School",
                        "Tuition - Term 2 2026", new BigDecimal("24000.00"), 12, paymentId));

        MvcResult createResult = mockMvc.perform(post("/api/v1/epp/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Active"))
                .andExpect(jsonPath("$.tenor").value(12))
                .andExpect(jsonPath("$.institutionType").value("SCHOOL"))
                .andExpect(jsonPath("$.firstPaymentDate").isNotEmpty())
                .andReturn();

        String planId = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc.perform(get("/api/v1/epp/plans/{id}", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.institutionType").value("SCHOOL"))
                .andExpect(jsonPath("$.progress.totalInstallments").value(12));

        mockMvc.perform(get("/api/v1/epp/plans/{id}/schedule", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(12))
                .andExpect(jsonPath("$[0].status").value("Upcoming"))
                .andExpect(jsonPath("$[0].installmentNumber").value(1))
                .andExpect(jsonPath("$[0].number").value(1));

        mockMvc.perform(get("/api/v1/epp/plans").param("search", planId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].institutionType").value("SCHOOL"));

        mockMvc.perform(get("/api/v1/epp/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(1));

        mockMvc.perform(patch("/api/v1/epp/plans/{id}", planId)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"status\":\"Cancelled\",\"reason\":\"Guardian requested cancellation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Cancelled"));

        mockMvc.perform(get("/api/v1/epp/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(0));

        org.junit.jupiter.api.Assertions.assertFalse(auditLogRepository.findByAction("CREATE_EPP_PLAN").isEmpty());
        org.junit.jupiter.api.Assertions.assertFalse(auditLogRepository.findByAction("UPDATE_EPP_PLAN_STATUS").isEmpty());
    }

    @Test
    void createPlan_rejectsUnknownSourcePayment() throws Exception {
        String createBody = objectMapper.writeValueAsString(
                new com.tuitionnetwork.epp.dto.CreateEppPlanRequest(
                        "tok_abc", "Ahmed Hassan", "29901011234567", "Cairo International School",
                        "Tuition", new BigDecimal("24000.00"), 12, UUID.randomUUID()));

        mockMvc.perform(post("/api/v1/epp/plans")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createBody))
                .andExpect(status().isNotFound());
    }

    @Test
    void getPlan_unknownId_returnsNotFound() throws Exception {
        mockMvc.perform(get("/api/v1/epp/plans/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }
}
