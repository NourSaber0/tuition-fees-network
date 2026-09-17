package com.tuitionnetwork.ai.service;

import org.springframework.stereotype.Service;

/**
 * Integration point for the portal's real authentication system.
 *
 * This development implementation returns SCHOOL_ADMIN.
 *
 * Before production, replace getCurrentRole() with the role obtained
 * from the authenticated Spring Security principal or access token.
 */
@Service
public class CurrentUserService {

    public String getCurrentRole() {

        // Development default only.
        // Do not use this as production authorization.
        return "SCHOOL_ADMIN";
    }
}