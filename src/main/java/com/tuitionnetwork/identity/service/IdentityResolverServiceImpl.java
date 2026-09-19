package com.tuitionnetwork.identity.service;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.identity.domain.Guardian;
import com.tuitionnetwork.identity.domain.Institution;
import com.tuitionnetwork.identity.domain.Student;
import com.tuitionnetwork.identity.dto.ResolvedGuardianDto;
import com.tuitionnetwork.identity.dto.ResolvedStudentDto;
import com.tuitionnetwork.identity.infrastructure.MockBankCustomerClient;
import com.tuitionnetwork.identity.infrastructure.MockBankCustomerClient.AccountDto;
import com.tuitionnetwork.identity.infrastructure.MockBankCustomerClient.CardDto;
import com.tuitionnetwork.identity.infrastructure.MockBankMoiClient;
import com.tuitionnetwork.identity.repository.GuardianRepository;
import com.tuitionnetwork.identity.repository.InstitutionRepository;
import com.tuitionnetwork.identity.repository.StudentRepository;
import com.tuitionnetwork.identity.security.SecurityUserPrincipal;
import com.tuitionnetwork.identity.security.UserRole;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Service;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
public class IdentityResolverServiceImpl implements IdentityResolverService {

    private final GuardianRepository guardianRepository;
    private final StudentRepository studentRepository;
    private final InstitutionRepository institutionRepository;
    private final AuditLogRepository auditLogRepository;
    private final MockBankCustomerClient mockBankCustomerClient;
    private final MockBankMoiClient mockBankMoiClient;
    private final String secretKey;

    @Autowired
    public IdentityResolverServiceImpl(
            GuardianRepository guardianRepository,
            StudentRepository studentRepository,
            InstitutionRepository institutionRepository,
            AuditLogRepository auditLogRepository,
            MockBankMoiClient mockBankMoiClient,
            MockBankCustomerClient mockBankCustomerClient,
            @Value("${app.security.hmac-secret:default-tuition-secret-key-32-chars-long!}") String secretKey) {
        this.guardianRepository = guardianRepository;
        this.studentRepository = studentRepository;
        this.institutionRepository = institutionRepository;
        this.auditLogRepository = auditLogRepository;
        this.mockBankCustomerClient = mockBankCustomerClient;
        this.mockBankMoiClient = mockBankMoiClient;
        this.secretKey = secretKey;
    }

    public IdentityResolverServiceImpl(
            GuardianRepository guardianRepository,
            StudentRepository studentRepository,
            InstitutionRepository institutionRepository,
            @Value("${app.security.hmac-secret:default-tuition-secret-key-32-chars-long!}") String secretKey) {
        this(guardianRepository, studentRepository, institutionRepository, null, null, null, secretKey);
    }

    public IdentityResolverServiceImpl(
            GuardianRepository guardianRepository,
            @Value("${app.security.hmac-secret:default-tuition-secret-key-32-chars-long!}") String secretKey) {
        this(guardianRepository, null, null, null, null, null, secretKey);
    }

