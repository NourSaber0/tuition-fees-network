package com.tuitionnetwork.ingestion;

import com.example.demo.DemoApplication;
import com.tuitionnetwork.ingestion.dto.FeeSubmissionDetailDto;
import com.tuitionnetwork.ingestion.dto.FeeSubmissionRowErrorDto;
import com.tuitionnetwork.ingestion.dto.FeeSubmissionSummaryDto;
import com.tuitionnetwork.ingestion.service.IngestionQueryService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpStatus;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
class InstitutionFeeSubmissionsControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private IngestionQueryService ingestionQueryService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void list_returns200() throws Exception {
        UUID instId = UUID.randomUUID();
        when(ingestionQueryService.listForInstitution(instId)).thenReturn(List.of(
                new FeeSubmissionSummaryDto(UUID.randomUUID(), "term1.csv", 100, 98, 2, "PARTIAL",
                        LocalDateTime.now())));

        mockMvc.perform(get("/api/v1/institutions/{id}/fee-submissions", instId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].fileName").value("term1.csv"))
                .andExpect(jsonPath("$[0].status").value("PARTIAL"))
                .andExpect(jsonPath("$[0].successfulRows").value(98));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void detail_returns200_withErrorReport() throws Exception {
        UUID instId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        when(ingestionQueryService.getForInstitution(instId, subId)).thenReturn(new FeeSubmissionDetailDto(
                subId, instId, "term1.csv", 100, 98, 2, "PARTIAL", LocalDateTime.now(),
                List.of(new FeeSubmissionRowErrorDto(14, "Invalid National ID: must be exactly 14 digits", "abc,Tuition,1000,EGP,2026"))));

        mockMvc.perform(get("/api/v1/institutions/{id}/fee-submissions/{submissionId}", instId, subId))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.failedRows").value(2))
                .andExpect(jsonPath("$.errors[0].rowNumber").value(14));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void detail_unknownSubmission_returns404() throws Exception {
        UUID instId = UUID.randomUUID();
        UUID subId = UUID.randomUUID();
        when(ingestionQueryService.getForInstitution(eq(instId), eq(subId))).thenThrow(
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Fee submission not found: " + subId));

        mockMvc.perform(get("/api/v1/institutions/{id}/fee-submissions/{submissionId}", instId, subId))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error").value("NOT_FOUND"));
    }

    @Test
    @WithMockUser(username = "admin@nile.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void institutionAdmin_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/institutions/{id}/fee-submissions", UUID.randomUUID()))
                .andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/institutions/{id}/fee-submissions", UUID.randomUUID()))
                .andExpect(status().isUnauthorized());
    }
}
