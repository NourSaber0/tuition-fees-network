package com.tuitionnetwork.identity.service;

import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.identity.dto.auth.RolePermissionsDto;
import com.tuitionnetwork.identity.dto.users.CreateSchoolUserRequest;
import com.tuitionnetwork.identity.dto.users.SchoolUserSummaryDto;
import com.tuitionnetwork.identity.dto.users.UpdateSchoolUserRequest;

import java.util.List;
import java.util.UUID;

public interface SchoolUserManagementService {

    PageResponse<SchoolUserSummaryDto> listUsers(UUID schoolId, String search, String role, String status, int page, int size);

    SchoolUserSummaryDto getUser(UUID schoolId, UUID userId);

    SchoolUserSummaryDto createUser(UUID schoolId, CreateSchoolUserRequest request, UUID actorId);

    SchoolUserSummaryDto updateUser(UUID schoolId, UUID userId, UpdateSchoolUserRequest request, UUID actorId);

    void deactivateUser(UUID schoolId, UUID userId, UUID actorId);

    void activateUser(UUID schoolId, UUID userId, UUID actorId);

    List<RolePermissionsDto> getSchoolRoles();
}
