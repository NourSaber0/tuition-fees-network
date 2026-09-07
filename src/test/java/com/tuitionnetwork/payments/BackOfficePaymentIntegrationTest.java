package com.tuitionnetwork.payments;

import com.example.demo.DemoApplication;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.identity.domain.Guardian;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.GuardianRepository;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.identity.service.IdentityResolverService;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
@Transactional
class BackOfficePaymentIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private PaymentRepository paymentRepository;

    @Autowired
    private FeeLineRepository feeLineRepository;

    @Autowired
    private StudentRepository studentRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private GuardianRepository guardianRepository;

    @Autowired
    private IdentityResolverService identityResolverService;

    private MockMvc mockMvc;
    private FeeLine sampleFee;
    private final String testNationalId = "29901011234567";

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Institution inst = new Institution("Modern Academy", "MOD-001", "ABSORBED");
        inst = institutionRepository.save(inst);

        String hmac = identityResolverService.computeHmacSha256(testNationalId);
        Guardian guardian = new Guardian(hmac, "enc", "Hassan Mahmoud", "hassan@example.com", "+201001234567", "pass", true);
        guardian = guardianRepository.save(guardian);

        Student student = new Student(guardian.getId(), inst.getId(), "shmac", "senc", "Kareem Hassan", LocalDate.of(2011, 4, 15));
        student = studentRepository.save(student);

        sampleFee = new FeeLine();
        sampleFee.setInstitutionId(inst.getId());
        sampleFee.setStudentId(student.getId());
        sampleFee.setFeeType(FeeType.TUITION);
        sampleFee.setTotalAmount(new BigDecimal("10000.00"));
        sampleFee.setPaidAmount(BigDecimal.ZERO);
        sampleFee.setRemainingAmount(new BigDecimal("10000.00"));
        sampleFee.setStatus(FeeStatus.OUTSTANDING);
        sampleFee.setCollectionPeriod("Term 1 - 2026");
        sampleFee.setDueDate(LocalDate.now().plusDays(15));
        sampleFee = feeLineRepository.save(sampleFee);
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void customerFees_returnsCustomerAndFees() throws Exception {
        mockMvc.perform(get("/api/v1/customers/fees").param("nationalId", testNationalId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.customer.name").value("Hassan Mahmoud"))
                .andExpect(jsonPath("$.customer.nationalIdMasked").value("299*******4567"))
                .andExpect(jsonPath("$.fees").isArray())
                .andExpect(jsonPath("$.fees[0].originalAmountEGP").value(10000.00))
                .andExpect(jsonPath("$.fees[0].eligible").value(true));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void customerFees_invalidNationalId_returns400() throws Exception {
        mockMvc.perform(get("/api/v1/customers/fees").param("nationalId", "12345"))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void customerFees_unknownCustomer_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/customers/fees").param("nationalId", "29999999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void processPayment_missingIdempotencyKey_returns400() throws Exception {
        String body = """
                {
                    "nationalId": "%s",
                    "feeIds": ["%s"],
                    "amountEGP": 5000.00,
                    "method": "CIB Credit Card"
                }
                """.formatted(testNationalId, sampleFee.getId());

        mockMvc.perform(post("/api/v1/payments")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void processPayment_zeroAmount_returns400() throws Exception {
        String body = """
                {
                    "nationalId": "%s",
                    "feeIds": ["%s"],
                    "amountEGP": 0,
                    "method": "CIB Credit Card"
                }
                """.formatted(testNationalId, sampleFee.getId());

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void processPayment_successfulCreditCardPayment_returns201() throws Exception {
        String idempKey = UUID.randomUUID().toString();
        String body = """
                {
                    "nationalId": "%s",
                    "feeIds": ["%s"],
                    "amountEGP": 5000.00,
                    "method": "CIB Credit Card",
                    "processedBy": "EMP-001"
                }
                """.formatted(testNationalId, sampleFee.getId());

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Successful"))
                .andExpect(jsonPath("$.amountPaidEGP").value(5000.00))
                .andExpect(jsonPath("$.isPartial").value(true))
                .andExpect(jsonPath("$.remainingBalanceEGP").value(5000.00))
                .andExpect(jsonPath("$.bankRef").exists())
                .andExpect(jsonPath("$.receiptRef").exists());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void processPayment_idempotentReplay_returnsExistingTransaction() throws Exception {
        String idempKey = UUID.randomUUID().toString();
        String body = """
                {
                    "nationalId": "%s",
                    "feeIds": ["%s"],
                    "amountEGP": 4000.00,
                    "method": "CIB Credit Card"
                }
                """.formatted(testNationalId, sampleFee.getId());

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountPaidEGP").value(4000.00));

        // Replay with exact same idempotency key
        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.amountPaidEGP").value(4000.00));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void processPayment_eppDebitCard_returns422() throws Exception {
        String body = """
                {
                    "nationalId": "%s",
                    "feeIds": ["%s"],
                    "amountEGP": 5000.00,
                    "method": "CIB Debit Card",
                    "creditPaymentType": "epp",
                    "eppTenor": 12
                }
                """.formatted(testNationalId, sampleFee.getId());

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", UUID.randomUUID().toString())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void getPaymentDetail_returnsDetail() throws Exception {
        Payment p = new Payment(UUID.randomUUID(), new BigDecimal("1000.00"), PaymentMethod.CIB_ACCOUNT, "idemp-detail-01");
        p.setStatus(PaymentStatus.CAPTURED);
        p = paymentRepository.save(p);

        mockMvc.perform(get("/api/v1/payments/{id}", p.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(p.getId().toString()))
                .andExpect(jsonPath("$.amountEGP").value(1000.00));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void getPaymentReceipt_returnsReceipt() throws Exception {
        Payment p = new Payment(UUID.randomUUID(), new BigDecimal("1000.00"), PaymentMethod.CIB_ACCOUNT, "idemp-rcp-01");
        p.setStatus(PaymentStatus.CAPTURED);
        p = paymentRepository.save(p);

        mockMvc.perform(get("/api/v1/payments/{id}/receipt", p.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paymentId").value(p.getId().toString()))
                .andExpect(jsonPath("$.amount").value(1000.00))
                .andExpect(jsonPath("$.receiptReference").exists());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void retryPayment_notFailed_returns400() throws Exception {
        Payment p = new Payment(UUID.randomUUID(), new BigDecimal("1000.00"), PaymentMethod.CIB_ACCOUNT, "idemp-not-failed");
        p.setStatus(PaymentStatus.CAPTURED);
        p = paymentRepository.save(p);

        mockMvc.perform(post("/api/v1/payments/{id}/retry", p.getId())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isBadRequest());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void retryPayment_failedPayment_returnsSuccess() throws Exception {
        Payment p = new Payment(UUID.randomUUID(), new BigDecimal("1000.00"), PaymentMethod.CIB_ACCOUNT, "idemp-failed-01");
        p.setStatus(PaymentStatus.FAILED);
        p = paymentRepository.save(p);

        mockMvc.perform(post("/api/v1/payments/{id}/retry", p.getId())
                        .header("Idempotency-Key", UUID.randomUUID().toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Successful"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void noRefundEndpoints_exist_perBusinessRule() throws Exception {
        // Asserting that refund endpoint is NOT found (404/405), strictly adhering to "no refund in any of the portals"
        mockMvc.perform(post("/api/v1/transactions/{id}/refund", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isNotFound());

        mockMvc.perform(post("/api/v1/transactions/{id}/reverse", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"test\"}"))
                .andExpect(status().isNotFound());
    }

    private FeeLine overdueFee(BigDecimal remaining) {
        FeeLine fl = new FeeLine();
        fl.setInstitutionId(sampleFee.getInstitutionId());
        fl.setStudentId(sampleFee.getStudentId());
        fl.setFeeType(FeeType.TUITION);
        fl.setTotalAmount(remaining);
        fl.setPaidAmount(BigDecimal.ZERO);
        fl.setRemainingAmount(remaining);
        fl.setStatus(FeeStatus.OUTSTANDING);
        fl.setCollectionPeriod("Term 1 - 2026");
        fl.setDueDate(LocalDate.now().minusDays(1));
        return feeLineRepository.save(fl);
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void processPayment_fullyClearsOverdueFeeAndPenalty_returnsBreakdown() throws Exception {
        FeeLine overdue = overdueFee(new BigDecimal("8000.00"));
        String idempKey = UUID.randomUUID().toString();
        String body = """
                {
                    "nationalId": "%s",
                    "feeIds": ["%s"],
                    "amountEGP": 8400.00,
                    "method": "CIB Credit Card",
                    "processedBy": "EMP-001"
                }
                """.formatted(testNationalId, overdue.getId());

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("Successful"))
                .andExpect(jsonPath("$.isPartial").value(false))
                .andExpect(jsonPath("$.remainingBalanceEGP").value(0.00))
                .andExpect(jsonPath("$.originalFeeEGP").value(8000.00))
                .andExpect(jsonPath("$.penaltyEGP").value(400.00))
                .andExpect(jsonPath("$.totalCollectedEGP").value(8400.00))
                .andExpect(jsonPath("$.penaltyAppliedAt").exists());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void processPayment_payingOnlyFeePortion_leavesPenaltyOutstanding() throws Exception {
        FeeLine overdue = overdueFee(new BigDecimal("8000.00"));
        String idempKey = UUID.randomUUID().toString();
        String body = """
                {
                    "nationalId": "%s",
                    "feeIds": ["%s"],
                    "amountEGP": 8000.00,
                    "method": "CIB Credit Card",
                    "processedBy": "EMP-001"
                }
                """.formatted(testNationalId, overdue.getId());

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.isPartial").value(true))
                .andExpect(jsonPath("$.remainingBalanceEGP").value(400.00))
                .andExpect(jsonPath("$.originalFeeEGP").value(8000.00))
                .andExpect(jsonPath("$.penaltyEGP").value(0.00));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void processPayment_amountExceedingTotalDue_returns400() throws Exception {
        FeeLine overdue = overdueFee(new BigDecimal("8000.00"));
        String idempKey = UUID.randomUUID().toString();
        String body = """
                {
                    "nationalId": "%s",
                    "feeIds": ["%s"],
                    "amountEGP": 9000.00,
                    "method": "CIB Credit Card",
                    "processedBy": "EMP-001"
                }
                """.formatted(testNationalId, overdue.getId());

        mockMvc.perform(post("/api/v1/payments")
                        .header("Idempotency-Key", idempKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isBadRequest());
    }
}
