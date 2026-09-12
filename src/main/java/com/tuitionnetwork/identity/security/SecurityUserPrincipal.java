package com.tuitionnetwork.identity.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

public record SecurityUserPrincipal(
        UUID userId,
        String email,
        String name,
        String role, // ROLE_BACK_OFFICE, ROLE_INSTITUTION_ADMIN, ROLE_GUARDIAN, ROLE_SCHOOL_ADMIN, ROLE_SCHOOL_FINANCE
        Collection<String> authorities,
        String password,
        UUID institutionId
) implements UserDetails {

    public SecurityUserPrincipal(UUID userId, String email, String name, String role) {
        this(userId, email, name, role, List.of(role), "", null);
    }

    public SecurityUserPrincipal(UUID userId, String email, String name, String role, Collection<String> authorities) {
        this(userId, email, name, role, authorities, "", null);
    }

    public SecurityUserPrincipal(UUID userId, String email, String name, String role, Collection<String> authorities, String password) {
        this(userId, email, name, role, authorities, password, null);
    }

    public SecurityUserPrincipal(UUID userId, String email, String name, String role, UUID institutionId) {
        this(userId, email, name, role, buildAuthorities(role), "", institutionId);
    }

    public SecurityUserPrincipal(UUID userId, String email, String name, String role, Collection<String> authorities, UUID institutionId) {
        this(userId, email, name, role, authorities, "", institutionId);
    }

    private static Collection<String> buildAuthorities(String role) {
        if (UserRole.ROLE_SCHOOL_ADMIN.equals(role) || UserRole.ROLE_SCHOOL_FINANCE.equals(role)) {
            return List.of(role, UserRole.ROLE_INSTITUTION_ADMIN);
        }
        if (UserRole.ROLE_INSTITUTION_ADMIN.equals(role)) {
            return List.of(UserRole.ROLE_INSTITUTION_ADMIN, UserRole.ROLE_SCHOOL_ADMIN);
        }
        return List.of(role);
    }

    public UUID id() {
        return userId;
    }

    public boolean hasRole(String roleName) {
        if (roleName == null) return false;
        if (roleName.equals(this.role)) return true;
        return authorities != null && authorities.contains(roleName);
    }

    public boolean isSchoolUser() {
        return hasRole(UserRole.ROLE_SCHOOL_ADMIN) || hasRole(UserRole.ROLE_SCHOOL_FINANCE) || hasRole(UserRole.ROLE_INSTITUTION_ADMIN);
    }

    public boolean isSchoolAdmin() {
        return hasRole(UserRole.ROLE_SCHOOL_ADMIN);
    }

    public boolean isSchoolFinance() {
        return hasRole(UserRole.ROLE_SCHOOL_FINANCE);
    }

    @Override
    public Collection<? extends GrantedAuthority> getAuthorities() {
        return authorities.stream()
                .map(SimpleGrantedAuthority::new)
                .toList();
    }

    @Override
    public String getPassword() {
        return password != null ? password : "";
    }

    @Override
    public String getUsername() {
        return email;
    }

    @Override
    public boolean isAccountNonExpired() {
        return true;
    }

    @Override
    public boolean isAccountNonLocked() {
        return true;
    }

    @Override
    public boolean isCredentialsNonExpired() {
        return true;
    }

    @Override
    public boolean isEnabled() {
        return true;
    }
}
