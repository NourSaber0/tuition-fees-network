package com.tuitionnetwork.t24;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.identity.domain.AccountStatus;
import com.tuitionnetwork.identity.domain.BankEmployee;
import com.tuitionnetwork.identity.domain.BankRole;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.InstitutionAdmin;
import com.tuitionnetwork.identity.repository.BankEmployeeRepository;
import com.tuitionnetwork.identity.repository.InstitutionAdminRepository;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.security.JwtTokenProvider;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.identity.security.UserRole;
import com.tuitionnetwork.payments.domain.PaymentMethod;
import com.tuitionnetwork.payments.event.PaymentCapturedEvent;
import com.tuitionnetwork.t24.dto.T24BillingDto.RequestBillingRequest;
import com.tuitionnetwork.t24.dto.T24BillingDto.UpdateBillingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
public class T24CustomerBillingIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private BankEmployeeRepository bankEmployeeRepository;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private InstitutionAdminRepository institutionAdminRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    private MockMvc mockMvc;
    private String tokenBankAdmin;
    private String tokenSchoolAdmin;

    @BeforeEach
    void setUp() {
        objectMapper.findAndRegisterModules();
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        institutionAdminRepository.deleteAll();
        bankEmployeeRepository.deleteAll();
        institutionRepository.deleteAll();

        // 1. Bank Admin
        BankEmployee bankAdmin = new BankEmployee(
                "Mohamed Ali",
                "mohamed.ali@cibeg.com",
                "EMP-001",
                "Password123!",
                "Operations",
                "bank-admin"
        );
        bankAdmin.setStatus("Active");
        bankAdmin = bankEmployeeRepository.save(bankAdmin);

        tokenBankAdmin = jwtTokenProvider.generateToken(new SecurityUserPrincipal(
                bankAdmin.getId(), bankAdmin.getEmail(), bankAdmin.getName(),
                UserRole.ROLE_BACK_OFFICE,
                List.of(UserRole.ROLE_BACK_OFFICE),
                (UUID) null
        ));

        // 2. School Admin
        Institution school = new Institution("Cairo International School", "SCH-001", "SCHOOL_ABSORBS");
        school.setAccountStatus(AccountStatus.ACTIVE);
        school = institutionRepository.save(school);

        InstitutionAdmin schoolAdmin = new InstitutionAdmin(school.getId(), "Amr Hassan", "amr.hassan@cis.edu.eg", "Password123!", "School Admin");
        schoolAdmin.setStatus("Active");
        schoolAdmin = institutionAdminRepository.save(schoolAdmin);

        tokenSchoolAdmin = jwtTokenProvider.generateToken(new SecurityUserPrincipal(
                schoolAdmin.getId(), schoolAdmin.getEmail(), schoolAdmin.getName(),
                UserRole.ROLE_SCHOOL_ADMIN,
                List.of(UserRole.ROLE_SCHOOL_ADMIN, UserRole.ROLE_INSTITUTION_ADMIN),
                school.getId()
        ));
    }

    @Test
    @DisplayName("GET /api/v1/t24/billing/retrieve: Invokes RetrieveCustomerBillingProcedure via REST adapter")
    void testRetrieveBillingAdapter() throws Exception {
        mockMvc.perform(get("/api/v1/t24/billing/retrieve?nationalId=29805150101023&accountNumber=100012345678")
                        .header("Authorization", "Bearer " + tokenBankAdmin))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("SUCCESS"))
                .andExpect(jsonPath("$.customerNumber", notNullValue()))
                .andExpect(jsonPath("$.accountNumber").value("100012345678"))
                .andExpect(jsonPath("$.items", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.items[0].billingId", startsWith("T24-BILL-")))
                .andExpect(jsonPath("$.totalOutstanding", notNullValue()));
    }

    @Test
    @DisplayName("POST /api/v1/t24/billing/request: Invokes RequestCustomerBillingProcedure via REST adapter")
    void testRequestBillingAdapter() throws Exception {
        RequestBillingRequest req = new RequestBillingRequest(
                "SCH-001", "31005120104921", "Yousef Adel", "Tuition",
                new BigDecimal("18000.00"), "EGP", "Term 1 2026/27", LocalDate.of(2026, 10, 15)
        );

        mockMvc.perform(post("/api/v1/t24/billing/request")
                        .header("Authorization", "Bearer " + tokenSchoolAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CREATED"))
                .andExpect(jsonPath("$.billingId", startsWith("T24-BILL-")))
                .andExpect(jsonPath("$.message", containsString("31005120104921")));
    }

    @Test
    @DisplayName("POST /api/v1/t24/billing/update: Invokes UpdateCustomerBillingProcedure via REST adapter")
    void testUpdateBillingAdapter() throws Exception {
        UpdateBillingRequest req = new UpdateBillingRequest(
                "T24-BILL-00101", new BigDecimal("18000.00"), "CREDIT_CARD",
                "TXN-2026-0981", BigDecimal.ZERO
        );

        mockMvc.perform(post("/api/v1/t24/billing/update")
                        .header("Authorization", "Bearer " + tokenBankAdmin)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(req)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("UPDATED"))
                .andExpect(jsonPath("$.billingId").value("T24-BILL-00101"))
                .andExpect(jsonPath("$.newRemainingAmount").value(0.00));
    }

    @Test
    @DisplayName("GET /api/v1/t24/billing/wsdl: Serves mock WSDL description")
    void testGetWsdl() throws Exception {
        mockMvc.perform(get("/api/v1/t24/billing/wsdl")
                        .header("Authorization", "Bearer " + tokenBankAdmin))
                .andExpect(status().isOk())
                .andExpect(content().contentTypeCompatibleWith(MediaType.APPLICATION_XML))
                .andExpect(content().string(containsString("CustomerBillingService")))
                .andExpect(content().string(containsString("RetrieveCustomerBillingProcedure")));
    }

    @Test
    @DisplayName("Security: 401 Unauthorized for unauthenticated access")
    void testSecurityUnauthenticated() throws Exception {
        mockMvc.perform(get("/api/v1/t24/billing/retrieve?nationalId=29805150101023"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/t24/billing/request"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/v1/t24/billing/update"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @DisplayName("Event Listener: PaymentCapturedEvent safely triggers T24 billing update")
    void testPaymentCapturedEventListener() {
        PaymentCapturedEvent event = new PaymentCapturedEvent(
                UUID.randomUUID(),
                UUID.randomUUID(),
                new BigDecimal("5000.00"),
                PaymentMethod.CIB_ACCOUNT,
                "IDEMP-" + System.currentTimeMillis(),
                "TXN-CIB-9921",
                "AUTH-0012",
                List.of(UUID.randomUUID()),
                null,
                LocalDateTime.now()
        );

        assertDoesNotThrow(() -> eventPublisher.publishEvent(event));
    }
}
