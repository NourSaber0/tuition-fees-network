package com.tuitionnetwork.identity.service;

import com.tuitionnetwork.identity.dto.ResolvedGuardianDto;
import java.util.Optional;

public interface IdentityResolverService {

    String computeHmacSha256(String rawNationalId);

    Optional<ResolvedGuardianDto> resolveGuardianByNationalId(String rawNationalId);
}
