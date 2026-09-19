package com.tuitionnetwork.payments;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.billing.domain.FeeLine;
import com.tuitionnetwork.billing.domain.FeeStatus;
import com.tuitionnetwork.billing.domain.FeeType;
import com.tuitionnetwork.billing.repository.FeeLineRepository;
import com.tuitionnetwork.identity.domain.AccountStatus;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.InstitutionType;
import com.tuitionnetwork.identity.domain.RegistrationStatus;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.payments.domain.Payment;
import com.tuitionnetwork.payments.domain.PaymentAllocation;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.domain.PaymentStatus;
import com.tuitionnetwork.payments.repository.PaymentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
@Transactional
class TransactionIntegrationTest {

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
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;
    private Payment samplePayment;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        Institution institution = new Institution("Cairo English School", "CES-001", "ABSORBED");
        institution = institutionRepository.save(institution);

        Student student = new Student("hash1", "enc1", "Youssef Ahmed", LocalDate.of(2010, 1, 1));
        student = studentRepository.save(student);

        FeeLine feeLine = new FeeLine();
        feeLine.setInstitutionId(institution.getId());
        feeLine.setStudentId(student.getId());
        feeLine.setFeeType(FeeType.TUITION);
        feeLine.setTotalAmount(new BigDecimal("15000.00"));
        feeLine.setPaidAmount(new BigDecimal("5000.00"));
        feeLine.setRemainingAmount(new BigDecimal("10000.00"));
        feeLine.setStatus(FeeStatus.PARTIALLY_PAID);
        feeLine.setCollectionPeriod("Term 1 2026");
        feeLine.setDueDate(LocalDate.now().plusMonths(1));
        feeLine = feeLineRepository.save(feeLine);

        Payment payment = new Payment(
                UUID.randomUUID(),
                new BigDecimal("5000.00"),
                PaymentMethod.CREDIT_CARD,
                "idemp-tx-001"
        );
        payment.setStatus(PaymentStatus.CAPTURED);
        payment.setTransactionReference("BNK-CIB-99881");
        payment.setAuthCode("A99881");
        payment.setCreatedAt(LocalDateTime.now().minusHours(2));

        PaymentAllocation allocation = new PaymentAllocation(payment, feeLine, new BigDecimal("5000.00"));
        payment.getAllocations().add(allocation);

        samplePayment = paymentRepository.save(payment);
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void list_isAccessibleToBackOffice_andReturnsPageEnvelope() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .param("pageSize", "10")
                        .param("page", "1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.pageSize").value(10))
                .andExpect(jsonPath("$.total").isNumber());
    }

    @Test
    @WithMockUser(username = "school@nile.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void list_isForbiddenToInstitutionAdmin() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isForbidden());
    }

    @Test
    void list_isUnauthorizedWhenNotLoggedIn() throws Exception {
        mockMvc.perform(get("/api/v1/transactions"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void list_filterByStatus_returnsMatching() throws Exception {
        mockMvc.perform(get("/api/v1/transactions")
                        .param("status", "Successful"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void tabCounts_returnsCounts() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/tab-counts"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.All").isNumber())
                .andExpect(jsonPath("$.Successful").isNumber())
                .andExpect(jsonPath("$.Pending").isNumber())
                .andExpect(jsonPath("$.Failed").isNumber());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void getDetail_returnsFullRecordWithTimeline() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/{id}", samplePayment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(samplePayment.getId().toString()))
                .andExpect(jsonPath("$.amountEGP").value(5000.00))
                .andExpect(jsonPath("$.timeline").isArray())
                .andExpect(jsonPath("$.timeline[0].label").value("Initiated"))
                .andExpect(jsonPath("$.timeline[0].done").value(true))
                .andExpect(jsonPath("$.timeline[1].label").value("Bank Authorisation"))
                .andExpect(jsonPath("$.allocations").isArray());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void getDetail_unknownId_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void exportCsv_returnsCsvFile() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/export").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.startsWith("text/csv")))
                .andExpect(content().string(org.hamcrest.Matchers.containsString("Transaction ID,Timestamp,Institution")));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void detail_includesDeadlineFields() throws Exception {
        mockMvc.perform(get("/api/v1/transactions/{id}", samplePayment.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dueDate").exists())
                .andExpect(jsonPath("$.priority").exists())
                .andExpect(jsonPath("$.totalDueEGP").exists());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void list_filterByPriority_matchesTheTransactionsActualPriority() throws Exception {
        MvcResult detail = mockMvc.perform(get("/api/v1/transactions/{id}", samplePayment.getId()))
                .andExpect(status().isOk())
                .andReturn();
        String actualPriority = objectMapper.readTree(detail.getResponse().getContentAsString()).get("priority").asText();

        mockMvc.perform(get("/api/v1/transactions").param("priority", actualPriority))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + samplePayment.getId() + "')]").exists());

        mockMvc.perform(get("/api/v1/transactions").param("priority", "OVERDUE_" + UUID.randomUUID()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + samplePayment.getId() + "')]").doesNotExist());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void list_filterByDueBucketOverdue_excludesFutureFee() throws Exception {
        // The fixture's fee line is due a month out, so it must never show up under dueBucket=overdue
        mockMvc.perform(get("/api/v1/transactions").param("dueBucket", "overdue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[?(@.id=='" + samplePayment.getId() + "')]").doesNotExist());
    }
}
