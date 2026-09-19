package com.tuitionnetwork.audit;

import com.example.demo.DemoApplication;
import com.tuitionnetwork.audit.dto.AuditLogDto;
import com.tuitionnetwork.audit.dto.AuditLogStatsDto;
import com.tuitionnetwork.audit.service.AuditLogService;
import com.tuitionnetwork.common.dto.PageResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
class AuditLogControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private AuditLogService auditLogService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private AuditLogDto sampleDto() {
        AuditLogDto dto = new AuditLogDto();
        dto.setId(UUID.randomUUID());
        dto.setUser("admin@cibeg.com");
        dto.setRole("BANK_ADMIN");
        dto.setAction("RECON_RESOLVE");
        dto.setEntity("ReconciliationException");
        dto.setEntityId("EXC-001");
        dto.setSeverity("warning");
        dto.setTimestamp(LocalDateTime.now());
        dto.setIpAddress("10.0.0.1");
        return dto;
    }

    @Test
    @WithMockUser(username = "admin@cibeg.com", roles = {"BACK_OFFICE"})
    void list_returns200_withPageResponse() throws Exception {
        AuditLogDto dto = sampleDto();
        PageResponse<AuditLogDto> page = new PageResponse<>(List.of(dto), 0, 25, 1, 1);
        when(auditLogService.search(any(), any(), any(), any(), any(), anyInt(), anyInt())).thenReturn(page);

        mockMvc.perform(get("/api/v1/audit-logs"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].action").value("RECON_RESOLVE"))
                .andExpect(jsonPath("$.data[0].user").value("admin@cibeg.com"))
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    @WithMockUser(username = "admin@cibeg.com", roles = {"BACK_OFFICE"})
    void list_withFilters_passesParams() throws Exception {
        PageResponse<AuditLogDto> page = new PageResponse<>(List.of(), 0, 10, 0, 0);
        when(auditLogService.search(eq("RECON"), eq("BANK_ADMIN"), eq("critical"), eq("2026-09-01"), eq("2026-09-05"), eq(0), eq(10)))
                .thenReturn(page);

        mockMvc.perform(get("/api/v1/audit-logs")
                        .param("search", "RECON")
                        .param("role", "BANK_ADMIN")
                        .param("severity", "critical")
                        .param("dateFrom", "2026-09-01")
                        .param("dateTo", "2026-09-05")
                        .param("pageSize", "10"))
                .andExpect(status().isOk());

        verify(auditLogService).search(eq("RECON"), eq("BANK_ADMIN"), eq("critical"), eq("2026-09-01"), eq("2026-09-05"), eq(0), eq(10));
    }

    @Test
    @WithMockUser(username = "admin@cibeg.com", roles = {"BACK_OFFICE"})
    void getById_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        AuditLogDto dto = sampleDto();
        dto.setId(id);
        when(auditLogService.getById(id)).thenReturn(dto);

        mockMvc.perform(get("/api/v1/audit-logs/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id.toString()))
                .andExpect(jsonPath("$.action").value("RECON_RESOLVE"));
    }

    @Test
    @WithMockUser(username = "admin@cibeg.com", roles = {"BACK_OFFICE"})
    void getById_notFound_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(auditLogService.getById(id))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Audit log not found: " + id));

        mockMvc.perform(get("/api/v1/audit-logs/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "admin@cibeg.com", roles = {"BACK_OFFICE"})
    void stats_returns200() throws Exception {
        AuditLogStatsDto stats = new AuditLogStatsDto(15, 5, 6, 4, Map.of("CRITICAL", 5L, "WARNING", 6L, "INFO", 4L));
        when(auditLogService.stats(any(), any())).thenReturn(stats);

        mockMvc.perform(get("/api/v1/audit-logs/stats"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(15))
                .andExpect(jsonPath("$.critical").value(5))
                .andExpect(jsonPath("$.warning").value(6))
                .andExpect(jsonPath("$.info").value(4));
    }

    @Test
    @WithMockUser(username = "admin@cibeg.com", roles = {"BACK_OFFICE"})
    void roles_returns200() throws Exception {
        when(auditLogService.roles()).thenReturn(List.of("BANK_ADMIN", "OPS_SUPERVISOR", "SYSTEM"));

        mockMvc.perform(get("/api/v1/audit-logs/roles"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0]").value("BANK_ADMIN"))
                .andExpect(jsonPath("$[1]").value("OPS_SUPERVISOR"));
    }

    @Test
    @WithMockUser(username = "admin@cibeg.com", roles = {"BACK_OFFICE"})
    void export_returns200_withCsvContent() throws Exception {
        byte[] csv = "id,timestamp,user,role,action,entity,entityId,severity,ipAddress\n".getBytes(StandardCharsets.UTF_8);
        when(auditLogService.export(any(), any(), any(), any(), any())).thenReturn(csv);

        mockMvc.perform(get("/api/v1/audit-logs/export"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", org.hamcrest.Matchers.containsString("text/csv")))
                .andExpect(content().string("id,timestamp,user,role,action,entity,entityId,severity,ipAddress\n"));
    }

    @Test
    @WithMockUser(username = "parent@example.com", roles = {"GUARDIAN"})
    void guardian_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs")).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/audit-logs")).andExpect(status().isUnauthorized());
    }
}
