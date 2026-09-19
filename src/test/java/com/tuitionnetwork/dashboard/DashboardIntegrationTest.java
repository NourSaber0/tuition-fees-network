package com.tuitionnetwork.dashboard;

import com.example.demo.DemoApplication;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
class DashboardIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    @WithMockUser(username = "emp@cibeg.com", roles = {"BACK_OFFICE"})
    void summary_isAccessibleToBackOffice() throws Exception {
        mockMvc().perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.kpis.totalStudents.value").exists())
                .andExpect(jsonPath("$.kpis.activeInstitutions.value").exists());
    }

    @Test
    @WithMockUser(username = "admin@nile.edu.eg", roles = {"INSTITUTION_ADMIN"})
    void summary_isForbiddenToInstitutionAdmin() throws Exception {
        mockMvc().perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isForbidden());
    }

    @Test
    void summary_isUnauthenticatedRejected() throws Exception {
        mockMvc().perform(get("/api/v1/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    @WithMockUser(username = "emp@cibeg.com", roles = {"BACK_OFFICE"})
    void weeklyCollections_returnsSevenDaySeries() throws Exception {
        mockMvc().perform(get("/api/v1/dashboard/collections/weekly").param("weekOf", "2026-08-25"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.series.length()").value(7))
                .andExpect(jsonPath("$.currency").value("EGP"));
    }

    @Test
    @WithMockUser(username = "emp@cibeg.com", roles = {"BACK_OFFICE"})
    void institutionStatus_returnsBreakdown() throws Exception {
        mockMvc().perform(get("/api/v1/dashboard/institution-status"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.breakdown").isArray());
    }

    @Test
    @WithMockUser(username = "emp@cibeg.com", roles = {"BACK_OFFICE"})
    void recentTransactions_returnsDataList() throws Exception {
        mockMvc().perform(get("/api/v1/dashboard/recent-transactions").param("limit", "6"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray());
    }
}
