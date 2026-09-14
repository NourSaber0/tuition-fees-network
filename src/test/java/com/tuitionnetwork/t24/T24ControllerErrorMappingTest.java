package com.tuitionnetwork.t24;

import com.example.demo.DemoApplication;
import com.tuitionnetwork.identity.domain.BankEmployee;
import com.tuitionnetwork.identity.repository.BankEmployeeRepository;
import com.tuitionnetwork.identity.security.JwtTokenProvider;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.identity.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.TestPropertySource;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;

import java.util.List;
import java.util.UUID;

import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * With t24.mock.fallback-to-local disabled, a T24 call that can't reach anything real (which is
 * every call in this environment - see T24_INTEGRATION_EXPLAINED.md) must surface as a genuine
 * HTTP error rather than the controller always returning 200/201 regardless of the internal
 * response status.
 */
@SpringBootTest(classes = DemoApplication.class)
@TestPropertySource(properties = "t24.mock.fallback-to-local=false")
class T24ControllerErrorMappingTest {

    @Autowired
    private WebApplicationContext context;

    @Autowired
    private BankEmployeeRepository bankEmployeeRepository;

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    private MockMvc mockMvc;
    private String token;

    @BeforeEach
    void setUp() {
        mockMvc = MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();

        BankEmployee bankAdmin = new BankEmployee(
                "Mohamed Ali", "mohamed.ali+errmap@cibeg.com", "EMP-ERR-001",
                "Password123!", "Operations", "bank-admin"
        );
        bankAdmin.setStatus("Active");
        bankAdmin = bankEmployeeRepository.save(bankAdmin);

        token = jwtTokenProvider.generateToken(new SecurityUserPrincipal(
                bankAdmin.getId(), bankAdmin.getEmail(), bankAdmin.getName(),
                UserRole.ROLE_BACK_OFFICE, List.of(UserRole.ROLE_BACK_OFFICE), (UUID) null
        ));
    }

    @Test
    @DisplayName("GET /retrieve: an unreachable T24 with fallback disabled returns 502, not 200")
    void retrieve_withFallbackDisabled_returns502NotFakeSuccess() throws Exception {
        mockMvc.perform(get("/api/v1/t24/billing/retrieve?nationalId=29805150101023")
                        .header("Authorization", "Bearer " + token))
                .andExpect(status().isBadGateway())
                .andExpect(jsonPath("$.status").value("ERROR"));
    }
}