    @Override
    public String computeHmacSha256(String rawNationalId) {
        if (rawNationalId == null || rawNationalId.isBlank()) {
            throw new IllegalArgumentException("National ID cannot be null or empty");
        }
        try {
            Mac sha256Hmac = Mac.getInstance("HmacSHA256");
            SecretKeySpec keySpec = new SecretKeySpec(secretKey.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
            sha256Hmac.init(keySpec);
            byte[] hash = sha256Hmac.doFinal(rawNationalId.trim().getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (Exception e) {
            throw new IllegalStateException("Failed to compute HMAC-SHA256 for National ID", e);
        }
    }

    @Override
    @PreAuthorize("hasAnyRole('BACK_OFFICE', 'GUARDIAN')")
    public Optional<ResolvedGuardianDto> resolveGuardianByNationalId(String rawNationalId) {
        String hmac = computeHmacSha256(rawNationalId);

        // Extract current authenticated actor from SecurityContextHolder
        UUID actorId = extractCurrentActorId();
        String actorType = extractCurrentActorType();

        // Privacy Tracking: Create and save AUDIT_LOG record (using HMAC hash, never plaintext)
        if (auditLogRepository != null) {
            AuditLog auditLog = new AuditLog(
                    actorId,
                    actorType,
                    "SEARCH_NATIONAL_ID",
                    hmac
            );
            auditLogRepository.save(auditLog);
        }

        
        if (mockBankMoiClient != null && !mockBankMoiClient.validateNationalId(rawNationalId)) {
            throw new org.springframework.web.server.ResponseStatusException(
                org.springframework.http.HttpStatus.BAD_REQUEST, "MOI Validation Failed: Invalid National ID");
        }

        Optional<Guardian> guardianOpt = guardianRepository.findByNationalIdHash(hmac);
        if (guardianOpt.isEmpty()) {
            return Optional.empty();
        }

        Guardian guardian = guardianOpt.get();

        if (mockBankCustomerClient != null) {
            try {
                MockBankCustomerClient.CustomerResponse customerResponse = mockBankCustomerClient.getCustomerByNationalId(rawNationalId);
                
                boolean updated = false;
                
                if (customerResponse.accounts() != null) {
                    for (AccountDto acc : customerResponse.accounts()) {
                        if ("ACTIVE".equalsIgnoreCase(acc.status())) {
                            guardian.setLinkedAccountId(acc.account_id());
                            updated = true;
                            break;
                        }
                    }
                }
                
                if (customerResponse.cards() != null) {
                    for (CardDto card : customerResponse.cards()) {
                        if ("ACTIVE".equalsIgnoreCase(card.status())) {
                            guardian.setLinkedCardId(card.card_id());
                            updated = true;
                            break;
                        }
                    }
                }
                
                if (updated) {
                    guardianRepository.save(guardian);
                }
                
            } catch (Exception ignored) {
                // Ignore if customer is not found in mock bank or service is down
            }
        }

        List<ResolvedStudentDto> studentDtos = loadStudentsForGuardian(guardian.getId());

        ResolvedGuardianDto dto = new ResolvedGuardianDto(
                guardian.getId(),
                guardian.getName(),
                guardian.getNationalIdHash(),
                guardian.getNationalIdEncrypted(),
                guardian.getEmail(),
                guardian.getPhone(),
                guardian.isCibAccountLinked(),
                guardian.getLinkedAccountId(),
                guardian.getLinkedCardId(),
                studentDtos
        );

        return Optional.of(dto);
    }

    private UUID extractCurrentActorId() {
        return extractCurrentBankEmployeeId();
    }

    private UUID extractCurrentBankEmployeeId() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                Object principal = auth.getPrincipal();
                if (principal instanceof SecurityUserPrincipal sup) {
                    return sup.userId();
                }
                if (principal instanceof String str) {
                    try {
                        return UUID.fromString(str);
                    } catch (IllegalArgumentException ignored) {}
                }
                if (auth.getName() != null) {
                    try {
                        return UUID.fromString(auth.getName());
                    } catch (IllegalArgumentException ignored) {}
                }
            }
        } catch (Exception ignored) {}
        return null;
    }

    private String extractCurrentActorType() {
        try {
            Authentication auth = SecurityContextHolder.getContext().getAuthentication();
            if (auth != null) {
                for (org.springframework.security.core.GrantedAuthority ga : auth.getAuthorities()) {
                    if (ga.getAuthority().equals(UserRole.ROLE_GUARDIAN)) return "GUARDIAN";
                    if (ga.getAuthority().equals(UserRole.ROLE_BACK_OFFICE)) return "BANK_EMPLOYEE";
                    if (ga.getAuthority().equals(UserRole.ROLE_INSTITUTION_ADMIN)) return "INSTITUTION_ADMIN";
                }
            }
        } catch (Exception ignored) {}
        return "BANK_EMPLOYEE";
    }

    private List<ResolvedStudentDto> loadStudentsForGuardian(UUID guardianId) {
        if (studentRepository == null || guardianId == null) {
            return List.of();
        }

        List<Student> students = studentRepository.findByGuardianId(guardianId);
        if (students.isEmpty()) {
            return List.of();
        }

        Map<UUID, String> institutionNames = institutionRepository != null
                ? institutionRepository.findAll().stream().collect(Collectors.toMap(Institution::getId, Institution::getName, (a, b) -> a))
                : Map.of();

        List<ResolvedStudentDto> dtos = new ArrayList<>();
        for (Student s : students) {
            String instName = (s.getInstitutionId() != null && institutionNames.containsKey(s.getInstitutionId()))
                    ? institutionNames.get(s.getInstitutionId())
                    : "Educational Institution";

            dtos.add(new ResolvedStudentDto(
                    s.getId(),
                    s.getFullName(),
                    s.getInstitutionId(),
                    instName
            ));
        }

        return dtos;
    }
}
