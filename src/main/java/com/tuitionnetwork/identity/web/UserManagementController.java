package com.tuitionnetwork.identity.web;

import com.tuitionnetwork.common.dto.ApiErrorResponse;
import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.identity.dto.auth.MessageResponse;
import com.tuitionnetwork.identity.dto.users.BankUserSummaryDto;
import com.tuitionnetwork.identity.dto.users.CreateBankUserRequest;
import com.tuitionnetwork.identity.dto.users.UpdateBankUserRequest;
import com.tuitionnetwork.identity.dto.users.UserStatusResponse;
import com.tuitionnetwork.identity.security.AuthException;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.identity.service.UserManagementService;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/users")
@PreAuthorize("hasRole('BACK_OFFICE')")
public class UserManagementController {

    private final UserManagementService userManagementService;

    public UserManagementController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping
    public ResponseEntity<PageResponse<BankUserSummaryDto>> listUsers(
            @RequestParam(value = "search", required = false) String search,
            @RequestParam(value = "role", required = false) String role,
            @RequestParam(value = "status", required = false) String status,
            @RequestParam(value = "page", defaultValue = "1") int page,
            @RequestParam(value = "pageSize", defaultValue = "25") int pageSize) {
        int zeroBasedPage = Math.max(page - 1, 0);
        return ResponseEntity.ok(userManagementService.listUsers(search, role, status, zeroBasedPage, pageSize));
    }

    @GetMapping("/summary")
    public ResponseEntity<Map<String, Long>> getSummary() {
        return ResponseEntity.ok(userManagementService.getSummary());
    }

    @GetMapping("/{id}")
    public ResponseEntity<BankUserSummaryDto> getUser(@PathVariable UUID id) {
        return ResponseEntity.ok(userManagementService.getUser(id));
    }

    @PostMapping
    public ResponseEntity<BankUserSummaryDto> createUser(@RequestBody CreateBankUserRequest request,
                                                           @AuthenticationPrincipal SecurityUserPrincipal principal) {
        BankUserSummaryDto created = userManagementService.createUser(request, principal);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PatchMapping("/{id}")
    public ResponseEntity<BankUserSummaryDto> updateUser(@PathVariable UUID id,
                                                           @RequestBody UpdateBankUserRequest request,
                                                           @AuthenticationPrincipal SecurityUserPrincipal principal) {
        return ResponseEntity.ok(userManagementService.updateUser(id, request, principal));
    }

    @PostMapping("/{id}/deactivate")
    public ResponseEntity<UserStatusResponse> deactivateUser(@PathVariable UUID id,
                                                               @AuthenticationPrincipal SecurityUserPrincipal principal) {
        return ResponseEntity.ok(userManagementService.deactivateUser(id, principal));
    }

    @PostMapping("/{id}/activate")
    public ResponseEntity<UserStatusResponse> activateUser(@PathVariable UUID id,
                                                             @AuthenticationPrincipal SecurityUserPrincipal principal) {
        return ResponseEntity.ok(userManagementService.activateUser(id, principal));
    }

    @PostMapping("/{id}/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@PathVariable UUID id,
                                                           @AuthenticationPrincipal SecurityUserPrincipal principal) {
        return ResponseEntity.ok(userManagementService.triggerPasswordReset(id, principal));
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthException(AuthException ex) {
        return ResponseEntity.status(ex.getStatus())
                .body(ApiErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getDetails()));
    }
}
