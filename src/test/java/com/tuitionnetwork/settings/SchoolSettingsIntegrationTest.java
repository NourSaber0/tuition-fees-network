package com.tuitionnetwork.settings;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.identity.domain.AccountStatus;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.InstitutionAdmin;
import com.tuitionnetwork.identity.repository.InstitutionAdminRepository;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.security.JwtTokenProvider;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.identity.security.UserRole;
import com.tuitionnetwork.settings.dto.ChangePasswordRequest;
import com.tuitionnetwork.settings.dto.SchoolNotificationSettingsRequest;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
class SchoolSettingsIntegrationTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private InstitutionRepository institutionRepository;

    @Autowired
    private InstitutionAdminRepository institutionAdminRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Autowired
    private ObjectMapper objectMapper;

    private MockMvc mockMvc;

    private Institution schoolA;
    private InstitutionAdmin adminA;
    private InstitutionAdmin financeA;

    private String tokenAdminA;
    private String tokenFinanceA;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        institutionAdminRepository.deleteAll();
        institutionRepository.deleteAll();

        schoolA = new Institution("Cairo International School", "SCH-001", "SCHOOL_ABSORBS");
        schoolA.setCity("Cairo");
        schoolA.setPrincipalName("Dr. Magdy");
        schoolA.setPhone("+20 2 12345678");
        schoolA.setEmail("info@cis.edu.eg");
        schoolA.setRegistrationNumber("MOEDU-SCH-SETTINGS-001");
        schoolA.setAccountStatus(AccountStatus.ACTIVE);
        schoolA.setNotifyInApp(true);
        schoolA.setNotifyEmail(true);
        schoolA = institutionRepository.save(schoolA);

        adminA = new InstitutionAdmin(schoolA.getId(), "Amr Hassan", "amr.hassan@cis.edu.eg", "Password123!", "School Admin");
        adminA.setStatus("Active");
        adminA.setCreatedAt(LocalDateTime.now());
        adminA = institutionAdminRepository.save(adminA);

        financeA = new InstitutionAdmin(schoolA.getId(), "Dina Fouad", "dina.fouad@cis.edu.eg", "Finance@2026", "School Finance");
        financeA.setStatus("Active");
        financeA.setCreatedAt(LocalDateTime.now());
        financeA = institutionAdminRepository.save(financeA);

        tokenAdminA = jwtTokenProvider.generateToken(new SecurityUserPrincipal(
                adminA.getId(), adminA.getEmail(), adminA.getName(),
                UserRole.ROLE_SCHOOL_ADMIN,
                List.of(UserRole.ROLE_SCHOOL_ADMIN, UserRole.ROLE_INSTITUTION_ADMIN),
                schoolA.getId()
        ));

        tokenFinanceA = jwtTokenProvider.generateToken(new SecurityUserPrincipal(
                financeA.getId(), financeA.getEmail(), financeA.getName(),
                UserRole.ROLE_SCHOOL_FINANCE,
                List.of(UserRole.ROLE_SCHOOL_FINANCE, UserRole.ROLE_INSTITUTION_ADMIN),
                schoolA.getId()
        ));
    }

    @AfterEach
    void tearDown() {
        institutionAdminRepository.deleteAll();
        institutionRepository.deleteAll();
    }

    @Test
    void testSchoolSettings_getProfile_returnsReadOnlySchoolMetadata() throws Exception {
        mockMvc.perform(get("/api/v1/settings/profile")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(schoolA.getId().toString()))
                .andExpect(jsonPath("$.name").value("Cairo International School"))
                .andExpect(jsonPath("$.code").value("SCH-001"))
                .andExpect(jsonPath("$.city").value("Cairo"))
                .andExpect(jsonPath("$.principalName").value("Dr. Magdy"))
                .andExpect(jsonPath("$.phone").value("+20 2 12345678"))
                .andExpect(jsonPath("$.email").value("info@cis.edu.eg"))
                .andExpect(jsonPath("$.registrationNumber").value("MOEDU-SCH-SETTINGS-001"))
                .andExpect(jsonPath("$.accountStatus").value("ACTIVE"));

        // Finance role can also view profile
        mockMvc.perform(get("/api/v1/settings/profile")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Cairo International School"));
    }

    @Test
    void testSchoolSettings_getAndPutNotifications_updatesToggles() throws Exception {
        // Initial GET
        mockMvc.perform(get("/api/v1/settings/notifications")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.inApp").value(true))
                .andExpect(jsonPath("$.channels.email").value(true));

        // PUT toggle changes
        SchoolNotificationSettingsRequest updateRequest = new SchoolNotificationSettingsRequest(
                Map.of("inApp", false, "email", true)
        );

        mockMvc.perform(put("/api/v1/settings/notifications")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.channels.inApp").value(false))
                .andExpect(jsonPath("$.channels.email").value(true));

        // Verify persisted state
        Institution reloaded = institutionRepository.findById(schoolA.getId()).orElseThrow();
        assertEquals(false, reloaded.isNotifyInApp());
        assertEquals(true, reloaded.isNotifyEmail());
    }

    @Test
    void testSchoolSettings_putNotifications_forbiddenForSchoolFinance() throws Exception {
        SchoolNotificationSettingsRequest updateRequest = new SchoolNotificationSettingsRequest(
                Map.of("inApp", false, "email", true)
        );

        mockMvc.perform(put("/api/v1/settings/notifications")
                        .header("Authorization", "Bearer " + tokenFinanceA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(updateRequest)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testSchoolSettings_changePassword_success() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("Password123!", "NewSecret@2026");

        mockMvc.perform(post("/api/v1/settings/change-password")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.message").value("Password updated. Please sign in."));

        InstitutionAdmin updated = institutionAdminRepository.findById(adminA.getId()).orElseThrow();
        assertEquals("NewSecret@2026", updated.getPasswordHash());
    }

    @Test
    void testSchoolSettings_changePassword_invalidCurrent_returns400() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("WrongPass123!", "NewSecret@2026");

        mockMvc.perform(post("/api/v1/settings/change-password")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_password"));
    }

    @Test
    void testSchoolSettings_changePassword_weakPassword_returns400() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("Password123!", "short");

        mockMvc.perform(post("/api/v1/settings/change-password")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("weak_password"));
    }

    @Test
    void testSchoolSettings_changePassword_reusedPassword_returns400() throws Exception {
        ChangePasswordRequest request = new ChangePasswordRequest("Password123!", "Password123!");

        mockMvc.perform(post("/api/v1/settings/change-password")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("password_reused"));
    }

    @Test
    void testSchoolSettings_bankOnlySettings_forbiddenForSchoolUsers() throws Exception {
        mockMvc.perform(get("/api/v1/settings/fee-types")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/settings/epp")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/settings/institutions")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());

        mockMvc.perform(get("/api/v1/settings/payment-statuses")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isForbidden());
    }

    @Test
    @WithMockUser(username = "ops@cibeg.com", roles = {"BACK_OFFICE"})
    void testSchoolSettings_schoolProfile_forbiddenForBankUsers() throws Exception {
        mockMvc.perform(get("/api/v1/settings/profile"))
                .andExpect(status().isForbidden());
    }
}
