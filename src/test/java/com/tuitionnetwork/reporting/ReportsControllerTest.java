package com.tuitionnetwork.reporting;

import com.example.demo.DemoApplication;
import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.reporting.dto.ReportCatalogueEntry;
import com.tuitionnetwork.reporting.dto.ReportHistoryEntry;
import com.tuitionnetwork.reporting.dto.ReportJobResponse;
import com.tuitionnetwork.reporting.dto.ReportPreview;
import com.tuitionnetwork.reporting.service.ReportsService;
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

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
class ReportsControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private ReportsService reportsService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    private ReportJobResponse job(UUID id) {
        return new ReportJobResponse(id, "payments", "READY", "report_payments_x.csv",
                "/api/v1/reports/jobs/" + id + "/download",
                new ReportPreview(List.of(), List.of(), "0 rows", "note"), LocalDateTime.now());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void catalogue_returns200() throws Exception {
        when(reportsService.catalogue()).thenReturn(List.of(new ReportCatalogueEntry(
                "payments", "Payments Report", "desc", "Payments", List.of("CSV"),
                false, List.of("feeType"), true, null)));

        mockMvc.perform(get("/api/v1/reports/catalogue"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value("payments"))
                .andExpect(jsonPath("$[0].available").value(true));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void generate_returns200_withReadyJob() throws Exception {
        UUID id = UUID.randomUUID();
        when(reportsService.generate(any())).thenReturn(job(id));

        mockMvc.perform(post("/api/v1/reports/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportId\":\"payments\",\"dateFrom\":\"2026-08-01\",\"dateTo\":\"2026-08-31\",\"format\":\"CSV\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("READY"))
                .andExpect(jsonPath("$.jobId").value(id.toString()));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void generate_missingReportId_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/reports/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dateFrom\":\"2026-08-01\",\"dateTo\":\"2026-08-31\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void generate_unavailableReport_returns422() throws Exception {
        when(reportsService.generate(any())).thenThrow(new ResponseStatusException(
                HttpStatus.UNPROCESSABLE_ENTITY, "The reconciliation module (Phase 5) is not implemented yet."));

        mockMvc.perform(post("/api/v1/reports/generate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"reportId\":\"reconciliation\",\"dateFrom\":\"2026-08-01\",\"dateTo\":\"2026-08-31\"}"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.error").value("UNPROCESSABLE_ENTITY"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void getJob_unknown_returns404() throws Exception {
        UUID id = UUID.randomUUID();
        when(reportsService.getJob(id)).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Report job not found: " + id));

        mockMvc.perform(get("/api/v1/reports/jobs/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void download_returnsCsvAttachment() throws Exception {
        UUID id = UUID.randomUUID();
        when(reportsService.download(id)).thenReturn(new ReportsService.DownloadPayload(
                "report_payments_x.csv", "a,b\r\n1,2\r\n".getBytes(), "text/csv"));

        mockMvc.perform(get("/api/v1/reports/jobs/{id}/download", id))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", "attachment; filename=\"report_payments_x.csv\""))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string("a,b\r\n1,2\r\n"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void history_returns200() throws Exception {
        when(reportsService.history(any(), anyInt(), anyInt())).thenReturn(new PageResponse<>(
                List.of(new ReportHistoryEntry(UUID.randomUUID(), "payments", "CSV",
                        "report_payments_x.csv", 42, LocalDateTime.now())),
                0, 25, 1, 1));

        mockMvc.perform(get("/api/v1/reports/history").param("reportId", "payments"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1))
                .andExpect(jsonPath("$.data[0].reportId").value("payments"));
    }

    @Test
    @WithMockUser(username = "parent@example.com", roles = {"GUARDIAN"})
    void guardian_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reports/catalogue")).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/reports/catalogue")).andExpect(status().isUnauthorized());
    }
}
