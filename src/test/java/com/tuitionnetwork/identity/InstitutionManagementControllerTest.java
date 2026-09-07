package com.tuitionnetwork.identity;

import com.example.demo.DemoApplication;
import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.identity.domain.AccountStatus;
import com.tuitionnetwork.identity.domain.IntegrationStatus;
import com.tuitionnetwork.identity.domain.InstitutionType;
import com.tuitionnetwork.identity.domain.RegistrationStatus;
import com.tuitionnetwork.identity.dto.InstitutionApplicationDto;
import com.tuitionnetwork.identity.dto.InstitutionDetailDto;
import com.tuitionnetwork.identity.dto.InstitutionIntegrationDto;
import com.tuitionnetwork.identity.dto.InstitutionStudentDto;
import com.tuitionnetwork.identity.dto.InstitutionSummaryDto;
import com.tuitionnetwork.identity.service.InstitutionManagementService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
class InstitutionManagementControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private InstitutionManagementService service;

    @MockitoBean
    private AuditLogRepository auditLogRepository;

    private static final String VALID_BODY = """
            {
              "name": "Nile International School",
              "institutionType": "SCHOOL",
              "subType": "International",
              "city": "Cairo",
              "principalName": "Dr. Ahmad Fawzy",
              "phone": "+20 2 2516 0000",
              "email": "finance@cis.edu.eg",
              "registrationNumber": "MOEDU-SCH-2026-0831",
              "studentCount": 850
            }
            """;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    private InstitutionDetailDto detail(UUID id, RegistrationStatus regStatus, AccountStatus accountStatus) {
        return new InstitutionDetailDto(
                id, "Nile International School", "SCH-001", InstitutionType.SCHOOL, "International",
                "Cairo", "Dr. Ahmad Fawzy", "+20 2 2516 0000", "finance@cis.edu.eg",
                "MOEDU-SCH-2026-0831", 850, regStatus, accountStatus, IntegrationStatus.NOT_INTEGRATED,
                null, null, LocalDate.now());
    }

    // ── Happy paths ─────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void register_returns201_andWritesAudit() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.register(any())).thenReturn(detail(id, RegistrationStatus.PENDING, AccountStatus.INACTIVE));

        mockMvc.perform(post("/api/v1/institutions")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.registrationStatus").value("PENDING"))
                .andExpect(jsonPath("$.accountStatus").value("INACTIVE"));

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void list_returns200_withPageEnvelope() throws Exception {
        InstitutionSummaryDto row = new InstitutionSummaryDto(
                UUID.randomUUID(), "Cairo International School", "SCH-001", "Cairo",
                InstitutionType.SCHOOL, "International", "MOEDU-SCH-2024-0112", 850,
                RegistrationStatus.APPROVED, AccountStatus.ACTIVE, IntegrationStatus.INTEGRATED, LocalDate.now());
        when(service.list(any(), any(), any(), any(), anyInt(), anyInt()))
                .thenReturn(new PageResponse<>(List.of(row), 0, 25, 1, 1));

        mockMvc.perform(get("/api/v1/institutions").param("search", "Cairo").param("pageSize", "25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.pageSize").value(25))
                .andExpect(jsonPath("$.size").value(25))
                .andExpect(jsonPath("$.data[0].code").value("SCH-001"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void approve_returns200_andWritesAudit() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.approve(id)).thenReturn(detail(id, RegistrationStatus.APPROVED, AccountStatus.ACTIVE));

        mockMvc.perform(post("/api/v1/institutions/{id}/approve", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationStatus").value("APPROVED"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void reject_withReason_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.reject(eq(id), any()))
                .thenReturn(detail(id, RegistrationStatus.REJECTED, AccountStatus.INACTIVE));

        mockMvc.perform(post("/api/v1/institutions/{id}/reject", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reason\":\"Incomplete documentation\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationStatus").value("REJECTED"));

        verify(auditLogRepository).save(any(AuditLog.class));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void deactivate_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.deactivate(id)).thenReturn(detail(id, RegistrationStatus.APPROVED, AccountStatus.INACTIVE));

        mockMvc.perform(post("/api/v1/institutions/{id}/deactivate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accountStatus").value("INACTIVE"));
    }

    // ── Error mapping ──────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void register_missingRequiredFields_returns400_validation() throws Exception {
        mockMvc.perform(post("/api/v1/institutions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"))
                .andExpect(jsonPath("$.fields").isNotEmpty());

        verify(auditLogRepository, never()).save(any());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void register_duplicateRegistrationNumber_returns409() throws Exception {
        when(service.register(any())).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "already exists"));

        mockMvc.perform(post("/api/v1/institutions")
                        .contentType(MediaType.APPLICATION_JSON).content(VALID_BODY))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("already exists"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void get_unknownId_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.get(id)).thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found: " + id));

        mockMvc.perform(get("/api/v1/institutions/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void approve_wrongState_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.approve(id)).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "Cannot approve an application in state APPROVED."));

        mockMvc.perform(post("/api/v1/institutions/{id}/approve", id))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void reject_missingReason_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/institutions/{id}/reject", UUID.randomUUID())
                        .contentType(MediaType.APPLICATION_JSON).content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void activate_notApproved_returns409() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.activate(id)).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "Only an APPROVED institution can be activated (current: PENDING)."));

        mockMvc.perform(post("/api/v1/institutions/{id}/activate", id))
                .andExpect(status().isConflict());
    }

    // ── Sub-resources: students / application / integration ────────────────

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void students_returns200_withRoster() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.students(id)).thenReturn(List.of(new InstitutionStudentDto(
                UUID.randomUUID(), "Ahmed Hassan", LocalDate.of(2010, 5, 1),
                new java.math.BigDecimal("18000"), new java.math.BigDecimal("5000"),
                new java.math.BigDecimal("13000"), "Partial")));

        mockMvc.perform(get("/api/v1/institutions/{id}/students", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fullName").value("Ahmed Hassan"))
                .andExpect(jsonPath("$[0].status").value("Partial"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void application_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.application(id)).thenReturn(new InstitutionApplicationDto(
                id, "Cairo International School", "MOEDU-SCH-2024-0112", InstitutionType.SCHOOL,
                "International", "Cairo", "Dr. Ahmad Fawzy", "+20 2 2516 0000", "admin@cis.edu.eg",
                850, LocalDate.now(), RegistrationStatus.PENDING, null,
                List.of("Commercial Registry", "Tax Card"), false));

        mockMvc.perform(get("/api/v1/institutions/{id}/application", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.registrationNumber").value("MOEDU-SCH-2024-0112"))
                .andExpect(jsonPath("$.documentsTracked").value(false))
                .andExpect(jsonPath("$.requiredDocuments").isArray());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void integration_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.integration(id)).thenReturn(new InstitutionIntegrationDto(
                id, IntegrationStatus.NOT_INTEGRATED, "This institution has not been enrolled...", false));

        mockMvc.perform(get("/api/v1/institutions/{id}/integration", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("NOT_INTEGRATED"))
                .andExpect(jsonPath("$.configured").value(false));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void students_unknownInstitution_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(service.students(id)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Institution not found: " + id));

        mockMvc.perform(get("/api/v1/institutions/{id}/students", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    // ── RBAC ──────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "parent@example.com", roles = {"GUARDIAN"})
    void guardian_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/institutions")).andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "admin@nile.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void institutionAdmin_isForbidden() throws Exception {
        mockMvc.perform(post("/api/v1/institutions/{id}/approve", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/institutions")).andExpect(status().isUnauthorized());
    }
}
