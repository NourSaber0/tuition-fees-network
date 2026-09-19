package com.tuitionnetwork.identity;

import com.example.demo.DemoApplication;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.identity.domain.AccountStatus;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.InstitutionAdmin;
import com.tuitionnetwork.identity.dto.users.CreateBankUserRequest;
import com.tuitionnetwork.identity.dto.users.UpdateBankUserRequest;
import com.tuitionnetwork.identity.repository.InstitutionAdminRepository;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.security.JwtTokenProvider;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.identity.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

import static org.hamcrest.Matchers.hasSize;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@Import(com.tuitionnetwork.MockBankTestConfig.class)
@SpringBootTest(classes = DemoApplication.class)
class SchoolUserManagementIntegrationTest {

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
    private Institution schoolB;

    private InstitutionAdmin adminA;
    private InstitutionAdmin financeA;
    private InstitutionAdmin adminB;

    private String tokenAdminA;
    private String tokenFinanceA;
    private String tokenAdminB;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context)
                .apply(springSecurity())
                .build();

        institutionAdminRepository.deleteAll();
        institutionRepository.deleteAll();

        schoolA = new Institution(
                "School Alpha",
                "SCH-ALPHA",
                "SCHOOL_ABSORBS"
        );
        schoolA.setAccountStatus(AccountStatus.ACTIVE);
        schoolA = institutionRepository.save(schoolA);

        schoolB = new Institution(
                "School Beta",
                "SCH-BETA",
                "SCHOOL_ABSORBS"
        );
        schoolB.setAccountStatus(AccountStatus.ACTIVE);
        schoolB = institutionRepository.save(schoolB);

        adminA = new InstitutionAdmin(schoolA.getId(), "Alpha Admin", "admin@alpha.edu.eg", "Pass123!", "School Admin");
        adminA.setStatus("Active");
        adminA.setCreatedAt(LocalDateTime.now());
        adminA = institutionAdminRepository.save(adminA);

        financeA = new InstitutionAdmin(schoolA.getId(), "Alpha Finance", "finance@alpha.edu.eg", "Pass123!", "School Finance");
        financeA.setStatus("Active");
        financeA.setCreatedAt(LocalDateTime.now());
        financeA = institutionAdminRepository.save(financeA);

        adminB = new InstitutionAdmin(schoolB.getId(), "Beta Admin", "admin@beta.edu.eg", "Pass123!", "School Admin");
        adminB.setStatus("Active");
        adminB.setCreatedAt(LocalDateTime.now());
        adminB = institutionAdminRepository.save(adminB);

        // Generate tokens
        tokenAdminA = jwtTokenProvider.generateToken(new SecurityUserPrincipal(
                adminA.getId(),
                adminA.getEmail(),
                adminA.getName(),
                UserRole.ROLE_SCHOOL_ADMIN,
                List.of(UserRole.ROLE_SCHOOL_ADMIN, UserRole.ROLE_INSTITUTION_ADMIN),
                schoolA.getId()
        ));

        tokenFinanceA = jwtTokenProvider.generateToken(new SecurityUserPrincipal(
                financeA.getId(),
                financeA.getEmail(),
                financeA.getName(),
                UserRole.ROLE_SCHOOL_FINANCE,
                List.of(UserRole.ROLE_SCHOOL_FINANCE, UserRole.ROLE_INSTITUTION_ADMIN),
                schoolA.getId()
        ));

        tokenAdminB = jwtTokenProvider.generateToken(new SecurityUserPrincipal(
                adminB.getId(),
                adminB.getEmail(),
                adminB.getName(),
                UserRole.ROLE_SCHOOL_ADMIN,
                List.of(UserRole.ROLE_SCHOOL_ADMIN, UserRole.ROLE_INSTITUTION_ADMIN),
                schoolB.getId()
        ));
    }

    @Test
    void testSchoolAdmin_listUsers_returnsOnlySameSchoolUsers() throws Exception {
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data", hasSize(2)))
                .andExpect(jsonPath("$.total").value(2))
                .andExpect(jsonPath("$.data[*].email", org.hamcrest.Matchers.containsInAnyOrder("admin@alpha.edu.eg", "finance@alpha.edu.eg")));
    }

    @Test
    void testSchoolAdmin_createUser_success() throws Exception {
        CreateBankUserRequest request = new CreateBankUserRequest(
                "Mostafa Sherif",
                "m.sherif@alpha.edu.eg",
                null,
                "School Finance",
                null,
                "Finance@123"
        );

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").isNotEmpty())
                .andExpect(jsonPath("$.name").value("Mostafa Sherif"))
                .andExpect(jsonPath("$.email").value("m.sherif@alpha.edu.eg"))
                .andExpect(jsonPath("$.role").value("School Finance"))
                .andExpect(jsonPath("$.status").value("Active"));

        // Verify total users in School A is now 3, while School B is still 1
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(3));

        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + tokenAdminB))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.total").value(1));
    }

    @Test
    void testSchoolAdmin_createUser_duplicateEmail_returns409() throws Exception {
        CreateBankUserRequest request = new CreateBankUserRequest(
                "Duplicate Person",
                "finance@alpha.edu.eg",
                null,
                "School Finance",
                null,
                null
        );

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error.code").value("email_exists"));
    }

    @Test
    void testSchoolAdmin_createUser_invalidRole_returns400() throws Exception {
        CreateBankUserRequest request = new CreateBankUserRequest(
                "Hacker User",
                "hacker@alpha.edu.eg",
                null,
                "Super Admin",
                null,
                null
        );

        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error.code").value("invalid_role"));
    }

    @Test
    void testSchoolAdmin_updateUser_success() throws Exception {
        UpdateBankUserRequest request = new UpdateBankUserRequest(
                "Dina Fouad Renamed",
                "dina.renamed@alpha.edu.eg",
                null,
                "School Admin",
                null
        );

        mockMvc.perform(patch("/api/v1/users/" + financeA.getId())
                        .header("Authorization", "Bearer " + tokenAdminA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.name").value("Dina Fouad Renamed"))
                .andExpect(jsonPath("$.email").value("dina.renamed@alpha.edu.eg"))
                .andExpect(jsonPath("$.role").value("School Admin"));
    }

    @Test
    void testSchoolAdmin_deactivateAndActivateUser_success() throws Exception {
        // Deactivate
        mockMvc.perform(post("/api/v1/users/" + financeA.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Inactive"));

        InstitutionAdmin updated = institutionAdminRepository.findById(financeA.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Inactive", updated.getStatus());

        // Activate
        mockMvc.perform(post("/api/v1/users/" + financeA.getId() + "/activate")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("Active"));

        InstitutionAdmin activated = institutionAdminRepository.findById(financeA.getId()).orElseThrow();
        org.junit.jupiter.api.Assertions.assertEquals("Active", activated.getStatus());
    }

    @Test
    void testSchoolFinance_accessUserManagement_isForbidden403() throws Exception {
        // School Finance user tries GET /api/v1/users -> 403 Forbidden
        mockMvc.perform(get("/api/v1/users")
                        .header("Authorization", "Bearer " + tokenFinanceA))
                .andExpect(status().isForbidden());

        // School Finance user tries POST /api/v1/users -> 403 Forbidden
        CreateBankUserRequest request = new CreateBankUserRequest(
                "Test User",
                "test@alpha.edu.eg",
                null,
                "School Finance",
                null,
                null
        );
        mockMvc.perform(post("/api/v1/users")
                        .header("Authorization", "Bearer " + tokenFinanceA)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isForbidden());
    }

    @Test
    void testCrossSchool_accessUser_isIsolated404Or403() throws Exception {
        // Admin A tries to view Admin B from School B -> 404 or 403 (zero cross-school data bleed)
        mockMvc.perform(get("/api/v1/users/" + adminB.getId())
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().is4xxClientError());

        // Admin A tries to deactivate Admin B from School B -> 404 or 403
        mockMvc.perform(post("/api/v1/users/" + adminB.getId() + "/deactivate")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void testSchoolRoles_returnsAdminAndFinancePermissions() throws Exception {
        // GET /api/v1/roles as School Admin
        mockMvc.perform(get("/api/v1/roles")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(2)))
                .andExpect(jsonPath("$[0].role").value("School Admin"))
                .andExpect(jsonPath("$[0].permissions", hasSize(10)))
                .andExpect(jsonPath("$[1].role").value("School Finance"))
                .andExpect(jsonPath("$[1].permissions", hasSize(8)));

        // GET /api/v1/roles/school-admin/permissions
        mockMvc.perform(get("/api/v1/roles/school-admin/permissions")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(10)));

        // GET /api/v1/roles/school-finance/permissions
        mockMvc.perform(get("/api/v1/roles/school-finance/permissions")
                        .header("Authorization", "Bearer " + tokenAdminA))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(8)));
    }
}
