package com.tuitionnetwork.settings.dto;

import java.time.LocalDate;
import java.util.UUID;

public record SchoolProfileDto(
        UUID id,
        String name,
        String code,
        String institutionType,
        String subType,
        String city,
        String principalName,
        String phone,
        String email,
        String registrationNumber,
        String taxRegistrationNumber,
        String commercialRegNumber,
        String bankAccountNumber,
        String iban,
        String feeAbsorptionPolicy,
        String accountStatus,
        String integrationStatus,
        LocalDate registeredAt
) {}
