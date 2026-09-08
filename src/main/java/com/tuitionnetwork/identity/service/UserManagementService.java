package com.tuitionnetwork.identity.service;

import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.identity.dto.auth.MessageResponse;
import com.tuitionnetwork.identity.dto.users.BankUserSummaryDto;
import com.tuitionnetwork.identity.dto.users.CreateBankUserRequest;
import com.tuitionnetwork.identity.dto.users.UpdateBankUserRequest;
import com.tuitionnetwork.identity.dto.users.UserStatusResponse;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;

import java.util.Map;
import java.util.UUID;

public interface UserManagementService {

    PageResponse<BankUserSummaryDto> listUsers(String search, String role, String status, int page, int pageSize);

    Map<String, Long> getSummary();

    BankUserSummaryDto getUser(UUID id);

    BankUserSummaryDto createUser(CreateBankUserRequest request, SecurityUserPrincipal actor);

    BankUserSummaryDto updateUser(UUID id, UpdateBankUserRequest request, SecurityUserPrincipal actor);

    UserStatusResponse deactivateUser(UUID id, SecurityUserPrincipal actor);

    UserStatusResponse activateUser(UUID id, SecurityUserPrincipal actor);

    MessageResponse triggerPasswordReset(UUID id, SecurityUserPrincipal actor);
}
