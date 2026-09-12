package com.tuitionnetwork.identity.service;

import com.tuitionnetwork.identity.dto.auth.BankUserDto;
import com.tuitionnetwork.identity.dto.auth.ForgotPasswordRequest;
import com.tuitionnetwork.identity.dto.auth.LoginRequest;
import com.tuitionnetwork.identity.dto.auth.LoginResponse;
import com.tuitionnetwork.identity.dto.auth.MessageResponse;
import com.tuitionnetwork.identity.dto.auth.MfaResendRequest;
import com.tuitionnetwork.identity.dto.auth.MfaResendResponse;
import com.tuitionnetwork.identity.dto.auth.MfaVerifyRequest;
import com.tuitionnetwork.identity.dto.auth.MfaVerifyResponse;
import com.tuitionnetwork.identity.dto.auth.RefreshTokenRequest;
import com.tuitionnetwork.identity.dto.auth.ResetPasswordRequest;
import com.tuitionnetwork.identity.dto.auth.RolePermissionsDto;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;

import java.util.List;

public interface BankAuthService {

    LoginResponse login(LoginRequest request);

    MfaVerifyResponse verifyMfa(MfaVerifyRequest request);

    MfaResendResponse resendMfa(MfaResendRequest request);

    MessageResponse forgotPassword(ForgotPasswordRequest request);

    MessageResponse resetPassword(ResetPasswordRequest request);

    MfaVerifyResponse refresh(RefreshTokenRequest request);

    void logout(String refreshToken, SecurityUserPrincipal principal);

    BankUserDto getMe(SecurityUserPrincipal principal);

    MessageResponse trustDevice(String mfaToken);

    List<RolePermissionsDto> getRoles();

    List<String> getRolePermissions(String role);
}
