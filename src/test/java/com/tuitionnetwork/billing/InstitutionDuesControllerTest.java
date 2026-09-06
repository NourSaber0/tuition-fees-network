package com.tuitionnetwork.billing;

import com.example.demo.DemoApplication;
import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.billing.dto.StudentFeeLineDto;
import com.tuitionnetwork.billing.service.BillingFeeCommandService;
import com.tuitionnetwork.billing.service.BillingFeeQueryService;
import com.tuitionnetwork.common.exceptions.PendingBusinessRuleException;
import com.tuitionnetwork.identity.domain.InstitutionAdmin;
import com.tuitionnetwork.identity.repository.InstitutionAdminRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
class InstitutionDuesControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private BillingFeeQueryService billingFeeQueryService;

    @MockitoBean
    private BillingFeeCommandService billingFeeCommandService;

    @MockitoBean
    private InstitutionAdminRepository institutionAdminRepository;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(username = "emp@cibeg.com", roles = {"BACK_OFFICE"})
    void testLedgerRbacEnforcement_bankEmployee_isForbidden() throws Exception {
        UUID institutionId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        // Bank employee cannot access institution student dues endpoint
        mockMvc.perform(get("/api/v1/institutions/{id}/students/{studentId}/dues", institutionId, studentId))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@nile.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void getInstitutionDues_returnsFeesAndLogsAudit() throws Exception {
        UUID instId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        StudentFeeLineDto dto = new StudentFeeLineDto(
                UUID.randomUUID(),
                studentId,
                instId,
                "Tuition",
                "Term 2 · 2026",
                new BigDecimal("15000.00"),
                BigDecimal.ZERO,
                new BigDecimal("15000.00"),
                "EGP",
                "OUTSTANDING",
                LocalDate.now().plusMonths(2)
        );

        when(billingFeeQueryService.findFeesByInstitution(instId)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/institutions/{id}/dues", instId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].feeType").value("Tuition"));

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @WithMockUser(username = "admin@nile.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void getStudentDuesAtInstitution_returnsStudentFeesAndLogsAudit() throws Exception {
        UUID instId = UUID.randomUUID();
        UUID studentId = UUID.randomUUID();

        StudentFeeLineDto dto = new StudentFeeLineDto(
                UUID.randomUUID(),
                studentId,
                instId,
                "Bus subscription",
                "Term 2 · 2026",
                new BigDecimal("4000.00"),
                new BigDecimal("4000.00"),
                BigDecimal.ZERO,
                "EGP",
                "PAID",
                LocalDate.now().plusMonths(2)
        );

        when(billingFeeQueryService.findFeesByInstitutionAndStudent(instId, studentId)).thenReturn(List.of(dto));

        mockMvc.perform(get("/api/v1/institutions/{id}/students/{studentId}/dues", instId, studentId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].status").value("PAID"));

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @WithMockUser(username = "admin@nile.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void cancelFeeLine_success_returnsCancelledStatus() throws Exception {
        UUID instId = UUID.randomUUID();
        UUID feeLineId = UUID.randomUUID();

        doNothing().when(billingFeeCommandService).cancelFeeLine(instId, feeLineId);

        mockMvc.perform(post("/api/v1/institutions/{id}/dues/{feeLineId}/cancel", instId, feeLineId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"))
                .andExpect(jsonPath("$.message").value("Fee line " + feeLineId + " has been successfully cancelled."));

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @WithMockUser(username = "admin@nile.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void cancelFeeLine_guardrail4_crossInstitution_rejectedWith422() throws Exception {
        UUID instId = UUID.randomUUID();
        UUID feeLineId = UUID.randomUUID();

        doThrow(new PendingBusinessRuleException(
                "Pending Business Rule: Cross-Institution Data Bleed is strictly prohibited."
        )).when(billingFeeCommandService).cancelFeeLine(instId, feeLineId);

        mockMvc.perform(post("/api/v1/institutions/{id}/dues/{feeLineId}/cancel", instId, feeLineId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PENDING_BUSINESS_RULE"))
                .andExpect(jsonPath("$.message").value("Pending Business Rule: Cross-Institution Data Bleed is strictly prohibited."));
    }

    @Test
    @WithMockUser(username = "admin@nile.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void cancelFeeLine_guardrail5_midYearEppCancellation_rejectedWith422() throws Exception {
        UUID instId = UUID.randomUUID();
        UUID feeLineId = UUID.randomUUID();

        doThrow(new PendingBusinessRuleException(
                "Pending Business Rule: Mid-Year EPP Cancellation is undefined. FeeLine " + feeLineId + " is actively locked in a 12-month EPP schedule and cannot be cancelled due to student withdrawal."
        )).when(billingFeeCommandService).cancelFeeLine(instId, feeLineId);

        mockMvc.perform(post("/api/v1/institutions/{id}/dues/{feeLineId}/cancel", instId, feeLineId))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PENDING_BUSINESS_RULE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Mid-Year EPP Cancellation is undefined")));
    }

    @Test
    @WithMockUser(username = "admin@nile.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void verifyInstitutionAccess_adminAccessingOtherInstitution_rejectedWith422() throws Exception {
        UUID schoolA = UUID.randomUUID();
        UUID schoolB = UUID.randomUUID();

        InstitutionAdmin admin = new InstitutionAdmin();
        admin.setInstitutionId(schoolA);
        admin.setEmail("admin@nile.edu.eg");

        when(institutionAdminRepository.findByEmail("admin@nile.edu.eg")).thenReturn(Optional.of(admin));

        // School A admin attempts to access School B
        mockMvc.perform(get("/api/v1/institutions/{id}/dues", schoolB))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("PENDING_BUSINESS_RULE"))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("Cross-Institution Data Bleed is strictly prohibited")));
    }
}
