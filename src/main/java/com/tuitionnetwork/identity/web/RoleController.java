package com.tuitionnetwork.identity.web;

import com.tuitionnetwork.common.dto.ApiErrorResponse;
import com.tuitionnetwork.identity.dto.auth.RolePermissionsDto;
import com.tuitionnetwork.identity.security.AuthException;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.identity.service.BankAuthService;
import com.tuitionnetwork.identity.service.SchoolUserManagementService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping({"/api/v1/roles", "/roles"})
public class RoleController {

    private final BankAuthService authService;
    private final SchoolUserManagementService schoolUserManagementService;

    @org.springframework.beans.factory.annotation.Autowired
    public RoleController(BankAuthService authService, SchoolUserManagementService schoolUserManagementService) {
        this.authService = authService;
        this.schoolUserManagementService = schoolUserManagementService;
    }

    @GetMapping
    public ResponseEntity<List<RolePermissionsDto>> getRoles(
            @RequestParam(value = "type", required = false) String type,
            @AuthenticationPrincipal SecurityUserPrincipal principal) {
        if ((principal != null && principal.isSchoolUser()) || "school".equalsIgnoreCase(type)) {
            if (schoolUserManagementService != null) {
                return ResponseEntity.ok(schoolUserManagementService.getSchoolRoles());
            }
        }
        return ResponseEntity.ok(authService.getRoles());
    }

    @GetMapping("/{role}/permissions")
    public ResponseEntity<List<String>> getRolePermissions(@PathVariable("role") String role) {
        if (schoolUserManagementService != null && role != null) {
            String norm = role.trim().toLowerCase().replace("-", " ");
            for (RolePermissionsDto dto : schoolUserManagementService.getSchoolRoles()) {
                if (dto.role().trim().toLowerCase().replace("-", " ").equals(norm)) {
                    return ResponseEntity.ok(dto.permissions());
                }
            }
        }
        return ResponseEntity.ok(authService.getRolePermissions(role));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthException(AuthException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getDetails()));
    }
}
