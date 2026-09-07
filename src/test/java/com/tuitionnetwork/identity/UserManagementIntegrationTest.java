package com.tuitionnetwork.identity;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.identity.dto.users.CreateBankUserRequest;
import com.tuitionnetwork.identity.dto.users.UpdateBankUserRequest;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
@WithMockUser(username = "admin@cibeg.com", roles = {"BACK_OFFICE"})
class UserManagementIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc() {
        return MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
    }

    @Test
    void listUsers_includesSeededAdmin() throws Exception {
        mockMvc().perform(get("/api/v1/users"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").isArray())
                .andExpect(jsonPath("$.data[?(@.email=='mohamed.ali@cibeg.com')]").exists());
    }

    @Test
    void getSummary_countsSeededAdminUnderBankAdmin() throws Exception {
        mockMvc().perform(get("/api/v1/users/summary"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$['Bank Admin']").value(org.hamcrest.Matchers.greaterThanOrEqualTo(1)));
    }

    @Test
    void createUser_autoGeneratesUsername_thenFetchesDetail() throws Exception {
        String email = "new.user." + UUID.randomUUID() + "@cibeg.com";
        CreateBankUserRequest request = new CreateBankUserRequest("Layla Farouk", email, null, "bank-operations", "Operations");

        MvcResult result = mockMvc().perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.username").exists())
                .andExpect(jsonPath("$.role").value("Operations"))
                .andExpect(jsonPath("$.status").value("Active"))
                .andReturn();

        String id = objectMapper.readTree(result.getResponse().getContentAsString()).get("id").asText();

        mockMvc().perform(get("/api/v1/users/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value(email));
    }

    @Test
    void createUser_rejectsDuplicateEmail() throws Exception {
        String email = "dup.user." + UUID.randomUUID() + "@cibeg.com";
        CreateBankUserRequest request = new CreateBankUserRequest("Dup User", email, null, "bank-finance", "Finance");
        String body = objectMapper.writeValueAsString(request);

        mockMvc().perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isCreated());

        mockMvc().perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("email_exists"));
    }

    @Test
    void updateThenDeactivateThenActivateUser() throws Exception {
        String email = "lifecycle." + UUID.randomUUID() + "@cibeg.com";
        CreateBankUserRequest createRequest = new CreateBankUserRequest("Lifecycle User", email, null, "bank-operations", "Operations");
        MvcResult createResult = mockMvc().perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        UpdateBankUserRequest updateRequest = new UpdateBankUserRequest(null, null, null, "bank-finance", "Finance");
        mockMvc().perform(patch("/api/v1/users/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("Finance"))
                .andExpect(jsonPath("$.department").value("Finance"));

        mockMvc().perform(post("/api/v1/users/{id}/deactivate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Inactive"));

        mockMvc().perform(post("/api/v1/users/{id}/activate", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Active"));
    }

    @Test
    void resetPassword_returnsConfirmationMessage() throws Exception {
        String email = "reset." + UUID.randomUUID() + "@cibeg.com";
        CreateBankUserRequest createRequest = new CreateBankUserRequest("Reset User", email, null, "bank-operations", "Operations");
        MvcResult createResult = mockMvc().perform(post("/api/v1/users")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(createRequest)))
                .andExpect(status().isCreated())
                .andReturn();
        String id = objectMapper.readTree(createResult.getResponse().getContentAsString()).get("id").asText();

        mockMvc().perform(post("/api/v1/users/{id}/reset-password", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password reset email sent"));
    }

    @Test
    void getUser_unknownId_returnsNotFoundWithContractErrorShape() throws Exception {
        mockMvc().perform(get("/api/v1/users/{id}", UUID.randomUUID()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.error.code").value("user_not_found"));
    }
}
