package com.tuitionnetwork.identity.dto;

import java.util.List;
import java.util.UUID;

public record ResolvedGuardianDto(
        UUID id,
        String fullName,
        String nationalIdHmac,
        String nationalIdEncrypted,
        String email,
        String phone,
        boolean cibAccountLinked,
        String linkedAccountId,
        String linkedCardId,
        List<ResolvedStudentDto> students
) {
}
