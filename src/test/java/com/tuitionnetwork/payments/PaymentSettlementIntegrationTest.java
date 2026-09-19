package com.tuitionnetwork.payments;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.dto.EppSelectionDto;
import com.tuitionnetwork.payments.dto.PaymentSettleRequest;
import com.tuitionnetwork.payments.dto.SelectedDueDto;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import com.tuitionnetwork.payments.service.PaymentSettlementService;
import com.tuitionnetwork.payments.spi.BankGatewayAdapterInterface;
import org.awaitility.Awaitility;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoSpyBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.time.Duration;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
@WithMockUser(username = "guardian@example.com", roles = {"GUARDIAN", "BACK_OFFICE"})
public class PaymentSettlementIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private FeeLineRepository feeLineRepository;

    @Autowired
    private PaymentSettlementService paymentSettlementService;

    @MockitoSpyBean
    private BankGatewayAdapterInterface bankGatewayAdapter;

    @Autowired
    private org.springframework.jdbc.core.JdbcTemplate jdbcTemplate;

    @BeforeEach
    void cleanDatabase() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        try {
            jdbcTemplate.execute("ALTER TABLE event_publication ALTER COLUMN serialized_event VARCHAR(65535)");
            // Wait for the previous test's async listeners (ReceiptGenerator, EppScheduleGenerator,
            // PaymentNotificationService) to finish before wiping tables, otherwise a late-arriving
            // receipt insert can race the payment delete below and fail on the FK constraint.
            Awaitility.await()
                    .atMost(Duration.ofSeconds(5))
                    .pollInterval(Duration.ofMillis(50))
                    .until(() -> jdbcTemplate.queryForObject(
                            "SELECT COUNT(*) FROM event_publication WHERE completion_date IS NULL",
                            Integer.class) == 0);
            jdbcTemplate.execute("DELETE FROM event_publication");
        } catch (Exception ignored) {}

        // Even after the wait above, a listener can still be mid-write (it finished its DB work but
        // hasn't marked event_publication complete yet). Retry the wipe itself so a late receipt/epp
        // insert that races the delete just gets swept up on the next attempt instead of failing the test.
        Awaitility.await()
                .atMost(Duration.ofSeconds(5))
                .pollInterval(Duration.ofMillis(50))
                .ignoreExceptions()
                .until(() -> {
                    jdbcTemplate.execute("DELETE FROM receipt");
                    jdbcTemplate.execute("DELETE FROM payment_allocation");
                    jdbcTemplate.execute("DELETE FROM payment_state_log");
                    jdbcTemplate.execute("DELETE FROM epp_installment");
                    jdbcTemplate.execute("DELETE FROM epp_schedule");
                    paymentRepository.deleteAll();
                    return true;
                });
        feeLineRepository.deleteAll();
        reset(bankGatewayAdapter);
    }

    @AfterEach
    void cleanup() {
        try {
            jdbcTemplate.execute("DELETE FROM event_publication");
        } catch (Exception ignored) {}
    }

    @Test
    void testIdempotency_preventsDoubleCharge() throws Exception {
        UUID institutionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        String idempotencyKey = "IDEMP-" + UUID.randomUUID();

        FeeLine feeLine = new FeeLine(
                institutionId,
                studentId,
                FeeType.TUITION,
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                "Term 2 · 2026",
                LocalDate.now().plusMonths(2)
        );
        feeLine = feeLineRepository.save(feeLine);

        SelectedDueDto due = new SelectedDueDto(
                feeLine.getId(),
                new BigDecimal("5000.00")
        );

        PaymentSettleRequest request = new PaymentSettleRequest(
                idempotencyKey,
                guardianId,
                PaymentMethod.CREDIT_CARD,
                List.of(due),
                new BigDecimal("5000.00"),
                null,
                "4111222233334444",
                null
        );

        String json = objectMapper.writeValueAsString(request);

        // First Request - must succeed
        MvcResult firstResult = mockMvc.perform(post("/api/v1/payments/settle")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();

        // Verify card charged exactly once
        verify(bankGatewayAdapter, times(1)).chargeCard(eq(new BigDecimal("5000.00")), eq(idempotencyKey));

        // Second Request with SAME Idempotency-Key
        MvcResult secondResult = mockMvc.perform(post("/api/v1/payments/settle")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andReturn();

        // Bank gateway chargeCard MUST NOT be called again
        verify(bankGatewayAdapter, times(1)).chargeCard(any(), any());

        // Verify both returned identical response payload
        assertEquals(firstResult.getResponse().getContentAsString(), secondResult.getResponse().getContentAsString());

        // Verify only 1 payment in database
        List<Payment> payments = paymentRepository.findAll();
        assertEquals(1, payments.size());
    }

    @Test
    void testOptimisticLocking_preventsOverpayment() throws Exception {
        UUID institutionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();

        FeeLine feeLine = new FeeLine(
                institutionId,
                studentId,
                FeeType.TUITION,
                new BigDecimal("4000.00"),
                new BigDecimal("4000.00"),
                "Term 2 · 2026",
                LocalDate.now().plusMonths(2)
        );
        feeLine = feeLineRepository.save(feeLine);

        int threadCount = 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(1);

        UUID feeLineId = feeLine.getId();

        List<Future<Boolean>> futures = new ArrayList<>();

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            futures.add(executor.submit(() -> {
                latch.await(); // wait for simultaneous trigger
                SelectedDueDto due = new SelectedDueDto(
                        feeLineId,
                        new BigDecimal("4000.00")
                );
                String idempKey = "CONCURRENT-" + index + "-" + UUID.randomUUID();
                PaymentSettleRequest request = new PaymentSettleRequest(
                        idempKey,
                        guardianId,
                        PaymentMethod.CREDIT_CARD,
                        List.of(due),
                        new BigDecimal("4000.00"),
                        null,
                        "4111222233334444",
                        null
                );
                try {
                    paymentSettlementService.settlePayment(request, idempKey);
                    return true;
                } catch (ObjectOptimisticLockingFailureException e) {
                    return false;
                } catch (Exception e) {
                    return false;
                }
            }));
        }

        latch.countDown(); // trigger all threads concurrently
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));

        int successCount = 0;
        int failureCount = 0;
        for (Future<Boolean> future : futures) {
            if (future.get()) {
                successCount++;
            } else {
                failureCount++;
            }
        }

        assertEquals(1, successCount, "Exactly one concurrent payment should succeed");
        assertEquals(1, failureCount, "The second concurrent payment must be rejected");

        // The losing payer must NEVER have been charged - the card gateway is hit exactly once.
        verify(bankGatewayAdapter, times(1)).chargeCard(any(), any());

        // Verify FeeLine status and balance in DB
        FeeLine updatedFeeLine = feeLineRepository.findById(feeLineId).orElseThrow();
        assertEquals(FeeStatus.PAID, updatedFeeLine.getStatus());
        assertEquals(0, BigDecimal.ZERO.compareTo(updatedFeeLine.getRemainingAmount()));
        assertEquals(0, new BigDecimal("4000.00").compareTo(updatedFeeLine.getPaidAmount()));
    }

    @Test
    void testPartialPayment_allocatesCorrectlyAndLeavesOutstandingBalance() throws Exception {
        UUID institutionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        String idempotencyKey = "PARTIAL-" + UUID.randomUUID();

        FeeLine feeLine = new FeeLine(
                institutionId,
                studentId,
                FeeType.TUITION,
                new BigDecimal("10000.00"),
                new BigDecimal("10000.00"),
                "Term 2 · 2026",
                LocalDate.now().plusMonths(2)
        );
        feeLine = feeLineRepository.save(feeLine);

        // Pay 4,000 out of 10,000 EGP
        SelectedDueDto due = new SelectedDueDto(
                feeLine.getId(),
                new BigDecimal("4000.00")
        );

        PaymentSettleRequest request = new PaymentSettleRequest(
                idempotencyKey,
                guardianId,
                PaymentMethod.CREDIT_CARD,
                List.of(due),
                new BigDecimal("4000.00"),
                null,
                "4111222233334444",
                null
        );

        String json = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/payments/settle")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk());

        FeeLine updatedFee = feeLineRepository.findById(feeLine.getId()).orElseThrow();
        assertEquals(FeeStatus.PARTIALLY_PAID, updatedFee.getStatus());
        assertEquals(0, new BigDecimal("4000.00").compareTo(updatedFee.getPaidAmount()));
        assertEquals(0, new BigDecimal("6000.00").compareTo(updatedFee.getRemainingAmount()));
    }

    @Test
    void testOverpaymentGuardrail_isRejected() throws Exception {
        UUID institutionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        String idempotencyKey = "OVERPAY-" + UUID.randomUUID();

        FeeLine feeLine = new FeeLine(
                institutionId,
                studentId,
                FeeType.TUITION,
                new BigDecimal("3000.00"),
                new BigDecimal("3000.00"),
                "Term 2 · 2026",
                LocalDate.now().plusMonths(2)
        );
        feeLine = feeLineRepository.save(feeLine);

        // Attempt to pay 3001.00 EGP (exceeding 3000.00 remaining)
        SelectedDueDto due = new SelectedDueDto(
                feeLine.getId(),
                new BigDecimal("3001.00")
        );

        PaymentSettleRequest request = new PaymentSettleRequest(
                idempotencyKey,
                guardianId,
                PaymentMethod.CREDIT_CARD,
                List.of(due),
                new BigDecimal("3001.00"),
                null,
                "4111222233334444",
                null
        );

        String json = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/payments/settle")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 400 || status == 422, "Expected 400 or 422 for overpayment rejection");
                })
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("exceeds remaining fee balance")));

        // Verify no card was charged
        verify(bankGatewayAdapter, never()).chargeCard(any(), any());
    }

    @Test
    void testEppDebitCardBlock_isRejected() throws Exception {
        UUID institutionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        String idempotencyKey = "EPP-DEBIT-" + UUID.randomUUID();

        FeeLine feeLine = new FeeLine(
                institutionId,
                studentId,
                FeeType.TUITION,
                new BigDecimal("5000.00"),
                new BigDecimal("5000.00"),
                "Term 2 · 2026",
                LocalDate.now().plusMonths(2)
        );
        feeLine = feeLineRepository.save(feeLine);

        SelectedDueDto due = new SelectedDueDto(
                feeLine.getId(),
                new BigDecimal("5000.00")
        );

        // Submit EPP payment with Debit Card BIN prefix "5078..."
        PaymentSettleRequest request = new PaymentSettleRequest(
                idempotencyKey,
                guardianId,
                PaymentMethod.EPP_INSTALMENTS,
                List.of(due),
                new BigDecimal("5000.00"),
                new EppSelectionDto(12),
                "5078000011112222",
                null
        );

        String json = objectMapper.writeValueAsString(request);

        mockMvc.perform(post("/api/v1/payments/settle")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(result -> {
                    int status = result.getResponse().getStatus();
                    assertTrue(status == 400 || status == 422, "Expected 400 or 422 for EPP debit card block");
                })
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Debit Card EPP Block")));

        // Verify no charge or EPP schedule created
        verify(bankGatewayAdapter, never()).chargeCard(any(), any());
    }

    @Test
    void testIdempotencyTamperProtection_rejectsModifiedPayload() throws Exception {
        UUID institutionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();
        UUID guardianId = UUID.randomUUID();
        String idempotencyKey = "TAMPER-KEY-1";

        FeeLine feeLine = new FeeLine(
                institutionId,
                studentId,
                FeeType.TUITION,
                new BigDecimal("15000.00"),
                new BigDecimal("15000.00"),
                "Term 2 · 2026",
                LocalDate.now().plusMonths(2)
        );
        feeLine = feeLineRepository.save(feeLine);

        SelectedDueDto due1 = new SelectedDueDto(
                feeLine.getId(),
                new BigDecimal("5000.00")
        );

        // 1. Submit valid initial request for 5000.00 EGP
        PaymentSettleRequest request1 = new PaymentSettleRequest(
                idempotencyKey,
                guardianId,
                PaymentMethod.CREDIT_CARD,
                List.of(due1),
                new BigDecimal("5000.00"),
                null,
                "4111222233334444",
                null
        );

        mockMvc.perform(post("/api/v1/payments/settle")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request1)))
                .andExpect(status().isOk());

        // 2. Submit SECOND request using EXACT SAME Idempotency-Key but modified amount 10000.00 EGP
        SelectedDueDto due2 = new SelectedDueDto(
                feeLine.getId(),
                new BigDecimal("10000.00")
        );

        PaymentSettleRequest request2 = new PaymentSettleRequest(
                idempotencyKey,
                guardianId,
                PaymentMethod.CREDIT_CARD,
                List.of(due2),
                new BigDecimal("10000.00"),
                null,
                "4111222233334444",
                null
        );

        mockMvc.perform(post("/api/v1/payments/settle")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Idempotency Payload Tamper Violation")));
    }
}
