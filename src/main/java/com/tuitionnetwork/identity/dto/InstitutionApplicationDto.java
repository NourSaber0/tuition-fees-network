package com.tuitionnetwork.identity.dto;

import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.InstitutionType;
import com.tuitionnetwork.identity.domain.RegistrationStatus;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Registration review packet (US-08).
 *
 * <p>{@code requiredDocuments} is the standard checklist for the institution type;
 * {@code documentsTracked} is false because a document-store is not implemented yet,
 * so verification state is not available.
 */
public record InstitutionApplicationDto(
        UUID id,
        String name,
        String registrationNumber,
        InstitutionType institutionType,
        String subType,
        String city,
        String principalName,
        String phone,
        String email,
        int studentCount,
        LocalDate registeredAt,
        RegistrationStatus registrationStatus,
        String rejectionReason,
        List<String> requiredDocuments,
        boolean documentsTracked
) {
    private static final List<String> SCHOOL_DOCS = List.of(
            "Commercial Registry", "Tax Card", "Educational License",
            "Bank Account Details", "Principal National ID", "MENA Certification");

    private static final List<String> UNIVERSITY_DOCS = List.of(
            "Commercial Registry", "Tax Card", "Educational License",
            "Bank Account Details", "Ministry of Higher Education Approval", "MENA Certification");

    public static InstitutionApplicationDto from(Institution i) {
        List<String> docs = i.getInstitutionType() == InstitutionType.UNIVERSITY
                ? UNIVERSITY_DOCS : SCHOOL_DOCS;
        return new InstitutionApplicationDto(
                i.getId(), i.getName(), i.getRegistrationNumber(), i.getInstitutionType(),
                i.getSubType(), i.getCity(), i.getPrincipalName(), i.getPhone(), i.getEmail(),
                i.getStudentCount(), i.getRegisteredAt(), i.getRegistrationStatus(),
                i.getRejectionReason(), docs, false);
    }
}
