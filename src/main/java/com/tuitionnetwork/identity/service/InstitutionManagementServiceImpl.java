package com.tuitionnetwork.identity.service;

import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.identity.domain.AccountStatus;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.InstitutionType;
import com.tuitionnetwork.identity.domain.RegistrationStatus;
import com.tuitionnetwork.identity.dto.InstitutionDetailDto;
import com.tuitionnetwork.identity.dto.InstitutionSummaryDto;
import com.tuitionnetwork.identity.dto.RegisterInstitutionRequest;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.util.EnumSet;
import java.util.Set;
import java.util.UUID;

@Service
public class InstitutionManagementServiceImpl implements InstitutionManagementService {

    /** States from which an application may still be approved or rejected. */
    private static final Set<RegistrationStatus> REVIEWABLE =
            EnumSet.of(RegistrationStatus.PENDING, RegistrationStatus.UNDER_REVIEW);

    private static final int MAX_PAGE_SIZE = 100;

    private final InstitutionRepository institutionRepository;

    public InstitutionManagementServiceImpl(InstitutionRepository institutionRepository) {
        this.institutionRepository = institutionRepository;
    }

    @Override
    @Transactional
    public InstitutionDetailDto register(RegisterInstitutionRequest request) {
        String registrationNumber = request.registrationNumber().trim();
        institutionRepository.findByRegistrationNumber(registrationNumber).ifPresent(existing -> {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "An institution with registration number '" + registrationNumber + "' already exists.");
        });

        Institution institution = new Institution();
        institution.setName(request.name().trim());
        institution.setInstitutionType(request.institutionType());
        institution.setSubType(request.subType().trim());
        institution.setCity(request.city().trim());
        institution.setPrincipalName(request.principalName().trim());
        institution.setPhone(request.phone().trim());
        institution.setEmail(request.email().trim().toLowerCase());
        institution.setRegistrationNumber(registrationNumber);
        institution.setStudentCount(request.studentCount());
        institution.setFeeAbsorptionPolicy(
                request.feeAbsorptionPolicy() != null && !request.feeAbsorptionPolicy().isBlank()
                        ? request.feeAbsorptionPolicy().trim()
                        : null);
        institution.setCode(resolveCode(request.code(), request.institutionType()));

        // New applications always start here regardless of any client-sent status.
        institution.setRegistrationStatus(RegistrationStatus.PENDING);
        institution.setAccountStatus(AccountStatus.INACTIVE);

        return InstitutionDetailDto.from(institutionRepository.save(institution));
    }

    @Override
    @Transactional(readOnly = true)
    public PageResponse<InstitutionSummaryDto> list(String search,
                                                    InstitutionType type,
                                                    RegistrationStatus registrationStatus,
                                                    AccountStatus accountStatus,
                                                    int page,
                                                    int size) {
        int safePage = Math.max(page, 0);
        int safeSize = Math.min(Math.max(size, 1), MAX_PAGE_SIZE);
        String normalisedSearch = (search == null || search.isBlank()) ? null : search.trim();

        Page<Institution> result = institutionRepository.search(
                normalisedSearch, type, registrationStatus, accountStatus,
                PageRequest.of(safePage, safeSize, Sort.by(Sort.Direction.DESC, "registeredAt")));

        return PageResponse.from(result, InstitutionSummaryDto::from);
    }

    @Override
    @Transactional(readOnly = true)
    public InstitutionDetailDto get(UUID id) {
        return InstitutionDetailDto.from(require(id));
    }

    @Override
    @Transactional
    public InstitutionDetailDto approve(UUID id) {
        Institution institution = require(id);
        if (!REVIEWABLE.contains(institution.getRegistrationStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot approve an application in state " + institution.getRegistrationStatus() + ".");
        }
        institution.setRegistrationStatus(RegistrationStatus.APPROVED);
        institution.setAccountStatus(AccountStatus.ACTIVE);
        institution.setRejectionReason(null);
        return InstitutionDetailDto.from(institutionRepository.save(institution));
    }

    @Override
    @Transactional
    public InstitutionDetailDto reject(UUID id, String reason) {
        if (reason == null || reason.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A rejection reason is required.");
        }
        Institution institution = require(id);
        if (!REVIEWABLE.contains(institution.getRegistrationStatus())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Cannot reject an application in state " + institution.getRegistrationStatus() + ".");
        }
        institution.setRegistrationStatus(RegistrationStatus.REJECTED);
        institution.setAccountStatus(AccountStatus.INACTIVE);
        institution.setRejectionReason(reason.trim());
        return InstitutionDetailDto.from(institutionRepository.save(institution));
    }

    @Override
    @Transactional
    public InstitutionDetailDto activate(UUID id) {
        Institution institution = require(id);
        if (institution.getRegistrationStatus() != RegistrationStatus.APPROVED) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Only an APPROVED institution can be activated (current: "
                            + institution.getRegistrationStatus() + ").");
        }
        institution.setAccountStatus(AccountStatus.ACTIVE);
        return InstitutionDetailDto.from(institutionRepository.save(institution));
    }

    @Override
    @Transactional
    public InstitutionDetailDto deactivate(UUID id) {
        Institution institution = require(id);
        institution.setAccountStatus(AccountStatus.INACTIVE);
        return InstitutionDetailDto.from(institutionRepository.save(institution));
    }

    private Institution require(UUID id) {
        return institutionRepository.findById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Institution not found: " + id));
    }

    /**
     * Uses the caller-supplied network code when present and free; otherwise
     * generates {@code SCH-NNN} / {@code UNI-NNN} from the count of that type,
     * skipping any collision.
     */
    private String resolveCode(String requestedCode, InstitutionType type) {
        if (requestedCode != null && !requestedCode.isBlank()) {
            String candidate = requestedCode.trim().toUpperCase();
            if (institutionRepository.findByCode(candidate).isPresent()) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Institution code '" + candidate + "' is already in use.");
            }
            return candidate;
        }
        String prefix = type == InstitutionType.UNIVERSITY ? "UNI" : "SCH";
        long next = institutionRepository.countByInstitutionType(type) + 1;
        String candidate = String.format("%s-%03d", prefix, next);
        while (institutionRepository.findByCode(candidate).isPresent()) {
            next++;
            candidate = String.format("%s-%03d", prefix, next);
        }
        return candidate;
    }
}
