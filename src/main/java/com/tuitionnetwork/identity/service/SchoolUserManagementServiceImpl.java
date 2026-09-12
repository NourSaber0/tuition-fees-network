package com.tuitionnetwork.identity.service;

import com.tuitionnetwork.audit.domain.AuditLog;
import com.tuitionnetwork.audit.repository.AuditLogRepository;
import com.tuitionnetwork.common.dto.PageResponse;
import com.tuitionnetwork.identity.domain.InstitutionAdmin;
import com.tuitionnetwork.identity.dto.auth.RolePermissionsDto;
import com.tuitionnetwork.identity.dto.users.CreateSchoolUserRequest;
import com.tuitionnetwork.identity.dto.users.SchoolUserSummaryDto;
import com.tuitionnetwork.identity.dto.users.UpdateSchoolUserRequest;
import com.tuitionnetwork.identity.repository.InstitutionAdminRepository;
import com.tuitionnetwork.identity.security.AuthException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.regex.Pattern;

@Service
public class SchoolUserManagementServiceImpl implements SchoolUserManagementService {

    private static final Pattern EMAIL_PATTERN = Pattern.compile("^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+$");

    private final InstitutionAdminRepository institutionAdminRepository;
    private final AuditLogRepository auditLogRepository;

    @Autowired
    public SchoolUserManagementServiceImpl(
            InstitutionAdminRepository institutionAdminRepository,
            @Autowired(required = false) AuditLogRepository auditLogRepository) {
        this.institutionAdminRepository = institutionAdminRepository;
        this.auditLogRepository = auditLogRepository;
    }

    @Override
    public PageResponse<SchoolUserSummaryDto> listUsers(UUID schoolId, String search, String role, String status, int page, int size) {
        if (schoolId == null) {
            return new PageResponse<>(List.of(), 1, size, 0, 0);
        }

        List<InstitutionAdmin> allForSchool = institutionAdminRepository.findAll().stream()
                .filter(admin -> schoolId.equals(admin.getInstitutionId()))
                .filter(admin -> {
                    if (search != null && !search.isBlank()) {
                        String s = search.trim().toLowerCase();
                        boolean nameMatch = admin.getName() != null && admin.getName().toLowerCase().contains(s);
                        boolean emailMatch = admin.getEmail() != null && admin.getEmail().toLowerCase().contains(s);
                        if (!nameMatch && !emailMatch) return false;
                    }
                    if (role != null && !role.isBlank()) {
                        String filterRole = normalizeRole(role);
                        String adminRole = normalizeRole(admin.getRole());
                        if (!filterRole.equalsIgnoreCase(adminRole)) return false;
                    }
                    if (status != null && !status.isBlank()) {
                        String adminStatus = admin.getStatus() != null ? admin.getStatus() : "Active";
                        if (!status.equalsIgnoreCase(adminStatus)) return false;
                    }
                    return true;
                })
                .sorted(Comparator.comparing((InstitutionAdmin a) -> a.getCreatedAt() != null ? a.getCreatedAt() : LocalDateTime.MIN).reversed())
                .toList();

        int total = allForSchool.size();
        int totalPages = size > 0 ? (int) Math.ceil((double) total / size) : 0;
        int fromIndex = Math.min(page * size, total);
        int toIndex = Math.min(fromIndex + size, total);

        List<SchoolUserSummaryDto> data = allForSchool.subList(fromIndex, toIndex).stream()
                .map(this::toDto)
                .toList();

        return new PageResponse<>(data, page + 1, size, total, totalPages);
    }

    @Override
    public SchoolUserSummaryDto getUser(UUID schoolId, UUID userId) {
        InstitutionAdmin admin = getAdminOrThrow(schoolId, userId);
        return toDto(admin);
    }

    @Override
    @Transactional
    public SchoolUserSummaryDto createUser(UUID schoolId, CreateSchoolUserRequest request, UUID actorId) {
        if (schoolId == null) {
            throw new AuthException(HttpStatus.FORBIDDEN, "missing_school_context", "School context is required");
        }
        if (request == null || request.name() == null || request.name().isBlank()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "validation_failed", "Name is required");
        }
        if (request.email() == null || request.email().isBlank() || !EMAIL_PATTERN.matcher(request.email().trim()).matches()) {
            throw new AuthException(HttpStatus.BAD_REQUEST, "invalid_email", "A valid email address is required");
        }

        String cleanEmail = request.email().trim().toLowerCase();
        if (institutionAdminRepository.findByEmail(cleanEmail).isPresent()) {
            throw new AuthException(HttpStatus.CONFLICT, "email_exists", "An account with this email address already exists");
        }

        String role = validateAndNormalizeRole(request.role());
        String password = (request.password() != null && !request.password().isBlank()) ? request.password() : "Password123!";

        InstitutionAdmin admin = new InstitutionAdmin(
                schoolId,
                request.name().trim(),
                cleanEmail,
                password,
                role
        );
        admin.setStatus("Active");
        admin.setCreatedAt(LocalDateTime.now());
        admin = institutionAdminRepository.save(admin);

