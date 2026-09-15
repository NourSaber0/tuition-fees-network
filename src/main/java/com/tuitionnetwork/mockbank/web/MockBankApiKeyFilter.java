package com.tuitionnetwork.mockbank.web;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tuitionnetwork.mockbank.dto.MockBankErrorResponse;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
@Order(1)
public class MockBankApiKeyFilter extends OncePerRequestFilter {

    public static final String API_KEY_HEADER = "X-API-Key";
    public static final String EXPECTED_API_KEY = "wit-intern-2026";

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI();
        // Only filter mock banking endpoints
        boolean isMockBankPath = path.startsWith("/api/v1/moi")
                || path.startsWith("/api/v1/payments/cards")
                || path.equals("/api/v1/customers")
                || path.startsWith("/api/v1/backoffice/payments")
                || path.equals("/api/v1/epp/quotes")
                || (path.equals("/api/v1/epp") && ("POST".equalsIgnoreCase(request.getMethod()) || "GET".equalsIgnoreCase(request.getMethod())))
                || (path.startsWith("/api/v1/epp/") && !path.startsWith("/api/v1/epp/plans") && !path.startsWith("/api/v1/epp/summary") && !path.startsWith("/api/v1/epp/quote") && !path.startsWith("/api/v1/epp/cards"))
                || path.equals("/api/v1/test-data")
                || path.equals("/api/v1/admin/reset");

        return !isMockBankPath;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {

        String apiKey = request.getHeader(API_KEY_HEADER);

        if (apiKey == null || apiKey.trim().isEmpty()) {
            sendError(response, HttpStatus.UNAUTHORIZED, "MISSING_API_KEY", "The X-API-Key header is missing");
            return;
        }

        if (!EXPECTED_API_KEY.equals(apiKey.trim())) {
            sendError(response, HttpStatus.UNAUTHORIZED, "INVALID_API_KEY", "The provided X-API-Key is invalid");
            return;
        }

        // Set authenticated principal for mock bank requests
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("mock-bank-service", null, List.of(new SimpleGrantedAuthority("ROLE_MOCK_BANK")))
        );

        filterChain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, HttpStatus status, String code, String message) throws IOException {
        response.setStatus(status.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        MockBankErrorResponse errorResponse = MockBankErrorResponse.of(code, message);
        response.getWriter().write(objectMapper.writeValueAsString(errorResponse));
    }
}
