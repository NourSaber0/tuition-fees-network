package com.tuitionnetwork.settings;

import com.example.demo.DemoApplication;
import com.tuitionnetwork.settings.dto.EppSettingsDto;
import com.tuitionnetwork.settings.dto.FeeTypeSettingDto;
import com.tuitionnetwork.settings.dto.InstitutionSettingsDto;
import com.tuitionnetwork.settings.dto.NotificationSettingsDto;
import com.tuitionnetwork.settings.dto.PaymentStatusInfo;
import com.tuitionnetwork.settings.service.SettingsService;
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

import java.util.List;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest(classes = DemoApplication.class)
class SettingsControllerTest {

    @Autowired
    private WebApplicationContext context;

    private MockMvc mockMvc;

    @MockitoBean
    private SettingsService settingsService;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
    }

    // ── Fee types ────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void getFeeTypes_returns200() throws Exception {
        when(settingsService.getFeeTypes()).thenReturn(List.of(
                new FeeTypeSettingDto(UUID.randomUUID(), "Tuition Fee", "TUITION", false, true)));

        mockMvc.perform(get("/api/v1/settings/fee-types"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("TUITION"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void createFeeType_returns201() throws Exception {
        when(settingsService.createFeeType(any())).thenReturn(
                new FeeTypeSettingDto(UUID.randomUUID(), "Meals", "MEALS", true, true));

        mockMvc.perform(post("/api/v1/settings/fee-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Meals\",\"code\":\"MEALS\",\"taxable\":true}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("MEALS"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void createFeeType_blankName_returns400() throws Exception {
        mockMvc.perform(post("/api/v1/settings/fee-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"\",\"code\":\"X\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("VALIDATION_FAILED"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void createFeeType_duplicateCode_returns409() throws Exception {
        when(settingsService.createFeeType(any())).thenThrow(
                new ResponseStatusException(HttpStatus.CONFLICT, "A fee type with code 'MEALS' already exists."));

        mockMvc.perform(post("/api/v1/settings/fee-types")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"name\":\"Meals\",\"code\":\"MEALS\",\"taxable\":true}"))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error").value("CONFLICT"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void patchFeeType_returns200() throws Exception {
        UUID id = UUID.randomUUID();
        when(settingsService.updateFeeType(any(), any())).thenReturn(
                new FeeTypeSettingDto(id, "Book Fee", "BOOKS", false, false));

        mockMvc.perform(patch("/api/v1/settings/fee-types/{id}", id)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"active\":false}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.active").value(false));
    }

    // ── Payment statuses / EPP / notifications / institutions ────────────────

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void getPaymentStatuses_returns200() throws Exception {
        when(settingsService.getPaymentStatuses()).thenReturn(PaymentStatusInfo.ALL);

        mockMvc.perform(get("/api/v1/settings/payment-statuses"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(6))
                .andExpect(jsonPath("$[1].status").value("Pending"))
                .andExpect(jsonPath("$[1].terminal").value(false));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void getEpp_returns200() throws Exception {
        when(settingsService.getEpp()).thenReturn(EppSettingsDto.defaults());

        mockMvc.perform(get("/api/v1/settings/epp"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.minAmountEGP").value(5000))
                .andExpect(jsonPath("$.maxPlansPerStudent").value(2));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void putEpp_returns200() throws Exception {
        when(settingsService.updateEpp(any())).thenReturn(EppSettingsDto.defaults());

        mockMvc.perform(put("/api/v1/settings/epp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenors\":{\"12\":true},\"minAmountEGP\":1000,\"maxAmountEGP\":50000," +
                                "\"interestRatePct\":{\"12\":14},\"adminFeeRatePct\":1.0,\"adminFeeCapEGP\":500," +
                                "\"requireApproval\":false,\"maxPlansPerStudent\":2}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void putEpp_invalid_returns400() throws Exception {
        when(settingsService.updateEpp(any())).thenThrow(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "maxAmountEGP must be greater than minAmountEGP."));

        mockMvc.perform(put("/api/v1/settings/epp")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"tenors\":{\"12\":true},\"minAmountEGP\":10000,\"maxAmountEGP\":1000," +
                                "\"interestRatePct\":{\"12\":14},\"adminFeeRatePct\":1.0,\"adminFeeCapEGP\":500," +
                                "\"requireApproval\":false,\"maxPlansPerStudent\":2}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("BAD_REQUEST"));
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void getAndPutNotifications_return200() throws Exception {
        when(settingsService.getNotifications()).thenReturn(NotificationSettingsDto.defaults());
        when(settingsService.updateNotifications(any())).thenReturn(NotificationSettingsDto.defaults());

        mockMvc.perform(get("/api/v1/settings/notifications"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.email").value(true));

        mockMvc.perform(put("/api/v1/settings/notifications")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"events\":{\"failedPayments\":true,\"reconExceptions\":true," +
                                "\"schoolUploadErrors\":true,\"newSchoolReg\":true,\"systemAlerts\":true," +
                                "\"dailySummary\":true},\"channels\":{\"inApp\":true,\"email\":true," +
                                "\"sms\":false,\"slack\":false}}"))
                .andExpect(status().isOk());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void getAndPutInstitutionSettings_return200() throws Exception {
        when(settingsService.getInstitutionSettings()).thenReturn(InstitutionSettingsDto.defaults());
        when(settingsService.updateInstitutionSettings(any())).thenReturn(InstitutionSettingsDto.defaults());

        mockMvc.perform(get("/api/v1/settings/institutions"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.requireDualApproval").value(true));

        mockMvc.perform(put("/api/v1/settings/institutions")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"requireDualApproval\":true,\"autoIntegrationAfterApproval\":false," +
                                "\"requireMoeCertificate\":true,\"maxStudentsPerUpload\":5000," +
                                "\"postApprovalActivationDelayHours\":24," +
                                "\"allowedUploadFormats\":{\"xlsx\":true,\"csv\":true,\"xml\":false}}"))
                .andExpect(status().isOk());
    }

    // ── RBAC ────────────────────────────────────────────────────────────────

    @Test
    @WithMockUser(username = "parent@example.com", roles = {"GUARDIAN"})
    void guardian_isForbidden() throws Exception {
        mockMvc.perform(get("/api/v1/settings/epp")).andExpect(status().isForbidden());
    }

    @Test
    void unauthenticated_isUnauthorized() throws Exception {
        mockMvc.perform(get("/api/v1/settings/fee-types")).andExpect(status().isUnauthorized());
    }
}
