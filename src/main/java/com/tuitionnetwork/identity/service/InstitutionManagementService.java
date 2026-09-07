package com.tuitionnetwork.identity.service;

import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.identity.domain.AccountStatus;
import com.tuitionnetwork.identity.domain.InstitutionType;
import com.tuitionnetwork.identity.domain.RegistrationStatus;
import com.tuitionnetwork.identity.dto.InstitutionDetailDto;
import com.tuitionnetwork.identity.dto.InstitutionSummaryDto;
import com.tuitionnetwork.identity.dto.RegisterInstitutionRequest;

import java.util.UUID;

/**
 * Back-office institution lifecycle (US-05 .. US-11).
 *
 * <p>State machine:
 * <pre>
 *   register            -> PENDING        / account INACTIVE
 *   approve  (PENDING|UNDER_REVIEW) -> APPROVED / account ACTIVE
 *   reject   (PENDING|UNDER_REVIEW) -> REJECTED / account INACTIVE
 *   activate (reg APPROVED)         -> account ACTIVE
 *   deactivate                      -> account INACTIVE
 * </pre>
 * Illegal transitions raise {@link org.springframework.web.server.ResponseStatusException}
 * with {@code 409 CONFLICT}; unknown ids raise {@code 404 NOT_FOUND}.
 */
public interface InstitutionManagementService {

    InstitutionDetailDto register(RegisterInstitutionRequest request);

    PageResponse<InstitutionSummaryDto> list(String search,
                                             InstitutionType type,
                                             RegistrationStatus registrationStatus,
                                             AccountStatus accountStatus,
                                             int page,
                                             int size);

    InstitutionDetailDto get(UUID id);

    InstitutionDetailDto approve(UUID id);

    InstitutionDetailDto reject(UUID id, String reason);

    InstitutionDetailDto activate(UUID id);

    InstitutionDetailDto deactivate(UUID id);
}
