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
        String role, // ROLE_BACK_OFFICE, ROLE_INSTITUTION_ADMIN, ROLE_GUARDIAN
        Collection<String> authorities,
        String password
) implements UserDetails {

    public SecurityUserPrincipal(UUID userId, String email, String name, String role) {
        this(userId, email, name, role, List.of(role), "");
    }

    public SecurityUserPrincipal(UUID userId, String email, String name, String role, Collection<String> authorities) {
        this(userId, email, name, role, authorities, "");
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
