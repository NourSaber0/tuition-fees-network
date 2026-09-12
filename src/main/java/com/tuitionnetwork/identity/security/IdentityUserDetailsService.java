package com.tuitionnetwork.identity.security;

import com.tuitionnetwork.identity.domain.BankEmployee;
import com.tuitionnetwork.identity.domain.Guardian;
import com.tuitionnetwork.identity.domain.InstitutionAdmin;
import com.tuitionnetwork.identity.repository.BankEmployeeRepository;
import com.tuitionnetwork.identity.repository.GuardianRepository;
import com.tuitionnetwork.identity.repository.InstitutionAdminRepository;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
public class IdentityUserDetailsService implements UserDetailsService {

    private final BankEmployeeRepository bankEmployeeRepository;
    private final InstitutionAdminRepository institutionAdminRepository;
    private final GuardianRepository guardianRepository;

    public IdentityUserDetailsService(BankEmployeeRepository bankEmployeeRepository,
                                      InstitutionAdminRepository institutionAdminRepository,
                                      GuardianRepository guardianRepository) {
        this.bankEmployeeRepository = bankEmployeeRepository;
        this.institutionAdminRepository = institutionAdminRepository;
        this.guardianRepository = guardianRepository;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return loadUserByEmail(username)
                .orElseThrow(() -> new UsernameNotFoundException("User not found with identifier: " + username));
    }

    /**
     * Resolves user by email across distinct user domains and maps to appropriate Spring Security role:
     * - BankEmployee      -> ROLE_BACK_OFFICE
     * - InstitutionAdmin  -> ROLE_INSTITUTION_ADMIN
     * - Guardian          -> ROLE_GUARDIAN
     */
    public Optional<SecurityUserPrincipal> loadUserByEmail(String email) {
        if (email == null || email.isBlank()) {
            return Optional.empty();
        }
        String cleanEmail = email.trim().toLowerCase();

        // 1. Check Bank Employee
        Optional<BankEmployee> bankEmployee = bankEmployeeRepository.findByEmail(cleanEmail);
        if (bankEmployee.isPresent()) {
            BankEmployee emp = bankEmployee.get();
            if ("Inactive".equalsIgnoreCase(emp.getStatus()) || emp.isAccountLocked()) {
                return Optional.empty();
            }
            return Optional.of(new SecurityUserPrincipal(
                    emp.getId(),
                    emp.getEmail(),
                    emp.getName(),
                    UserRole.ROLE_BACK_OFFICE
            ));
        }

        // 2. Check Institution Admin
        Optional<InstitutionAdmin> institutionAdmin = institutionAdminRepository.findByEmail(cleanEmail);
        if (institutionAdmin.isPresent()) {
            InstitutionAdmin admin = institutionAdmin.get();
            if ("Inactive".equalsIgnoreCase(admin.getStatus()) || admin.isAccountLocked()) {
                return Optional.empty();
            }
            String primaryRole = resolveSchoolRole(admin.getRole());
            List<String> authorities = List.of(UserRole.ROLE_INSTITUTION_ADMIN, primaryRole);
            return Optional.of(new SecurityUserPrincipal(
                    admin.getId(),
                    admin.getEmail(),
                    admin.getName(),
                    UserRole.ROLE_INSTITUTION_ADMIN,
                    authorities,
                    admin.getInstitutionId()
            ));
        }

        // 3. Check Guardian
        Optional<Guardian> guardian = guardianRepository.findByEmail(cleanEmail);
        if (guardian.isPresent()) {
            Guardian g = guardian.get();
            return Optional.of(new SecurityUserPrincipal(
                    g.getId(),
                    g.getEmail(),
                    g.getName(),
                    UserRole.ROLE_GUARDIAN
            ));
        }

        return Optional.empty();
    }

    /**
     * Resolves user by ID across distinct user domains and maps to appropriate Spring Security role:
     * - BankEmployee      -> ROLE_BACK_OFFICE
     * - InstitutionAdmin  -> ROLE_INSTITUTION_ADMIN, ROLE_SCHOOL_ADMIN / ROLE_SCHOOL_FINANCE
     * - Guardian          -> ROLE_GUARDIAN
     */
    public Optional<SecurityUserPrincipal> loadUserById(UUID userId) {
        if (userId == null) {
            return Optional.empty();
        }

        // 1. Check Bank Employee
        Optional<BankEmployee> bankEmployee = bankEmployeeRepository.findById(userId);
        if (bankEmployee.isPresent()) {
            BankEmployee emp = bankEmployee.get();
            if ("Inactive".equalsIgnoreCase(emp.getStatus()) || emp.isAccountLocked()) {
                return Optional.empty();
            }
            return Optional.of(new SecurityUserPrincipal(
                    emp.getId(),
                    emp.getEmail(),
                    emp.getName(),
                    UserRole.ROLE_BACK_OFFICE
            ));
        }

        // 2. Check Institution Admin
        Optional<InstitutionAdmin> institutionAdmin = institutionAdminRepository.findById(userId);
        if (institutionAdmin.isPresent()) {
            InstitutionAdmin admin = institutionAdmin.get();
            if ("Inactive".equalsIgnoreCase(admin.getStatus()) || admin.isAccountLocked()) {
                return Optional.empty();
            }
            String primaryRole = resolveSchoolRole(admin.getRole());
            List<String> authorities = List.of(primaryRole, UserRole.ROLE_INSTITUTION_ADMIN);
            return Optional.of(new SecurityUserPrincipal(
                    admin.getId(),
                    admin.getEmail(),
                    admin.getName(),
                    primaryRole,
                    authorities,
                    admin.getInstitutionId()
            ));
        }

        // 3. Check Guardian
        Optional<Guardian> guardian = guardianRepository.findById(userId);
        if (guardian.isPresent()) {
            Guardian g = guardian.get();
            return Optional.of(new SecurityUserPrincipal(
                    g.getId(),
                    g.getEmail(),
                    g.getName(),
                    UserRole.ROLE_GUARDIAN
            ));
        }

        return Optional.empty();
    }

    private String resolveSchoolRole(String role) {
        if (role == null) {
            return UserRole.ROLE_SCHOOL_ADMIN;
        }
        String r = role.trim().toLowerCase();
        if (r.contains("finance")) {
            return UserRole.ROLE_SCHOOL_FINANCE;
        }
        return UserRole.ROLE_SCHOOL_ADMIN;
    }
}
