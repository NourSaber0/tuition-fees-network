package com.tuitionnetwork.identity.dto;

import com.tuitionnetwork.identity.domain.InstitutionType;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;

/**
 * Back-office "Register New Institution" form (US-06).
 * {@code code} and {@code feeAbsorptionPolicy} are optional — {@code code}
 * is generated from the institution type when blank.
 */
public record RegisterInstitutionRequest(
        @NotBlank String name,
        @NotNull InstitutionType institutionType,
        @NotBlank String subType,
        @NotBlank String city,
        @NotBlank String principalName,
        @NotBlank String phone,
        @NotBlank @Email String email,
        @NotBlank String registrationNumber,
        @NotNull @Positive Integer studentCount,
        String code,
        String feeAbsorptionPolicy
) {
}
