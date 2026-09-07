package com.tuitionnetwork.identity.web;

import com.tuitionnetwork.common.dto.ApiErrorResponse;
import com.tuitionnetwork.identity.dto.auth.RolePermissionsDto;
import com.tuitionnetwork.identity.security.AuthException;
import com.tuitionnetwork.identity.service.BankAuthService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/roles", "/roles"})
public class RoleController {

    private final BankAuthService authService;

    public RoleController(BankAuthService authService) {
        this.authService = authService;
    }

    @GetMapping
    public ResponseEntity<List<RolePermissionsDto>> getRoles() {
        return ResponseEntity.ok(authService.getRoles());
    }

    @GetMapping("/{role}/permissions")
    public ResponseEntity<List<String>> getRolePermissions(@PathVariable("role") String role) {
        return ResponseEntity.ok(authService.getRolePermissions(role));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthException(AuthException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getDetails()));
    }
}
