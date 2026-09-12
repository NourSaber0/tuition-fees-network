package com.tuitionnetwork.identity.web;

import com.tuitionnetwork.common.dto.ApiErrorResponse;
import com.tuitionnetwork.identity.dto.auth.BankUserDto;
import com.tuitionnetwork.identity.dto.auth.ForgotPasswordRequest;
import com.tuitionnetwork.identity.dto.auth.LoginRequest;
import com.tuitionnetwork.identity.dto.auth.LoginResponse;
import com.tuitionnetwork.identity.dto.auth.LogoutRequest;
import com.tuitionnetwork.identity.dto.auth.MessageResponse;
import com.tuitionnetwork.identity.dto.auth.MfaResendRequest;
import com.tuitionnetwork.identity.dto.auth.MfaResendResponse;
import com.tuitionnetwork.identity.dto.auth.MfaVerifyRequest;
import com.tuitionnetwork.identity.dto.auth.MfaVerifyResponse;
import com.tuitionnetwork.identity.dto.auth.RefreshTokenRequest;
import com.tuitionnetwork.identity.dto.auth.ResetPasswordRequest;
import com.tuitionnetwork.identity.security.AuthException;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.identity.service.BankAuthService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping({"/api/v1/auth", "/auth"})
public class AuthController {

    private final BankAuthService authService;

    public AuthController(BankAuthService authService) {
        this.authService = authService;
    }

    @PostMapping("/login")
    public ResponseEntity<LoginResponse> login(@RequestBody(required = false) LoginRequest request) {
        LoginResponse response = authService.login(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/verify")
    public ResponseEntity<MfaVerifyResponse> verifyMfa(@RequestBody(required = false) MfaVerifyRequest request) {
        MfaVerifyResponse response = authService.verifyMfa(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/resend")
    public ResponseEntity<MfaResendResponse> resendMfa(@RequestBody(required = false) MfaResendRequest request) {
        MfaResendResponse response = authService.resendMfa(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/mfa/trust-device")
    public ResponseEntity<MessageResponse> trustDevice(@RequestBody(required = false) com.tuitionnetwork.identity.dto.auth.TrustDeviceRequest request) {
        String token = request != null ? request.mfaToken() : null;
        MessageResponse response = authService.trustDevice(token);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/forgot-password")
    public ResponseEntity<MessageResponse> forgotPassword(@RequestBody(required = false) ForgotPasswordRequest request) {
        MessageResponse response = authService.forgotPassword(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/reset-password")
    public ResponseEntity<MessageResponse> resetPassword(@RequestBody(required = false) ResetPasswordRequest request) {
        MessageResponse response = authService.resetPassword(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/refresh")
    public ResponseEntity<MfaVerifyResponse> refresh(@RequestBody(required = false) RefreshTokenRequest request) {
        MfaVerifyResponse response = authService.refresh(request);
        return ResponseEntity.ok(response);
    }

    @PostMapping("/logout")
    public ResponseEntity<Void> logout(@RequestBody(required = false) LogoutRequest request,
                                       @AuthenticationPrincipal SecurityUserPrincipal principal) {
        String token = request != null ? request.refreshToken() : null;
        authService.logout(token, principal);
        return ResponseEntity.noContent().build();
    }

    @GetMapping("/me")
    public ResponseEntity<BankUserDto> getMe(@AuthenticationPrincipal SecurityUserPrincipal principal) {
        BankUserDto user = authService.getMe(principal);
        return ResponseEntity.ok(user);
    }

    @ExceptionHandler(AuthException.class)
    public ResponseEntity<ApiErrorResponse> handleAuthException(AuthException ex) {
        HttpHeaders headers = new HttpHeaders();
        if (ex.getStatus() == HttpStatus.TOO_MANY_REQUESTS && ex.getDetails() instanceof Map<?, ?> map) {
            Object retryAfter = map.get("retryAfterSeconds");
            if (retryAfter != null) {
                headers.add("Retry-After", String.valueOf(retryAfter));
            }
        }
        return ResponseEntity.status(ex.getStatus())
                .headers(headers)
                .body(ApiErrorResponse.of(ex.getCode(), ex.getMessage(), ex.getDetails()));
    }
}
