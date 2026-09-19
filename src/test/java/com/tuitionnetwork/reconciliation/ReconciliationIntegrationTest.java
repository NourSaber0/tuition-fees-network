package com.tuitionnetwork.reconciliation;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.reconciliation.domain.ReconciliationException;
import com.tuitionnetwork.reconciliation.domain.ReconciliationRun;
import com.tuitionnetwork.reconciliation.dto.AssignRequest;
import com.tuitionnetwork.reconciliation.dto.ResolutionRequest;
import com.tuitionnetwork.reconciliation.dto.TriggerRunRequest;
import com.tuitionnetwork.reconciliation.repository.ReconciliationExceptionRepository;
import com.tuitionnetwork.reconciliation.repository.ReconciliationRunRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.UUID;

import static org.hamcrest.Matchers.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
@Transactional
class ReconciliationIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ReconciliationRunRepository runRepository;

    @Autowired
    private ReconciliationExceptionRepository exceptionRepository;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private ReconciliationRun testRun;
    private ReconciliationException testException;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders
                .webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        testRun = new ReconciliationRun("Matched");
        testRun.setInstitution("Cairo American College");
        testRun.setInstitutionType("School");
        testRun.setRunDate(LocalDate.now());
        testRun.setTxCount(100);
        testRun.setTotalTransactions(100);
        testRun.setMatchedCount(98);
        testRun.setExceptionCount(2);
        testRun.setBankAmountEGP(500000L);
        testRun.setSystemAmountEGP(500000L);
        testRun.setSchoolAmountEGP(500000L);
        testRun.setCreatedAt(LocalDateTime.now());
        testRun = runRepository.save(testRun);

        testException = new ReconciliationException();
        testException.setReconciliationRunId(testRun.getId());
        testException.setTxRef("TXN-TEST-12345");
        testException.setInstitution("Cairo American College");
        testException.setInstitutionType("School");
        testException.setBankAmountEGP(4000L);
        testException.setSystemAmountEGP(8000L);
        testException.setSchoolAmountEGP(8000L);
        testException.setDifferenceEGP(4000L);
        testException.setType("Amount Mismatch");
        testException.setDate(LocalDate.now());
        testException.setStatus("Open");
        testException.setPriority("High");
        testException.setAssignedTo("Rania Mostafa");
        testException.setTxStatus("SUCCESS");
        testException.setPayMethod("CREDIT_CARD");
        testException.setBankRef("BANK-REF-9921");
        testException.setBankStatus("SETTLED");
        testException.setReason("Discrepancy in batch deduction");
        testException.setCreatedAt(LocalDateTime.now());
        testException = exceptionRepository.save(testException);
    }

    @Test
    @WithMockUser(roles = "BACK_OFFICE")
    void summary_asBackOffice_returnsSummaryMetrics() throws Exception {
        mockMvc.perform(get("/api/v1/reconciliation/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.totalTransactions", greaterThanOrEqualTo(100)))
                .andExpect(jsonPath("$.matched", greaterThanOrEqualTo(98)))
                .andExpect(jsonPath("$.exceptions", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalRuns", greaterThanOrEqualTo(1)));
    }

    @Test
    @WithMockUser(roles = "BACK_OFFICE")
    void listRuns_withPaginationAndFilters_returnsPageResponse() throws Exception {
        mockMvc.perform(get("/api/v1/reconciliation/runs")
                        .param("page", "1")
                        .param("pageSize", "10")
                        .param("institution", "Cairo"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].institution", containsString("Cairo")))
                .andExpect(jsonPath("$.pageSize", is(10)))
                .andExpect(jsonPath("$.total", greaterThanOrEqualTo(1)))
                .andExpect(jsonPath("$.totalPages", greaterThanOrEqualTo(1)));
    }

    @Test
    @WithMockUser(roles = "BACK_OFFICE")
    void getRunById_returnsDetailAndTransactions() throws Exception {
        mockMvc.perform(get("/api/v1/reconciliation/runs/" + testRun.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(testRun.getId().toString())))
                .andExpect(jsonPath("$.institution", is("Cairo American College")))
                .andExpect(jsonPath("$.transactions", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    @WithMockUser(roles = "BACK_OFFICE")
    void getRunById_notFound_returns404() throws Exception {
        mockMvc.perform(get("/api/v1/reconciliation/runs/" + UUID.randomUUID()))
                .andExpect(status().isNotFound());
    }

    @Test
    @WithMockUser(roles = "BACK_OFFICE")
    void triggerRun_returnsAccepted() throws Exception {
        String json = "{\"date\":\"2026-09-07\"}";

        mockMvc.perform(post("/api/v1/reconciliation/runs")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.id", notNullValue()))
                .andExpect(jsonPath("$.status", is("Matched")))
                .andExpect(jsonPath("$.totalTransactions", is(120)));
    }

    @Test
    @WithMockUser(roles = "BACK_OFFICE")
    void listExceptions_withFilters_returnsFilteredList() throws Exception {
        mockMvc.perform(get("/api/v1/reconciliation/exceptions")
                        .param("status", "Open")
                        .param("priority", "High")
                        .param("includeResolved", "false"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(greaterThanOrEqualTo(1))))
                .andExpect(jsonPath("$.data[0].status", is("Open")))
                .andExpect(jsonPath("$.data[0].priority", is("High")))
                .andExpect(jsonPath("$.data[0].differenceEGP", is(4000)));
    }

    @Test
    @WithMockUser(roles = "BACK_OFFICE")
    void getExceptionById_returns3WayComparisonWorkflowAndSla() throws Exception {
        mockMvc.perform(get("/api/v1/reconciliation/exceptions/" + testException.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id", is(testException.getId().toString())))
                .andExpect(jsonPath("$.comparisonRows", hasSize(3)))
                .andExpect(jsonPath("$.comparisonRows[0].source", is("Bank Account Statement")))
                .andExpect(jsonPath("$.workflow", hasSize(4)))
                .andExpect(jsonPath("$.sla.percent", is(75)));
    }

    @Test
    @WithMockUser(roles = "BACK_OFFICE")
    void resolveException_missingResolutionAction_returns400() throws Exception {
        ResolutionRequest request = new ResolutionRequest("Resolved", "Rania Mostafa", "Mismatch confirmed", null, "REF-123", "Notes");

        mockMvc.perform(patch("/api/v1/reconciliation/exceptions/" + testException.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("resolution_action_required")));
    }

    @Test
    @WithMockUser(roles = "BACK_OFFICE")
    void resolveException_withAction_returnsUpdatedException() throws Exception {
        ResolutionRequest request = new ResolutionRequest(
                "Resolved",
                "Rania Mostafa",
                "Mismatch confirmed with bank gateway",
                "Manual ledger adjustment",
                "BANK-ADV-8842",
                "Customer paid remaining piastres at branch counter"
        );

        mockMvc.perform(patch("/api/v1/reconciliation/exceptions/" + testException.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status", is("Resolved")))
                .andExpect(jsonPath("$.resolutionAction", is("Manual ledger adjustment")))
                .andExpect(jsonPath("$.supportingReference", is("BANK-ADV-8842")))
                .andExpect(jsonPath("$.resolvedAt", notNullValue()));
    }

    @Test
    @WithMockUser(roles = "BACK_OFFICE")
    void assignException_updatesInvestigator() throws Exception {
        AssignRequest request = new AssignRequest("Tarek Al-Mansoor");

        mockMvc.perform(post("/api/v1/reconciliation/exceptions/" + testException.getId() + "/assign")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.assignedTo", is("Tarek Al-Mansoor")))
                .andExpect(jsonPath("$.status", is("Under Investigation")));
    }

    @Test
    @WithMockUser(roles = "BACK_OFFICE")
    void listAssignees_returnsListOfOfficers() throws Exception {
        mockMvc.perform(get("/api/v1/reconciliation/assignees"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasItem("Rania Mostafa")))
                .andExpect(jsonPath("$", hasItem("Tarek Al-Mansoor")));
    }

    @Test
    @WithMockUser(roles = "BACK_OFFICE")
    void exportReport_streamsCsvFile() throws Exception {
        mockMvc.perform(get("/api/v1/reconciliation/export").param("format", "csv"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Disposition", containsString("attachment; filename=reconciliation.csv")))
                .andExpect(content().contentTypeCompatibleWith("text/csv"))
                .andExpect(content().string(containsString("RUN ID,INSTITUTION,TYPE,DATE")))
                .andExpect(content().string(containsString("Cairo American College")));
    }

    @Test
    void unauthenticatedAccess_isDenied() throws Exception {
        mockMvc.perform(get("/api/v1/reconciliation/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(roles = "GUARDIAN")
    void wrongRoleAccess_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/reconciliation/summary"))
                .andExpect(status().isForbidden());
    }
}
