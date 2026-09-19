package com.tuitionnetwork.identity.security;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Optional;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private final JwtTokenProvider jwtTokenProvider;
    private final IdentityUserDetailsService identityUserDetailsService;

    public JwtAuthenticationFilter(JwtTokenProvider jwtTokenProvider, IdentityUserDetailsService identityUserDetailsService) {
        this.jwtTokenProvider = jwtTokenProvider;
        this.identityUserDetailsService = identityUserDetailsService;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            String token = authHeader.substring(7).trim();
            Optional<SecurityUserPrincipal> principalOpt = jwtTokenProvider.validateAndParseToken(token);
            if (principalOpt.isPresent()) {
                SecurityUserPrincipal principal = principalOpt.get();
                
                // CRITICAL FIX: Ensure user is still active in the database
                Optional<SecurityUserPrincipal> activePrincipalOpt = identityUserDetailsService.loadUserById(principal.userId());
                
                if (activePrincipalOpt.isPresent()) {
                    SecurityUserPrincipal activePrincipal = activePrincipalOpt.get();
                    UsernamePasswordAuthenticationToken authentication =
                            new UsernamePasswordAuthenticationToken(activePrincipal, null, activePrincipal.getAuthorities());
                    SecurityContextHolder.getContext().setAuthentication(authentication);
                }
            }
        }
        filterChain.doFilter(request, response);
    }
}
