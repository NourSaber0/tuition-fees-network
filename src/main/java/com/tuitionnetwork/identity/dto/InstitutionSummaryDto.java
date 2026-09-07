package com.tuitionnetwork.identity.dto;

import com.tuitionnetwork.identity.domain.AccountStatus;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.IntegrationStatus;
import com.tuitionnetwork.identity.domain.InstitutionType;
import com.tuitionnetwork.identity.domain.RegistrationStatus;

import java.time.LocalDate;
import java.util.UUID;

/** Row shape for {@code GET /api/v1/institutions} (the back-office list table). */
public record InstitutionSummaryDto(
        UUID id,
        String name,
        String code,
        String city,
        InstitutionType institutionType,
        String subType,
        String registrationNumber,
        int studentCount,
        RegistrationStatus registrationStatus,
        AccountStatus accountStatus,
        IntegrationStatus integrationStatus,
        LocalDate registeredAt
) {
    public static InstitutionSummaryDto from(Institution i) {
        return new InstitutionSummaryDto(
                i.getId(),
                i.getName(),
                i.getCode(),
                i.getCity(),
                i.getInstitutionType(),
                i.getSubType(),
                i.getRegistrationNumber(),
                i.getStudentCount(),
                i.getRegistrationStatus(),
                i.getAccountStatus(),
                i.getIntegrationStatus(),
                i.getRegisteredAt()
        );
    }
}
