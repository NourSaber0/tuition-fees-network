package com.tuitionnetwork.mockbank;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.mockbank.dto.MoiValidateRequest;
import com.tuitionnetwork.mockbank.store.MockBankStore;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.is;
import static org.hamcrest.Matchers.notNullValue;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
class MoiValidationTest {

    private static final String API_KEY = "wit-intern-2026";

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private MockBankStore mockBankStore;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();
        mockBankStore.reset();
    }

    @Test
    void validate_missingApiKey_returns401() throws Exception {
        MoiValidateRequest request = new MoiValidateRequest("29805150101023", "Mona Samir", "CARD_ISSUANCE");

        mockMvc.perform(post("/api/v1/moi/validate")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("MISSING_API_KEY")))
                .andExpect(jsonPath("$.request_id", notNullValue()));
    }

    @Test
    void validate_invalidApiKey_returns401() throws Exception {
        MoiValidateRequest request = new MoiValidateRequest("29805150101023", "Mona Samir", "CARD_ISSUANCE");

        mockMvc.perform(post("/api/v1/moi/validate")
                        .header("X-API-Key", "wrong-key")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.error.code", is("INVALID_API_KEY")));
    }

    @Test
    void validate_validMonaSamir_returns200AndValid() throws Exception {
        MoiValidateRequest request = new MoiValidateRequest("29805150101023", "Mona Samir Abdelrahman", "CARD_ISSUANCE");

        mockMvc.perform(post("/api/v1/moi/validate")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(true)))
                .andExpect(jsonPath("$.national_id", is("29805150101023")))
                .andExpect(jsonPath("$.record_status", is("ACTIVE")))
                .andExpect(jsonPath("$.holder.full_name_en", is("Mona Samir Abdelrahman")))
                .andExpect(jsonPath("$.holder.gender", is("FEMALE")))
                .andExpect(jsonPath("$.holder.governorate", is("Cairo")))
                .andExpect(jsonPath("$.holder.birth_date", is("1998-05-15")))
                .andExpect(jsonPath("$.name_match.matched", is(true)))
                .andExpect(jsonPath("$.eligibility.is_adult", is(true)))
                .andExpect(jsonPath("$.eligibility.can_be_issued_card", is(true)))
                .andExpect(jsonPath("$.reasons").isEmpty());
    }

    @Test
    void validate_underMinimumAge_returnsValidFalseWithReason() throws Exception {
        MoiValidateRequest request = new MoiValidateRequest("31204010102041", "Malak Hany Sobhy", "CARD_ISSUANCE");

        mockMvc.perform(post("/api/v1/moi/validate")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.reasons", hasItem("UNDER_MINIMUM_AGE")))
                .andExpect(jsonPath("$.eligibility.can_be_issued_card", is(false)));
    }

    @Test
    void validate_deceasedCitizen_returnsDeceasedStatus() throws Exception {
        MoiValidateRequest request = new MoiValidateRequest("29805150188889", "Deceased Person", "CARD_ISSUANCE");

        mockMvc.perform(post("/api/v1/moi/validate")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.record_status", is("DECEASED")))
                .andExpect(jsonPath("$.reasons", hasItem("HOLDER_DECEASED")));
    }

    @Test
    void validate_blockedCitizen_returnsBlockedStatus() throws Exception {
        MoiValidateRequest request = new MoiValidateRequest("29805150199996", "Blocked Citizen", "CARD_ISSUANCE");

        mockMvc.perform(post("/api/v1/moi/validate")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.valid", is(false)))
                .andExpect(jsonPath("$.record_status", is("BLOCKED")))
                .andExpect(jsonPath("$.reasons", hasItem("RECORD_BLOCKED_BY_AUTHORITY")));
    }

    @Test
    void validate_invalidNationalIdFormat_returns400() throws Exception {
        MoiValidateRequest request = new MoiValidateRequest("123", "Wrong ID", "CARD_ISSUANCE");

        mockMvc.perform(post("/api/v1/moi/validate")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code", is("INVALID_NATIONAL_ID")));
    }

    @Test
    void getVerification_byIdAndList_returnsHistory() throws Exception {
        MoiValidateRequest request = new MoiValidateRequest("29511020204536", "Ahmed Tarek Mahmoud", "CARD_ISSUANCE");

        MvcResult result = mockMvc.perform(post("/api/v1/moi/validate")
                        .header("X-API-Key", API_KEY)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andReturn();

        String verId = objectMapper.readTree(result.getResponse().getContentAsString()).get("verification_id").asText();

        mockMvc.perform(get("/api/v1/moi/verifications/" + verId)
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.verification_id", is(verId)))
                .andExpect(jsonPath("$.holder.full_name_en", is("Ahmed Tarek Mahmoud")));

        mockMvc.perform(get("/api/v1/moi/verifications")
                        .header("X-API-Key", API_KEY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].verification_id", is(verId)));
    }
}