        logAudit(actorId, "CREATE_SCHOOL_USER", "Created school user: " + cleanEmail + " (" + role + ") for school: " + schoolId);

        return toDto(admin);
    }

    @Override
    @Transactional
    public SchoolUserSummaryDto updateUser(UUID schoolId, UUID userId, UpdateSchoolUserRequest request, UUID actorId) {
        InstitutionAdmin admin = getAdminOrThrow(schoolId, userId);

        if (request != null) {
            if (request.name() != null && !request.name().isBlank()) {
                admin.setName(request.name().trim());
            }
            if (request.email() != null && !request.email().isBlank()) {
                String cleanEmail = request.email().trim().toLowerCase();
                if (!cleanEmail.equalsIgnoreCase(admin.getEmail())) {
                    if (!EMAIL_PATTERN.matcher(cleanEmail).matches()) {
                        throw new AuthException(HttpStatus.BAD_REQUEST, "invalid_email", "A valid email address is required");
                    }
                    if (institutionAdminRepository.findByEmail(cleanEmail).isPresent()) {
                        throw new AuthException(HttpStatus.CONFLICT, "email_exists", "An account with this email address already exists");
                    }
                    admin.setEmail(cleanEmail);
                }
            }
            if (request.role() != null && !request.role().isBlank()) {
                admin.setRole(validateAndNormalizeRole(request.role()));
            }
        }

        admin = institutionAdminRepository.save(admin);
        logAudit(actorId, "UPDATE_SCHOOL_USER", "Updated school user: " + admin.getEmail());

        return toDto(admin);
    }

    @Override
    @Transactional
    public void deactivateUser(UUID schoolId, UUID userId, UUID actorId) {
        InstitutionAdmin admin = getAdminOrThrow(schoolId, userId);
        admin.setStatus("Inactive");
        institutionAdminRepository.save(admin);
        logAudit(actorId, "DEACTIVATE_SCHOOL_USER", "Deactivated school user: " + admin.getEmail());
    }

    @Override
    @Transactional
    public void activateUser(UUID schoolId, UUID userId, UUID actorId) {
        InstitutionAdmin admin = getAdminOrThrow(schoolId, userId);
        admin.setStatus("Active");
        institutionAdminRepository.save(admin);
        logAudit(actorId, "ACTIVATE_SCHOOL_USER", "Activated school user: " + admin.getEmail());
    }

    @Override
    public List<RolePermissionsDto> getSchoolRoles() {
        return List.of(
                new RolePermissionsDto(
                        "School Admin",
                        List.of("dashboard", "students", "fee-management", "fee-upload", "payments", "reconciliation", "reports", "notifications", "users", "settings")
                ),
                new RolePermissionsDto(
                        "School Finance",
                        List.of("dashboard", "students", "fee-management", "fee-upload", "payments", "reconciliation", "reports", "notifications")
                )
        );
    }

    private InstitutionAdmin getAdminOrThrow(UUID schoolId, UUID userId) {
        InstitutionAdmin admin = institutionAdminRepository.findById(userId)
                .orElseThrow(() -> new AuthException(HttpStatus.NOT_FOUND, "user_not_found", "School user not found"));

        if (schoolId == null || !schoolId.equals(admin.getInstitutionId())) {
            throw new AuthException(HttpStatus.FORBIDDEN, "cross_school_access", "Cannot access user from another school");
        }
        return admin;
    }

    private String validateAndNormalizeRole(String inputRole) {
        if (inputRole == null || inputRole.isBlank()) {
            return "School Finance";
        }
        String normalized = inputRole.trim().toLowerCase().replace("-", " ").replace("_", " ");
        if ("school finance".equals(normalized) || "finance".equals(normalized) || "role school finance".equals(normalized)) {
            return "School Finance";
        }
        if ("school admin".equals(normalized) || "admin".equals(normalized) || "role school admin".equals(normalized)) {
            return "School Admin";
        }
        throw new AuthException(HttpStatus.BAD_REQUEST, "invalid_role", "Role must be School Admin or School Finance");
    }

    private String normalizeRole(String role) {
        if (role == null) return "School Admin";
        String r = role.trim().toLowerCase();
        if (r.contains("finance")) return "School Finance";
        return "School Admin";
    }

    private SchoolUserSummaryDto toDto(InstitutionAdmin admin) {
        return new SchoolUserSummaryDto(
                admin.getId(),
                admin.getName(),
                admin.getEmail(),
                normalizeRole(admin.getRole()),
                admin.getStatus() != null ? admin.getStatus() : "Active",
                admin.getLastLoginAt(),
                admin.getCreatedAt() != null ? admin.getCreatedAt() : LocalDateTime.now()
        );
    }

    private void logAudit(UUID actorId, String action, String description) {
        if (auditLogRepository != null) {
            AuditLog log = new AuditLog(
                    actorId,
                    "SCHOOL_ADMIN",
                    action,
                    description
            );
            auditLogRepository.save(log);
        }
    }
}
