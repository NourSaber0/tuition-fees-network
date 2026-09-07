package com.tuitionnetwork.identity.dto;

import com.tuitionnetwork.identity.domain.AccountStatus;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.IntegrationStatus;
import com.tuitionnetwork.identity.domain.InstitutionType;
import com.tuitionnetwork.identity.domain.RegistrationStatus;

import java.time.LocalDate;
import java.util.UUID;

/** Full institution record for {@code GET /api/v1/institutions/{id}} and every mutation response. */
public record InstitutionDetailDto(
        UUID id,
        String name,
        String code,
        InstitutionType institutionType,
        String subType,
        String city,
        String principalName,
        String phone,
        String email,
        String registrationNumber,
        int studentCount,
        RegistrationStatus registrationStatus,
        AccountStatus accountStatus,
        IntegrationStatus integrationStatus,
        String feeAbsorptionPolicy,
        String rejectionReason,
        LocalDate registeredAt
) {
    public static InstitutionDetailDto from(Institution i) {
        return new InstitutionDetailDto(
                i.getId(),
                i.getName(),
                i.getCode(),
                i.getInstitutionType(),
                i.getSubType(),
                i.getCity(),
                i.getPrincipalName(),
                i.getPhone(),
                i.getEmail(),
                i.getRegistrationNumber(),
                i.getStudentCount(),
                i.getRegistrationStatus(),
                i.getAccountStatus(),
                i.getIntegrationStatus(),
                i.getFeeAbsorptionPolicy(),
                i.getRejectionReason(),
                i.getRegisteredAt()
        );
    }
}
