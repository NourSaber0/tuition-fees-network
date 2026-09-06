package com.tuitionnetwork.identity.security;

import org.springframework.stereotype.Component;

import java.util.Optional;
import java.util.UUID;

@Component
public class SecurityContextProvider {

    private static final ThreadLocal<SecurityUserPrincipal> CURRENT_USER = new ThreadLocal<>();

    public void setAuthenticatedUser(SecurityUserPrincipal user) {
        CURRENT_USER.set(user);
    }

    public void setAuthenticatedUser(UUID userId, String email, String name, String role) {
        CURRENT_USER.set(new SecurityUserPrincipal(userId, email, name, role));
    }

    public Optional<SecurityUserPrincipal> getCurrentUser() {
        return Optional.ofNullable(CURRENT_USER.get());
    }

    public boolean hasRole(String role) {
        return getCurrentUser()
                .map(u -> u.role().equals(role) || u.authorities().contains(role))
                .orElse(false);
    }

    public void clear() {
        CURRENT_USER.remove();
    }
}
