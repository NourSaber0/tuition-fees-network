package com.tuitionnetwork.identity;

import com.tuitionnetwork.identity.domain.BankEmployee;
import com.tuitionnetwork.identity.domain.Guardian;
import com.tuitionnetwork.identity.domain.InstitutionAdmin;
import com.tuitionnetwork.identity.repository.BankEmployeeRepository;
import com.tuitionnetwork.identity.repository.GuardianRepository;
import com.tuitionnetwork.identity.repository.InstitutionAdminRepository;
import com.tuitionnetwork.identity.security.IdentityUserDetailsService;
import com.tuitionnetwork.identity.security.JwtTokenProvider;
import com.tuitionnetwork.identity.security.SecurityContextProvider;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.identity.security.UserRole;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class IdentityUserDetailsServiceTest {

    private BankEmployeeRepository bankEmployeeRepository;
    private InstitutionAdminRepository institutionAdminRepository;
    private GuardianRepository guardianRepository;
    private IdentityUserDetailsService userDetailsService;
    private JwtTokenProvider jwtTokenProvider;
    private SecurityContextProvider securityContextProvider;

    @BeforeEach
    void setUp() {
        bankEmployeeRepository = mock(BankEmployeeRepository.class);
        institutionAdminRepository = mock(InstitutionAdminRepository.class);
        guardianRepository = mock(GuardianRepository.class);

        userDetailsService = new IdentityUserDetailsService(
                bankEmployeeRepository,
                institutionAdminRepository,
                guardianRepository
        );

        jwtTokenProvider = new JwtTokenProvider("test-secret-key-32-characters-minimum!");
        securityContextProvider = new SecurityContextProvider();
        securityContextProvider.clear();
    }

    @Test
    void loadUserByEmail_bankEmployee_mapsToRoleBackOffice() {
        UUID empId = UUID.randomUUID();
        BankEmployee employee = new BankEmployee("Tarek Mostafa", "tarek@cibeg.com", "EMP-1001", "hash", "Operations");
        employee.setId(empId);

        when(bankEmployeeRepository.findByEmail("tarek@cibeg.com")).thenReturn(Optional.of(employee));

        Optional<SecurityUserPrincipal> principal = userDetailsService.loadUserByEmail("tarek@cibeg.com");

        assertTrue(principal.isPresent());
        assertEquals(empId, principal.get().userId());
        assertEquals(UserRole.ROLE_BACK_OFFICE, principal.get().role());
        assertTrue(principal.get().authorities().contains(UserRole.ROLE_BACK_OFFICE));
    }

    @Test
    void loadUserByEmail_institutionAdmin_mapsToRoleInstitutionAdmin() {
        UUID adminId = UUID.randomUUID();
        InstitutionAdmin admin = new InstitutionAdmin(UUID.randomUUID(), "School Finance Admin", "admin@nile.edu.eg", "hash", "Finance");
        admin.setId(adminId);

        when(institutionAdminRepository.findByEmail("admin@nile.edu.eg")).thenReturn(Optional.of(admin));

        Optional<SecurityUserPrincipal> principal = userDetailsService.loadUserByEmail("admin@nile.edu.eg");

        assertTrue(principal.isPresent());
        assertEquals(adminId, principal.get().userId());
        assertEquals(UserRole.ROLE_INSTITUTION_ADMIN, principal.get().role());
        assertTrue(principal.get().authorities().contains(UserRole.ROLE_INSTITUTION_ADMIN));
    }

    @Test
    void loadUserByEmail_guardian_mapsToRoleGuardian() {
        UUID guardianId = UUID.randomUUID();
        Guardian guardian = new Guardian("hmac123", "enc123", "Ahmed Saber", "ahmed@example.com", "+201012345678", "hash", true);
        guardian.setId(guardianId);

        when(guardianRepository.findByEmail("ahmed@example.com")).thenReturn(Optional.of(guardian));

        Optional<SecurityUserPrincipal> principal = userDetailsService.loadUserByEmail("ahmed@example.com");

        assertTrue(principal.isPresent());
        assertEquals(guardianId, principal.get().userId());
        assertEquals(UserRole.ROLE_GUARDIAN, principal.get().role());
        assertTrue(principal.get().authorities().contains(UserRole.ROLE_GUARDIAN));
    }

    @Test
    void jwtTokenProvider_generatesAndValidatesToken() {
        UUID userId = UUID.randomUUID();
        SecurityUserPrincipal principal = new SecurityUserPrincipal(userId, "tarek@cibeg.com", "Tarek", UserRole.ROLE_BACK_OFFICE);

        String token = jwtTokenProvider.generateToken(principal);
        assertNotNull(token);

        Optional<SecurityUserPrincipal> parsed = jwtTokenProvider.validateAndParseToken(token);
        assertTrue(parsed.isPresent());
        assertEquals(userId, parsed.get().userId());
        assertEquals("tarek@cibeg.com", parsed.get().email());
        assertEquals(UserRole.ROLE_BACK_OFFICE, parsed.get().role());
    }

    @Test
    void securityContextProvider_managesCurrentThreadUser() {
        UUID userId = UUID.randomUUID();
        SecurityUserPrincipal principal = new SecurityUserPrincipal(userId, "guardian@example.com", "Parent", UserRole.ROLE_GUARDIAN);

        securityContextProvider.setAuthenticatedUser(principal);

        assertTrue(securityContextProvider.getCurrentUser().isPresent());
        assertEquals(userId, securityContextProvider.getCurrentUser().get().userId());
        assertTrue(securityContextProvider.hasRole(UserRole.ROLE_GUARDIAN));
        assertFalse(securityContextProvider.hasRole(UserRole.ROLE_BACK_OFFICE));

        securityContextProvider.clear();
        assertFalse(securityContextProvider.getCurrentUser().isPresent());
    }
}
